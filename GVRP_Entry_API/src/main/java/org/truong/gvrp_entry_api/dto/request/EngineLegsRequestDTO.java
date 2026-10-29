package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request of the engine {@code POST /api/v1/optimization/legs} endpoint
 * (LEZ-001, design §16.2-4): ordered points plus an optional instruction locale.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineLegsRequestDTO {

    private List<Point> points = new ArrayList<>();

    /** Language tag for turn instructions, e.g. {@code vi}; null means the engine default. */
    private String locale;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Double latitude;
        private Double longitude;
    }
}
