package org.truong.gvrp_entry_api.entity.enums;

/**
 * Classification of a Pareto candidate (PARETO-001).
 *
 * <p>Service-first: a candidate serving fewer orders than the maximum is
 * {@code SERVICE_DOMINATED} regardless of (cost, CO2); among the remaining
 * candidates, (cost, CO2) dominance yields {@code FRONTIER} / {@code DOMINATED}.
 */
public enum ParetoFrontierStatus {
    FRONTIER,
    DOMINATED,
    SERVICE_DOMINATED
}
