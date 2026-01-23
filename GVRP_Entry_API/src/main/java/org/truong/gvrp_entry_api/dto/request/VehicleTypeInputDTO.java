package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehicleTypeInputDTO {

    @NotNull(message = "Type name is required")
    private String typeName;
    private VehicleFeaturesDTO vehicleFeatures;
    private String description;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @DecimalMin(value = "0.0", message = "Fixed cost must be non-negative")
    private BigDecimal fixedCost;

    @DecimalMin(value = "0.0", message = "Cost per km must be non-negative")
    private BigDecimal costPerKm;

    @DecimalMin(value = "0.0", message = "Cost per hour must be non-negative")
    private BigDecimal costPerHour;

    @DecimalMin(value = "0.0", message = "Max distance must be non-negative")
    private BigDecimal maxDistance;

    @DecimalMin(value = "0.0", message = "Max duration must be non-negative")
    private BigDecimal maxDuration;
}
