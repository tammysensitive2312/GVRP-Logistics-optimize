package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Stop {
    private String type;
    private Integer sequenceNumber;
    private Long orderId;
    private String orderCode;
    private String locationId;
    private String locationName;
    private Double demand;
    private String arrivalTime;
    private String departureTime;
    private Double serviceTime;  // minutes
    private Double waitTime;     // minutes
    private Double loadAfter;    // kg
    private Double distanceToNext;
    private Double timeToNext;
    private Double latitude;
    private Double longitude;
    /**
     * Encoded polyline (precision 1e5, {@code [lat, lon]}) of the leg to the next
     * stop; null on the last stop or when the leg could not be routed (LEZ-001).
     */
    private String geometryToNext;
}
