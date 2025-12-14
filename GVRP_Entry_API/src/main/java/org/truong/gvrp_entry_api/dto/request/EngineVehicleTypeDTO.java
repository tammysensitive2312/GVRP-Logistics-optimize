package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineVehicleTypeDTO {
    private Long id;
    private String typeName;

    private Integer capacity;
    private BigDecimal fixedCost;
    private BigDecimal costPerKm;
    private BigDecimal costPerHour;

    private BigDecimal maxDistance;
    private BigDecimal maxDuration;

    private Double emissionFactor;
}
