package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@Builder
public class DriverStopDTO {
    private Integer sequenceNumber;
    private String type;
    private Long orderId;
    private String locationName;
    private String address;
    private Double latitude;
    private Double longitude;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private String customerName;
    private String customerPhone;
    private String deliveryNotes;
    private BigDecimal demand;

    // ---------- LEZ (LEZ-001, D1) ----------

    /** Encoded polyline of the leg to the next stop; null on the last stop. */
    private String geometryToNext;

    /** Turn-by-turn instructions of that leg; empty when none were produced. */
    private List<RouteLegInstructionDTO> instructionsToNext;

    /** Names of the branch's enabled zones covering this stop (current data). */
    private List<String> zoneNames;
}
