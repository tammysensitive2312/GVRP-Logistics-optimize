package org.truong.gvrp_entry_api.dto.response;

import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDTO {

    private Long id;
    private Long fleetId;
    private String vehicleLicensePlate;
    private String vehicleFeature;
    private Integer capacity;
    private Double fixedCost;
    private Double costPerKm;
    private Double costPerHour;
    private Double maxDistance;
    private Double maxDuration;
    private VehicleStatus status;
    private Long startDepotId;
    private String startDepotName;
    private Long endDepotId;
    private String endDepotName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
