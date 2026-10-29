package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request of {@code POST /api/v1/optimization/legs}: ordered points plus an
 * optional instruction locale (design §16.2-4).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteLegsRequest {
    private List<Point> points;
    private String locale;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Point {
        private Double latitude;
        private Double longitude;
    }
}
