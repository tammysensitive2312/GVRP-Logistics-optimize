package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for Engine callbacks
 */
public class EngineCallbackRequest {

    /**
     * Completion callback - Engine gọi khi optimization xong
     */
    @Data
    public static class CompletionCallback {

        @NotNull
        private Long jobId;

        @NotNull
        private SolutionData solution;

        private Long processingTimeSeconds;
    }

    /**
     * Failure callback - Engine gọi khi optimization thất bại
     */
    @Data
    public static class FailureCallback {

        @NotNull
        private Long jobId;

        @NotNull
        private String errorMessage;

        private String errorType;  // e.g., "TIMEOUT", "INFEASIBLE", "INTERNAL_ERROR"

        private Long processingTimeSeconds;
    }

    /**
     * Progress callback - Engine gọi để update progress
     */
    @Data
    public static class ProgressCallback {

        @NotNull
        private Long jobId;

        @NotNull
        private Integer progress;  // 0-100

        private String currentPhase;  // e.g., "INITIALIZING", "OPTIMIZING", "FINALIZING"

        private Integer estimatedRemainingSeconds;
    }

    /**
     * Solution data structure
     */
    @Data
    public static class SolutionData {

        private List<RouteData> routes;
        private List<UnassignedOrderData> unassignedOrders;

        // Metrics
        private BigDecimal totalDistance;  // km
        private BigDecimal totalCost;      // VND
        private BigDecimal totalCO2;       // kg
        private BigDecimal totalTime;      // hours
        private BigDecimal totalServiceTime; // hours

        private Integer totalVehiclesUsed;
        private Integer servedOrders;
        private Integer unservedOrders;
    }

    /**
     * Route data
     */
    @Data
    public static class RouteData {

        private Long vehicleId;
        private Integer routeOrder;  // Sequence: 0, 1, 2, ...

        private List<StopData> stops;

        // Route metrics
        private BigDecimal distance;
        private BigDecimal duration;
        private BigDecimal co2Emission;
        private Integer orderCount;
        private BigDecimal loadUtilization;  // %
    }

    /**
     * Stop data
     */
    @Data
    public static class StopData {

        private String type;  // "DEPOT" or "ORDER"
        private Integer sequenceNumber;

        // Order info (if type = ORDER)
        private Long orderId;
        private String orderCode;

        // Location info
        private String locationId;  // depot_id or order_id
        private String locationName;
        private Double longitude;
        private Double latitude;

        // Timing
        private String arrivalTime;   // ISO 8601 or HH:mm:ss
        private String departureTime; // ISO 8601 or HH:mm:ss
        private BigDecimal serviceTime;  // seconds
        private BigDecimal waitTime;     // seconds

        // Load
        private BigDecimal demand;
        private BigDecimal loadAfter;

        private BigDecimal distanceToNext;
        private BigDecimal timeToNext;
    }

    /**
     * Unassigned order data
     */
    @Data
    public static class UnassignedOrderData {

        private Long orderId;
        private String orderCode;
        private String reason;  // e.g., "CAPACITY_EXCEEDED", "TIME_WINDOW_VIOLATED"
    }
}
