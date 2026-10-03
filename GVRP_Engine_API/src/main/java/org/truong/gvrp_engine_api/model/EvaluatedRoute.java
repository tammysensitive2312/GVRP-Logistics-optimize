package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

/**
 * One candidate route inside a {@link RouteEvaluateRequest}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EvaluatedRoute {
    private Long vehicleId;
    private String vehicleLicensePlate;
    private VehicleType vehicleType;
    private Depot startDepot;
    private Depot endDepot;
    /** Departure from the start depot, {@code HH:mm:ss}. */
    private String startTime;
    /** Orders in intended visit order (may be empty only if the route itself is rejected). */
    private List<Order> orders;
}
