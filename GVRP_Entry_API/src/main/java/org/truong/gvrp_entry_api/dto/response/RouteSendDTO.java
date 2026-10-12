package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class RouteSendDTO {
    private Long id;
    private Long solutionId;
    private Long routeId;
    private String vehicleLicensePlate;
    private Long driverId;
    private String driverName;
    private String senderName;
    private LocalDateTime sentAt;
    private String status;
}
