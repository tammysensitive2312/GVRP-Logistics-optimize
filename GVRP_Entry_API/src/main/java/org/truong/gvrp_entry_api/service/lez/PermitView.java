package org.truong.gvrp_entry_api.service.lez;

import java.time.LocalDate;

/**
 * One permit of a vehicle for a zone, as the evaluator sees it.
 *
 * <p>A null schedule covers the whole day. Otherwise the schedule uses the
 * rule vocabulary and the permit covers the minutes the schedule does
 * <em>not</em> forbid: {@code ALLOWED_ONLY 21:00–06:00} covers 21:00–06:00.
 */
public record PermitView(
        Long id,
        RuleSchedule schedule,
        LocalDate validFrom,
        LocalDate validTo) {

    /** {@code validFrom ≤ date ≤ validTo}. */
    public boolean isValidOn(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }
}
