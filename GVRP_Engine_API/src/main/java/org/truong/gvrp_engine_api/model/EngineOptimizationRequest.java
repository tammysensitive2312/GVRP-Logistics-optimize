package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EngineOptimizationRequest {
    private Long jobId;
    private List<Depot> depots;
    private List<Order> orders;
    private List<VehicleType> vehicleTypes;
    private List<Vehicle> vehicles;

    private OptimizationConfig config;
}
