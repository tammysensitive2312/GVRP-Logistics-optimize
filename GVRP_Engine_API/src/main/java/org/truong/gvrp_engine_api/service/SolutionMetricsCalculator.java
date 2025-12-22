package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.model.*;

/**
 * Solution Metrics Calculator
 *
 * Calculates REAL metrics after Jsprit optimization
 * Separates solver cost from business metrics
 *
 * WHY SEPARATE:
 * - Jsprit cost is for optimization (weighted, normalized)
 * - Business metrics are for reporting (real cost, real CO2)
 * - Different stakeholders care about different metrics
 *
 * @author Truong
 */
@Slf4j
public class SolutionMetricsCalculator {

    public static SolutionMetrics calculate(
            VehicleRoutingProblemSolution solution,
            OptimizationContext context,
            DistanceTimeMatrix matrix
    ) {
        // Initialize accumulators
        double totalDistance = 0.0;     // km
        double totalTime = 0.0;          // hours
        double totalFuelCost = 0.0;      // VND
        double totalTimeCost = 0.0;      // VND
        double totalFixedCost = 0.0;     // VND
        double totalCO2 = 0.0;           // kg
        int vehiclesUsed = solution.getRoutes().size();
        int ordersServed = 0;
        double totalLoadUtilization = 0.0;
        double totalTimeUtilization = 0.0;

        // Process each route
        for (VehicleRoute route : solution.getRoutes()) {

            // Get vehicle and its type
            String vehicleId = route.getVehicle().getId().replace("vehicle-", "");
            Vehicle vehicleDTO = context.vehicleDTOs().get(Long.parseLong(vehicleId));
            VehicleType typeDTO = context.vehicleTypeDTOs().get(vehicleDTO.getVehicleTypeId());

            // ========== Calculate route distance ==========
            double routeDistance = 0.0;  // meters
            TourActivity prevActivity = route.getStart();

            // Sum up all segments
            for (TourActivity activity : route.getActivities()) {
                double segmentDistance = getTransportDistance(
                        prevActivity.getLocation(),
                        activity.getLocation(),
                        matrix,
                        context
                );
                routeDistance += segmentDistance;
                prevActivity = activity;
            }

            // Add return to depot
            routeDistance += getTransportDistance(
                    prevActivity.getLocation(),
                    route.getEnd().getLocation(),
                    matrix,
                    context
            );

            double routeDistanceKm = routeDistance / 1000.0;
            totalDistance += routeDistanceKm;

            // ========== Calculate route time ==========
            double routeTimeSeconds = route.getEnd().getArrTime() - route.getStart().getEndTime();
            double routeTimeHours = routeTimeSeconds / 3600.0;
            totalTime += routeTimeHours;

            // ========== Calculate costs ==========

            // Fuel/energy cost
            double routeFuelCost = routeDistanceKm * typeDTO.getCostPerKm();
            totalFuelCost += routeFuelCost;

            // Time cost
            double routeTimeCost = routeTimeHours * typeDTO.getCostPerHour();
            totalTimeCost += routeTimeCost;

            // Fixed cost
            totalFixedCost += typeDTO.getFixedCost();

            // ========== Calculate CO2 ==========
            double routeCO2 = GreenVRPCostCalculator.calculateRouteCO2(
                    routeDistanceKm,
                    typeDTO
            );
            totalCO2 += routeCO2;

            // ========== Calculate utilization ==========

            // Load utilization
            double routeLoad = route.getActivities().stream()
                    .mapToDouble(act -> {
                        if (act instanceof TourActivity.JobActivity jobAct) {
                            return jobAct.getJob().getSize().get(0);
                        }
                        return 0.0;
                    })
                    .sum();
            double loadUtil = (routeLoad / typeDTO.getCapacity()) * 100.0;
            totalLoadUtilization += loadUtil;

            // Time utilization (simplified)
            double maxDuration = typeDTO.getMaxDuration() != null
                    ? typeDTO.getMaxDuration()
                    : 12.0;
            double timeUtil = (routeTimeHours / maxDuration) * 100.0;
            totalTimeUtilization += timeUtil;

            // Count orders
            ordersServed += route.getActivities().size();
        }

        // ========== Calculate averages ==========
        double avgLoadUtil = vehiclesUsed > 0 ? totalLoadUtilization / vehiclesUsed : 0.0;
        double avgTimeUtil = vehiclesUsed > 0 ? totalTimeUtilization / vehiclesUsed : 0.0;

        // ========== Calculate total cost ==========
        double totalCost = totalFixedCost + totalFuelCost + totalTimeCost;

        // ========== Calculate CO2 cost (for reporting) ==========
        double co2CostVnd = totalCO2 * GreenVRPCostCalculator.getCarbonPrice();

        // ========== Build metrics object ==========
        SolutionMetrics metrics = SolutionMetrics.builder()
                .totalDistance(totalDistance)
                .totalTime(totalTime)
                .totalCostVnd(totalCost)
                .fuelCostVnd(totalFuelCost)
                .timeCostVnd(totalTimeCost)
                .fixedCostVnd(totalFixedCost)
                .totalCo2Kg(totalCO2)
                .co2CostVnd(co2CostVnd)
                .vehiclesUsed(vehiclesUsed)
                .ordersServed(ordersServed)
                .ordersUnserved(solution.getUnassignedJobs().size())
                .avgLoadUtilization(avgLoadUtil)
                .avgTimeUtilization(avgTimeUtil)
                .jspritCost(solution.getCost())
                .build();

        // ========== Log summary ==========
        log.info("📊 Solution Metrics:");
        log.info("   Distance: {} km | Time: {} hours", totalDistance, totalTime);
        log.info("   Cost: {} VND (Fuel: {}, Time: {}, Fixed: {})",
                (long) totalCost, (long) totalFuelCost, (long) totalTimeCost, (long) totalFixedCost);
        log.info("   CO2: {} kg (Cost: {} VND)", totalCO2, (long) co2CostVnd);
        log.info("   Vehicles: {} | Orders: {} / {} | Utilization: {}%",
                vehiclesUsed,
                ordersServed,
                ordersServed + solution.getUnassignedJobs().size(),
                avgLoadUtil);

        return metrics;
    }

    /**
     * Get distance between two locations from matrix
     */
    private static double getTransportDistance(
            Location from,
            Location to,
            DistanceTimeMatrix matrix,
            OptimizationContext context) {

        int fromIndex = context.allLocations().indexOf(from);
        int toIndex = context.allLocations().indexOf(to);

        if (fromIndex == -1 || toIndex == -1) {
            log.warn("Location not found in matrix: {} -> {}", from.getId(), to.getId());
            return 0.0;
        }

        return matrix.distanceMatrix()[fromIndex][toIndex];
    }

    /**
     * Get time between two locations from matrix
     */
    private static double getTransportTime(
            Location from,
            Location to,
            DistanceTimeMatrix matrix,
            OptimizationContext context) {

        int fromIndex = context.allLocations().indexOf(from);
        int toIndex = context.allLocations().indexOf(to);

        if (fromIndex == -1 || toIndex == -1) {
            log.warn("Location not found in matrix: {} -> {}", from.getId(), to.getId());
            return 0.0;
        }

        return matrix.timeMatrix()[fromIndex][toIndex];
    }
}
