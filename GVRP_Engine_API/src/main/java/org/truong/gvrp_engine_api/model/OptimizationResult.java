package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OptimizationResult {
    private Long jobId;

    // Summary
    private Double totalCost;
    private Double totalDistance;  // km
    private Double totalTime;      // hours
    private Double totalCO2;       // kg
    private Integer totalVehiclesUsed;
    private Integer totalOrdersServed;
    private Integer totalOrdersUnassigned;

    // Details
    private List<RouteDetail> routes;
    private List<UnassignedOrder> unassignedOrders;
}
