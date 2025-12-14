package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Service;
import org.truong.gvrp_entry_api.dto.request.VehicleFeaturesDTO;
import org.truong.gvrp_entry_api.entity.VehicleType;

/**
 * Helper service to parse vehicle features JSON
 * KISS: One place to handle JSON serialization/deserialization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleFeaturesService {

    private final ObjectMapper objectMapper;

    /**
     * Parse JSON string to DTO
     */
    public VehicleFeaturesDTO parseFeatures(String featuresJson) {
        if (featuresJson == null || featuresJson.isEmpty()) {
            return VehicleFeaturesDTO.defaultFeatures();
        }

        try {
            return objectMapper.readValue(featuresJson, VehicleFeaturesDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse vehicle features: {}", e.getMessage());
            return VehicleFeaturesDTO.defaultFeatures();
        }
    }

    /**
     * Parse features from Vehicle entity
     */
    public VehicleFeaturesDTO parseFeatures(VehicleType type) {
        return parseFeatures(type.getVehicleFeatures());
    }

    /**
     * Convert DTO to JSON string
     */
    public String toJson(VehicleFeaturesDTO features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize vehicle features: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update vehicle features
     */
    public void updateVehicleFeatures(VehicleType type, VehicleFeaturesDTO features) {
        type.setVehicleFeatures(toJson(features));
    }

    @Named("getEmissionFactor")
    public Double getEmissionFactor(VehicleType type) {
        VehicleFeaturesDTO features = parseFeatures(type);

        if (features != null && features.getEmissionFactor() != null) {
            return features.getEmissionFactor();
        }
        return 0.0;
    }
}
