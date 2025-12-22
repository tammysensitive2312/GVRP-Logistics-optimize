package org.truong.gvrp_engine_api.model;

import lombok.*;
import org.truong.gvrp_engine_api.service.GreenVRPCostCalculator;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OptimizationConfig {

    // Weight configuration
    private Double costWeight;
    private Double co2Weight;

    // Algorithm configuration
    private Integer maxIterations;     // Jsprit iterations (default: 2000)
    private Integer numThreads;        // Parallel threads (default: 4)
    private Integer timeoutSeconds;    // Max execution time

    private Boolean strictTimeWindows;
    private Double unassignedJobPenalty;

    private Boolean enableParetoAnalysis;

    @Deprecated
    private Double distanceWeight;

    /**
     * Get effective weights with defaults
     */
    public double[] getEffectiveWeights() {
        double cost = costWeight != null ? costWeight : 0.7;
        double co2 = co2Weight != null ? co2Weight : 0.3;
        return GreenVRPCostCalculator.normalizeWeights(cost, co2);
    }

    /**
     * Clone config for multi-run
     */
    public OptimizationConfig clone() {
        OptimizationConfig copy = new OptimizationConfig();
        copy.costWeight = this.costWeight;
        copy.co2Weight = this.co2Weight;
        copy.maxIterations = this.maxIterations;
        copy.numThreads = this.numThreads;
        copy.timeoutSeconds = this.timeoutSeconds;
        copy.enableParetoAnalysis = this.enableParetoAnalysis;
        copy.strictTimeWindows = this.strictTimeWindows;
        copy.unassignedJobPenalty = this.unassignedJobPenalty;
        return copy;
    }
}
