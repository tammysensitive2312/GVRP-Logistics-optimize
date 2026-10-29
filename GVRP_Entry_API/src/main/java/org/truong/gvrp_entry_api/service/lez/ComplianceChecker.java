package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.truong.gvrp_entry_api.entity.enums.LocationType;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single verdict on whether a route may enter the branch's low-emission
 * zones (LEZ-001, design §5.3, §16.2-5). Pure: no Spring, no repositories, no
 * persistence.
 *
 * <p>In P1 it evaluates both {@code ALL_DAY} and {@code PARTIAL} restrictions on
 * the stored geometry: a time-of-day conflict is caught here and blocks the send
 * even though the optimiser cannot avoid it yet (P2).
 *
 * <p>Times are minutes of the service date; jobs are one-day jobs (D4). A leg is
 * the straight-free line of its stored polyline, so the test is
 * {@code LineString.intersects(polygon)} on x = longitude, y = latitude.
 */
public final class ComplianceChecker {

    public static final String ZONE_ACCESS = "ZONE_ACCESS";
    public static final String ZONE_TRANSIT = "ZONE_TRANSIT";
    public static final String LEZ_DATA_MISSING = "LEZ_DATA_MISSING";
    public static final String LEZ_GEOMETRY_MISSING = "LEZ_GEOMETRY_MISSING";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private ComplianceChecker() {
    }

    /** One stop of the route under test, in plan order. */
    public record StopView(
            LocationType type,
            Long orderId,
            Point location,
            LocalTime arrival,
            LocalTime departure,
            String geometryToNext) {
    }

    /** One finding. {@code seq} is the 0-based plan index of the offending stop. */
    public record Violation(String code, int seq, Long orderId, String zoneKey, String detail) {
    }

    /**
     * @param vehicleId    vehicle of the route
     * @param stops        stops in plan order (start depot first, end depot last)
     * @param restrictions compiled restrictions of the route's branch and date
     * @return violations in plan order; empty when the route is compliant
     */
    public static List<Violation> check(Long vehicleId, List<StopView> stops,
                                        CompiledRestrictions restrictions) {
        List<Violation> violations = new ArrayList<>();
        if (restrictions == null || !restrictions.hasZones() || stops == null || stops.isEmpty()) {
            return violations;
        }

        Map<String, MultiPolygon> geometryByKey = geometries(restrictions);

        // ---------- stops ----------
        for (int i = 0; i < stops.size(); i++) {
            StopView stop = stops.get(i);
            if (stop.location() == null) {
                continue;
            }
            List<Integer> window = window(stop.arrival(), stop.departure());
            for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
                MultiPolygon geometry = geometryByKey.get(zone.getKey());
                if (geometry == null || !GeoJsonGeometryParser.covers(geometry, stop.location())) {
                    continue;
                }
                CompiledRestrictions.VehicleRestriction restriction =
                        restrictions.restriction(vehicleId, zone.getKey());
                if (restriction == null) {
                    continue;
                }
                if (restricted(restriction) && !restriction.getMissingAttributes().isEmpty()) {
                    violations.add(new Violation(LEZ_DATA_MISSING, i, stop.orderId(), zone.getKey(),
                            "Missing vehicle data decides zone access: "
                                    + String.join(", ", restriction.getMissingAttributes())));
                } else if (restriction.isAllDay()) {
                    violations.add(new Violation(ZONE_ACCESS, i, stop.orderId(), zone.getKey(),
                            "Stop is in zone " + zone.getKey() + " and the vehicle is banned all day there"));
                } else if (window != null && overlaps(restriction.getForbidden(), window.get(0), window.get(1))) {
                    violations.add(new Violation(ZONE_ACCESS, i, stop.orderId(), zone.getKey(),
                            "Stop is in zone " + zone.getKey() + " during its forbidden hours"));
                }
            }
        }

        // ---------- legs ----------
        for (int i = 0; i + 1 < stops.size(); i++) {
            StopView from = stops.get(i);
            StopView to = stops.get(i + 1);
            String polyline = from.geometryToNext();
            if (polyline == null || polyline.isEmpty()) {
                violations.add(new Violation(LEZ_GEOMETRY_MISSING, i, from.orderId(), null,
                        "Leg from stop " + i + " to stop " + (i + 1) + " has no geometry"));
                continue;
            }
            LineString line = line(polyline);
            if (line == null) {
                violations.add(new Violation(LEZ_GEOMETRY_MISSING, i, from.orderId(), null,
                        "Leg from stop " + i + " to stop " + (i + 1) + " has undecodable geometry"));
                continue;
            }
            List<Integer> window = window(from.departure() != null ? from.departure() : from.arrival(),
                    to.arrival() != null ? to.arrival() : to.departure());
            for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
                MultiPolygon geometry = geometryByKey.get(zone.getKey());
                if (geometry == null || !line.intersects(geometry)) {
                    continue;
                }
                CompiledRestrictions.VehicleRestriction restriction =
                        restrictions.restriction(vehicleId, zone.getKey());
                if (restriction == null) {
                    continue;
                }
                if (!restriction.getMissingAttributes().isEmpty() && restricted(restriction)) {
                    violations.add(new Violation(LEZ_DATA_MISSING, i, from.orderId(), zone.getKey(),
                            "Missing vehicle data decides transit through " + zone.getKey() + ": "
                                    + String.join(", ", restriction.getMissingAttributes())));
                } else if (restriction.isAllDay()) {
                    violations.add(new Violation(ZONE_TRANSIT, i, from.orderId(), zone.getKey(),
                            "Leg crosses zone " + zone.getKey() + " where the vehicle is banned all day"));
                } else if (window != null && overlaps(restriction.getForbidden(), window.get(0), window.get(1))) {
                    violations.add(new Violation(ZONE_TRANSIT, i, from.orderId(), zone.getKey(),
                            "Leg crosses zone " + zone.getKey() + " during its forbidden hours"));
                }
            }
        }

        return violations;
    }

    /** True when any violation blocks the plan (every code does in P1). */
    public static boolean blocks(List<Violation> violations) {
        return violations != null && !violations.isEmpty();
    }

    /** Zones of the compilation whose polygon covers the point (map badges, driver DTO). */
    public static List<CompiledRestrictions.CompiledZone> zonesCovering(CompiledRestrictions restrictions,
                                                                        Point point) {
        List<CompiledRestrictions.CompiledZone> covering = new ArrayList<>();
        if (restrictions == null || !restrictions.hasZones() || point == null) {
            return covering;
        }
        for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
            MultiPolygon geometry = geometryOf(zone);
            if (geometry != null && GeoJsonGeometryParser.covers(geometry, point)) {
                covering.add(zone);
            }
        }
        return covering;
    }

    /** Parsed polygon of a compiled zone; null when the zone carries no geometry. */
    public static MultiPolygon geometryOf(CompiledRestrictions.CompiledZone zone) {
        if (zone == null || zone.getGeometry() == null) {
            return null;
        }
        try {
            return GeoJsonGeometryParser.parse(MAPPER.readTree(zone.getGeometry()));
        } catch (Exception e) {
            // A corrupted snapshot must not silently pass a route: fail closed.
            throw new IllegalStateException("Zone " + zone.getKey() + " has unreadable geometry", e);
        }
    }

    // ---------- helpers ----------

    /** True when the verdict forbids the vehicle at least part of the day. */
    private static boolean restricted(CompiledRestrictions.VehicleRestriction restriction) {
        return restriction.isAllDay()
                || (restriction.getForbidden() != null && !restriction.getForbidden().isEmpty());
    }

    private static Map<String, MultiPolygon> geometries(CompiledRestrictions restrictions) {
        Map<String, MultiPolygon> result = new HashMap<>();
        for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
            MultiPolygon geometry = geometryOf(zone);
            if (geometry != null) {
                result.put(zone.getKey(), geometry);
            }
        }
        return result;
    }

    private static LineString line(String encoded) {
        try {
            List<double[]> points = PolylineCodec.decode(encoded);
            if (points.size() < 2) {
                return null;
            }
            Coordinate[] coordinates = new Coordinate[points.size()];
            for (int i = 0; i < points.size(); i++) {
                double[] point = points.get(i);
                coordinates[i] = new Coordinate(point[1], point[0]);
            }
            return FACTORY.createLineString(coordinates);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Minutes of the day for a stop; null when neither time is known. */
    private static List<Integer> window(LocalTime arrival, LocalTime departure) {
        if (arrival == null && departure == null) {
            return null;
        }
        int first = minutes(arrival != null ? arrival : departure);
        int second = minutes(departure != null ? departure : arrival);
        return List.of(first, second);
    }

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static boolean overlaps(List<int[]> forbidden, int start, int end) {
        if (forbidden == null || forbidden.isEmpty()) {
            return false;
        }
        if (end < start) {
            // Crossed midnight: without a date we cannot split, so fail closed.
            return true;
        }
        if (end == start) {
            for (int[] interval : forbidden) {
                if (interval.length >= 2 && interval[0] <= start && start < interval[1]) {
                    return true;
                }
            }
            return false;
        }
        for (int[] interval : forbidden) {
            if (interval.length >= 2 && start < interval[1] && interval[0] < end) {
                return true;
            }
        }
        return false;
    }

    /** Point in the storage convention: x = longitude, y = latitude. */
    public static Point point(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
