package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.*;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;
import org.truong.gvrp_entry_api.repository.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private final EmailService emailService;
    private final GeometryMapper geometryMapper;
    private final TransactionTemplate transactionTemplate;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Transactional
    public void handleCompletion(EngineCallbackRequest.CompletionCallback callback) {
        try {
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

            if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
                log.warn("⚠️ Job #{} is not in PROCESSING state (current: {}). Ignoring callback.", job.getId(), job.getStatus());
                return;
            }

            Solution solution = saveSolution(job, callback);

            job.setStatus(OptimizationJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setSolution(solution);
            jobRepository.save(job);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        emailService.sendOptimizationSuccessEmail(
                                job.getCreatedBy().getId(),
                                job.getId(),
                                solution.getId());
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to trigger async email: {}", e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            log.error("❌ Failed to process completion callback for job #{}: {}", callback.getJobId(), e.getMessage(), e);
            transactionTemplate.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            );
            transactionTemplate.execute(status -> {
                markJobFailed(callback.getJobId(), e.getMessage());
                return null;
            });
            throw e;
        }
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
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

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
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

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
            List<UnassignedOrder> unassignedList = buildUnassignedOrder(solution, solutionData.getUnassignedOrders(), orderMap);
            solution.getUnassignedOrders().addAll(unassignedList);

            List<Long> unassignedOrderIds = unassignedList.stream().map(uo -> uo.getOrder().getId()).toList();
            orderRepository.updateStatusByIds(unassignedOrderIds, OrderStatus.UNASSIGNED);
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
                    .map(routeData -> buildRoute(
                            solution,
                            routeData,
                            vehicleMap,
                            orderMap
                    ))
                    .collect(Collectors.toList());

            routeRepository.saveAll(routes);

            List<RouteStop> allStops = new ArrayList<>();
            for (int i = 0; i < routes.size(); i++) {
                Route route = routes.get(i);
                EngineCallbackRequest.RouteData routeData = solutionData.getRoutes().get(i);

                if (routeData.getStops() != null) {
                    List<RouteStop> stops = buildStop(route, routeData.getStops(), orderMap);
                    route.getSegments().addAll(stops);
                    allStops.addAll(stops);
                }
            }

            routeStopRepository.saveAll(allStops);
            solution.getRoutes().addAll(routes);

            if (!assignedOrderIds.isEmpty()) {
                orderRepository.updateStatusByIds(assignedOrderIds.stream().toList(), OrderStatus.ON_ROUTE);
            }
        }

//        solution = solutionRepository.save(solution);
        return solution;
    }

    private List<UnassignedOrder> buildUnassignedOrder(Solution solution, List<EngineCallbackRequest.UnassignedOrderData> solutionData, Map<Long, Order> orderMap) {
        return solutionData.stream()
                .map(uaData -> {
                    Order order = orderMap.get(uaData.getOrderId());
                    if (order == null) return null;
                    return UnassignedOrder.builder()
                            .solution(solution)
                            .order(order)
                            .reason(uaData.getReason())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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

    private Route buildRoute(
            Solution solution,
            EngineCallbackRequest.RouteData routeData,
            Map<Long, Vehicle> vehicleMap,
            Map<Long, Order> orderMap
    ) {

        Route route = Route.builder()
                .solution(solution)
                .vehicle(vehicleMap.get(routeData.getVehicleId()))
                .routeOrder(routeData.getRouteOrder() != null ? routeData.getRouteOrder() : 1)
                .distance(routeData.getDistance())
                .serviceTime(routeData.getDuration())
                .co2Emission(routeData.getCo2Emission())
                .orderCount(routeData.getOrderCount())
                .loadUtilization(routeData.getLoadUtilization())
                .segments(new ArrayList<>())
                .build();
        return route;
    }

    private List<RouteStop> buildStop(Route route, List<EngineCallbackRequest.StopData> stopData, Map<Long, Order> orderMap) {
        return stopData.stream()
                .map(stop -> {
                    Order order = null;

                    if ("ORDER".equals(stop.getType()) || "CUSTOMER".equals(stop.getType())) {
                        order = orderMap.get(stop.getOrderId());
                    }

                    if (order == null && ("ORDER".equals(stop.getType())
                            || "CUSTOMER".equals(stop.getType()))) {
                        log.warn("Order not found for stop: {}", stop.getOrderId());
                        return null;
                    }

                    return RouteStop.builder()
                            .route(route)
                            .order(order)
                            .sequenceNumber(stop.getSequenceNumber())
                            .type(mapLocationType(stop.getType()))
                            .locationId(stop.getLocationId())
                            .locationName(stop.getLocationName())
                            .location(geometryMapper.createPoint(stop.getLatitude(), stop.getLongitude()))
                            .arrivalTime(parseTime(stop.getArrivalTime()))
                            .departureTime(parseTime(stop.getDepartureTime()))
                            .serviceTime(stop.getWaitTime())
                            .demand(stop.getDemand())
                            .loadAfter(stop.getLoadAfter())
                            .distanceToNext(stop.getDistanceToNext())
                            .timeToNext(stop.getTimeToNext())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private LocationType mapLocationType(String type) {
        if ("CUSTOMER".equals(type)) {
            return LocationType.ORDER;
        }
        try {
            return LocationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown location type: {}, defaulting to ORDER", type);
            return LocationType.ORDER;
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception e2) {
                log.warn("⚠️  Failed to parse time: {}", timeStr);
                return null;
            }
        }
    }
}