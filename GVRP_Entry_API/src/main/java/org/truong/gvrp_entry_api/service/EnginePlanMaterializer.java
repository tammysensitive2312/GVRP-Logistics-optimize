package org.truong.gvrp_entry_api.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns an engine route/unassigned payload into JPA rows.
 *
 * <p>Extracted (PARETO-001, B2) from {@code OptimizationCallbackService} so the
 * completion callback and the Pareto apply endpoint build routes, stops and
 * unassigned orders exactly the same way. Behaviour is identical to the previous
 * private helpers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnginePlanMaterializer {

    private final GeometryMapper geometryMapper;

    public Route buildRoute(
            Solution solution,
            EngineCallbackRequest.RouteData routeData,
            Map<Long, Vehicle> vehicleMap,
            Map<Long, Order> orderMap
    ) {

        return Route.builder()
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
    }

    public List<RouteStop> buildStop(Route route, List<EngineCallbackRequest.StopData> stopData, Map<Long, Order> orderMap) {
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
                            .type(RouteStopSupport.mapLocationType(stop.getType()))
                            .locationId(stop.getLocationId())
                            .locationName(stop.getLocationName())
                            .location(geometryMapper.createPoint(stop.getLatitude(), stop.getLongitude()))
                            .arrivalTime(RouteStopSupport.parseEngineTime(stop.getArrivalTime()))
                            .departureTime(RouteStopSupport.parseEngineTime(stop.getDepartureTime()))
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

    public List<UnassignedOrder> buildUnassignedOrder(Solution solution, List<EngineCallbackRequest.UnassignedOrderData> solutionData, Map<Long, Order> orderMap) {
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

    /**
     * Shape stored in {@code pareto_candidates.plan_json}: the candidate's routes and
     * unassigned orders exactly as received. Serialised/deserialised with the injected
     * {@code ObjectMapper} so the two directions stay symmetric under the global
     * snake_case naming strategy.
     */
    @Data
    public static class PlanPayload {
        private List<EngineCallbackRequest.RouteData> routes;
        private List<EngineCallbackRequest.UnassignedOrderData> unassignedOrders;

        public PlanPayload() {
        }

        public PlanPayload(List<EngineCallbackRequest.RouteData> routes,
                           List<EngineCallbackRequest.UnassignedOrderData> unassignedOrders) {
            this.routes = routes;
            this.unassignedOrders = unassignedOrders;
        }
    }
}
