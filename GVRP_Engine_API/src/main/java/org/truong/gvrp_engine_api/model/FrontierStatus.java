package org.truong.gvrp_engine_api.model;

/**
 * Service-first classification of a candidate in a Pareto run (PAR-002).
 * <p>
 * {@code SERVICE_DOMINATED} is assigned before the (cost, CO2) comparison: a candidate that
 * serves fewer orders than the maximum served count is never eligible for the frontier, even
 * if it is the cheapest and lowest-emission point. Among the remaining candidates, (cost, CO2)
 * dominance distinguishes {@code FRONTIER} from {@code DOMINATED}.
 */
public enum FrontierStatus {
    FRONTIER,
    DOMINATED,
    SERVICE_DOMINATED
}
