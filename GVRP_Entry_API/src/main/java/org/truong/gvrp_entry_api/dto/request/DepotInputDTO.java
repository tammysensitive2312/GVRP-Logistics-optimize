package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for {@link org.truong.gvrp_entry_api.entity.Depot}
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class DepotInputDTO {
    @Size(message = "Depot name must be between 2 and 100 characters", min = 2, max = 100)
    @NotBlank(message = "Depot name is required")
    private String name;

    @Size(message = "Address must be between 5 and 255 characters", min = 5, max = 255)
    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;
}