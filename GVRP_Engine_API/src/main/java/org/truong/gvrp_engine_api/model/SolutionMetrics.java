package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solution Metrics - Calculated AFTER optimization
 *
 * Separates Jsprit's internal cost from real-world metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionMetrics {
    // Physical metrics
    private Double totalDistance;      // km
    private Double totalTime;          // hours

    // Cost metrics (real business cost, not Jsprit cost)
    private Double totalCostVnd;       // VND - actual money spent
    private Double fuelCostVnd;        // VND - fuel/energy only
    private Double timeCostVnd;        // VND - time cost only
    private Double fixedCostVnd;       // VND - fixed costs

    // Environmental metrics
    private Double totalCo2Kg;         // kg CO2 - actual emissions
    private Double co2CostVnd;         // VND - monetized CO2 cost

    // Operational metrics
    private Integer vehiclesUsed;
    private Integer ordersServed;
    private Integer ordersUnserved;

    // Utilization metrics
    private Double avgLoadUtilization; // % - average capacity utilization
    private Double avgTimeUtilization; // % - average time window utilization

    // Jsprit internal (for debugging)
    private Double jspritCost;         // Jsprit's optimization cost

    /**
     * Calculate weighted multi-objective score
     * Used for Pareto frontier selection
     */
    public double getWeightedScore(double costWeight, double co2Weight) {
        return (totalCostVnd * costWeight) + (totalCo2Kg * co2Weight);
    }
}
