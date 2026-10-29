package org.truong.gvrp_entry_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Zone membership of one order (LEZ-001 P1c, design-p1c §2.1).
 *
 * <p>Read-only and branch-scoped: the caller only ever sees its own branch's
 * enabled zones, and orders of another branch are omitted from the response.
 * {@code zones} is empty, never null, when the order lies outside every zone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderZoneMembershipDTO {

    private Long orderId;

    private List<Zone> zones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Zone {
        private Long branchZoneId;
        /** Template name for LINKED zones, copy name for COPIED zones. */
        private String name;
    }
}
