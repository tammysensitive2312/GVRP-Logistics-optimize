package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_engine_api.model.DistanceTimeMatrix;
import org.truong.gvrp_engine_api.model.FrontierStatus;
import org.truong.gvrp_engine_api.model.OptimizationMode;
import org.truong.gvrp_engine_api.model.OptimizationResult;
import org.truong.gvrp_engine_api.model.ParetoAlternative;
import org.truong.gvrp_engine_api.model.RouteDetail;
import org.truong.gvrp_engine_api.model.SolutionCandidate;
import org.truong.gvrp_engine_api.model.SolutionMetrics;
import org.truong.gvrp_engine_api.model.Stop;
import org.truong.gvrp_engine_api.utils.AppConstant;

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

        // ===== Pareto block (PAR-004) — only for multi-objective runs =====
        if (result.getOptimizationMode() == OptimizationMode.MULTI_PARETO
                && result.getParetoAlternatives() != null) {
            solutionData.put("pareto", convertPareto(result));
        }
        return solutionData;
    }

    // --------------------------------------------------

    /**
     * Build the {@code "pareto"} block shared with the Entry callback. Single-objective
     * payloads never reach this method, so their key set is unchanged.
     */
    private Map<String, Object> convertPareto(OptimizationResult result) {
        Map<String, Object> pareto = new HashMap<>();
        pareto.put("sampler_n", AppConstant.PARETO_SAMPLER_N);
        pareto.put("sampler_p", AppConstant.PARETO_SAMPLER_P);

        Integer clusterCount = result.getClusterCount();
        pareto.put("clustered", clusterCount != null);
        pareto.put("cluster_count", clusterCount);
        pareto.put("selection_rule", "SERVICE_FIRST_MIN_MAX");

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (ParetoAlternative alternative : result.getParetoAlternatives()) {
            SolutionCandidate candidate = alternative.getCandidate();
            SolutionMetrics metrics = candidate.getMetrics();

            Map<String, Object> row = new HashMap<>();
            row.put("index", alternative.getIndex());
            row.put("label", candidate.getPresetName());
            row.put("cost_weight", candidate.getCostWeight());
            row.put("co2_weight", candidate.getCo2Weight());
            row.put("total_cost", metrics.getTotalCostVnd());
            row.put("total_distance", metrics.getTotalDistance());
            row.put("total_co2", metrics.getTotalCo2Kg());
            row.put("total_time", metrics.getTotalTime());
            row.put("total_vehicles_used", metrics.getVehiclesUsed());
            row.put("served_orders", metrics.getOrdersServed());
            row.put("unserved_orders", metrics.getOrdersUnserved());
            row.put("frontier_status",
                    alternative.getStatus() == null ? null : alternative.getStatus().name());
            row.put("selected", alternative.isSelected());

            // Full plan only for FRONTIER points; unassigned_orders may be an empty list.
            if (alternative.getStatus() == FrontierStatus.FRONTIER) {
                row.put("routes", convertRoutes(alternative.getRoutes(), result.getDistanceTimeMatrix()));
                row.put("unassigned_orders", alternative.getUnassignedOrders());
            }

            candidates.add(row);
        }
        pareto.put("candidates", candidates);
        return pareto;
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

            // ===== Geometry to next stop (LEZ-001) =====
            stopData.put("geometry_to_next", stop.getGeometryToNext());

            // ===== Enrichment: distance & time to next =====
            if (i < stops.size() - 1 && matrix != null) {
                Stop next = stops.get(i + 1);

                int fromIdx = locationIndex(matrix, stop.getLocationId());
                int toIdx = locationIndex(matrix, next.getLocationId());

                if (fromIdx >= 0 && toIdx >= 0) {
                    stopData.put("distance_to_next", matrix.distance(fromIdx, toIdx));
                    stopData.put("time_to_next", matrix.time(fromIdx, toIdx));
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
