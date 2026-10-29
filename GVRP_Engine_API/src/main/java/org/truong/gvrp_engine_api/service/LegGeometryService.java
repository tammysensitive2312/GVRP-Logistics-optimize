package org.truong.gvrp_engine_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.distance_matrix.RoutePathProvider;
import org.truong.gvrp_engine_api.model.LegPath;
import org.truong.gvrp_engine_api.model.Stop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Fills {@code Stop.geometryToNext} for every consecutive stop pair of a route
 * (design §16.2-3). One place, so the solve path and the evaluate path agree.
 *
 * <p>Instructions are not part of an optimisation result; they come from the
 * legs endpoint at send time. A leg that cannot be routed leaves the field null
 * and is logged with the route index and the stop sequence numbers only — never
 * with coordinates (AGENTS.md §15).
 */
@Slf4j
@Service
public class LegGeometryService {

    private final RoutePathProvider routePathProvider;

    public LegGeometryService(RoutePathProvider routePathProvider) {
        this.routePathProvider = routePathProvider;
    }

    /** Mutates {@code stops}: every stop except the last gets the encoded polyline of its outgoing leg. */
    public void fillGeometry(List<Stop> stops, int routeIndex) {
        if (stops == null || stops.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < stops.size(); i++) {
            Stop from = stops.get(i);
            Stop to = stops.get(i + 1);
            from.setGeometryToNext(null);
            if (!hasCoordinates(from) || !hasCoordinates(to)) {
                log.warn("Leg geometry unavailable for route {} stops {} -> {} (missing coordinates)",
                        routeIndex, from.getSequenceNumber(), to.getSequenceNumber());
                continue;
            }
            try {
                LegPath path = routePathProvider.path(coordinates(from), coordinates(to), Locale.ENGLISH);
                from.setGeometryToNext(path != null ? path.getPolyline() : null);
            } catch (RuntimeException e) {
                log.warn("Leg geometry unavailable for route {} stops {} -> {} ({})",
                        routeIndex, from.getSequenceNumber(), to.getSequenceNumber(),
                        e.getClass().getSimpleName());
            }
        }
        stops.get(stops.size() - 1).setGeometryToNext(null);
    }

    private static boolean hasCoordinates(Stop stop) {
        return stop != null && stop.getLatitude() != null && stop.getLongitude() != null;
    }

    private static OptCoordinates coordinates(Stop stop) {
        return new OptCoordinates(
                BigDecimal.valueOf(stop.getLatitude()),
                BigDecimal.valueOf(stop.getLongitude()));
    }
}
