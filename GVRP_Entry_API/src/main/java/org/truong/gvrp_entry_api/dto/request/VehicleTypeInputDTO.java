package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;

import java.math.BigDecimal;

@Data
public class VehicleTypeInputDTO {

    @NotNull(message = "Type name is required")
    private String typeName;
    private String description;
    /**
     * Vehicle features. The canonical wire name is {@code features}; both web
     * clients send the response field name {@code vehicle_features} instead, so
     * the alias keeps that payload valid (LEZ-001 P1c follow-up).
     */
    @JsonAlias("vehicle_features")
    @Valid
    private VehicleFeaturesDTO features;

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

    // Legal attributes (LEZ-R04), all optional; validated in VehicleTypeService.
    private LegalClass legalClass;
    private Integer grossWeightKg;
    private EnergyType energyType;
    private Integer emissionStandard;
}
