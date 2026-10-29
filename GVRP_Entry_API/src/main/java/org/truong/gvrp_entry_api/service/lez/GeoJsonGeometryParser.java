package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * GeoJSON ↔ JTS for zone polygons (jts-core has no GeoJSON reader).
 *
 * <p>Accepts a {@code Polygon}, a {@code MultiPolygon}, or a {@code Feature}
 * wrapping one, and always returns a {@link MultiPolygon} with SRID 4326 and
 * x = longitude, y = latitude, like {@code Order.location}. Everything else is
 * refused with HTTP 400 ({@link DataInvalidException}).
 */
public final class GeoJsonGeometryParser {

    public static final int SRID = 4326;
    public static final int MAX_VERTICES = 5_000;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    private GeoJsonGeometryParser() {
    }

    public static MultiPolygon parse(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("Geometry must be a GeoJSON object");
        }
        String type = node.path("type").asText("");
        if ("Feature".equals(type)) {
            JsonNode geometry = node.get("geometry");
            if (geometry == null || !geometry.isObject()) {
                throw invalid("Feature has no geometry");
            }
            node = geometry;
            type = node.path("type").asText("");
        }

        int[] vertices = {0};
        List<Polygon> polygons = new ArrayList<>();
        JsonNode coordinates = node.get("coordinates");
        switch (type) {
            case "Polygon" -> polygons.add(polygon(coordinates, vertices));
            case "MultiPolygon" -> {
                if (coordinates == null || !coordinates.isArray() || coordinates.isEmpty()) {
                    throw invalid("MultiPolygon coordinates must be a non-empty array");
                }
                for (JsonNode polygon : coordinates) {
                    polygons.add(polygon(polygon, vertices));
                }
            }
            default -> throw invalid("Geometry type must be Polygon or MultiPolygon");
        }

        MultiPolygon result = FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
        if (!result.isValid()) {
            throw invalid("Geometry is not valid (for example self-intersecting or overlapping)");
        }
        return result;
    }

    /** GeoJSON {@code MultiPolygon} object for a response; null stays null. */
    public static JsonNode toGeoJson(MultiPolygon geometry) {
        if (geometry == null) {
            return null;
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode root = f.objectNode();
        root.put("type", "MultiPolygon");
        ArrayNode polygons = root.putArray("coordinates");
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) geometry.getGeometryN(i);
            ArrayNode rings = polygons.addArray();
            writeRing(rings.addArray(), polygon.getExteriorRing());
            for (int h = 0; h < polygon.getNumInteriorRing(); h++) {
                writeRing(rings.addArray(), polygon.getInteriorRingN(h));
            }
        }
        return root;
    }

    /** Point in zone; the boundary counts as inside (design §11). */
    public static boolean covers(Geometry zone, Point point) {
        return zone != null && point != null && zone.covers(point);
    }

    // ---------- helpers ----------

    private static Polygon polygon(JsonNode rings, int[] vertices) {
        if (rings == null || !rings.isArray() || rings.isEmpty()) {
            throw invalid("Polygon coordinates must be a non-empty array of rings");
        }
        LinearRing shell = ring(rings.get(0), vertices);
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) {
            holes[i - 1] = ring(rings.get(i), vertices);
        }
        return FACTORY.createPolygon(shell, holes);
    }

    private static LinearRing ring(JsonNode positions, int[] vertices) {
        if (positions == null || !positions.isArray() || positions.size() < 4) {
            throw invalid("A ring must be an array of at least 4 positions");
        }
        vertices[0] += positions.size();
        if (vertices[0] > MAX_VERTICES) {
            throw invalid("Geometry has more than " + MAX_VERTICES + " vertices");
        }
        Coordinate[] coordinates = new Coordinate[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            coordinates[i] = position(positions.get(i));
        }
        try {
            return FACTORY.createLinearRing(coordinates);
        } catch (IllegalArgumentException e) {
            throw invalid("A ring must be closed and have at least 4 positions");
        }
    }

    private static Coordinate position(JsonNode position) {
        if (position == null || !position.isArray() || position.size() < 2
                || !position.get(0).isNumber() || !position.get(1).isNumber()) {
            throw invalid("A position must be [longitude, latitude]");
        }
        double lon = position.get(0).asDouble();
        double lat = position.get(1).asDouble();
        if (!Double.isFinite(lon) || !Double.isFinite(lat)
                || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw invalid("Coordinates are outside the longitude/latitude range");
        }
        return new Coordinate(lon, lat);
    }

    private static void writeRing(ArrayNode target, LineString ring) {
        for (Coordinate c : ring.getCoordinates()) {
            target.addArray().add(c.getX()).add(c.getY());
        }
    }

    private static DataInvalidException invalid(String message) {
        return new DataInvalidException(List.of(ErrorDetail.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(message)
                .field(AppConstant.GEOMETRY)
                .build()));
    }
}
