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

    // ===== Execution context =====
    private final DistanceTimeMatrix distanceTimeMatrix;

    // ===== Selected solution =====
    private final SolutionCandidate selected;

    // ===== Pareto frontier (optional) =====
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<SolutionCandidate> paretoFrontier;

    // ===== Details =====
    private final List<RouteDetail> routes;
    private final List<UnassignedOrder> unassignedOrders;

    // =========================================================
    // =============== Derived summary metrics =================
    // =========================================================

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

    // =========================================================
    // ================= Factory methods =======================
    // =========================================================

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
            DistanceTimeMatrix matrix,
            List<RouteDetail> routes,
            List<UnassignedOrder> unassigned
    ) {
        return OptimizationResult.builder()
                .jobId(jobId)
                .optimizationMode(OptimizationMode.MULTI_PARETO)
                .selected(selected)
                .paretoFrontier(paretoFrontier)
                .distanceTimeMatrix(matrix)
                .routes(routes)
                .unassignedOrders(unassigned)
                .build();
    }
}
