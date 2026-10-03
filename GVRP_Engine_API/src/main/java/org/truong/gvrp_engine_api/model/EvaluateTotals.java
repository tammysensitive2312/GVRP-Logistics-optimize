package org.truong.gvrp_engine_api.model;

import lombok.*;

/**
 * Solution-level totals of an evaluation, same units as the solve path
 * (distance km, time hours, money VND, CO2 kg).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EvaluateTotals {
    private Double totalDistance;
    private Double totalTime;
    private Double totalCost;
    private Double fuelCost;
    private Double timeCost;
    private Double fixedCost;
    private Double totalCO2;
    private Integer vehiclesUsed;
    private Integer ordersServed;
}
