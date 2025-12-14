package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineVehicleDTO {
    private Long id;
    private String vehicleLicensePlate;

    private Long vehicleTypeId;
    private Long startDepotId;
    private Long endDepotId;

}
