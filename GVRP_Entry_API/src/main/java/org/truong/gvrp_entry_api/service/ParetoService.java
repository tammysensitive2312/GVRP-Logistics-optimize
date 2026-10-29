package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.dto.response.ParetoCandidateDTO;
import org.truong.gvrp_entry_api.dto.response.ParetoFrontierDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.ParetoCandidate;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.ParetoFrontierStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.service.lez.CompiledRestrictions;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pareto frontier read (PAR-007) and apply (PAR-008) for a solution's revision chain.
 *
 * <p>Every lookup is branch-scoped: another branch's solution or candidate answers exactly
 * like a missing id. Applying a candidate never changes order statuses and never calls the
 * engine — it materialises the stored plan into a new {@code PARETO_ALTERNATIVE} revision.
 */
@Service
@RequiredArgsConstructor
public class ParetoService {

    static final String NOT_ON_FRONTIER = "NOT_ON_FRONTIER";
    static final String NO_PLAN = "NO_PLAN";
    static final String ALREADY_ACTIVE = "ALREADY_ACTIVE";
    static final String ORDER_SET_DIFFERS = "ORDER_SET_DIFFERS";

    private final SolutionRepository solutionRepository;
    private final ParetoCandidateRepository paretoCandidateRepository;
    private final RouteCustomizationService routeCustomizationService;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final EnginePlanMaterializer enginePlanMaterializer;
    private final SolutionMapper solutionMapper;
    private final ObjectMapper objectMapper;

    /**
     * LEZ certification (LEZ-001 N6b). Field-injected and optional so the
     * historical unit tests, which build this service without it, keep their
     * exact behaviour; a null field leaves the routes {@code UNCHECKED}.
     */
    @Autowired(required = false)
    private RouteComplianceService routeComplianceService;

    @Transactional(readOnly = true)
    public ParetoFrontierDTO getFrontier(Long solutionId, Long branchId) {
        // Tenant check first: another branch's solution answers exactly like a missing one.
        Solution solution = solutionRepository.findById(solutionId)
                .filter(found -> SolutionService.belongsTo(found, branchId))
                .orElseThrow(SolutionService::solutionNotFound);

        Solution root = rootOf(solution);
        Long latestId = routeCustomizationService.latestRevisionId(root.getId(), branchId);

        if (root.getJob() == null) {
            return ParetoFrontierDTO.builder()
                    .jobId(null)
                    .solutionId(solutionId)
                    .latestSolutionId(latestId)
                    .activeCandidateId(null)
                    .clustered(null)
                    .clusterCount(null)
                    .candidates(List.of())
                    .build();
        }

        Long jobId = root.getJob().getId();
        List<ParetoCandidate> rows =
                paretoCandidateRepository.findByJobIdAndBranchIdOrderByCandidateIndexAsc(jobId, branchId);

        Solution latest = solutionRepository.findWithDetailsById(latestId)
                .orElseThrow(SolutionService::solutionNotFound);

        Long activeCandidateId = latest.getParetoCandidate() != null
                ? latest.getParetoCandidate().getId()
                : null;
        Set<Long> latestAssigned = assignedOrderIds(latest);

        List<ParetoCandidateDTO> candidates = new ArrayList<>();
        for (ParetoCandidate row : rows) {
            candidates.add(toDTO(row, applicability(row, latestAssigned, activeCandidateId)));
        }

        ParetoCandidate first = rows.isEmpty() ? null : rows.get(0);
        return ParetoFrontierDTO.builder()
                .jobId(jobId)
                .solutionId(solutionId)
                .latestSolutionId(latestId)
                .activeCandidateId(activeCandidateId)
                .clustered(first != null ? first.getClustered() : null)
                .clusterCount(first != null ? first.getClusterCount() : null)
                .candidates(candidates)
                .build();
    }

    @Transactional
    public SolutionDetailResponseDTO apply(Long solutionId, Long candidateId, Long branchId) {
        // 1. Base solution, tenant-scoped.
        Solution base = solutionRepository.findWithDetailsById(solutionId)
                .filter(found -> SolutionService.belongsTo(found, branchId))
                .orElseThrow(SolutionService::solutionNotFound);

        // 2. Root of the chain and its job.
        Solution root = rootOf(base);
        if (root.getJob() == null) {
            throw candidateNotFound();
        }
        Long rootJobId = root.getJob().getId();

        // 3. Candidate of this branch and this job.
        ParetoCandidate candidate = paretoCandidateRepository.findByIdAndBranchId(candidateId, branchId)
                .filter(found -> found.getJob() != null
                        && found.getJob().getId() != null
                        && found.getJob().getId().equals(rootJobId))
                .orElseThrow(this::candidateNotFound);

        // 4. The base must be the latest revision.
        Long latestId = routeCustomizationService.latestRevisionId(root.getId(), branchId);
        if (!base.getId().equals(latestId)) {
            throw new RevisionConflictException(
                    "Solution #" + solutionId + " is no longer the latest revision.", latestId);
        }

        // 5. Already active — idempotent, no new revision.
        if (base.getParetoCandidate() != null
                && base.getParetoCandidate().getId() != null
                && base.getParetoCandidate().getId().equals(candidateId)) {
            return solutionMapper.toDTO(base);
        }

        // 6. Only a FRONTIER candidate with a stored plan can be applied.
        if (candidate.getFrontierStatus() != ParetoFrontierStatus.FRONTIER
                || candidate.getPlanJson() == null) {
            throw new UnprocessablePlanException(
                    "Candidate #" + candidateId + " is not an applicable frontier plan.");
        }

        // 7. The plan must serve the same order set as the current revision.
        EnginePlanMaterializer.PlanPayload plan = readPlan(candidate.getPlanJson());
        Set<Long> planAssigned = collectAssignedOrderIds(plan.getRoutes());
        if (!planAssigned.equals(assignedOrderIds(base))) {
            throw new UnprocessablePlanException(
                    "Candidate #" + candidateId + " serves a different order set than the current revision.");
        }

        // 8. Orders and vehicles must exist and belong to this branch.
        Map<Long, Order> orderMap = loadOrders(plan, branchId);
        Map<Long, Vehicle> vehicleMap = loadVehicles(plan, branchId);

        // 9. Materialise the revision from the stored plan.
        Solution revision = Solution.builder()
                .branch(base.getBranch())
                .parentSolution(base)
                .job(null)
                .type(SolutionType.PARETO_ALTERNATIVE)
                .paretoCandidate(candidate)
                .status(candidate.getUnservedOrders() != null && candidate.getUnservedOrders() > 0
                        ? SolutionStatus.PARTIAL_SUCCESS : SolutionStatus.SUCCESS)
                .totalCost(candidate.getTotalCost())
                .totalDistance(candidate.getTotalDistance())
                .totalCO2(candidate.getTotalCO2())
                .totalTime(candidate.getTotalTime())
                .totalVehiclesUsed(candidate.getTotalVehiclesUsed())
                .servedOrders(candidate.getServedOrders())
                .unservedOrders(candidate.getUnservedOrders())
                .createdAt(LocalDateTime.now())
                .build();

        List<Route> routes = new ArrayList<>();
        for (EngineCallbackRequest.RouteData routeData : Optional.ofNullable(plan.getRoutes()).orElse(List.of())) {
            Route route = enginePlanMaterializer.buildRoute(revision, routeData, vehicleMap, orderMap);
            if (routeData.getStops() != null) {
                route.getSegments().addAll(
                        enginePlanMaterializer.buildStop(route, routeData.getStops(), orderMap));
            }
            routes.add(route);
        }
        revision.getRoutes().addAll(routes);

        // LEZ certification with the CURRENT rules (LEZ-001 N6b): applying a stored
        // candidate must not certify against a snapshot that may be older than now.
        certifyRoutes(base, routes);

        if (plan.getUnassignedOrders() != null && !plan.getUnassignedOrders().isEmpty()) {
            revision.getUnassignedOrders().addAll(
                    enginePlanMaterializer.buildUnassignedOrder(
                            revision, plan.getUnassignedOrders(), orderMap));
        }

        // 10. Order statuses are intentionally untouched: switching plans is a view/apply
        // operation and the order state machine has no ON_ROUTE <-> UNASSIGNED edge.
        return solutionMapper.toDTO(solutionRepository.save(revision));
    }

    // --------------------------------------------------

    private Solution rootOf(Solution solution) {
        Solution current = solution;
        while (current.getParentSolution() != null) {
            current = current.getParentSolution();
        }
        return current;
    }

    private Applicability applicability(ParetoCandidate candidate, Set<Long> latestAssigned,
                                        Long activeCandidateId) {
        if (candidate.getFrontierStatus() != ParetoFrontierStatus.FRONTIER) {
            return new Applicability(false, NOT_ON_FRONTIER);
        }
        if (candidate.getPlanJson() == null) {
            return new Applicability(false, NO_PLAN);
        }
        if (activeCandidateId != null && activeCandidateId.equals(candidate.getId())) {
            return new Applicability(false, ALREADY_ACTIVE);
        }
        Set<Long> planAssigned = tryPlanOrderIds(candidate.getPlanJson());
        if (planAssigned == null || !planAssigned.equals(latestAssigned)) {
            return new Applicability(false, ORDER_SET_DIFFERS);
        }
        return new Applicability(true, null);
    }

    private ParetoCandidateDTO toDTO(ParetoCandidate candidate, Applicability applicability) {
        return ParetoCandidateDTO.builder()
                .id(candidate.getId())
                .index(candidate.getCandidateIndex())
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
                .frontierStatus(candidate.getFrontierStatus() != null
                        ? candidate.getFrontierStatus().name() : null)
                .engineSelected(candidate.getEngineSelected())
                .applicable(applicability.applicable())
                .notApplicableReason(applicability.reason())
                .build();
    }

    /** Order ids assigned through ORDER/CUSTOMER stops of a solution's routes. */
    private Set<Long> assignedOrderIds(Solution solution) {
        Set<Long> ids = new HashSet<>();
        if (solution.getRoutes() == null) {
            return ids;
        }
        for (Route route : solution.getRoutes()) {
            if (route == null || route.getSegments() == null) {
                continue;
            }
            for (RouteStop stop : route.getSegments()) {
                if (stop != null && stop.getType() == LocationType.ORDER && stop.getOrder() != null) {
                    ids.add(stop.getOrder().getId());
                }
            }
        }
        return ids;
    }

    private Set<Long> collectAssignedOrderIds(List<EngineCallbackRequest.RouteData> routes) {
        Set<Long> ids = new HashSet<>();
        if (routes == null) {
            return ids;
        }
        for (EngineCallbackRequest.RouteData route : routes) {
            if (route == null || route.getStops() == null) {
                continue;
            }
            for (EngineCallbackRequest.StopData stop : route.getStops()) {
                if (stop == null) {
                    continue;
                }
                String type = stop.getType();
                if (("ORDER".equals(type) || "CUSTOMER".equals(type)) && stop.getOrderId() != null) {
                    ids.add(stop.getOrderId());
                }
            }
        }
        return ids;
    }

    private Set<Long> tryPlanOrderIds(String planJson) {
        try {
            return collectAssignedOrderIds(readPlan(planJson).getRoutes());
        } catch (UnprocessablePlanException e) {
            return null;
        }
    }

    private EnginePlanMaterializer.PlanPayload readPlan(String planJson) {
        try {
            return objectMapper.readValue(planJson, EnginePlanMaterializer.PlanPayload.class);
        } catch (JsonProcessingException e) {
            throw new UnprocessablePlanException(
                    "Stored Pareto plan is not readable: " + e.getMessage());
        }
    }

    private Map<Long, Order> loadOrders(EnginePlanMaterializer.PlanPayload plan, Long branchId) {
        Set<Long> orderIds = new LinkedHashSet<>(collectAssignedOrderIds(plan.getRoutes()));
        if (plan.getUnassignedOrders() != null) {
            for (EngineCallbackRequest.UnassignedOrderData unassigned : plan.getUnassignedOrders()) {
                if (unassigned != null && unassigned.getOrderId() != null) {
                    orderIds.add(unassigned.getOrderId());
                }
            }
        }
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Order> orderMap = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
        for (Long orderId : orderIds) {
            Order order = orderMap.get(orderId);
            if (order == null || order.getBranch() == null
                    || !branchId.equals(order.getBranch().getId())) {
                throw new UnprocessablePlanException(
                        "Order " + orderId + " is not part of this branch's plan.");
            }
        }
        return orderMap;
    }

    private Map<Long, Vehicle> loadVehicles(EnginePlanMaterializer.PlanPayload plan, Long branchId) {
        Set<Long> vehicleIds = new LinkedHashSet<>();
        if (plan.getRoutes() != null) {
            for (EngineCallbackRequest.RouteData route : plan.getRoutes()) {
                if (route != null && route.getVehicleId() != null) {
                    vehicleIds.add(route.getVehicleId());
                }
            }
        }
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Vehicle> vehicleMap = vehicleRepository.findAllById(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, vehicle -> vehicle));
        for (Long vehicleId : vehicleIds) {
            Vehicle vehicle = vehicleMap.get(vehicleId);
            if (vehicle == null || vehicle.getFleet() == null
                    || vehicle.getFleet().getBranch() == null
                    || !branchId.equals(vehicle.getFleet().getBranch().getId())) {
                throw new UnprocessablePlanException(
                        "Vehicle " + vehicleId + " is not part of this branch's fleet.");
            }
        }
        return vehicleMap;
    }

    /**
     * Certifies the routes of a newly applied revision against the branch's
     * <em>current</em> restrictions (LEZ-001 N6b): the stored candidate plan may
     * be older than a permit or a rule change, so the job snapshot is not used
     * here. The compilation is scoped to the base solution's orders and vehicles,
     * the same scope the read APIs use, so the stored hash stays comparable.
     * Runs before the revision is saved, so the verdict is persisted with it.
     */
    private void certifyRoutes(Solution base, List<Route> routes) {
        if (routeComplianceService == null || base == null || routes == null || routes.isEmpty()) {
            return;
        }
        CompiledRestrictions restrictions = routeComplianceService.currentFor(base);
        if (restrictions == null) {
            // Mixed dates cannot be certified for one day; D4 only stops new jobs.
            return;
        }
        for (Route route : routes) {
            routeComplianceService.certify(route,
                    routeComplianceService.check(route, restrictions), restrictions);
        }
    }

    private DataInvalidException candidateNotFound() {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                        .resource(AppConstant.PARETO_CANDIDATE)
                        .build()));
    }

    private record Applicability(boolean applicable, String reason) {
    }
}
