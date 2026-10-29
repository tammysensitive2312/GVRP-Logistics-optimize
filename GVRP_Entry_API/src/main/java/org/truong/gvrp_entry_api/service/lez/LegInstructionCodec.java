package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.truong.gvrp_entry_api.dto.response.EngineLegsResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteLegInstructionDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts engine turn instructions to the public DTO and to the JSON text stored
 * in {@code route_stops.instructions_to_next} (LEZ-001, N7/N8).
 *
 * <p>Static and dependency-free: the letters of a leg are data, not policy.
 */
public final class LegInstructionCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<RouteLegInstructionDTO>> LIST_TYPE = new TypeReference<>() {
    };

    private LegInstructionCodec() {
    }

    /** Engine instructions → public DTO; null stays null. */
    public static List<RouteLegInstructionDTO> toDto(List<EngineLegsResponseDTO.InstructionDTO> engine) {
        if (engine == null) {
            return null;
        }
        List<RouteLegInstructionDTO> result = new ArrayList<>(engine.size());
        for (EngineLegsResponseDTO.InstructionDTO instruction : engine) {
            if (instruction == null) {
                continue;
            }
            result.add(RouteLegInstructionDTO.builder()
                    .text(instruction.getText())
                    .distanceM(instruction.getDistanceM())
                    .timeMs(instruction.getTimeMs())
                    .sign(instruction.getSign())
                    .streetName(instruction.getStreetName())
                    .build());
        }
        return result;
    }

    /** JSON text for storage; null when there is nothing to store. */
    public static String toJson(List<EngineLegsResponseDTO.InstructionDTO> engine) {
        List<RouteLegInstructionDTO> dto = toDto(engine);
        if (dto == null || dto.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize leg instructions", e);
        }
    }

    /**
     * JSON text → DTO list. Unreadable or absent text yields an empty list: a
     * client that cannot render instructions still gets the route.
     */
    public static List<RouteLegInstructionDTO> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
