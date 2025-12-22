package org.truong.gvrp_engine_api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OptimizationResult {
    private Long jobId;
    private String optimizationMode;

    // Selected solution
    private SolutionCandidate selected;

    // Alternative solutions (Pareto frontier)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SolutionCandidate> paretoFrontier;

    // Summary
    private Double totalCost;
    private Double totalDistance;  // km
    private Double totalTime;      // hours
    private Double totalCO2;       // kg
    private Integer totalVehiclesUsed;
    private Integer totalOrdersServed;
    private Integer totalOrdersUnassigned;

    // Details
    private List<RouteDetail> routes;
    private List<UnassignedOrder> unassignedOrders;

    /**
     * Create result from single solution (backward compatible)
     */
    public static OptimizationResult fromSingleSolution(
            Long jobId,
            SolutionCandidate candidate,
            List<RouteDetail> routes,
            List<UnassignedOrder> unassigned) {

        return OptimizationResult.builder()
                .jobId(jobId)
                .optimizationMode("SINGLE_WEIGHTED")
                .selected(candidate)
                .paretoFrontier(null)
                .routes(routes)
                .unassignedOrders(unassigned)
                .totalCost(candidate.getMetrics().getTotalCostVnd())
                .totalDistance(candidate.getMetrics().getTotalDistance())
                .totalTime(candidate.getMetrics().getTotalTime())
                .totalCO2(candidate.getMetrics().getTotalCo2Kg())
                .totalVehiclesUsed(candidate.getMetrics().getVehiclesUsed())
                .totalOrdersServed(candidate.getMetrics().getOrdersServed())
                .totalOrdersUnassigned(candidate.getMetrics().getOrdersUnserved())
                .build();
    }

    /**
     * Create result with Pareto frontier
     */
    public static OptimizationResult fromPareto(
            Long jobId,
            SolutionCandidate selected,
            List<SolutionCandidate> pareto,
            List<RouteDetail> routes,
            List<UnassignedOrder> unassigned) {

        OptimizationResult result = fromSingleSolution(jobId, selected, routes, unassigned);
        result.setOptimizationMode("MULTI_PARETO");
        result.setParetoFrontier(pareto);
        return result;
    }
}
