package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VehicleType {
    private Long id;
    private String typeName;
    private Integer capacity;
    private Double fixedCost;
    private Double costPerKm;
    private Double costPerHour;
    private Double maxDistance;
    private Double maxDuration;
    private Double emissionFactor;
}
