package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DriverRouteDTO {
    private Long sendId;
    private Long solutionId;
    private Long routeId;
    private String vehicleLicensePlate;
    private String status;
    private List<DriverStopDTO> stops;
}
