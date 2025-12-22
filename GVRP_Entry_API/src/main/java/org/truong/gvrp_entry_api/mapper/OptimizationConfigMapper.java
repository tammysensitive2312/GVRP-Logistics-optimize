package org.truong.gvrp_entry_api.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;

@Component
@Slf4j
public class OptimizationConfigMapper {

    /**
     * Map user preferences to engine config
     */
    public EngineOptimizationRequest.OptimizationConfig toEngineConfig(
            RoutePlanningRequest.OptimizationPreferences userPrefs) {

        if (userPrefs == null) {
            return getDefaultConfig();
        }

        EngineOptimizationRequest.OptimizationConfig engineConfig =
                new EngineOptimizationRequest.OptimizationConfig();

        // Map speed → iterations & timeout
        mapSpeed(userPrefs.getSpeed(), engineConfig);

        // Map goal → objective function weights
        mapGoal(userPrefs.getGoal(), engineConfig);

        // Map time window mode
        engineConfig.setStrictTimeWindows(
                userPrefs.getTimeWindowMode() == RoutePlanningRequest.TimeWindowMode.STRICT
        );

        // Map unassigned penalty
        engineConfig.setUnassignedJobPenalty(
                userPrefs.getAllowUnassignedOrders() ? 100.0 : 10000.0
        );

        engineConfig.setNumThreads(4);

        return engineConfig;
    }

    /**
     * Map speed → iterations & timeout
     */
    private void mapSpeed(
            RoutePlanningRequest.OptimizationSpeed speed,
            EngineOptimizationRequest.OptimizationConfig config) {

        switch (speed) {
            case FAST:
                config.setMaxIterations(500);
                config.setTimeoutSeconds(180);  // 3 minutes
                break;
            case NORMAL:
                config.setMaxIterations(2000);
                config.setTimeoutSeconds(480);  // 8 minutes
                break;
            case HIGH_QUALITY:
                config.setMaxIterations(5000);
                config.setTimeoutSeconds(900);  // 15 minutes
                break;
            default:
                config.setMaxIterations(2000);
                config.setTimeoutSeconds(480);
        }
    }

    /**
     * Map goal → objective function weights
     */
    private void mapGoal(
            RoutePlanningRequest.OptimizationGoal goal,
            EngineOptimizationRequest.OptimizationConfig config) {

        if (goal == null) {
            goal = RoutePlanningRequest.OptimizationGoal.MINIMIZE_COST;
        }

        switch (goal) {
            case MINIMIZE_COST:
                // Traditional VRP: Focus on monetary cost
                // Prefer: cheap fuel, short time, low fixed costs
                // CO2 is secondary consideration
                config.setCostWeight(0.9);
                config.setCo2Weight(0.1);
                break;

            case MINIMIZE_DISTANCE:
                // Distance is part of both cost and CO2
                // So we balance them equally
                // Shorter routes = less fuel cost + less CO2
                config.setCostWeight(0.5);
                config.setCo2Weight(0.5);
                break;

            case MINIMIZE_CO2:
                // GREEN VRP: Focus on environmental impact
                // Prefer: electric vehicles, shorter routes, efficient routing
                // Cost is secondary (within acceptable limits)
                config.setCostWeight(0.1);
                config.setCo2Weight(0.9);
                break;

            case BALANCED:
                // Equal consideration for cost and environmental impact
                // Good for companies with ESG commitments
                config.setCostWeight(0.5);
                config.setCo2Weight(0.5);
                break;

            default:
                // Default: Cost-focused (traditional VRP)
                config.setCostWeight(0.7);
                config.setCo2Weight(0.3);
        }
    }

    /**
     * Default config khi user không specify preferences
     */
    private EngineOptimizationRequest.OptimizationConfig getDefaultConfig() {
        EngineOptimizationRequest.OptimizationConfig config =
                new EngineOptimizationRequest.OptimizationConfig();

        config.setMaxIterations(2000);
        config.setTimeoutSeconds(480);
        config.setNumThreads(4);
        config.setCostWeight(0.7);
        config.setCo2Weight(0.3);
        config.setStrictTimeWindows(true);
        config.setUnassignedJobPenalty(10000.0);
        config.setEnableParetoAnalysis(false);

        return config;
    }

    /**
     * Validate config before sending to engine
     *
     * Ensures all required fields are set and values are valid
     */
    public void validateConfig(EngineOptimizationRequest.OptimizationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        // Validate weights
        if (config.getCostWeight() == null || config.getCo2Weight() == null) {
            throw new IllegalArgumentException("Cost and CO2 weights are required");
        }

        if (config.getCostWeight() < 0 || config.getCo2Weight() < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }

        double sum = config.getCostWeight() + config.getCo2Weight();
        if (sum == 0) {
            throw new IllegalArgumentException("At least one weight must be > 0");
        }

        // Warn if distanceWeight is set (deprecated)
        if (config.getDistanceWeight() != null && config.getDistanceWeight() > 0) {
            log.warn("⚠️  distanceWeight is DEPRECATED and will be ignored. " +
                    "Use costWeight and co2Weight instead.");
        }

        // Validate algorithm parameters
        if (config.getMaxIterations() != null && config.getMaxIterations() < 100) {
            log.warn("MaxIterations {} is very low, may produce poor results",
                    config.getMaxIterations());
        }

        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() < 60) {
            log.warn("Timeout {}s is very short, optimization may not complete",
                    config.getTimeoutSeconds());
        }

        log.debug("Config validated successfully");
    }
}