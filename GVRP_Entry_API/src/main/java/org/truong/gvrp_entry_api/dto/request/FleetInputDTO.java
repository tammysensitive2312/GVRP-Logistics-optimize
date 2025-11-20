package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * DTO for {@link org.truong.gvrp_entry_api.entity.Fleet}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetInputDTO {

    @NotBlank(message = "Fleet name is required")
    @Size(max = 100, message = "Fleet name must not exceed 100 characters")
    private String fleetName;

    @NotNull(message = "Vehicles list is required")
    @NotEmpty(message = "Fleet must have at least one vehicle")
    @Valid
    private List<VehicleInputDTO> vehicles;
}