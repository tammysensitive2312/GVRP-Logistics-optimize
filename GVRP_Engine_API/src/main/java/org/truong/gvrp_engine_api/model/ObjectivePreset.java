package org.truong.gvrp_engine_api.model;

import lombok.Getter;
import lombok.ToString;

/**
 * Objective Preset - Pre-defined weight combinations
 * <p>
 * Provides common optimization strategies
 */
@Getter
@ToString
public enum ObjectivePreset {

    /**
     * Cost-focused: Minimize monetary cost (traditional VRP)
     * Use when: Budget is tight, carbon tax is low
     */
    COST_FOCUSED("Cost Minimization", 0.9, 0.1),

    /**
     * Balanced: Equal weight to cost and emissions
     * Use when: Want reasonable trade-off between cost and environment
     */
    BALANCED("Balanced Optimization", 0.5, 0.5),

    /**
     * Eco-focused: Minimize CO2 emissions (GREEN VRP)
     * Use when: Strong ESG commitment, high carbon tax, green branding
     */
    ECO_FOCUSED("Eco Minimization", 0.2, 0.8),

    /**
     * Pure eco: Only minimize emissions
     * Use when: Carbon neutrality mandate, regulations
     */
    PURE_ECO("Pure Green", 0.0, 1.0);

    private final String displayName;
    public final double costWeight;
    public final double co2Weight;

    ObjectivePreset(String displayName, double costWeight, double co2Weight) {
        this.displayName = displayName;
        this.costWeight = costWeight;
        this.co2Weight = co2Weight;
    }

}

