package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OptimizationConfig {
    private Integer maxIterations;
    private Integer timeoutSeconds;
    private Double costWeight;
    private Double distanceWeight;
    private Double co2Weight;
    private Boolean strictTimeWindows;
    private Double unassignedJobPenalty;
    private Integer numThreads;
}
