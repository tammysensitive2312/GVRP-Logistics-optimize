package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Payload sent to the engine {@code POST /api/v1/optimization/evaluate}.
 * Reuses the same engine DTO shapes as the solve path.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineEvaluateRequestDTO {
    private List<EngineEvaluateRouteDTO> routes;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EngineEvaluateRouteDTO {
        private Long vehicleId;
        private String vehicleLicensePlate;
        private EngineVehicleTypeDTO vehicleType;
        private EngineDepotDTO startDepot;
        private EngineDepotDTO endDepot;
        private String startTime;
        private List<EngineOrderDTO> orders;
    }
}
