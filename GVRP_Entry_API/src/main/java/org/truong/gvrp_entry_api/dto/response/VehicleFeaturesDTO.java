package org.truong.gvrp_entry_api.dto.response;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vehicle features stored as JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleFeaturesDTO {

    private String vehicleType;
    private Double emissionFactor;
    private String fuelType;

    // Optional capabilities
    private Boolean isRefrigerated;
    private Boolean isFragileCapable;
    private Boolean canAccessUrbanZone;

    public Double getEmissionFactor() {
        if (emissionFactor != null) {
            return emissionFactor;
        }
        return getDefaultEmissionFactor(vehicleType);
    }

    public boolean isElectric() {
        return "ELECTRIC".equalsIgnoreCase(fuelType);
    }

    /**
     * Get skills for Jsprit
     */
    public List<String> getSkills() {
        List<String> skills = new ArrayList<>();

        if (isElectric()) {
            skills.add("electric");
        }
        if (Boolean.TRUE.equals(isRefrigerated)) {
            skills.add("refrigerated");
        }
        if (Boolean.TRUE.equals(isFragileCapable)) {
            skills.add("fragile");
        }
        if (Boolean.TRUE.equals(canAccessUrbanZone)) {
            skills.add("urban-access");
        }

        return skills;
    }

    /**
     * Default emission factors
     */
    private static Double getDefaultEmissionFactor(String vehicleType) {
        if (vehicleType == null) return 150.0;

        return switch (vehicleType) {
            case "ELECTRIC_MOTORCYCLE", "ELECTRIC_CAR", "ELECTRIC_VAN" -> 0.0;
            case "HYBRID_CAR" -> 95.0;
            case "PETROL_MOTORCYCLE" -> 120.0;
            case "PETROL_CAR" -> 180.0;
            case "PETROL_VAN" -> 220.0;
            case "DIESEL_TRUCK" -> 280.0;
            default -> 150.0;
        };
    }

    public static VehicleFeaturesDTO defaultFeatures() {
        return VehicleFeaturesDTO.builder()
                .vehicleType("PETROL_CAR")
                .emissionFactor(180.0)
                .fuelType("PETROL")
                .isRefrigerated(false)
                .isFragileCapable(false)
                .canAccessUrbanZone(true)
                .build();
    }
}