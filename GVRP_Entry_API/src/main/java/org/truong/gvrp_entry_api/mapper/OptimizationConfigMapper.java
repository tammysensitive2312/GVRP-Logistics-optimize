package org.truong.gvrp_entry_api.mapper;

import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;

@Component
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

        switch (goal) {
            case MINIMIZE_COST:
                // Cost: 70%, Distance: 20%, CO2: 10%
                config.setCostWeight(0.7);
                config.setDistanceWeight(0.2);
                config.setCo2Weight(0.1);
                break;

            case MINIMIZE_DISTANCE:
                // Distance: 70%, Cost: 20%, CO2: 10%
                config.setCostWeight(0.2);
                config.setDistanceWeight(0.7);
                config.setCo2Weight(0.1);
                break;

            case MINIMIZE_CO2:
                // CO2: 70%, Distance: 20%, Cost: 10%
                config.setCostWeight(0.1);
                config.setDistanceWeight(0.2);
                config.setCo2Weight(0.7);
                break;

            case BALANCED:
                // Equal weights
                config.setCostWeight(0.33);
                config.setDistanceWeight(0.33);
                config.setCo2Weight(0.34);
                break;

            default:
                // Default: Minimize cost
                config.setCostWeight(0.7);
                config.setDistanceWeight(0.2);
                config.setCo2Weight(0.1);
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
        config.setCostWeight(0.7);
        config.setDistanceWeight(0.2);
        config.setCo2Weight(0.1);
        config.setStrictTimeWindows(true);
        config.setUnassignedJobPenalty(10000.0);

        return config;
    }
}