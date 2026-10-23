package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.model.FrontierStatus;
import org.truong.gvrp_engine_api.model.SolutionCandidate;
import org.truong.gvrp_engine_api.model.SolutionMetrics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Second service tier of the Pareto classification (PARETO-004): among candidates that serve the
 * maximum number of orders, only those with the maximum delivered demand stay eligible.
 */
class ParetoFrontierSelectorServiceTierTest {

    private static SolutionCandidate candidate(String label, int served, Double demand,
                                               double cost, double co2) {
        SolutionMetrics metrics = SolutionMetrics.builder()
                .ordersServed(served)
                .ordersUnserved(Math.max(0, 10 - served))
                .servedDemand(demand)
                .totalCostVnd(cost)
                .totalCo2Kg(co2)
                .build();
        return new SolutionCandidate(label, null, metrics, 0.5, 0.5);
    }

    private static List<String> names(List<FrontierStatus> statuses) {
        return statuses.stream().map(Enum::name).toList();
    }

    @Test
    void sameCountLowerDemandIsServiceDominated() {
        // B is cheaper and cleaner but delivers 40 kg less with the same order count.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 500.0, 200.0, 20.0),
                candidate("B", 10, 460.0, 150.0, 15.0));

        assertEquals(List.of("FRONTIER", "SERVICE_DOMINATED"),
                names(ParetoFrontierSelector.classify(candidates)));
    }

    @Test
    void orderCountStillComesFirst() {
        // C delivers more demand but serves fewer orders: the count tier decides first.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 500.0, 200.0, 20.0),
                candidate("C", 9, 900.0, 100.0, 10.0));

        assertEquals(List.of("FRONTIER", "SERVICE_DOMINATED"),
                names(ParetoFrontierSelector.classify(candidates)));
    }

    @Test
    void demandWithinFloatingToleranceStaysEligible() {
        // 0.1 + 0.2 != 0.3 in binary floating point; both must still compete on cost/CO2.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 0.1 + 0.2, 100.0, 10.0),
                candidate("B", 10, 0.3, 120.0, 8.0));

        assertEquals(List.of("FRONTIER", "FRONTIER"),
                names(ParetoFrontierSelector.classify(candidates)));
    }

    @Test
    void nullDemandFallsBackToCountOnly() {
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, null, 100.0, 10.0),
                candidate("B", 10, null, 120.0, 8.0),
                candidate("C", 10, null, 130.0, 12.0));

        assertEquals(List.of("FRONTIER", "FRONTIER", "DOMINATED"),
                names(ParetoFrontierSelector.classify(candidates)));
    }

    @Test
    void dominanceAppliesOnlyWithinTheEligibleGroup() {
        // D (lower demand) would dominate A and E on cost/CO2, but it is not eligible, so it
        // must not knock A or E off the frontier.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 500.0, 200.0, 20.0),
                candidate("D", 10, 450.0, 50.0, 5.0),
                candidate("E", 10, 500.0, 180.0, 25.0));

        assertEquals(List.of("FRONTIER", "SERVICE_DOMINATED", "FRONTIER"),
                names(ParetoFrontierSelector.classify(candidates)));
    }
}
