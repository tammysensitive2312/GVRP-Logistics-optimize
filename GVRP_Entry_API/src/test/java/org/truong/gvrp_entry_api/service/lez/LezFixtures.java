package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixtures for the LEZ P1b tests (new test-only helper, never run as a
 * test). Polygons and polylines are built here so every test speaks the same
 * storage convention: x = longitude, y = latitude, encoded polyline points in
 * {@code [lat, lon]} order.
 */
public final class LezFixtures {

    public static final LocalDate WEDNESDAY = LocalDate.of(2027, 3, 3);

    public static final int PEAK_START = 6 * 60;
    public static final int PEAK_END = 9 * 60;
    public static final int EVENING_START = 16 * 60;
    public static final int EVENING_END = 19 * 60 + 30;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private LezFixtures() {
    }

    // ---------- geometry ----------

    /** Axis-aligned square in degrees, x = longitude, y = latitude. */
    public static MultiPolygon square(double minLon, double minLat, double maxLon, double maxLat) {
        Coordinate[] ring = new Coordinate[]{
                new Coordinate(minLon, minLat),
                new Coordinate(maxLon, minLat),
                new Coordinate(maxLon, maxLat),
                new Coordinate(minLon, maxLat),
                new Coordinate(minLon, minLat)};
        return FACTORY.createMultiPolygon(new org.locationtech.jts.geom.Polygon[]{
                FACTORY.createPolygon(ring)});
    }

    /** GeoJSON text of a zone geometry, as stored in a snapshot. */
    public static String geoJson(MultiPolygon geometry) {
        try {
            return JSON.writeValueAsString(GeoJsonGeometryParser.toGeoJson(geometry));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Point in the storage convention: x = longitude, y = latitude. */
    public static Point point(double latitude, double longitude) {
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    /** Google encoded polyline (precision 1e5) from {@code lat, lon} pairs. */
    public static String encode(double... latLonPairs) {
        StringBuilder out = new StringBuilder();
        long lastLat = 0;
        long lastLon = 0;
        for (int i = 0; i + 1 < latLonPairs.length; i += 2) {
            long lat = Math.round(latLonPairs[i] * 1e5);
            long lon = Math.round(latLonPairs[i + 1] * 1e5);
            encodeValue(out, lat - lastLat);
            encodeValue(out, lon - lastLon);
            lastLat = lat;
            lastLon = lon;
        }
        return out.toString();
    }

    private static void encodeValue(StringBuilder out, long delta) {
        long value = delta < 0 ? ~(delta << 1) : (delta << 1);
        while (value >= 0x20) {
            out.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        out.append((char) (value + 63));
    }

    // ---------- compiled restrictions ----------

    public static CompiledRestrictions.CompiledZone zone(String key, MultiPolygon geometry) {
        CompiledRestrictions.CompiledZone zone = new CompiledRestrictions.CompiledZone();
        zone.setKey(key);
        zone.setName("Zone " + key);
        zone.setGeometry(geoJson(geometry));
        return zone;
    }

    public static CompiledRestrictions.CompiledZone zone(String key, MultiPolygon geometry, String name) {
        CompiledRestrictions.CompiledZone zone = zone(key, geometry);
        zone.setName(name);
        return zone;
    }

    public static CompiledRestrictions.VehicleRestriction allDay(long vehicleId, String key) {
        CompiledRestrictions.VehicleRestriction restriction =
                new CompiledRestrictions.VehicleRestriction();
        restriction.setVehicleId(vehicleId);
        restriction.setZoneKey(key);
        restriction.setKind(ZoneVerdict.Kind.ALL_DAY.name());
        restriction.getForbidden().add(new int[]{0, 1440});
        return restriction;
    }

    public static CompiledRestrictions.VehicleRestriction partial(long vehicleId, String key,
                                                                 int[]... intervals) {
        CompiledRestrictions.VehicleRestriction restriction =
                new CompiledRestrictions.VehicleRestriction();
        restriction.setVehicleId(vehicleId);
        restriction.setZoneKey(key);
        restriction.setKind(ZoneVerdict.Kind.PARTIAL.name());
        for (int[] interval : intervals) {
            restriction.getForbidden().add(interval);
        }
        return restriction;
    }

    public static CompiledRestrictions.VehicleRestriction allowed(long vehicleId, String key) {
        CompiledRestrictions.VehicleRestriction restriction =
                new CompiledRestrictions.VehicleRestriction();
        restriction.setVehicleId(vehicleId);
        restriction.setZoneKey(key);
        restriction.setKind(ZoneVerdict.Kind.ALLOWED.name());
        return restriction;
    }

    /** Compilation with a fixed hash, one zone and the given verdicts. */
    public static CompiledRestrictions compilation(String hash,
                                                   CompiledRestrictions.CompiledZone zone,
                                                   CompiledRestrictions.VehicleRestriction... verdicts) {
        CompiledRestrictions restrictions = new CompiledRestrictions();
        restrictions.setServiceDate(WEDNESDAY);
        restrictions.setHash(hash);
        if (zone != null) {
            restrictions.getZones().add(zone);
        }
        for (CompiledRestrictions.VehicleRestriction verdict : verdicts) {
            restrictions.getVehicleRestrictions().add(verdict);
        }
        return restrictions;
    }

    /** Compilation without zones (design §16.2-7). */
    public static CompiledRestrictions noZones(String hash) {
        CompiledRestrictions restrictions = new CompiledRestrictions();
        restrictions.setServiceDate(WEDNESDAY);
        restrictions.setHash(hash);
        return restrictions;
    }

    /** One stop of a route as the checker sees it. */
    public static ComplianceChecker.StopView stop(org.truong.gvrp_entry_api.entity.enums.LocationType type,
                                                  Long orderId, Point location,
                                                  java.time.LocalTime arrival,
                                                  java.time.LocalTime departure,
                                                  String geometryToNext) {
        return new ComplianceChecker.StopView(type, orderId, location, arrival, departure, geometryToNext);
    }

    public static List<int[]> intervals(int[]... values) {
        return new ArrayList<>(List.of(values));
    }
}
