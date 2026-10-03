package org.truong.gvrp_entry_api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Engine {@code POST /api/v1/optimization/evaluate} response, as seen by Entry.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineEvaluateResponseDTO {
    private boolean feasible;
    private List<EngineViolationDTO> violations;
    private List<EngineEvaluatedRouteDTO> routes;
    private EngineEvaluateTotalsDTO totals;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EngineViolationDTO {
        private String code;
        private Integer routeIndex;
        private Long orderId;
        private String detail;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EngineEvaluatedRouteDTO {
        private Long vehicleId;
        private String vehicleLicensePlate;
        private String vehicleType;
        private Double emissionFactor;
        private Integer orderCount;
        private Double totalDistance;
        private Double totalTime;
        private Double totalLoad;
        private Double loadUtilization;
        private Double totalCO2;
        private List<EngineEvaluatedStopDTO> stops;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EngineEvaluatedStopDTO {
        private String type;
        private Integer sequenceNumber;
        private Long orderId;
        private String orderCode;
        private String locationId;
        private String locationName;
        private Double demand;
        private String arrivalTime;
        private String departureTime;
        private Double serviceTime;
        private Double waitTime;
        private Double loadAfter;
        private Double distanceToNext;
        private Double timeToNext;
        private Double latitude;
        private Double longitude;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EngineEvaluateTotalsDTO {
        private Double totalDistance;
        private Double totalTime;
        private Double totalCost;
        private Double fuelCost;
        private Double timeCost;
        private Double fixedCost;
        private Double totalCO2;
        private Integer vehiclesUsed;
        private Integer ordersServed;
    }
}
