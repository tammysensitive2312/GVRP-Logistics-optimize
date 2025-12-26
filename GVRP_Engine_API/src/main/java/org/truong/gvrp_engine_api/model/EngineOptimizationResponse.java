package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EngineOptimizationResponse {
    private String externalJobId;
    private String status;  // ACCEPTED, REJECTED, ERROR
    private String message;
}
