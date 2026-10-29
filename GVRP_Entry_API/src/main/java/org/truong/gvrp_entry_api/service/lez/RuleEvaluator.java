package org.truong.gvrp_entry_api.service.lez;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides when one vehicle may not be in one zone on one date (design §3.3).
 * Pure function: no persistence, no Spring.
 *
 * <ol>
 *   <li>Keep rules effective on the date, then rules whose weekday filter names it.</li>
 *   <li>Keep rules whose selector matches; an unknown fact needed by a present
 *       selector field counts as a match and is reported in {@code missingAttributes}.</li>
 *   <li>Forbidden = union of the matching rules' forbidden minutes.</li>
 *   <li>Subtract the windows of permits valid on the date.</li>
 *   <li>Empty → {@code ALLOWED}; whole day → {@code ALL_DAY}; otherwise {@code PARTIAL}.</li>
 * </ol>
 */
public final class RuleEvaluator {

    public static final String ATTR_LEGAL_CLASS = "legalClass";
    public static final String ATTR_ENERGY_TYPE = "energyType";
    public static final String ATTR_GROSS_WEIGHT = "grossWeightKg";
    public static final String ATTR_CAPACITY = "capacity";
    public static final String ATTR_EMISSION_STANDARD = "emissionStandard";

    private RuleEvaluator() {
    }

    public static ZoneVerdict evaluate(VehicleFacts vehicle,
                                       List<RuleView> rules,
                                       List<PermitView> permits,
                                       LocalDate date,
                                       boolean branchAppBased) {
        Set<String> missing = new LinkedHashSet<>();
        List<MinuteInterval> forbidden = new ArrayList<>();

        for (RuleView rule : rules) {
            if (!rule.isEffectiveOn(date) || !rule.schedule().appliesOn(date.getDayOfWeek())) {
                continue;
            }
            Set<String> ruleMissing = new LinkedHashSet<>();
            if (!matches(rule.selector(), vehicle, branchAppBased, ruleMissing)) {
                continue;
            }
            missing.addAll(ruleMissing);
            forbidden.addAll(ScheduleNormalizer.forbidden(rule.schedule()));
        }
        List<MinuteInterval> remaining = ScheduleNormalizer.merge(forbidden);

        List<Long> applied = new ArrayList<>();
        for (PermitView permit : permits) {
            if (remaining.isEmpty()) {
                break;
            }
            if (!permit.isValidOn(date)) {
                continue;
            }
            List<MinuteInterval> window = permitWindow(permit, date);
            List<MinuteInterval> reduced = ScheduleNormalizer.subtract(remaining, window);
            if (!reduced.equals(remaining)) {
                applied.add(permit.id());
                remaining = reduced;
            }
        }

        ZoneVerdict.Kind kind;
        if (remaining.isEmpty()) {
            kind = ZoneVerdict.Kind.ALLOWED;
        } else if (ScheduleNormalizer.isFullDay(remaining)) {
            kind = ZoneVerdict.Kind.ALL_DAY;
        } else {
            kind = ZoneVerdict.Kind.PARTIAL;
        }
        return new ZoneVerdict(kind, remaining, List.copyOf(missing), List.copyOf(applied));
    }

    /** Minutes a valid permit covers on the date (see {@link PermitView}). */
    static List<MinuteInterval> permitWindow(PermitView permit, LocalDate date) {
        RuleSchedule schedule = permit.schedule();
        if (schedule == null) {
            return ScheduleNormalizer.fullDay();
        }
        if (!schedule.appliesOn(date.getDayOfWeek())) {
            return List.of();
        }
        return ScheduleNormalizer.complement(ScheduleNormalizer.forbidden(schedule));
    }

    /**
     * AND of every present selector field. Unknown facts needed by a present
     * field are collected in {@code missing} and count as a match.
     */
    static boolean matches(RuleSelector selector, VehicleFacts vehicle, boolean branchAppBased, Set<String> missing) {
        if (selector == null) {
            return true;
        }
        if (selector.appBased() != null && selector.appBased() != branchAppBased) {
            return false;
        }
        if (selector.legalClasses() != null) {
            if (vehicle.legalClass() == null) {
                missing.add(ATTR_LEGAL_CLASS);
            } else if (!selector.legalClasses().contains(vehicle.legalClass())) {
                return false;
            }
        }
        if (selector.energyTypes() != null) {
            if (vehicle.energyType() == null) {
                missing.add(ATTR_ENERGY_TYPE);
            } else if (!selector.energyTypes().contains(vehicle.energyType())) {
                return false;
            }
        }
        if (selector.weightKg() != null) {
            boolean payload = selector.weightMeasure() == RuleSelector.WeightMeasure.PAYLOAD;
            Integer weight = payload ? vehicle.capacity() : vehicle.grossWeightKg();
            if (weight == null) {
                missing.add(payload ? ATTR_CAPACITY : ATTR_GROSS_WEIGHT);
            } else if (!selector.weightKg().holds(weight)) {
                return false;
            }
        }
        if (selector.emissionStandardBelow() != null) {
            if (vehicle.emissionStandard() == null) {
                missing.add(ATTR_EMISSION_STANDARD);
            } else if (vehicle.emissionStandard() >= selector.emissionStandardBelow()) {
                return false;
            }
        }
        return true;
    }
}
