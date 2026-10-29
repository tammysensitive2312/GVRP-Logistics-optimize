package org.truong.gvrp_entry_api.dto.response;

import lombok.Data;
import org.truong.gvrp_entry_api.dto.request.VehicleFeaturesDTO;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;

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
    private LegalClass legalClass;
    private Integer grossWeightKg;
    private EnergyType energyType;
    private Integer emissionStandard;
    /** Read-only suggestion from the vehicle category; never written to legalClass. */
    private LegalClass suggestedLegalClass;
}
