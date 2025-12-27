package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Builder
public class StopDetailResponseDTO {
    private Long id;
    private Integer sequenceNumber;
    private String type; // DEPOT or ORDER
    private Long orderId; // Null if depot
    private String locationId;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private BigDecimal serviceTime;
    private BigDecimal waitTime;
    private BigDecimal demand;
    private BigDecimal loadAfter;
    private BigDecimal distanceToNext;
    private BigDecimal timeToNext;
}
