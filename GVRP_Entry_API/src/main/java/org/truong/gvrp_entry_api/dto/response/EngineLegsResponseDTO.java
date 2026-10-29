package org.truong.gvrp_entry_api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Engine {@code POST /api/v1/optimization/legs} response, as seen by Entry
 * (LEZ-001, design §16.2-4). One leg per consecutive point pair; distances are
 * metres and times milliseconds.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineLegsResponseDTO {

    /** Locale actually used for the instructions (the engine falls back to {@code en}). */
    private String locale;

    private List<LegDTO> legs;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LegDTO {
        /** Encoded polyline (precision 1e5, {@code [lat, lon]}); null when unroutable. */
        private String polyline;
        private Double distanceM;
        private Long timeMs;
        private List<InstructionDTO> instructions;
        /** Non-null error code, e.g. {@code ROUTE_NOT_FOUND}, when the leg could not be routed. */
        private String error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstructionDTO {
        private String text;
        private Double distanceM;
        private Long timeMs;
        private Integer sign;
        private String streetName;
    }
}
