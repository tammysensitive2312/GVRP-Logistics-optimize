package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.model.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Callback payload shape for Pareto runs (E-T7, E-T8). No Spring, no matrix.
 */
class SolutionConverterParetoTest {

    private final SolutionConverter converter = new SolutionConverter();

    private static SolutionCandidate candidate(String label, int served, double cost, double co2,
                                               double costWeight, double co2Weight) {
        SolutionMetrics metrics = SolutionMetrics.builder()
                .ordersServed(served)
                .ordersUnserved(0)
                .totalCostVnd(cost)
                .totalDistance(85.2)
                .totalCo2Kg(co2)
                .totalTime(6.3)
                .vehiclesUsed(4)
                .build();
        return new SolutionCandidate(label, null, metrics, costWeight, co2Weight);
    }

    private static RouteDetail route(long vehicleId) {
        return new RouteDetail(vehicleId, "PLATE-" + vehicleId, "TRUCK", 200.0, 1,
                10.0, 0.5, 5.0, 50.0, 2.0, List.of());
    }

    @Test
    void paretoBlock() {
        SolutionCandidate selected = candidate("PARETO_K0_OF_4", 120, 1_234_567.0, 14.1, 0.0001, 0.9999);
        SolutionCandidate otherFrontier = candidate("PARETO_K2_OF_4", 120, 1_100_000.0, 18.0, 0.25, 0.75);
        SolutionCandidate dominated = candidate("PARETO_K4_OF_4", 120, 900_000.0, 25.0, 1.0, 0.0001);

        RouteDetail selectedRoute = route(1L);
        List<ParetoAlternative> alternatives = List.of(
                ParetoAlternative.builder().index(0).candidate(selected)
                        .status(FrontierStatus.FRONTIER).selected(true)
                        .routes(List.of(selectedRoute)).unassignedOrders(List.of()).build(),
                ParetoAlternative.builder().index(1).candidate(otherFrontier)
                        .status(FrontierStatus.FRONTIER).selected(false)
                        .routes(List.of(route(2L)))
                        .unassignedOrders(List.of(new UnassignedOrder(7L, "ORD-7", "reason"))).build(),
                ParetoAlternative.builder().index(2).candidate(dominated)
                        .status(FrontierStatus.DOMINATED).selected(false)
                        .routes(null).unassignedOrders(null).build());

        OptimizationResult result = OptimizationResult.pareto(
                1L, selected, List.of(selected, otherFrontier), alternatives, 7, null,
                List.of(selectedRoute), List.of());

        Map<String, Object> data = converter.convertToSolutionData(result);

        // Top-level keys remain the single-objective shape.
        assertNotNull(data.get("routes"));
        assertEquals(1, ((List<?>) data.get("routes")).size());
        assertFalse(data.containsKey("unassigned_orders")); // empty top-level list is omitted

        assertTrue(data.containsKey("pareto"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pareto = (Map<String, Object>) data.get("pareto");
        assertEquals(4, pareto.get("sampler_n"));
        assertEquals(2.0, pareto.get("sampler_p"));
        assertEquals(true, pareto.get("clustered"));
        assertEquals(7, pareto.get("cluster_count"));
        assertEquals("SERVICE_FIRST_MIN_MAX", pareto.get("selection_rule"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) pareto.get("candidates");
        assertEquals(3, candidates.size());

        assertTrue(candidates.get(0).containsKey("routes"));
        assertEquals(true, candidates.get(0).get("selected"));
        assertEquals("FRONTIER", candidates.get(0).get("frontier_status"));
        assertEquals(0, candidates.get(0).get("index"));
        assertEquals("PARETO_K0_OF_4", candidates.get(0).get("label"));
        assertEquals(1_234_567.0, candidates.get(0).get("total_cost"));
        assertEquals(120, candidates.get(0).get("served_orders"));

        assertTrue(candidates.get(1).containsKey("routes"));
        assertTrue(candidates.get(1).containsKey("unassigned_orders"));
        assertEquals(false, candidates.get(1).get("selected"));

        assertFalse(candidates.get(2).containsKey("routes"));
        assertFalse(candidates.get(2).containsKey("unassigned_orders"));
        assertEquals(false, candidates.get(2).get("selected"));
        assertEquals("DOMINATED", candidates.get(2).get("frontier_status"));

        // Exactly one engine-selected candidate.
        assertEquals(1, candidates.stream().filter(c -> Boolean.TRUE.equals(c.get("selected"))).count());
    }

    @Test
    void singleHasNoParetoKey() {
        SolutionCandidate selected = candidate("WEIGHTED", 12, 500_000.0, 9.0, 0.7, 0.3);
        OptimizationResult result = OptimizationResult.single(
                1L, selected, null, List.of(route(1L)), List.of());

        Map<String, Object> data = converter.convertToSolutionData(result);

        assertFalse(data.containsKey("pareto"));
        assertEquals(Set.of(
                "total_distance", "total_cost", "total_co2", "total_time",
                "total_vehicles_used", "served_orders", "unserved_orders", "routes"),
                data.keySet());
    }
}
