package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Service;
import org.truong.gvrp_entry_api.dto.request.VehicleFeaturesDTO;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;
import java.util.Set;
import org.truong.gvrp_entry_api.entity.enums.VehicleSkill;

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
            throw new DataInvalidException(
                    List.of(
                            ErrorDetail.builder()
                                    .code(ErrorCode.EMPTY_FIELD_ERROR.getCode())
                                    .message(ErrorCode.EMPTY_FIELD_ERROR.getMessage())
                                    .resource(AppConstant.VEHICLE_FEATURES)
                                    .build()
                    )
            );
        }
        try {
            return objectMapper.readValue(featuresJson, VehicleFeaturesDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Corrupted vehicle features JSON: {}", e.getMessage());
            throw new DataInvalidException(
                    List.of(
                            ErrorDetail.builder()
                                    .code(ErrorCode.BACKEND_SERVER_ERROR.getCode())
                                    .message(ErrorCode.BACKEND_SERVER_ERROR.getMessage())
                                    .resource(AppConstant.VEHICLE_FEATURES)
                                    .build()
                    )
            );
        }
    }

    /**
     * Parse features from Vehicle entity
     */
    public VehicleFeaturesDTO parseFeatures(VehicleType type) {
        return parseFeatures(type.getVehicleFeatures());
    }

    @Named("getFeatures")
    public VehicleFeaturesDTO getFeatures(VehicleType type) {
        return parseFeatures(type);
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

    @Named("getEmissionFactor")
    public Double getEmissionFactor(VehicleType type) {
        VehicleFeaturesDTO features = parseFeatures(type);
        return features != null ? features.getEmissionFactor() : null;
    }

    @Named("getSkills")
    public Set<VehicleSkill> getSkills(VehicleType type) {
        VehicleFeaturesDTO features = parseFeatures(type);
        return features.getSkills() == null ? Set.of() : Set.copyOf(features.getSkills());
    }
}
