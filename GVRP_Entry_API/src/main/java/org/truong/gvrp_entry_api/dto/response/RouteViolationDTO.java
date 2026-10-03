package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RouteViolationDTO {
    private String code;
    private Integer routeIndex;
    private Long orderId;
    private String detail;
}
