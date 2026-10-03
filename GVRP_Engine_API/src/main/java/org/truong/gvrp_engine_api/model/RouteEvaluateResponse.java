package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

/**
 * Response of route-sequence evaluation: recalculated routes, totals and violations.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteEvaluateResponse {
    private boolean feasible;
    private List<RouteViolation> violations;
    private List<RouteDetail> routes;
    private EvaluateTotals totals;
}
