package org.truong.gvrp_entry_api.service.lez;

import java.util.List;

/**
 * Result of evaluating one vehicle against one zone on one date (design §3.3).
 *
 * @param forbidden         minutes of the day the vehicle may not be in the zone
 * @param missingAttributes vehicle facts that were unknown and decided a matching rule
 * @param appliedPermitIds  permits that removed forbidden minutes
 */
public record ZoneVerdict(
        Kind kind,
        List<MinuteInterval> forbidden,
        List<String> missingAttributes,
        List<Long> appliedPermitIds) {

    public enum Kind {
        ALLOWED,
        ALL_DAY,
        PARTIAL
    }
}
