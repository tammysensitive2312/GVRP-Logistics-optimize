package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.CustomizeRoutesRequest;
import org.truong.gvrp_entry_api.dto.request.EngineDepotDTO;
import org.truong.gvrp_entry_api.dto.request.EngineEvaluateRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineOrderDTO;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleTypeDTO;
import org.truong.gvrp_entry_api.dto.request.RouteSequenceRequest;
import org.truong.gvrp_entry_api.dto.response.EngineEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.EvaluateTotalsDTO;
import org.truong.gvrp_entry_api.dto.response.RouteDetailResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteViolationDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.dto.response.StopDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.entity.enums.VehicleSkill;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dispatcher route customization (reorder within a route, move orders between routes).
 *
 * <p>Preview ({@link #evaluate}) never persists. Saving ({@link #saveRevision}) always
 * re-evaluates server-side and stores a NEW solution revision; the base solution is
 * never modified and order statuses are never changed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteCustomizationService {

    private final SolutionRepository solutionRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderMapper orderMapper;
    private final DepotMapper depotMapper;
    private final VehicleTypeMapper vehicleTypeMapper;
    private final VehicleFeaturesService vehicleFeaturesService;
    private final GeometryMapper geometryMapper;
    private final SolutionMapper solutionMapper;
    private final EngineApiClient engineApiClient;
    @Transactional(readOnly = true)
    public RouteEvaluateResponseDTO evaluate(Long baseSolutionId, CustomizeRoutesRequest request) {
        ValidatedPlan plan = validateAndPrepare(baseSolutionId, request);
        EngineEvaluateResponseDTO engine = engineApiClient.evaluateRoutes(plan.engineRequest());
        return toPreview(plan, engine);
    }

    /** Id of the newest revision in the chain (the id itself when unedited). */
    @Transactional(readOnly = true)
    public Long latestRevisionId(Long solutionId) {
        Solution base = solutionRepository.findById(solutionId)
                .orElseThrow(() -> notFound(AppConstant.SOLUTION));
        return latestRevision(base).getId();
    }

    @Transactional
    public SolutionDetailResponseDTO saveRevision(Long baseSolutionId, CustomizeRoutesRequest request) {
        ValidatedPlan plan = validateAndPrepare(baseSolutionId, request);

        Solution latest = latestRevision(plan.base());
        if (!latest.getId().equals(plan.base().getId())) {
            throw new RevisionConflictException(
                    "Solution #" + baseSolutionId + " is no longer the latest revision.",
                    latest.getId());
        }

        EngineEvaluateResponseDTO engine = engineApiClient.evaluateRoutes(plan.engineRequest());
        List<RouteViolationDTO> violations = mergeViolations(plan.entryViolations(), engine);
        List<RouteViolationDTO> hard = violations.stream()
                .filter(v -> !"UNASSIGNED_INCREASE".equals(v.getCode()))
                .toList();
        if (!engine.isFeasible() || !hard.isEmpty()) {
            String summary = hard.stream()
                    .map(v -> v.getCode() + " route=" + v.getRouteIndex() + " order=" + v.getOrderId())
                    .collect(Collectors.joining("; "));
            throw new UnprocessablePlanException("Route plan violates hard constraints: " + summary);
        }

        Solution revision = persistRevision(plan, engine);
        return solutionMapper.toDTO(revision);
    }

    // --------------------------------------------------

    /** Shared validation + engine-payload building for preview and save. */
    private ValidatedPlan validateAndPrepare(Long baseSolutionId, CustomizeRoutesRequest request) {
        Solution base = solutionRepository.findWithDetailsById(baseSolutionId)
                .orElseThrow(() -> notFound(AppConstant.SOLUTION));

        if (request == null || request.getRoutes() == null || request.getRoutes().isEmpty()) {
            throw invalid("Routes list cannot be empty");
        }

        Map<Long, Route> baseRouteByVehicle = new HashMap<>();
        Set<Long> baseOrderIds = new HashSet<>();
        for (Route route : base.getRoutes()) {
            baseRouteByVehicle.put(route.getVehicle().getId(), route);
            route.getSegments().forEach(stop -> {
                if (stop.getType() == LocationType.ORDER && stop.getOrder() != null) {
                    baseOrderIds.add(stop.getOrder().getId());
                }
            });
        }

        Set<Long> seenVehicles = new HashSet<>();
        Set<Long> seenOrders = new HashSet<>();
        for (RouteSequenceRequest seq : request.getRoutes()) {
            if (seq.getVehicleId() == null || !baseRouteByVehicle.containsKey(seq.getVehicleId())) {
                throw invalid("Vehicle " + seq.getVehicleId()
                        + " is not part of this plan (v1 keeps every route vehicle)");
            }
            if (!seenVehicles.add(seq.getVehicleId())) {
                throw invalid("Vehicle " + seq.getVehicleId() + " appears twice");
            }
            if (seq.getStopOrderIds() == null || seq.getStopOrderIds().isEmpty()) {
                throw invalid("Route for vehicle " + seq.getVehicleId()
                        + " has no stops (EMPTY_ROUTE)");
            }
            for (Long orderId : seq.getStopOrderIds()) {
                if (!seenOrders.add(orderId)) {
                    throw invalid("Order " + orderId + " appears twice (DUPLICATE_ORDER)");
                }
            }
        }
        // v1 excludes add/remove: the request must cover exactly the plan orders.
        if (!seenOrders.equals(baseOrderIds)) {
            throw invalid("Sequences must cover exactly the plan orders "
                    + "(v1 excludes adding or removing stops)");
        }

        Map<Long, Order> orderMap = orderRepository.findAllById(seenOrders).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        List<Long> missing = seenOrders.stream().filter(id -> !orderMap.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw notFound("Orders " + missing);
        }
        for (Order order : orderMap.values()) {
            if (!order.getBranch().getId().equals(base.getBranch().getId())) {
                throw invalid("Order " + order.getId() + " belongs to another branch");
            }
        }

        Map<Long, Vehicle> vehicleMap = vehicleRepository.findAllById(seenVehicles).stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v));

        List<RouteViolationDTO> entryViolations = new ArrayList<>();
        EngineEvaluateRequestDTO engineRequest = new EngineEvaluateRequestDTO();
        List<EngineEvaluateRequestDTO.EngineEvaluateRouteDTO> engineRoutes = new ArrayList<>();

        int routeIndex = 0;
        for (RouteSequenceRequest seq : request.getRoutes()) {
            Vehicle vehicle = vehicleMap.get(seq.getVehicleId());
            if (vehicle.getStartDepot() == null || vehicle.getEndDepot() == null) {
                throw invalid("Vehicle " + vehicle.getId() + " has no start/end depot");
            }
            Route baseRoute = baseRouteByVehicle.get(seq.getVehicleId());
            LocalTime baseStart = resolveBaseStartTime(baseRoute, vehicle.getId());

            List<Order> ordered = seq.getStopOrderIds().stream().map(orderMap::get).toList();
            checkCapacityAndSkills(routeIndex, vehicle, ordered, entryViolations);

            EngineEvaluateRequestDTO.EngineEvaluateRouteDTO engineRoute =
                    new EngineEvaluateRequestDTO.EngineEvaluateRouteDTO();
            engineRoute.setVehicleId(vehicle.getId());
            engineRoute.setVehicleLicensePlate(vehicle.getVehicleLicensePlate());
            EngineVehicleTypeDTO typeDTO = vehicleTypeMapper.toEngineDTO(vehicle.getVehicleType());
            engineRoute.setVehicleType(typeDTO);
            EngineDepotDTO startDepot = depotMapper.toEngineDTO(vehicle.getStartDepot());
            EngineDepotDTO endDepot = depotMapper.toEngineDTO(vehicle.getEndDepot());
            engineRoute.setStartDepot(startDepot);
            engineRoute.setEndDepot(endDepot);
            engineRoute.setStartTime(baseStart.toString());
            List<EngineOrderDTO> engineOrders = ordered.stream().map(orderMapper::toEngineDTO).toList();
            engineRoute.setOrders(engineOrders);
            engineRoutes.add(engineRoute);
            routeIndex++;
        }
        engineRequest.setRoutes(engineRoutes);

        return new ValidatedPlan(base, baseOrderIds, engineRequest, entryViolations);
    }

    /**
     * Start-time anchor for evaluation. Real engine rows may carry a start-depot
     * stop without departure time — fall back to the first usable time reading.
     */
    private LocalTime resolveBaseStartTime(Route baseRoute, Long vehicleId) {
        Optional<LocalTime> departure = baseRoute.getSegments().stream()
                .map(RouteStop::getDepartureTime)
                .filter(t -> t != null)
                .findFirst();
        if (departure.isPresent()) {
            return departure.get();
        }
        return baseRoute.getSegments().stream()
                .map(RouteStop::getArrivalTime)
                .filter(t -> t != null)
                .findFirst()
                .orElseThrow(() -> invalid(
                        "Base route for vehicle " + vehicleId + " has no usable start time"));
    }

    /** Cheap Entry-side checks; the engine re-verifies authoritatively. */    private void checkCapacityAndSkills(int routeIndex, Vehicle vehicle, List<Order> ordered,
                                        List<RouteViolationDTO> violations) {
        BigDecimal load = ordered.stream()
                .map(Order::getDemand)
                .filter(d -> d != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (vehicle.getVehicleType().getCapacity() != null
                && load.compareTo(BigDecimal.valueOf(vehicle.getVehicleType().getCapacity())) > 0) {
            violations.add(RouteViolationDTO.builder()
                    .code("OVER_CAPACITY").routeIndex(routeIndex)
                    .orderId(ordered.get(ordered.size() - 1).getId())
                    .detail("Load " + load + " kg exceeds capacity "
                            + vehicle.getVehicleType().getCapacity() + " kg")
                    .build());
        }
        Set<VehicleSkill> skills = vehicleFeaturesService.getSkills(vehicle.getVehicleType());
        Set<VehicleSkill> offered = skills != null ? skills : Set.of();
        for (Order order : ordered) {
            if (order.getRequiredSkills() != null && !offered.containsAll(order.getRequiredSkills())) {
                violations.add(RouteViolationDTO.builder()
                        .code("SKILL_MISMATCH").routeIndex(routeIndex).orderId(order.getId())
                        .detail("Order requires " + order.getRequiredSkills())
                        .build());
            }
        }
    }

    private List<RouteViolationDTO> mergeViolations(List<RouteViolationDTO> entry,
                                                   EngineEvaluateResponseDTO engine) {
        List<RouteViolationDTO> all = new ArrayList<>(entry);
        Optional.ofNullable(engine.getViolations()).orElse(List.of()).forEach(v ->
                all.add(RouteViolationDTO.builder()
                        .code(v.getCode()).routeIndex(v.getRouteIndex())
                        .orderId(v.getOrderId()).detail(v.getDetail())
                        .build()));
        return all;
    }

    private RouteEvaluateResponseDTO toPreview(ValidatedPlan plan, EngineEvaluateResponseDTO engine) {
        List<RouteViolationDTO> violations = mergeViolations(plan.entryViolations(), engine);
        boolean feasible = engine.isFeasible() && violations.stream()
                .noneMatch(v -> !"UNASSIGNED_INCREASE".equals(v.getCode()));

        List<RouteDetailResponseDTO> routes = Optional.ofNullable(engine.getRoutes())
                .orElse(List.of()).stream().map(this::toRoutePreview).toList();

        EngineEvaluateResponseDTO.EngineEvaluateTotalsDTO t = engine.getTotals();
        EvaluateTotalsDTO totals = t == null ? null : EvaluateTotalsDTO.builder()
                .totalDistance(toBigDecimal(t.getTotalDistance()))
                .totalTime(toBigDecimal(t.getTotalTime()))
                .totalCost(toBigDecimal(t.getTotalCost()))
                .totalCO2(toBigDecimal(t.getTotalCO2()))
                .vehiclesUsed(t.getVehiclesUsed())
                .ordersServed(t.getOrdersServed())
                .build();

        return RouteEvaluateResponseDTO.builder()
                .feasible(feasible)
                .violations(violations)
                .routes(routes)
                .totals(totals)
                .warnings(List.of())
                .build();
    }

    private RouteDetailResponseDTO toRoutePreview(EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO route) {
        List<StopDetailResponseDTO> stops = Optional.ofNullable(route.getStops())
                .orElse(List.of()).stream().map(s -> StopDetailResponseDTO.builder()
                        .sequenceNumber(s.getSequenceNumber())
                        .type("CUSTOMER".equals(s.getType()) ? "ORDER" : s.getType())
                        .orderId(s.getOrderId())
                        .locationId(s.getLocationId())
                        .locationName(s.getLocationName())
                        .latitude(s.getLatitude())
                        .longitude(s.getLongitude())
                        .arrivalTime(parseTime(s.getArrivalTime()))
                        .departureTime(parseTime(s.getDepartureTime()))
                        .serviceTime(toBigDecimal(s.getServiceTime()))
                        .waitTime(toBigDecimal(s.getWaitTime()))
                        .demand(toBigDecimal(s.getDemand()))
                        .loadAfter(toBigDecimal(s.getLoadAfter()))
                        .distanceToNext(toBigDecimal(s.getDistanceToNext()))
                        .timeToNext(toBigDecimal(s.getTimeToNext()))
                        .build()).toList();

        LocalTime start = stops.isEmpty() ? null : stops.get(0).getArrivalTime();
        LocalTime end = stops.isEmpty() ? null : stops.get(stops.size() - 1).getArrivalTime();
        return RouteDetailResponseDTO.builder()
                .routeOrder(null)
                .vehicleId(route.getVehicleId())
                .vehicleLicensePlate(route.getVehicleLicensePlate())
                .distance(toBigDecimal(route.getTotalDistance()))
                .co2Emission(toBigDecimal(route.getTotalCO2()))
                .serviceTime(toBigDecimal(route.getTotalTime()))
                .orderCount(route.getOrderCount())
                .loadUtilization(toBigDecimal(route.getLoadUtilization()))
                .startTime(start)
                .endTime(end)
                .stops(stops)
                .build();
    }

    // --------------------------------------------------

    /** Latest revision of the chain containing the base (the base itself if unedited). */
    private Solution latestRevision(Solution base) {
        Solution current = base;
        while (true) {
            List<Solution> children = solutionRepository.findByParentSolutionId(current.getId());
            if (children.isEmpty()) {
                return current;
            }
            current = children.stream()
                    .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .orElse(children.get(0));
        }
    }

    private Solution persistRevision(ValidatedPlan plan, EngineEvaluateResponseDTO engine) {
        Solution base = plan.base();
        Map<Long, Order> orderMap = orderRepository.findAllById(plan.baseOrderIds()).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));
        Map<Long, Vehicle> vehicleMap = vehicleRepository.findAllById(
                engine.getRoutes().stream().map(EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO::getVehicleId).toList()
        ).stream().collect(Collectors.toMap(Vehicle::getId, v -> v));

        // Revisions hang off the parent chain with a NULL job: solutions.job_id
        // stays 1:1 for engine originals (callback idempotency and job history
        // untouched), and MySQL UNIQUE permits multiple NULLs.
        Solution revision = Solution.builder()
                .branch(base.getBranch())
                .parentSolution(base)
                .type(SolutionType.MANUAL_EDITED)
                .status(base.getUnservedOrders() != null && base.getUnservedOrders() > 0
                        ? SolutionStatus.PARTIAL_SUCCESS : SolutionStatus.SUCCESS)
                .totalDistance(toBigDecimal(engine.getTotals().getTotalDistance()))
                .totalCost(toBigDecimal(engine.getTotals().getTotalCost()))
                .totalCO2(toBigDecimal(engine.getTotals().getTotalCO2()))
                .totalTime(toBigDecimal(engine.getTotals().getTotalTime()))
                .totalVehiclesUsed(engine.getTotals().getVehiclesUsed())
                .servedOrders(engine.getTotals().getOrdersServed())
                .unservedOrders(base.getUnservedOrders())
                .createdAt(LocalDateTime.now())
                .build();

        int routeOrder = 0;
        for (EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO routeData : engine.getRoutes()) {
            routeOrder++;
            Route route = Route.builder()
                    .solution(revision)
                    .vehicle(vehicleMap.get(routeData.getVehicleId()))
                    .routeOrder(routeOrder)
                    .distance(toBigDecimal(routeData.getTotalDistance()))
                    .serviceTime(toBigDecimal(routeData.getTotalTime()))
                    .co2Emission(toBigDecimal(routeData.getTotalCO2()))
                    .orderCount(routeData.getOrderCount())
                    .loadUtilization(toBigDecimal(routeData.getLoadUtilization()))
                    .segments(new ArrayList<>())
                    .build();
            revision.getRoutes().add(route);

            int position = 0;
            for (EngineEvaluateResponseDTO.EngineEvaluatedStopDTO stopData : routeData.getStops()) {
                Order order = null;
                if ("ORDER".equals(stopData.getType()) || "CUSTOMER".equals(stopData.getType())) {
                    order = orderMap.get(stopData.getOrderId());
                }
                route.getSegments().add(RouteStop.builder()
                        .route(route)
                        .order(order)
                        .sequenceNumber(stopData.getSequenceNumber() != null
                                ? stopData.getSequenceNumber() : position)
                        .type(mapLocationType(stopData.getType()))
                        .locationId(stopData.getLocationId())
                        .locationName(stopData.getLocationName())
                        .location(geometryMapper.createPoint(
                                stopData.getLatitude(), stopData.getLongitude()))
                        .arrivalTime(parseTime(stopData.getArrivalTime()))
                        .departureTime(parseTime(stopData.getDepartureTime()))
                        .serviceTime(toBigDecimal(stopData.getServiceTime()))
                        .waitTime(toBigDecimal(stopData.getWaitTime()))
                        .demand(toBigDecimal(stopData.getDemand()))
                        .loadAfter(toBigDecimal(stopData.getLoadAfter()))
                        .distanceToNext(toBigDecimal(stopData.getDistanceToNext()))
                        .timeToNext(toBigDecimal(stopData.getTimeToNext()))
                        .build());
                position++;
            }
        }

        // Unassigned membership cannot change in v1 — carry the base rows over.
        for (UnassignedOrder baseRow : base.getUnassignedOrders()) {
            revision.getUnassignedOrders().add(UnassignedOrder.builder()
                    .solution(revision)
                    .order(baseRow.getOrder())
                    .reason(baseRow.getReason())
                    .build());
        }

        // Order statuses are intentionally untouched: membership is unchanged and every
        // assigned order already transitioned through the callback path.
        return solutionRepository.save(revision);
    }

    // --------------------------------------------------

    private LocationType mapLocationType(String type) {
        if ("CUSTOMER".equals(type)) {
            return LocationType.ORDER;
        }
        try {
            return LocationType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unknown location type: {}, defaulting to ORDER", type);
            return LocationType.ORDER;
        }
    }

    private LocalTime parseTime(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        return LocalTime.parse(time);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    private BigDecimal toBigDecimal(BigDecimal value) {
        return value;
    }

    private DataInvalidException notFound(String resource) {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                        .resource(resource)
                        .build()));
    }

    private DataInvalidException invalid(String message) {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(message)
                        .build()));
    }

    private record ValidatedPlan(
            Solution base,
            Set<Long> baseOrderIds,
            EngineEvaluateRequestDTO engineRequest,
            List<RouteViolationDTO> entryViolations) {
    }
}
