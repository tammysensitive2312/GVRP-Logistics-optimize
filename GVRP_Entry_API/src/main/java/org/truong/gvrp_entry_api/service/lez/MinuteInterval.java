package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Half-open interval [start, end) in minutes of the day, 0 ≤ start < end ≤ 1440.
 * Serialized as {@code [start, end]}.
 */
public record MinuteInterval(int start, int end) {

    public static final int DAY_MINUTES = 1440;

    public MinuteInterval {
        if (start < 0 || end > DAY_MINUTES || start >= end) {
            throw new IllegalArgumentException("Invalid minute interval [" + start + "," + end + ")");
        }
    }

    @JsonValue
    public int[] toArray() {
        return new int[]{start, end};
    }
}
