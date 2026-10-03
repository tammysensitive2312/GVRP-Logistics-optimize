package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RouteEvaluateResponseDTO {
    private boolean feasible;
    private List<RouteViolationDTO> violations;
    private List<RouteDetailResponseDTO> routes;
    private EvaluateTotalsDTO totals;
    /** Non-blocking notes, e.g. newly unassigned orders (RC-011 warn-only). */
    private List<String> warnings;
}
