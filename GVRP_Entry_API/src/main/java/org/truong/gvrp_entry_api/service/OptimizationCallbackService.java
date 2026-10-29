package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.*;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.JobNotReadyException;
import org.truong.gvrp_entry_api.repository.*;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;
import org.truong.gvrp_entry_api.service.event.JobCompletionEvent;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationCallbackService {

    private final OptimizationJobRepository jobRepository;
    private final SolutionRepository solutionRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final ParetoCandidateRepository paretoCandidateRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final EnginePlanMaterializer enginePlanMaterializer;
    private final ApplicationEventPublisher eventPublisher;
    private final JobStatusUpdater jobStatusUpdater;
    private final OrderStatusTransitionService orderStatusTransitionService;

    /**
     * LEZ certification (LEZ-001). Field-injected and optional so the P1a/earlier
     * unit tests, which construct this service through @InjectMocks with the
     * historical collaborators, keep their exact behaviour.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.truong.gvrp_entry_api.service.lez.RouteComplianceService routeComplianceService;

    @Transactional
    public void handleCompletion(EngineCallbackRequest.CompletionCallback callback) {
        try {
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(() -> new DataInvalidException(
                            List.of(
                                    ErrorDetail.builder()
                                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                            .resource(AppConstant.JOB)
                                            .build()
                            ))
                    );

            // ===== Idempotency =====
            // Engine có outbox và sẽ phát lại cùng một payload trong 2 tình huống:
            //   (a) lần trước Entry trả 5xx;
            //   (b) lần trước Entry xử lý XONG nhưng phản hồi về sau read timeout —
            //       engine tưởng thất bại dù DB đã có dữ liệu (đúng ca job #23).
            // Trường hợp (b) là lý do bắt buộc phải có chốt này: không có nó thì mỗi
            // lần retry sẽ nhân đôi solution/routes/route_stops.
            // Trả về lặng lẽ (200) để engine đánh dấu đã giao và ngừng retry.
            if (solutionRepository.findByJobId(job.getId()).isPresent()) {
                log.warn("⚠️ Job #{} đã có solution — bỏ qua callback lặp (idempotent).", job.getId());
                return;
            }

            // Chấp nhận cả FAILED, không chỉ PROCESSING.
            // Khối catch cuối phương thức này đánh dấu job FAILED khi xử lý lỗi. Nếu
            // guard chỉ cho PROCESSING thì bản phát lại vĩnh viễn không vào được và
            // outbox thành vô dụng — đúng kịch bản job #21 (tràn total_cost).
            if (job.getStatus() != OptimizationJobStatus.PROCESSING
                    && job.getStatus() != OptimizationJobStatus.FAILED) {

                // PENDING = "chưa tới lúc", KHÔNG phải "bỏ qua vĩnh viễn".
                // Race: submitJob tạo job ở PENDING và chỉ set PROCESSING sau khi
                // engine trả 202; job nhỏ giải xong trước thời điểm đó nên callback
                // về khi DB còn PENDING. Trả 200 ở đây thì engine markSent và không
                // bao giờ retry → kết quả vĩnh viễn không vào DB dù log báo thành
                // công. Ném để thành 503, engine giữ payload trong pending/ và gửi
                // lại sau ~60s, lúc đó job đã sang PROCESSING.
                if (job.getStatus() == OptimizationJobStatus.PENDING) {
                    throw new JobNotReadyException(
                            "Job #" + job.getId() + " còn PENDING — engine gửi callback quá sớm");
                }

                // COMPLETED / CANCELLED: trạng thái cuối, bỏ qua thật.
                log.warn("⚠️ Job #{} ở trạng thái {} — bỏ qua callback.", job.getId(), job.getStatus());
                return;
            }

            Solution solution = saveSolution(job, callback);

            job.setStatus(OptimizationJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setSolution(solution);
            jobRepository.save(job);
            eventPublisher.publishEvent(buildCompletionEvent(job, solution));

        } catch (JobNotReadyException e) {
            // KHÔNG markAsFailed: job không hỏng, chỉ là callback tới sớm. Đánh dấu
            // FAILED ở đây sẽ ghi đè trạng thái đúng và làm nhiễu báo cáo. Chỉ để nó
            // nổi lên GlobalExceptionHandler thành 503 cho engine retry.
            // Phải bắt TRƯỚC catch(Exception) bên dưới, nếu không sẽ bị nuốt mất.
            throw e;

        } catch (Exception e) {
            log.error("❌ Failed to process completion callback for job #{}: {}", callback.getJobId(), e.getMessage(), e);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    jobStatusUpdater.markAsFailed(callback.getJobId(), e.getMessage());
                }
            });
            throw e;
        }
    }

    private JobCompletionEvent buildCompletionEvent(OptimizationJob job, Solution solution) {
        return new JobCompletionEvent(
                this,
                job.getId(),
                job.getBranch().getId(),
                job.getBranch().getName(),
                job.getCreatedBy().getId(),
                job.getCreatedBy().getSlackUserId(),
                job.getBranch().getBranchWebhookURL(),
                solution.getId(),
                solution.getStatus(),
                solution.getTotalDistance() != null ? solution.getTotalDistance().doubleValue() : 0,
                solution.getTotalCost() != null ? solution.getTotalCost().doubleValue() : 0,
                solution.getTotalCO2() != null ? solution.getTotalCO2().doubleValue() : 0,
                solution.getTotalVehiclesUsed() != null ? solution.getTotalVehiclesUsed() : 0,
                solution.getServedOrders() != null ? solution.getServedOrders() : 0,
                solution.getUnservedOrders() != null ? solution.getUnservedOrders() : 0,
                job.getCompletedAt(),
                null
        );
    }

    private void markJobFailed(Long jobId, String errorMessage) {
        try {
            OptimizationJob job = jobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(OptimizationJobStatus.FAILED);
                job.setErrorMessage("Internal error: " + errorMessage);
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        } catch (Exception e) {
            log.error("❌ Failed to mark job as FAILED: {}", e.getMessage());
        }
    }

    /**
     * Handle optimization failure callback
     * <p>
     * 🔧 CHANGES:
     * - Added error handling
     * - Made email sending optional
     */
    @Transactional
    public void handleFailure(EngineCallbackRequest.FailureCallback callback) {

        log.warn("📥 Processing failure callback for job #{}", callback.getJobId());

        try {
            // 1. Load job
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(
                            () -> new DataInvalidException(
                                    List.of(
                                            ErrorDetail.builder()
                                                    .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                                    .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                                    .resource(AppConstant.JOB)
                                                    .build()
                                    ))
                    );

            // 2. Validate job can receive failure
            if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
                log.warn("⚠️  Job #{} is not in PROCESSING state (current: {}). Ignoring callback.",
                        job.getId(), job.getStatus());
                return;
            }

            // 3. Update job
            job.setStatus(OptimizationJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());

            job.setErrorMessage(callback.getErrorMessage());
            jobRepository.save(job);

            log.info("✅ Job #{} marked as FAILED: {}", job.getId(), callback.getErrorMessage());

            // 4. Send failure email
            // 🔧 CHANGE: Made optional
            try {
                Exception error = new RuntimeException(callback.getErrorMessage());
                emailService.sendOptimizationFailureEmail(job.getCreatedBy().getId(), job.getId(), error);
            } catch (Exception e) {
                log.warn("⚠️  Failed to send failure email: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Failed to process failure callback for job #{}: {}",
                    callback.getJobId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handle cancellation callback — Engine báo job đã dừng theo yêu cầu.
     * <p>
     * Idempotent: nếu job đã ở trạng thái kết thúc (thường là CANCELLED do
     * cancelJob() set trước khi gọi engine), callback này bị bỏ qua — tránh
     * ghi đè trạng thái đã đúng.
     */
    @Transactional
    public void handleCancellation(EngineCallbackRequest.CancellationCallback callback) {

        log.warn("📥 Processing cancellation callback for job #{}", callback.getJobId());

        try {
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(
                            () -> new DataInvalidException(
                                    List.of(
                                            ErrorDetail.builder()
                                                    .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                                    .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                                    .resource(AppConstant.JOB)
                                                    .build()
                                    ))
                    );

            // Chỉ set CANCELLED nếu job còn đang chạy — nếu đã CANCELLED/COMPLETED/FAILED thì bỏ qua.
            if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
                log.warn("⚠️  Job #{} không ở trạng thái PROCESSING (hiện: {}). Bỏ qua cancellation callback.",
                        job.getId(), job.getStatus());
                return;
            }

            job.setStatus(OptimizationJobStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(callback.getReason() != null ? callback.getReason() : "Cancelled");
            jobRepository.save(job);

            log.info("✅ Job #{} marked as CANCELLED", job.getId());

        } catch (Exception e) {
            log.error("❌ Failed to process cancellation callback for job #{}: {}",
                    callback.getJobId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handle progress update callback (optional)
     * <p>
     * 🔧 CHANGES:
     * - Added error handling
     */
    @Transactional
    public void handleProgressUpdate(EngineCallbackRequest.ProgressCallback callback) {

        try {
            // 1. Load job
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(

                    );

            // 2. Update progress (if you have progress field)
            // 🔧 TODO: Add progress field to OptimizationJob entity if needed
            // job.setProgress(callback.getProgress());
            // jobRepository.save(job);

            log.debug("📊 Job #{} progress: {}%", job.getId(), callback.getProgress());

            // 3. Optional: Send WebSocket notification to frontend
            // 🔧 TODO: Implement WebSocket if needed
            // webSocketService.sendProgressUpdate(job.getBranch().getId(), callback);

        } catch (Exception e) {
            log.warn("⚠️  Failed to process progress update for job #{}: {}",
                    callback.getJobId(), e.getMessage());
            // Don't throw - progress updates are optional
        }
    }


    private Solution  saveSolution(OptimizationJob job, EngineCallbackRequest.CompletionCallback callback) {

        EngineCallbackRequest.SolutionData solutionData = callback.getSolution();
        log.debug("💾 Saving solution for job #{}...", job.getId());

        Solution solution = solutionRepository.findByJobId(job.getId())
                .orElseGet(() -> Solution.builder()
                        .job(job)
                        .branch(job.getBranch())
                        .createdAt(LocalDateTime.now())
                        .build());

        solution.setType(SolutionType.ENGINE_GENERATED);
        solution.setStatus(calculateSolutionStatus(callback));
        solution.setTotalDistance(solutionData.getTotalDistance());
        solution.setTotalCost(solutionData.getTotalCost());
        solution.setTotalCO2(solutionData.getTotalCO2());
        solution.setTotalTime(solutionData.getTotalTime());
        solution.setTotalVehiclesUsed(solutionData.getTotalVehiclesUsed());
        solution.setServedOrders(solutionData.getServedOrders());
        solution.setUnservedOrders(solutionData.getUnservedOrders());
        solution.setErrorMessage(null);

        if (solution.getRoutes() == null) {
            solution.setRoutes(new ArrayList<>());
        } else {
            solution.getRoutes().clear();
        }

        Set<Long> orderIds = Stream.concat(
                Optional.ofNullable(solutionData.getRoutes()).orElse(Collections.emptyList()).stream()
                        .flatMap(route -> Optional.ofNullable(route.getStops()).orElse(Collections.emptyList()).stream())
                        .map(EngineCallbackRequest.StopData::getOrderId),
                Optional.ofNullable(solutionData.getUnassignedOrders()).orElse(Collections.emptyList()).stream()
                        .map(EngineCallbackRequest.UnassignedOrderData::getOrderId)
        ).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Order> orders = orderRepository.findAllById(orderIds);
        Map<Long, Order> orderMap = orders.stream().collect(Collectors.toMap(Order::getId, o -> o));

        if (solutionData.getUnassignedOrders() != null && !solutionData.getUnassignedOrders().isEmpty()) {
            List<UnassignedOrder> unassignedList = enginePlanMaterializer.buildUnassignedOrder(solution, solutionData.getUnassignedOrders(), orderMap);
            applyLezUnassignedReasons(job, unassignedList);
            solution.getUnassignedOrders().addAll(unassignedList);
            solution.getUnassignedOrders().addAll(unassignedList);

            if (!unassignedList.isEmpty()) {
                List<Order> unassignedOrders = unassignedList.stream()
                        .map(UnassignedOrder::getOrder)
                        .toList();
                orderStatusTransitionService.bulkTransition(
                        unassignedOrders, OrderStatus.UNASSIGNED);
            }
        }

        Set<Long> assignedOrderIds = new HashSet<>();
        if (solutionData.getRoutes() != null) {
            Set<Long> vehicleIds = solutionData.getRoutes().stream()
                    .filter(Objects::nonNull)
                    .map(EngineCallbackRequest.RouteData::getVehicleId)
                    .collect(Collectors.toSet());

            Map<Long, Vehicle> vehicleMap = vehicleRepository.findAllById(vehicleIds)
                    .stream()
                    .collect(Collectors.toMap(Vehicle::getId, v -> v));

            for (EngineCallbackRequest.RouteData routeData : solutionData.getRoutes()) {
                if (routeData.getStops() != null) {
                    assignedOrderIds.addAll(routeData.getStops().stream()
                            .map(EngineCallbackRequest.StopData::getOrderId)
                            .filter(Objects::nonNull)
                            .toList());
                }
            }

            List<Route> routes = solutionData.getRoutes().stream()
                    .map(routeData -> enginePlanMaterializer.buildRoute(
                            solution,
                            routeData,
                            vehicleMap,
                            orderMap
                    ))
                    .toList();

//            routeRepository.saveAll(routes);

            List<RouteStop> allStops = new ArrayList<>();
            for (int i = 0; i < routes.size(); i++) {
                Route route = routes.get(i);
                EngineCallbackRequest.RouteData routeData = solutionData.getRoutes().get(i);

                if (routeData.getStops() != null) {
                    List<RouteStop> stops = enginePlanMaterializer.buildStop(route, routeData.getStops(), orderMap);
                    route.getSegments().addAll(stops);
                    allStops.addAll(stops);
                }
            }

//            routeStopRepository.saveAll(allStops);
            certifyRoutes(job, routes, orderMap);
            solution.getRoutes().addAll(routes);

            if (!assignedOrderIds.isEmpty()) {
                List<Order> assignedOrders = orders.stream()
                        .filter(o -> assignedOrderIds.contains(o.getId()))
                        .toList();
                orderStatusTransitionService.bulkTransition(
                        assignedOrders, OrderStatus.ON_ROUTE);
            }
        }

        // Pareto candidates (PARETO-001): persisted branch-scoped and linked to the
        // engine-selected row; order statuses above are driven by the top-level
        // routes/unassigned only, so this does not change single-objective behaviour.
        ParetoCandidate engineSelected = saveParetoCandidates(job, solutionData.getPareto());
        if (engineSelected != null) {
            solution.setParetoCandidate(engineSelected);
        }

        return solutionRepository.save(solution);
    }

    /**
     * Certifies every materialised route against the job snapshot (LEZ-001 N6a).
     * The snapshot is what the solver ran with, so a template changed after the
     * job was submitted cannot retroactively rewrite this result.
     *
     * <p>The stored {@code lez_ruleset_hash} is the hash of a <em>solution-scoped</em>
     * compile taken now (same orders and vehicles the read APIs use), not the
     * snapshot hash: the snapshot covers the job's full vehicle set, so comparing
     * it later would mark every route {@code STALE} as soon as one vehicle ended
     * up unused.
     */
    private void certifyRoutes(OptimizationJob job, List<Route> routes, Map<Long, Order> orderMap) {
        if (routeComplianceService == null || routes == null || routes.isEmpty()) {
            return;
        }
        org.truong.gvrp_entry_api.service.lez.CompiledRestrictions verdict =
                routeComplianceService.readSnapshot(job.getLezSnapshot());
        if (verdict == null) {
            return;
        }
        org.truong.gvrp_entry_api.service.lez.CompiledRestrictions forHash =
                currentScope(job, routes, orderMap);
        for (Route route : routes) {
            List<org.truong.gvrp_entry_api.service.lez.ComplianceChecker.Violation> violations =
                    routeComplianceService.check(route, verdict);
            routeComplianceService.certify(route, violations, forHash != null ? forHash : verdict);
        }
    }

    /** Branch, date, orders and vehicles the read APIs will use to recompute the hash. */
    private org.truong.gvrp_entry_api.service.lez.CompiledRestrictions currentScope(
            OptimizationJob job, List<Route> routes, Map<Long, Order> orderMap) {
        if (job.getBranch() == null || orderMap == null || orderMap.isEmpty()) {
            return null;
        }
        java.time.LocalDate serviceDate =
                org.truong.gvrp_entry_api.service.lez.RouteComplianceService
                        .serviceDateOf(orderMap.values());
        if (serviceDate == null) {
            return null;
        }
        List<Vehicle> vehicles = routes.stream()
                .map(Route::getVehicle)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Vehicle::getId, vehicle -> vehicle, (a, b) -> a))
                .values().stream().toList();
        return routeComplianceService.compileFor(
                job.getBranch().getId(), serviceDate, orderMap.values(), vehicles);
    }

    /**
     * Marks unassigned orders that no vehicle in the request could serve because of
     * an LEZ ban (design §16.2-2). Orders outside every zone keep the engine reason.
     */
    private void applyLezUnassignedReasons(OptimizationJob job, List<UnassignedOrder> unassigned) {
        if (routeComplianceService == null || unassigned == null || unassigned.isEmpty()) {
            return;
        }
        org.truong.gvrp_entry_api.service.lez.CompiledRestrictions restrictions =
                routeComplianceService.readSnapshot(job.getLezSnapshot());
        if (restrictions == null || !restrictions.hasZones()) {
            return;
        }
        for (UnassignedOrder row : unassigned) {
            Long orderId = row.getOrder() != null ? row.getOrder().getId() : null;
            if (org.truong.gvrp_entry_api.service.lez.RouteComplianceService
                    .noCompliantVehicle(restrictions, orderId)) {
                row.setReasonCode(org.truong.gvrp_entry_api.service.lez.RouteComplianceService.LEZ_NO_COMPLIANT_VEHICLE);
                row.setReason(org.truong.gvrp_entry_api.service.lez.RouteComplianceService
                        .noCompliantVehicleReason(restrictions, orderId));
            }
        }
    }


    /**
     * Persist every Pareto candidate of a job and return the engine-selected row
     * (null when the callback carries no Pareto block). Validation failures raise
     * {@link DataInvalidException} so the existing catch marks the job FAILED and
     * the engine moves the payload to {@code poison/}.
     */
    private ParetoCandidate saveParetoCandidates(OptimizationJob job, EngineCallbackRequest.ParetoData pareto) {
        if (pareto == null || pareto.getCandidates() == null || pareto.getCandidates().isEmpty()) {
            return null;
        }

        List<EngineCallbackRequest.ParetoCandidateData> candidates = pareto.getCandidates();
        validateParetoCandidates(candidates);

        boolean clustered = Boolean.TRUE.equals(pareto.getClustered());
        ParetoCandidate selected = null;
        List<ParetoCandidate> rows = new ArrayList<>();

        for (EngineCallbackRequest.ParetoCandidateData candidate : candidates) {
            ParetoFrontierStatus status = ParetoFrontierStatus.valueOf(candidate.getFrontierStatus());
            boolean isFrontier = status == ParetoFrontierStatus.FRONTIER;
            boolean isSelected = Boolean.TRUE.equals(candidate.getSelected());

            ParetoCandidate row = ParetoCandidate.builder()
                    .job(job)
                    .branch(job.getBranch())
                    .candidateIndex(candidate.getIndex())
                    .label(candidate.getLabel())
                    .costWeight(candidate.getCostWeight())
                    .co2Weight(candidate.getCo2Weight())
                    .totalCost(candidate.getTotalCost())
                    .totalDistance(candidate.getTotalDistance())
                    .totalCO2(candidate.getTotalCO2())
                    .totalTime(candidate.getTotalTime())
                    .totalVehiclesUsed(candidate.getTotalVehiclesUsed())
                    .servedOrders(candidate.getServedOrders())
                    .unservedOrders(candidate.getUnservedOrders())
                    .frontierStatus(status)
                    .engineSelected(isSelected)
                    .clustered(clustered)
                    .clusterCount(pareto.getClusterCount())
                    .planJson(isFrontier
                            ? serializePlan(candidate.getRoutes(), candidate.getUnassignedOrders())
                            : null)
                    .createdAt(LocalDateTime.now())
                    .build();

            if (isSelected) {
                selected = row;
            }
            rows.add(row);
        }

        paretoCandidateRepository.saveAll(rows);
        return selected;
    }

    private void validateParetoCandidates(List<EngineCallbackRequest.ParetoCandidateData> candidates) {
        Set<Integer> indices = new HashSet<>();
        int selectedCount = 0;
        EngineCallbackRequest.ParetoCandidateData selected = null;

        for (EngineCallbackRequest.ParetoCandidateData candidate : candidates) {
            if (candidate.getIndex() == null || !indices.add(candidate.getIndex())) {
                throw paretoInvalid("Pareto candidate indices must be present and unique.");
            }
            if (candidate.getFrontierStatus() == null) {
                throw paretoInvalid("Every Pareto candidate needs a frontier_status.");
            }
            try {
                ParetoFrontierStatus.valueOf(candidate.getFrontierStatus());
            } catch (IllegalArgumentException e) {
                throw paretoInvalid("Unknown frontier_status: " + candidate.getFrontierStatus());
            }
            if (Boolean.TRUE.equals(candidate.getSelected())) {
                selectedCount++;
                selected = candidate;
            }
        }

        if (selectedCount != 1) {
            throw paretoInvalid("Exactly one Pareto candidate must be marked selected (found "
                    + selectedCount + ").");
        }
        if (!ParetoFrontierStatus.FRONTIER.name().equals(selected.getFrontierStatus())) {
            throw paretoInvalid("The selected Pareto candidate must be on the FRONTIER.");
        }
    }

    private String serializePlan(List<EngineCallbackRequest.RouteData> routes,
                                 List<EngineCallbackRequest.UnassignedOrderData> unassignedOrders) {
        try {
            return objectMapper.writeValueAsString(
                    new EnginePlanMaterializer.PlanPayload(routes, unassignedOrders));
        } catch (JsonProcessingException e) {
            throw paretoInvalid("Failed to serialize Pareto plan: " + e.getMessage());
        }
    }

    private DataInvalidException paretoInvalid(String message) {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(message)
                        .build()));
    }

    private SolutionStatus calculateSolutionStatus(EngineCallbackRequest.CompletionCallback callback) {

        EngineCallbackRequest.SolutionData solutionData = callback.getSolution();

        Integer servedOrders = solutionData.getServedOrders();
        Integer unservedOrders = solutionData.getUnservedOrders();
        List<EngineCallbackRequest.RouteData> routes = solutionData.getRoutes();

        if (servedOrders == null || unservedOrders == null || routes == null) {
            return SolutionStatus.INFEASIBLE;
        }

        if (routes.isEmpty() || servedOrders == 0) {
            return SolutionStatus.INFEASIBLE;
        }

        if (servedOrders > 0 && unservedOrders == 0) {
            return SolutionStatus.SUCCESS;
        }

        if (servedOrders > 0 && unservedOrders > 0) {
            return SolutionStatus.PARTIAL_SUCCESS;
        }

        return SolutionStatus.INFEASIBLE;
    }
}