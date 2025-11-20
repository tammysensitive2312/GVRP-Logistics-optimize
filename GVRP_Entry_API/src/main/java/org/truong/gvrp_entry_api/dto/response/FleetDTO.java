package org.truong.gvrp_entry_api.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetDTO {

    private Long id;
    private Long branchId;
    private String fleetName;
    private List<VehicleDTO> vehicles;
    private Integer totalCapacity;
    private Integer vehicleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
