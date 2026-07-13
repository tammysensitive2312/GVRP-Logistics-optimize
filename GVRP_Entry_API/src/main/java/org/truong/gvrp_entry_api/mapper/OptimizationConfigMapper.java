package org.truong.gvrp_entry_api.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.util.AppConstant;

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

        engineConfig.setEnableParetoAnalysis(userPrefs.getEnableParetoAnalysis());

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
                config.setMaxIterations(800);
                config.setTimeoutSeconds(180);  // 3 minutes
                config.setNumThreads(1);
                break;
            case NORMAL:
                config.setMaxIterations(2000);
                config.setTimeoutSeconds(480);  // 8 minutes
                config.setNumThreads(2);
                break;
            case HIGH_QUALITY:
                config.setMaxIterations(5000);
                config.setTimeoutSeconds(900);  // 15 minutes
                config.setNumThreads(4);
                break;
            default:
                config.setMaxIterations(2000);
                config.setTimeoutSeconds(480);
                config.setNumThreads(2);
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
                config.setCostWeight(1.0);
                config.setCo2Weight(0.0);
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
                config.setCostWeight(AppConstant.EPSILON);
                config.setCo2Weight(1.0);
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
}