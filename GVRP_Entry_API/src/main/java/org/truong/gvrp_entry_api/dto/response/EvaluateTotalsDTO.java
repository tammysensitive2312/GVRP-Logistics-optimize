package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
public class EvaluateTotalsDTO {
    private BigDecimal totalDistance;
    private BigDecimal totalTime;
    private BigDecimal totalCost;
    private BigDecimal totalCO2;
    private Integer vehiclesUsed;
    private Integer ordersServed;
}
