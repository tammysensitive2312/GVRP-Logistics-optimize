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

    @NotNull
    private Long vehicleTypeId;

    @NotNull(message = "Start depot ID is required")
    private Long startDepotId;

    @NotNull(message = "End depot ID is required")
    private Long endDepotId;
}