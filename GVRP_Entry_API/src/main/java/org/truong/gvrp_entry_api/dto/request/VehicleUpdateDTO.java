package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class VehicleUpdateDTO extends VehicleInputDTO {
    private VehicleStatus status;

    public VehicleUpdateDTO(
            @NotBlank(message = "Vehicle license plate is required")
            @Size(max = 20, message = "License plate must not exceed 20 characters")
            String vehicleLicensePlate,
            @NotNull
            Long vehicleTypeId,
            @NotNull(message = "Start depot ID is required")
            Long startDepotId,
            @NotNull(message = "End depot ID is required")
            Long endDepotId,
            VehicleStatus status) {
        super(vehicleLicensePlate, vehicleTypeId, startDepotId, endDepotId);
        this.status = status;
    }
}
