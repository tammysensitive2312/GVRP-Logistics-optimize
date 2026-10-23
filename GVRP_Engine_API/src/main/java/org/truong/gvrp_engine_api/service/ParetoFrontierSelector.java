package org.truong.gvrp_engine_api.service;

import org.truong.gvrp_engine_api.model.FrontierStatus;
import org.truong.gvrp_engine_api.model.SolutionCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, static service-first Pareto classification and engine selection (PAR-002, PAR-003).
 * <p>
 * No Spring, no shared state — safe to unit test in isolation.
 * <p>
 * <b>Classification</b> is service-first and lexicographic: a candidate is eligible only if it
 * serves the maximum number of orders <em>and</em>, among those, the maximum delivered demand
 * ({@code SolutionMetrics.servedDemand}, compared with a relative tolerance). Every other
 * candidate is {@code SERVICE_DOMINATED}. The demand tier stops a plan that serves the same
 * number of orders but drops heavier ones from competing on cost/CO2 with a plan that delivers
 * more (AGENTS.md §9, service parity). A {@code null} served demand counts as 0, so callers that
 * do not populate it keep the order-count-only behaviour. Among eligible candidates, dominance on
 * both objectives yields {@code FRONTIER} / {@code DOMINATED}; identical objective vectors keep
 * only the lowest index on the frontier.
 * <p>
 * Equal count and equal demand do not imply the same order set; that residual case is still
 * compared on cost/CO2 (the Entry API refuses to apply a plan whose order set differs).
 * <p>
 * <b>Selection</b> min-max normalises cost and CO2 over the frontier <em>without</em> the
 * {@code +1} denominator of the legacy formula, guards against a zero range, and breaks ties
 * by lower cost then lower index.
 */
public final class ParetoFrontierSelector {

    private static final double TIE_EPSILON = 1e-12;

    /** Relative tolerance for comparing delivered demand (sums of doubles). */
    private static final double DEMAND_REL_TOLERANCE = 1e-9;

    private ParetoFrontierSelector() {
    }

    /**
     * Classify candidates in input order.
     *
     * @throws IllegalArgumentException if any candidate or its metrics is {@code null}
     */
    public static List<FrontierStatus> classify(List<SolutionCandidate> candidates) {
        List<FrontierStatus> statuses = new ArrayList<>(candidates.size());
        int maxServed = 0;
        for (SolutionCandidate candidate : candidates) {
            if (candidate == null || candidate.getMetrics() == null) {
                throw new IllegalArgumentException("Candidate and its metrics must be non-null");
            }
            int served = servedCount(candidate);
            if (served > maxServed) {
                maxServed = served;
            }
        }

        // Tier 2: highest delivered demand among the candidates with the maximum order count.
        double maxDemand = 0.0;
        for (SolutionCandidate candidate : candidates) {
            if (servedCount(candidate) == maxServed) {
                maxDemand = Math.max(maxDemand, servedDemand(candidate));
            }
        }
        double demandTolerance = DEMAND_REL_TOLERANCE * Math.max(1.0, maxDemand);

        for (SolutionCandidate candidate : candidates) {
            boolean eligible = servedCount(candidate) == maxServed
                    && servedDemand(candidate) >= maxDemand - demandTolerance;
            statuses.add(eligible ? FrontierStatus.FRONTIER : FrontierStatus.SERVICE_DOMINATED);
        }

        for (int j = 0; j < candidates.size(); j++) {
            if (statuses.get(j) != FrontierStatus.FRONTIER) {
                continue;
            }
            double costJ = candidates.get(j).getMetrics().getTotalCostVnd();
            double co2J = candidates.get(j).getMetrics().getTotalCo2Kg();

            for (int i = 0; i < candidates.size(); i++) {
                if (i == j || statuses.get(i) != FrontierStatus.FRONTIER) {
                    continue;
                }
                double costI = candidates.get(i).getMetrics().getTotalCostVnd();
                double co2I = candidates.get(i).getMetrics().getTotalCo2Kg();

                if (costI <= costJ && co2I <= co2J && (costI < costJ || co2I < co2J)) {
                    statuses.set(j, FrontierStatus.DOMINATED);
                    break;
                }
                if (i < j && costI == costJ && co2I == co2J) {
                    statuses.set(j, FrontierStatus.DOMINATED);
                    break;
                }
            }
        }

        return statuses;
    }

    /**
     * Return the index (into {@code candidates}) of the engine-recommended frontier point.
     *
     * @throws IllegalStateException if no candidate is {@code FRONTIER}
     */
    public static int select(List<SolutionCandidate> candidates, List<FrontierStatus> statuses,
                             double costWeight, double co2Weight) {
        List<Integer> frontier = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            if (statuses.get(i) == FrontierStatus.FRONTIER) {
                frontier.add(i);
            }
        }
        if (frontier.isEmpty()) {
            throw new IllegalStateException("No FRONTIER candidate to select");
        }

        double[] weights = GreenVRPCostCalculator.normalizeWeights(costWeight, co2Weight);

        double minCost = Double.POSITIVE_INFINITY;
        double maxCost = Double.NEGATIVE_INFINITY;
        double minCo2 = Double.POSITIVE_INFINITY;
        double maxCo2 = Double.NEGATIVE_INFINITY;
        for (int i : frontier) {
            double cost = candidates.get(i).getMetrics().getTotalCostVnd();
            double co2 = candidates.get(i).getMetrics().getTotalCo2Kg();
            minCost = Math.min(minCost, cost);
            maxCost = Math.max(maxCost, cost);
            minCo2 = Math.min(minCo2, co2);
            maxCo2 = Math.max(maxCo2, co2);
        }

        int best = -1;
        double bestScore = 0.0;
        double bestCost = 0.0;
        for (int i : frontier) {
            double cost = candidates.get(i).getMetrics().getTotalCostVnd();
            double co2 = candidates.get(i).getMetrics().getTotalCo2Kg();
            double normCost = maxCost > minCost ? (cost - minCost) / (maxCost - minCost) : 0.0;
            double normCo2 = maxCo2 > minCo2 ? (co2 - minCo2) / (maxCo2 - minCo2) : 0.0;
            double score = weights[0] * normCost + weights[1] * normCo2;

            if (best == -1) {
                best = i;
                bestScore = score;
                bestCost = cost;
                continue;
            }
            if (score < bestScore - TIE_EPSILON) {
                best = i;
                bestScore = score;
                bestCost = cost;
            } else if (Math.abs(score - bestScore) <= TIE_EPSILON && cost < bestCost) {
                best = i;
                bestScore = score;
                bestCost = cost;
            }
        }
        return best;
    }

    private static int servedCount(SolutionCandidate candidate) {
        Integer served = candidate.getMetrics().getOrdersServed();
        return served == null ? 0 : served;
    }

    private static double servedDemand(SolutionCandidate candidate) {
        Double demand = candidate.getMetrics().getServedDemand();
        return demand == null || !Double.isFinite(demand) ? 0.0 : demand;
    }
}
