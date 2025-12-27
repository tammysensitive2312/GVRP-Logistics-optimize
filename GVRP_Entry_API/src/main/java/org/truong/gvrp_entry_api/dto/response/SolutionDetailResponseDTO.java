package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class SolutionDetailResponseDTO {
    private Long id;
    private Long jobId;
    private Long branchId;
    private String status;
    private String type;
    private BigDecimal totalCost;
    private BigDecimal totalDistance;
    private BigDecimal totalCO2;
    private BigDecimal totalTime;
    private Integer totalVehiclesUsed;
    private Integer servedOrders;
    private Integer unservedOrders;
    private String errorMessage;
    private LocalDateTime createdAt;
    private List<RouteDetailResponseDTO> routes;
}