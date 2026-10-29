package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Time-of-day part of a zone rule or permit (design §3.2).
 *
 * <p>{@code intervals} holds {@code ["HH:mm","HH:mm"]} pairs; {@code 24:00}
 * is accepted as an end, and an interval whose start is after its end crosses
 * midnight. {@code daysOfWeek} absent means every day.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleSchedule(
        @JsonProperty("type") Type type,
        @JsonProperty("daysOfWeek") List<Day> daysOfWeek,
        @JsonProperty("intervals") List<List<String>> intervals) {

    public enum Type {
        ALWAYS_FORBIDDEN,
        FORBIDDEN_DURING,
        ALLOWED_ONLY
    }

    public enum Day {
        MON(DayOfWeek.MONDAY),
        TUE(DayOfWeek.TUESDAY),
        WED(DayOfWeek.WEDNESDAY),
        THU(DayOfWeek.THURSDAY),
        FRI(DayOfWeek.FRIDAY),
        SAT(DayOfWeek.SATURDAY),
        SUN(DayOfWeek.SUNDAY);

        private final DayOfWeek dayOfWeek;

        Day(DayOfWeek dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public DayOfWeek toDayOfWeek() {
            return dayOfWeek;
        }
    }

    /** True when the schedule has no weekday filter or names the given day. */
    public boolean appliesOn(DayOfWeek day) {
        return daysOfWeek == null
                || daysOfWeek.stream().anyMatch(d -> d != null && d.toDayOfWeek() == day);
    }
}
