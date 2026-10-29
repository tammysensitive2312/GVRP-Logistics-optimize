package org.truong.gvrp_entry_api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compliance state of one solution, as the planner panel reads it (LEZ-001, N8).
 * Nothing is written: {@code STALE} is derived by comparing the stored ruleset
 * hash with a fresh compilation of the same scope (design §16.2-6).
 *
 * <p>{@code zones} and {@code lezUnassigned} were added by P1c (design-p1c §2.2)
 * so the panel can name the zones and show why an order was left out; both come
 * from the same read, no extra evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionComplianceDTO {

    private Long solutionId;

    private List<RouteCompliance> routes;

    /** Enabled zones of the same compilation that produced the hash; empty when there are none. */
    private List<ZoneRef> zones;

    /** The solution's unassigned orders whose engine reason is an LEZ reason. */
    private List<LezUnassigned> lezUnassigned;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteCompliance {
        private Long routeId;
        private Long vehicleId;
        /** {@code UNCHECKED}, {@code COMPLIANT}, {@code VIOLATION} or {@code STALE}. */
        private String status;
        private LocalDateTime checkedAt;
        /** Violations recorded by the last check; null when compliant or never checked. */
        private JsonNode violations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneRef {
        /** Stable key {@code Z<branchZoneId>}; the same key a violation names. */
        private String key;
        private Long branchZoneId;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LezUnassigned {
        private Long orderId;
        private String orderCode;
        /** Engine reason, e.g. {@code LEZ_NO_COMPLIANT_VEHICLE}. */
        private String reasonCode;
        private String reason;
    }
}
