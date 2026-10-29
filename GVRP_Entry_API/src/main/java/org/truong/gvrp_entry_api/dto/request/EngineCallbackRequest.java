package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
     * Cancellation callback - Engine gọi khi job bị hủy theo yêu cầu (KHÔNG kèm lời giải)
     */
    @Data
    public static class CancellationCallback {

        @NotNull
        private Long jobId;

        private String reason;
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

        /**
         * Pareto block (PARETO-001). Absent/empty for single-objective callbacks, which
         * keeps the payload unchanged for those jobs.
         */
        private ParetoData pareto;
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

        /** Encoded polyline of the leg to the next stop (LEZ-001); null on the last stop. */
        private String geometryToNext;
    }

    /**
     * Unassigned order data
     */
    @Data
    public static class  UnassignedOrderData {
        private Long orderId;
        private String orderCode;
        private String reason;
    }

    /**
     * Pareto block of a multi-objective completion callback (PARETO-001).
     *
     * <p>Field names follow the existing camelCase style; the global snake_case
     * mapping turns {@code totalCO2} into {@code total_co2}, as for
     * {@link SolutionData#getTotalCO2()}.
     */
    @Data
    public static class ParetoData {

        private Integer samplerN;
        private Double samplerP;
        private Boolean clustered;
        private Integer clusterCount;
        private String selectionRule;
        private List<ParetoCandidateData> candidates;
    }

    /**
     * One candidate of a Pareto job. {@code routes} / {@code unassignedOrders} are
     * only present for {@code FRONTIER} candidates.
     */
    @Data
    public static class ParetoCandidateData {

        private Integer index;
        private String label;
        private BigDecimal costWeight;
        private BigDecimal co2Weight;
        private BigDecimal totalCost;
        private BigDecimal totalDistance;
        private BigDecimal totalCO2;
        private BigDecimal totalTime;
        private Integer totalVehiclesUsed;
        private Integer servedOrders;
        private Integer unservedOrders;
        private String frontierStatus;
        private Boolean selected;
        private List<RouteData> routes;
        private List<UnassignedOrderData> unassignedOrders;
    }
}
