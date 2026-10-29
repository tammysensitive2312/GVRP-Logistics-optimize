package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response of {@code POST /api/v1/optimization/legs}: one leg per consecutive
 * point pair, plus the locale actually used for the instructions.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteLegsResponse {
    private String locale;
    private List<RouteLeg> legs;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouteLeg {
        private String polyline;
        private Double distanceM;
        private Long timeMs;
        private List<LegInstruction> instructions;
        /** Non-null when this leg could not be routed; the request still succeeds. */
        private String error;
    }
}
