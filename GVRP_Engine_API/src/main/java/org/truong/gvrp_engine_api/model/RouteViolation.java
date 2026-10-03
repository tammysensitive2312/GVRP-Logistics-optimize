package org.truong.gvrp_engine_api.model;

import lombok.*;

/**
 * One feasibility violation found while evaluating a candidate route.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteViolation {
    /** OVER_CAPACITY, TIME_WINDOW, SKILL_MISMATCH, MAX_DISTANCE, ROUTING_UNAVAILABLE. */
    private String code;
    private Integer routeIndex;
    private Long orderId;
    private String detail;
}
