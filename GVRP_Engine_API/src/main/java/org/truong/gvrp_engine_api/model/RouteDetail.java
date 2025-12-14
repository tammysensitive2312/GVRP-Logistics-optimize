package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RouteDetail {
    private Long vehicleId;
    private String vehicleLicensePlate;
    private String vehicleType;
    private Double emissionFactor;
    private Integer orderCount;
    private Double totalDistance;
    private Double totalTime;
    private Double totalLoad;
    private Double loadUtilization;
    private Double totalCO2;
    private List<Stop> stops;
}
