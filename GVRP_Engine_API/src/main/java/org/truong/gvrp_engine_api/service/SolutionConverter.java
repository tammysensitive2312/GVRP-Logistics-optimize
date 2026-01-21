package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_engine_api.model.DistanceTimeMatrix;
import org.truong.gvrp_engine_api.model.OptimizationResult;
import org.truong.gvrp_engine_api.model.RouteDetail;
import org.truong.gvrp_engine_api.model.Stop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionConverter {

    public Map<String, Object> convertToSolutionData(OptimizationResult result) {
        log.debug("Converting optimization result to solution data");

        DistanceTimeMatrix matrix = result.getDistanceTimeMatrix();

        Map<String, Object> solutionData = new HashMap<>();

        // ===== Summary metrics =====
        solutionData.put("total_distance", result.getTotalDistance());
        solutionData.put("total_cost", result.getTotalCost());
        solutionData.put("total_co2", result.getTotalCO2());
        solutionData.put("total_time", result.getTotalTime());
        solutionData.put("total_vehicles_used", result.getTotalVehiclesUsed());
        solutionData.put("served_orders", result.getTotalOrdersServed());
        solutionData.put("unserved_orders", result.getTotalOrdersUnassigned());

        // ===== Routes =====
        solutionData.put(
                "routes",
                convertRoutes(result.getRoutes(), matrix)
        );

        // ===== Unassigned orders =====
        if (result.getUnassignedOrders() != null && !result.getUnassignedOrders().isEmpty()) {
            solutionData.put("unassigned_orders", result.getUnassignedOrders());
        }

        log.debug("✓ Converted {} routes",
                result.getRoutes().size());

        return solutionData;
    }

    // --------------------------------------------------

    private List<Map<String, Object>> convertRoutes(
            List<RouteDetail> routes,
            DistanceTimeMatrix matrix
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        int routeOrder = 0;
        for (RouteDetail routeDetail : routes) {
            routeOrder++;

            Map<String, Object> route = new HashMap<>();

            // Metadata
            route.put("vehicle_id", routeDetail.getVehicleId());
            route.put("route_order", routeOrder);

            // Metrics
            route.put("distance", routeDetail.getTotalDistance());
            route.put("duration", routeDetail.getTotalTime());
            route.put("co2_emission", routeDetail.getTotalCO2());
            route.put("order_count", routeDetail.getOrderCount());
            route.put("load_utilization", routeDetail.getLoadUtilization());

            // Stops (enriched)
            route.put(
                    "stops",
                    convertStops(routeDetail.getStops(), matrix)
            );

            result.add(route);
        }

        return result;
    }

    // --------------------------------------------------

    private List<Map<String, Object>> convertStops(
            List<Stop> stops,
            DistanceTimeMatrix matrix
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < stops.size(); i++) {
            Stop stop = stops.get(i);
            Map<String, Object> stopData = new HashMap<>();

            // ===== Basic info =====
            stopData.put("sequence_number", stop.getSequenceNumber());
            stopData.put("type", stop.getType());
            stopData.put("order_id", stop.getOrderId());
            stopData.put("order_code", stop.getOrderCode());
            stopData.put("location_id", stop.getLocationId());
            stopData.put("location_name", stop.getLocationName());
            stopData.put("latitude", stop.getLatitude());
            stopData.put("longitude", stop.getLongitude());

            // ===== Time =====
            stopData.put("arrival_time", stop.getArrivalTime());
            stopData.put("departure_time", stop.getDepartureTime());
            stopData.put("service_time", stop.getServiceTime());
            stopData.put("wait_time", stop.getWaitTime());

            // ===== Load =====
            stopData.put("demand", stop.getDemand());
            stopData.put("load_after", stop.getLoadAfter());

            // ===== Enrichment: distance & time to next =====
            if (i < stops.size() - 1 && matrix != null) {
                Stop next = stops.get(i + 1);

                int fromIdx = locationIndex(matrix, stop.getLocationId());
                int toIdx = locationIndex(matrix, next.getLocationId());

                if (fromIdx >= 0 && toIdx >= 0) {
                    stopData.put(
                            "distance_to_next",
                            matrix.distanceMatrix()[fromIdx][toIdx]
                    );
                    stopData.put(
                            "time_to_next",
                            matrix.timeMatrix()[fromIdx][toIdx]
                    );
                } else {
                    // fallback safety
                    stopData.put("distance_to_next", 0.0);
                    stopData.put("time_to_next", 0.0);
                }
            } else {
                stopData.put("distance_to_next", 0.0);
                stopData.put("time_to_next", 0.0);
            }

            result.add(stopData);
        }

        return result;
    }

    // --------------------------------------------------

    /**
     * Map locationId -> index in DistanceTimeMatrix.locations
     */
    private int locationIndex(DistanceTimeMatrix matrix, String locationId) {
        List<Location> locations = matrix.locations();

        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getId().equals(locationId)) {
                return i;
            }
        }

        log.warn("⚠️ Location not found in matrix: {}", locationId);
        return -1;
    }
}
