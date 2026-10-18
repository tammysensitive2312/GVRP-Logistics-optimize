package org.truong.gvrp_engine_api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OptimizationResult {

    private final Long jobId;
    private final OptimizationMode optimizationMode;

    private final DistanceTimeMatrix distanceTimeMatrix;

    private final SolutionCandidate selected;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<SolutionCandidate> paretoFrontier;

    /**
     * Every weight-point outcome with service-first status and (for frontier points) full
     * routes. Null for single-objective results (PAR-004).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<ParetoAlternative> paretoAlternatives;

    /** Number of clusters shared by all weight points; null when cluster-first was not applied. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer clusterCount;

    private final List<RouteDetail> routes;
    private final List<UnassignedOrder> unassignedOrders;

    public Double getTotalCost() {
        return selected.getMetrics().getTotalCostVnd();
    }

    public Double getTotalDistance() {
        return selected.getMetrics().getTotalDistance();
    }

    public Double getTotalTime() {
        return selected.getMetrics().getTotalTime();
    }

    public Double getTotalCO2() {
        return selected.getMetrics().getTotalCo2Kg();
    }

    public Integer getTotalVehiclesUsed() {
        return selected.getMetrics().getVehiclesUsed();
    }

    public Integer getTotalOrdersServed() {
        return selected.getMetrics().getOrdersServed();
    }

    public Integer getTotalOrdersUnassigned() {
        return selected.getMetrics().getOrdersUnserved();
    }


    /**
     * Single-objective optimization result
     */
    public static OptimizationResult single(
            Long jobId,
            SolutionCandidate candidate,
            DistanceTimeMatrix matrix,
            List<RouteDetail> routes,
            List<UnassignedOrder> unassigned
    ) {
        return OptimizationResult.builder()
                .jobId(jobId)
                .optimizationMode(OptimizationMode.SINGLE_WEIGHTED)
                .selected(candidate)
                .paretoFrontier(null)
                .paretoAlternatives(null)
                .clusterCount(null)
                .distanceTimeMatrix(matrix)
                .routes(routes)
                .unassignedOrders(unassigned)
                .build();
    }

    /**
     * Multi-objective (Pareto) optimization result
     */
    public static OptimizationResult pareto(
            Long jobId,
            SolutionCandidate selected,
            List<SolutionCandidate> paretoFrontier,
            List<ParetoAlternative> paretoAlternatives,
            Integer clusterCount,
            DistanceTimeMatrix matrix,
            List<RouteDetail> routes,
            List<UnassignedOrder> unassigned
    ) {
        return OptimizationResult.builder()
                .jobId(jobId)
                .optimizationMode(OptimizationMode.MULTI_PARETO)
                .selected(selected)
                .paretoFrontier(paretoFrontier)
                .paretoAlternatives(paretoAlternatives)
                .clusterCount(clusterCount)
                .distanceTimeMatrix(matrix)
                .routes(routes)
                .unassignedOrders(unassigned)
                .build();
    }
}
