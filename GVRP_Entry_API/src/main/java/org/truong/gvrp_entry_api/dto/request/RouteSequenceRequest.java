package org.truong.gvrp_entry_api.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class RouteSequenceRequest {
    private Long vehicleId;
    private List<Long> stopOrderIds;
}
