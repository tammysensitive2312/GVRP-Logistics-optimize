package org.truong.gvrp_entry_api.dto.response;

import lombok.Data;
import org.truong.gvrp_entry_api.dto.request.VehicleFeaturesDTO;

import java.math.BigDecimal;

@Data
public class VehicleTypeDTO {
    private Long id;
    private String name;
    private VehicleFeaturesDTO vehicleFeatures;
    private String description;
    private Integer capacity;
    private BigDecimal fixedCost;
    private BigDecimal costPerKm;
    private BigDecimal costPerHour;
    private BigDecimal maxDistance;
    private BigDecimal maxDuration;
}
