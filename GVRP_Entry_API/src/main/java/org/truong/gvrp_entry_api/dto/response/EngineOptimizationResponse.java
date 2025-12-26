package org.truong.gvrp_entry_api.dto.response;

import lombok.Data;

@Data
public class EngineOptimizationResponse {
    private String externalJobId;
    private String status;
    private String message;
}
