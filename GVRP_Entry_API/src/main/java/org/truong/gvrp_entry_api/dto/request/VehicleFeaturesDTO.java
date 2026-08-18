package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.VehicleCategory;
import org.truong.gvrp_entry_api.entity.enums.VehicleSkill;

import java.util.HashSet;
import java.util.Set;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleFeaturesDTO {

    private VehicleCategory category;

    @NotNull(message = "Emission factor is required")
    @DecimalMin(value = "0.0", message = "Emission factor must be non-negative")
    private Double emissionFactor;

    @Builder.Default
    private Set<VehicleSkill> skills = new HashSet<>();
}