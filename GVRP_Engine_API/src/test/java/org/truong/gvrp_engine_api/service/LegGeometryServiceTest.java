package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.distance_matrix.RoutePathProvider;
import org.truong.gvrp_engine_api.model.LegPath;
import org.truong.gvrp_engine_api.model.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EB-T4: one unroutable leg must not abort the route; it leaves that stop's
 * geometry null and the other legs keep theirs. No GraphHopper graph: the path
 * provider is a fake.
 */
class LegGeometryServiceTest {

    private static Stop stop(int sequence, double lat, double lon) {
        Stop stop = new Stop();
        stop.setSequenceNumber(sequence);
        stop.setLatitude(lat);
        stop.setLongitude(lon);
        return stop;
    }

    @Test
    void fillsEveryLegExceptTheFailingAndTheLastOne() {
        // Four stops, three legs; the middle leg throws.
        List<Stop> stops = new ArrayList<>(List.of(
                stop(1, 21.0, 105.0),
                stop(2, 21.1, 105.1),
                stop(3, 21.2, 105.2),
                stop(4, 21.3, 105.3)));

        RoutePathProvider provider = (OptCoordinates from, OptCoordinates to, Locale locale) -> {
            if (from.latDouble() > 21.05 && from.latDouble() < 21.15) {
                throw new IllegalStateException("no route for this leg");
            }
            return new LegPath("poly-" + from.latDouble(), 100.0, 1_000L, List.of());
        };

        new LegGeometryService(provider).fillGeometry(stops, 7);

        assertEquals("poly-21.0", stops.get(0).getGeometryToNext());
        assertNull(stops.get(1).getGeometryToNext(), "the unroutable leg stays null");
        assertEquals("poly-21.2", stops.get(2).getGeometryToNext());
        assertNull(stops.get(3).getGeometryToNext(), "the last stop has no outgoing leg");
    }

    @Test
    void stopsWithoutCoordinatesLeaveTheirLegNull() {
        List<Stop> stops = new ArrayList<>(List.of(stop(1, 21.0, 105.0), stop(2, 21.1, 105.1)));
        stops.get(1).setLatitude(null);

        new LegGeometryService((from, to, locale) -> {
            throw new AssertionError("must not route a leg without coordinates");
        }).fillGeometry(stops, 0);

        assertNull(stops.get(0).getGeometryToNext());
    }

    @Test
    void emptyAndSingleStopRoutesAreNoOps() {
        new LegGeometryService((from, to, locale) -> null).fillGeometry(List.of(), 0);
        List<Stop> single = new ArrayList<>(List.of(stop(1, 21.0, 105.0)));
        new LegGeometryService((from, to, locale) -> null).fillGeometry(single, 0);
        assertNull(single.get(0).getGeometryToNext());
    }
}
