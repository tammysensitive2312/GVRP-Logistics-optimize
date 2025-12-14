package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Vehicle {
    private Long id;
    private String vehicleLicensePlate;
    private Long vehicleTypeId;
    private Long startDepotId;
    private Long endDepotId;
}
