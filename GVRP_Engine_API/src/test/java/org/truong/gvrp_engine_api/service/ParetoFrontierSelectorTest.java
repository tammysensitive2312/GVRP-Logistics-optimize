package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.model.FrontierStatus;
import org.truong.gvrp_engine_api.model.SolutionCandidate;
import org.truong.gvrp_engine_api.model.SolutionMetrics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DB-free unit tests for service-first classification and unbiased min-max selection
 * (E-T1..E-T6). No Spring, no GraphHopper, no matrix.
 */
class ParetoFrontierSelectorTest {

    private static SolutionCandidate candidate(String label, int served, double cost, double co2) {
        SolutionMetrics metrics = SolutionMetrics.builder()
                .ordersServed(served)
                .ordersUnserved(Math.max(0, 10 - served))
                .totalCostVnd(cost)
                .totalCo2Kg(co2)
                .build();
        return new SolutionCandidate(label, null, metrics, 0.5, 0.5);
    }

    private static String[] names(List<FrontierStatus> statuses) {
        return statuses.stream().map(Enum::name).toArray(String[]::new);
    }

    @Test
    void serviceFirst() {
        // The cheapest, lowest-CO2 candidate serves fewer orders -> SERVICE_DOMINATED.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 200.0, 20.0),
                candidate("B", 10, 180.0, 25.0),
                candidate("C", 9, 100.0, 10.0));

        List<FrontierStatus> statuses = ParetoFrontierSelector.classify(candidates);

        assertEquals(List.of("FRONTIER", "FRONTIER", "SERVICE_DOMINATED"), List.of(names(statuses)));
    }

    @Test
    void dominance() {
        // A(100,10), B(120,8), C(130,12) with the same served count: A and B are
        // mutually non-dominated, C is dominated by both.
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 100.0, 10.0),
                candidate("B", 10, 120.0, 8.0),
                candidate("C", 10, 130.0, 12.0));

        List<FrontierStatus> statuses = ParetoFrontierSelector.classify(candidates);

        assertEquals(List.of("FRONTIER", "FRONTIER", "DOMINATED"), List.of(names(statuses)));
    }

    @Test
    void duplicates() {
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 10, 100.0, 10.0),
                candidate("B", 10, 100.0, 10.0));

        List<FrontierStatus> statuses = ParetoFrontierSelector.classify(candidates);

        assertEquals(FrontierStatus.FRONTIER, statuses.get(0));
        assertEquals(FrontierStatus.DOMINATED, statuses.get(1));
    }

    @Test
    void selectUsesMinMaxWithoutBias() {
        // Case (a): stated scenario. Both points are FRONTIER; unbiased min-max gives an
        // exact tie (0.5 each) and the tie-break picks the lower cost (index 0).
        List<SolutionCandidate> tie = List.of(
                candidate("A", 10, 1_000_000.0, 10.0),
                candidate("B", 10, 1_100_000.0, 8.0));
        List<FrontierStatus> tieStatuses = ParetoFrontierSelector.classify(tie);
        assertEquals(0, ParetoFrontierSelector.select(tie, tieStatuses, 0.5, 0.5));

        // Case (b): a discriminating case. With the same cost/CO2 values the legacy `+1`
        // denominator would pick index 1; the unbiased formula picks index 0.
        List<SolutionCandidate> biased = List.of(
                candidate("A", 10, 1_000_000.0, 10.0),
                candidate("B", 10, 1_000_001.0, 0.0));
        List<FrontierStatus> biasedStatuses = ParetoFrontierSelector.classify(biased);
        assertEquals(0, ParetoFrontierSelector.select(biased, biasedStatuses, 0.5, 0.5));

        int legacyPick = legacyPlusOnePick(biased, biasedStatuses, 0.5, 0.5);
        assertEquals(1, legacyPick, "The legacy +1 formula must disagree, proving the bias was removed");
    }

    @Test
    void selectZeroRangeAndTieBreak() {
        // Single frontier point is returned.
        List<SolutionCandidate> single = List.of(candidate("A", 10, 500.0, 5.0));
        List<FrontierStatus> singleStatuses = ParetoFrontierSelector.classify(single);
        assertEquals(0, ParetoFrontierSelector.select(single, singleStatuses, 0.5, 0.5));

        // Zero-range guard: two points with identical cost -> normCost = 0 for both, so the
        // CO2 term decides. Statuses are supplied directly to isolate the guard from
        // dominance (equal cost + different CO2 would otherwise collapse the frontier).
        List<SolutionCandidate> zeroCostRange = List.of(
                candidate("A", 10, 100.0, 10.0),
                candidate("B", 10, 100.0, 5.0));
        List<FrontierStatus> zeroStatuses = List.of(FrontierStatus.FRONTIER, FrontierStatus.FRONTIER);
        assertEquals(1, ParetoFrontierSelector.select(zeroCostRange, zeroStatuses, 0.5, 0.5));

        // Equal score -> lower cost wins.
        List<SolutionCandidate> tie = List.of(
                candidate("A", 10, 1_000_000.0, 10.0),
                candidate("B", 10, 1_100_000.0, 8.0));
        List<FrontierStatus> tieStatuses = ParetoFrontierSelector.classify(tie);
        assertEquals(0, ParetoFrontierSelector.select(tie, tieStatuses, 0.5, 0.5));
    }

    @Test
    void selectNoFrontierThrows() {
        List<SolutionCandidate> candidates = List.of(
                candidate("A", 9, 100.0, 10.0),
                candidate("B", 10, 200.0, 20.0));
        List<FrontierStatus> statuses = List.of(
                FrontierStatus.SERVICE_DOMINATED,
                FrontierStatus.DOMINATED);

        assertThrows(IllegalStateException.class,
                () -> ParetoFrontierSelector.select(candidates, statuses, 0.5, 0.5));
    }

    @Test
    void classifyNullMetricsThrows() {
        SolutionCandidate noMetrics = new SolutionCandidate("A", null, null, 0.5, 0.5);
        assertThrows(IllegalArgumentException.class,
                () -> ParetoFrontierSelector.classify(List.of(noMetrics)));
        assertThrows(IllegalArgumentException.class,
                () -> ParetoFrontierSelector.classify(java.util.Arrays.asList(
                        candidate("A", 10, 1.0, 1.0), null)));
    }

    /** Reference implementation of the legacy formula (raw min-max with a +1 denominator). */
    private static int legacyPlusOnePick(List<SolutionCandidate> candidates,
                                         List<FrontierStatus> statuses,
                                         double costWeight, double co2Weight) {
        double minCost = Double.POSITIVE_INFINITY, maxCost = Double.NEGATIVE_INFINITY;
        double minCo2 = Double.POSITIVE_INFINITY, maxCo2 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            if (statuses.get(i) != FrontierStatus.FRONTIER) continue;
            double cost = candidates.get(i).getMetrics().getTotalCostVnd();
            double co2 = candidates.get(i).getMetrics().getTotalCo2Kg();
            minCost = Math.min(minCost, cost);
            maxCost = Math.max(maxCost, cost);
            minCo2 = Math.min(minCo2, co2);
            maxCo2 = Math.max(maxCo2, co2);
        }
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < candidates.size(); i++) {
            if (statuses.get(i) != FrontierStatus.FRONTIER) continue;
            double cost = candidates.get(i).getMetrics().getTotalCostVnd();
            double co2 = candidates.get(i).getMetrics().getTotalCo2Kg();
            double normCost = (cost - minCost) / (maxCost - minCost + 1);
            double normCo2 = (co2 - minCo2) / (maxCo2 - minCo2 + 1);
            double score = costWeight * normCost + co2Weight * normCo2;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
