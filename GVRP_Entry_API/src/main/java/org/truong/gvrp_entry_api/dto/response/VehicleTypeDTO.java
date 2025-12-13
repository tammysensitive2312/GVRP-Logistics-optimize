package org.truong.gvrp_entry_api.dto.response;

import jakarta.persistence.Column;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleTypeDTO {
    private Long id;
    private String name;
    private String vehicleFeatures;
    private String description;
    private Integer capacity;
    private BigDecimal fixedCost;
    private BigDecimal costPerKm;
    private BigDecimal costPerHour;
    private BigDecimal maxDistance;
    private BigDecimal maxDuration;
}
