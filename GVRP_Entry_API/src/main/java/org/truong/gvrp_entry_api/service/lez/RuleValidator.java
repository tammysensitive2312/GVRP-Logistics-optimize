package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Write-side validation of rules and permit schedules (design §3, §10).
 * Every failure is a {@link RuleValidationException}.
 */
public final class RuleValidator {

    public static final int MAX_RULES_PER_ZONE = 50;

    /** Strict and camelCase, independent of the web mapper's naming strategy. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private RuleValidator() {
    }

    /** Parses and validates a selector; an absent selector is refused, {@code {}} matches every vehicle. */
    public static RuleSelector parseSelector(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new RuleValidationException("selector is required");
        }
        RuleSelector selector = read(node, RuleSelector.class, "selector");
        validateSelector(selector);
        return selector;
    }

    /** Parses and validates a rule schedule. */
    public static RuleSchedule parseSchedule(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new RuleValidationException("schedule is required");
        }
        RuleSchedule schedule = read(node, RuleSchedule.class, "schedule");
        ScheduleNormalizer.validate(schedule);
        return schedule;
    }

    /**
     * Parses a permit schedule. Null means the whole day. {@code ALWAYS_FORBIDDEN}
     * is refused because such a permit would cover nothing.
     */
    public static RuleSchedule parsePermitSchedule(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        RuleSchedule schedule = parseSchedule(node);
        if (schedule.type() == RuleSchedule.Type.ALWAYS_FORBIDDEN) {
            throw new RuleValidationException("A permit schedule of type ALWAYS_FORBIDDEN covers no time");
        }
        return schedule;
    }

    public static void validateSelector(RuleSelector selector) {
        if (selector == null) {
            throw new RuleValidationException("selector is required");
        }
        if (selector.legalClasses() != null
                && (selector.legalClasses().isEmpty() || selector.legalClasses().stream().anyMatch(Objects::isNull))) {
            throw new RuleValidationException("selector.legalClasses must be a non-empty list of legal classes");
        }
        if (selector.energyTypes() != null
                && (selector.energyTypes().isEmpty() || selector.energyTypes().stream().anyMatch(Objects::isNull))) {
            throw new RuleValidationException("selector.energyTypes must be a non-empty list of energy types");
        }
        if ((selector.weightKg() == null) != (selector.weightMeasure() == null)) {
            throw new RuleValidationException("selector.weightKg and selector.weightMeasure must be given together");
        }
        RuleSelector.WeightBounds bounds = selector.weightKg();
        if (bounds != null) {
            if (bounds.isEmpty()) {
                throw new RuleValidationException("selector.weightKg needs at least one of gt, gte, lt, lte");
            }
            if (isNegative(bounds.gt()) || isNegative(bounds.gte())
                    || isNegative(bounds.lt()) || isNegative(bounds.lte())) {
                throw new RuleValidationException("selector.weightKg bounds must be non-negative");
            }
        }
        if (isNegative(selector.emissionStandardBelow())) {
            throw new RuleValidationException("selector.emissionStandardBelow must be non-negative");
        }
    }

    /** {@code effectiveFrom} is required and {@code effectiveTo}, when present, is not before it. */
    public static void validateEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom == null) {
            throw new RuleValidationException("effectiveFrom is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new RuleValidationException("effectiveTo must not be before effectiveFrom");
        }
    }

    public static void validateRuleCount(int count) {
        if (count > MAX_RULES_PER_ZONE) {
            throw new RuleValidationException("A zone may have at most " + MAX_RULES_PER_ZONE + " rules");
        }
    }

    private static boolean isNegative(Integer value) {
        return value != null && value < 0;
    }

    private static <T> T read(JsonNode node, Class<T> type, String field) {
        try {
            return JSON.treeToValue(node, type);
        } catch (Exception e) {
            // The parser message may echo input; the field name is enough for the caller.
            throw new RuleValidationException(field + " is not well-formed");
        }
    }
}
