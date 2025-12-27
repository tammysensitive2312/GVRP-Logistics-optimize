package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@Builder
public class RouteDetailResponseDTO {
    private Long id;
    private Integer routeOrder;
    private Long vehicleId;
    private String vehicleLicensePlate;
    private BigDecimal distance;
    private BigDecimal co2Emission;
    private BigDecimal serviceTime;
    private Integer orderCount;
    private BigDecimal loadUtilization;
    private LocalTime startTime;
    private LocalTime endTime;
    private List<StopDetailResponseDTO> stops;
}