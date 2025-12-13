package org.truong.gvrp_entry_api.dto.response;

import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class VehicleDTO {

    private Long id;
    private Long fleetId;
    private String vehicleLicensePlate;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private VehicleStatus status;
    private Long startDepotId;
    private String startDepotName;
    private Long endDepotId;
    private String endDepotName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
