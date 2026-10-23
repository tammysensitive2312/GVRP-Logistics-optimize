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
     * Plain two-objective (cost, CO2) dominance. It ignores served orders and delivered demand,
     * so it must NOT be used to build the Pareto frontier.
     *
     * @deprecated Not used by the engine. Pareto classification is service-first and lives in
     * {@code ParetoFrontierSelector.classify}; kept only so existing callers fail loudly at
     * review time rather than silently compiling against a different rule.
     */
    @Deprecated
    public boolean dominates(SolutionCandidate other) {
        boolean betterCost = this.metrics.getTotalCostVnd() <= other.metrics.getTotalCostVnd();
        boolean betterCO2 = this.metrics.getTotalCo2Kg() <= other.metrics.getTotalCo2Kg();
        boolean strictlyBetter =
                this.metrics.getTotalCostVnd() < other.metrics.getTotalCostVnd() ||
                        this.metrics.getTotalCo2Kg() < other.metrics.getTotalCo2Kg();

        return betterCost && betterCO2 && strictlyBetter;
    }

}
