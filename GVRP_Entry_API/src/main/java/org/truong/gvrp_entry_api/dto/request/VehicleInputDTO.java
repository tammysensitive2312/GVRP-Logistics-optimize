package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleInputDTO {

    @NotBlank(message = "Vehicle license plate is required")
    @Size(max = 20, message = "License plate must not exceed 20 characters")
    private String vehicleLicensePlate;

    @Size(max = 255, message = "Vehicle feature must not exceed 255 characters")
    private String vehicleFeature;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @DecimalMin(value = "0.0", message = "Fixed cost must be non-negative")
    private Double fixedCost;

    @DecimalMin(value = "0.0", message = "Cost per km must be non-negative")
    private Double costPerKm;

    @DecimalMin(value = "0.0", message = "Cost per hour must be non-negative")
    private Double costPerHour;

    @DecimalMin(value = "0.0", message = "Max distance must be non-negative")
    private Double maxDistance;

    @DecimalMin(value = "0.0", message = "Max duration must be non-negative")
    private Double maxDuration;

    @NotNull(message = "Start depot ID is required")
    private Long startDepotId;

    @NotNull(message = "End depot ID is required")
    private Long endDepotId;
}