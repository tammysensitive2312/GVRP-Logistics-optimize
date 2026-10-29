package org.truong.gvrp_entry_api.service.lez;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a {@link RuleSchedule} into the set of forbidden minutes of one day,
 * as sorted, merged, half-open intervals in [0, 1440) (design §3.2).
 *
 * <ul>
 *   <li>{@code ALWAYS_FORBIDDEN}: the whole day.</li>
 *   <li>{@code FORBIDDEN_DURING}: the union of the intervals.</li>
 *   <li>{@code ALLOWED_ONLY}: the complement of the union of the intervals.</li>
 *   <li>start &gt; end crosses midnight and is split into [start, 1440) and [0, end);
 *       both parts belong to the same service date.</li>
 *   <li>start == end is invalid; {@code 24:00} is accepted only as an end.</li>
 * </ul>
 *
 * <p>The weekday filter is not applied here; see {@link RuleSchedule#appliesOn}.
 */
public final class ScheduleNormalizer {

    /** Upper bound on intervals per schedule (bounded input, design §10). */
    public static final int MAX_INTERVALS = 48;

    private static final Pattern TIME = Pattern.compile("^(\\d{2}):(\\d{2})$");

    private ScheduleNormalizer() {
    }

    /** Forbidden minutes of the schedule. Throws {@link RuleValidationException} when malformed. */
    public static List<MinuteInterval> forbidden(RuleSchedule schedule) {
        validate(schedule);
        return switch (schedule.type()) {
            case ALWAYS_FORBIDDEN -> fullDay();
            case FORBIDDEN_DURING -> parseIntervals(schedule.intervals());
            case ALLOWED_ONLY -> complement(parseIntervals(schedule.intervals()));
        };
    }

    /** Checks the structure of a schedule without computing it. */
    public static void validate(RuleSchedule schedule) {
        if (schedule == null) {
            throw new RuleValidationException("schedule is required");
        }
        if (schedule.type() == null) {
            throw new RuleValidationException("schedule.type is required");
        }
        if (schedule.daysOfWeek() != null) {
            if (schedule.daysOfWeek().isEmpty()) {
                throw new RuleValidationException("schedule.daysOfWeek must not be empty when present");
            }
            if (schedule.daysOfWeek().stream().anyMatch(d -> d == null)) {
                throw new RuleValidationException("schedule.daysOfWeek contains an invalid day");
            }
        }
        List<List<String>> intervals = schedule.intervals();
        if (schedule.type() == RuleSchedule.Type.ALWAYS_FORBIDDEN) {
            if (intervals != null && !intervals.isEmpty()) {
                throw new RuleValidationException("schedule.intervals must be absent for ALWAYS_FORBIDDEN");
            }
            return;
        }
        if (intervals == null || intervals.isEmpty()) {
            throw new RuleValidationException("schedule.intervals is required for " + schedule.type());
        }
        if (intervals.size() > MAX_INTERVALS) {
            throw new RuleValidationException("schedule.intervals has more than " + MAX_INTERVALS + " entries");
        }
        // Parsing checks every pair; the result is discarded here.
        parseIntervals(intervals);
    }

    // ---------- interval set operations (inputs and outputs sorted and merged) ----------

    public static List<MinuteInterval> fullDay() {
        return List.of(new MinuteInterval(0, MinuteInterval.DAY_MINUTES));
    }

    public static boolean isFullDay(List<MinuteInterval> set) {
        return set.size() == 1 && set.get(0).start() == 0 && set.get(0).end() == MinuteInterval.DAY_MINUTES;
    }

    /** Sorts and merges overlapping or touching intervals. */
    public static List<MinuteInterval> merge(List<MinuteInterval> intervals) {
        List<MinuteInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt(MinuteInterval::start).thenComparingInt(MinuteInterval::end));
        List<MinuteInterval> merged = new ArrayList<>();
        for (MinuteInterval next : sorted) {
            if (!merged.isEmpty() && next.start() <= merged.get(merged.size() - 1).end()) {
                MinuteInterval last = merged.remove(merged.size() - 1);
                merged.add(new MinuteInterval(last.start(), Math.max(last.end(), next.end())));
            } else {
                merged.add(next);
            }
        }
        return List.copyOf(merged);
    }

    /** [0, 1440) minus the given set. */
    public static List<MinuteInterval> complement(List<MinuteInterval> set) {
        return subtract(fullDay(), set);
    }

    /** Minutes in {@code from} that are not in {@code remove}. */
    public static List<MinuteInterval> subtract(List<MinuteInterval> from, List<MinuteInterval> remove) {
        List<MinuteInterval> cuts = merge(remove);
        List<MinuteInterval> result = new ArrayList<>();
        for (MinuteInterval piece : merge(from)) {
            int cursor = piece.start();
            for (MinuteInterval cut : cuts) {
                if (cut.end() <= cursor || cut.start() >= piece.end()) {
                    continue;
                }
                if (cut.start() > cursor) {
                    result.add(new MinuteInterval(cursor, cut.start()));
                }
                cursor = Math.max(cursor, cut.end());
                if (cursor >= piece.end()) {
                    break;
                }
            }
            if (cursor < piece.end()) {
                result.add(new MinuteInterval(cursor, piece.end()));
            }
        }
        return List.copyOf(result);
    }

    // ---------- parsing ----------

    private static List<MinuteInterval> parseIntervals(List<List<String>> intervals) {
        List<MinuteInterval> parts = new ArrayList<>();
        for (List<String> pair : intervals) {
            if (pair == null || pair.size() != 2) {
                throw new RuleValidationException("Each schedule interval must be [\"HH:mm\",\"HH:mm\"]");
            }
            int start = parseMinute(pair.get(0), false);
            int end = parseMinute(pair.get(1), true);
            if (start == end) {
                throw new RuleValidationException("Schedule interval " + pair + " has equal start and end");
            }
            if (start < end) {
                parts.add(new MinuteInterval(start, end));
            } else {
                // Crosses midnight: split on the same service date.
                parts.add(new MinuteInterval(start, MinuteInterval.DAY_MINUTES));
                if (end > 0) {
                    parts.add(new MinuteInterval(0, end));
                }
            }
        }
        return merge(parts);
    }

    private static int parseMinute(String value, boolean allowEndOfDay) {
        if (value == null) {
            throw new RuleValidationException("Schedule time is missing");
        }
        Matcher matcher = TIME.matcher(value);
        if (!matcher.matches()) {
            throw new RuleValidationException("Schedule time '" + value + "' is not HH:mm");
        }
        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        if (hours == 24 && minutes == 0 && allowEndOfDay) {
            return MinuteInterval.DAY_MINUTES;
        }
        if (hours > 23 || minutes > 59) {
            throw new RuleValidationException("Schedule time '" + value + "' is out of range");
        }
        return hours * 60 + minutes;
    }
}
