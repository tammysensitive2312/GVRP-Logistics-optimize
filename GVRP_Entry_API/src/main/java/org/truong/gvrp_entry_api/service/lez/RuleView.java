package org.truong.gvrp_entry_api.service.lez;

import java.time.LocalDate;

/** One rule as the evaluator sees it, whether it comes from a template or a branch copy. */
public record RuleView(
        Long id,
        RuleSelector selector,
        RuleSchedule schedule,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    /** {@code effectiveFrom ≤ date ≤ effectiveTo}; a null {@code effectiveTo} is open. */
    public boolean isEffectiveOn(LocalDate date) {
        return (effectiveFrom == null || !date.isBefore(effectiveFrom))
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
