package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

/**
 * Request to evaluate candidate route sequences without solving.
 *
 * <p>Each route carries its vehicle type data, depots, start time and the orders
 * in intended visit order. Depot legs are derived from the vehicle depots.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteEvaluateRequest {
    private List<EvaluatedRoute> routes;
}
