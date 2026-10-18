package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One Pareto candidate in the frontier view (PARETO-001). {@code applicable} /
 * {@code notApplicableReason} are computed relative to the latest revision.
 * The stored {@code plan_json} is deliberately not exposed.
 */
@Getter
@Setter
@Builder
public class ParetoCandidateDTO {

    private Long id;
    private Integer index;
    private String label;
    private BigDecimal costWeight;
    private BigDecimal co2Weight;
    private BigDecimal totalCost;
    private BigDecimal totalDistance;
    private BigDecimal totalCO2;
    private BigDecimal totalTime;
    private Integer totalVehiclesUsed;
    private Integer servedOrders;
    private Integer unservedOrders;
    private String frontierStatus;
    private Boolean engineSelected;
    private Boolean applicable;
    private String notApplicableReason;
}
