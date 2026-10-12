package org.truong.gvrp_entry_api.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_entry_api.entity.enums.LocationType;

/**
 * Shared helpers for engine route-stop mapping.
 */
@Slf4j
public final class RouteStopSupport {

    private RouteStopSupport() {
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FALLBACK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Map engine location-type string to {@link LocationType}.
     * CUSTOMER maps to ORDER; unknown or null values default to ORDER with a warning.
     */
    public static LocationType mapLocationType(String type) {
        if ("CUSTOMER".equals(type)) {
            return LocationType.ORDER;
        }
        try {
            return LocationType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unknown location type: {}, defaulting to ORDER", type);
            return LocationType.ORDER;
        }
    }

    /**
     * Null-safe engine time parser. Null/empty returns null;
     * tries HH:mm:ss then HH:mm fallback, otherwise warns and returns null.
     */
    public static LocalTime parseEngineTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalTime.parse(timeStr, FALLBACK_FORMATTER);
            } catch (Exception e2) {
                log.warn("Failed to parse time: {}", timeStr);
                return null;
            }
        }
    }
}
