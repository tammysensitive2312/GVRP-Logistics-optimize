package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Request gửi tới Engine API
 * Chứa technical configuration mà engine cần
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineOptimizationRequest {

    private Long jobId;

    private List<EngineDepotDTO> depots;
    private List<EngineOrderDTO> orders;
    private List<EngineVehicleTypeDTO> vehicleTypes;
    private List<EngineVehicleDTO> vehicles;

    // Technical configuration for engine
    private OptimizationConfig config;


    @Data
    public static class OptimizationConfig {

        // Algorithm parameters
        private Integer maxIterations;      // e.g., 500, 2000, 5000
        private Integer timeoutSeconds;     // e.g., 180, 480, 900
        private Integer numThreads = 1;         // Parallel processing threads


        @Deprecated
        private Double distanceWeight;
        // Objective function weights (sum = 1.0)
        private Double costWeight;          // e.g., 0.7
        private Double co2Weight;           // e.g., 0.1

        // Constraints
        private Boolean strictTimeWindows;  // true = hard, false = soft
        private Double unassignedJobPenalty; // e.g., 10000 or 100

        private Boolean enableParetoAnalysis;
    }
}
