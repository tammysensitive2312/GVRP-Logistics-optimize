package org.truong.gvrp_engine_api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solution Candidate - One solution with its metrics
 *
 * Used in multi-objective optimization to compare different solutions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolutionCandidate {

    private String presetName;         // "COST_FOCUSED", "BALANCED", "ECO_FOCUSED"

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private VehicleRoutingProblemSolution solution;  // Jsprit solution object

    private SolutionMetrics metrics;   // Calculated metrics

    private Double costWeight;         // Weight used for this solution
    private Double co2Weight;          // Weight used for this solution

    /**
     * Check if this solution dominates another in Pareto sense
     * A dominates B if A is better in ALL objectives
     */
    public boolean dominates(SolutionCandidate other) {
        boolean betterCost = this.metrics.getTotalCostVnd() <= other.metrics.getTotalCostVnd();
        boolean betterCO2 = this.metrics.getTotalCo2Kg() <= other.metrics.getTotalCo2Kg();
        boolean strictlyBetter =
                this.metrics.getTotalCostVnd() < other.metrics.getTotalCostVnd() ||
                        this.metrics.getTotalCo2Kg() < other.metrics.getTotalCo2Kg();

        return betterCost && betterCO2 && strictlyBetter;
    }

}
