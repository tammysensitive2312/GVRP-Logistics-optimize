package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.truong.gvrp_entry_api.exception.DataInvalidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GeoJSON → JTS parsing of zone polygons (LEZ-001 P1a, GJ-T1). Coordinates are
 * abstract test shapes, not real places.
 */
class GeoJsonGeometryParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private static final String SQUARE = "[[[0,0],[10,0],[10,10],[0,10],[0,0]]]";
    private static final String OTHER_SQUARE = "[[[20,20],[30,20],[30,30],[20,30],[20,20]]]";

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    private static Point point(double lon, double lat) {
        return FACTORY.createPoint(new Coordinate(lon, lat));
    }

    /** A closed ring of {@code positions} positions (the last repeats the first) on a circle. */
    private static String circle(int positions) {
        StringBuilder ring = new StringBuilder("[[");
        int distinct = positions - 1;
        for (int i = 0; i < distinct; i++) {
            double angle = 2 * Math.PI * i / distinct;
            ring.append('[').append(Math.cos(angle)).append(',').append(Math.sin(angle)).append("],");
        }
        ring.append("[1.0,0.0]]]");
        return "{\"type\":\"Polygon\",\"coordinates\":" + ring + "}";
    }

    // ---------- accepted ----------

    @Test
    void gjT1_polygon_becomesMultiPolygonWithSrid4326AndLonLatAxes() throws Exception {
        MultiPolygon result = GeoJsonGeometryParser.parse(
                json("{\"type\":\"Polygon\",\"coordinates\":[[[100,1],[101,1],[101,2],[100,2],[100,1]]]}"));

        assertThat(result.getNumGeometries()).isEqualTo(1);
        assertThat(result.getSRID()).isEqualTo(4326);
        // x = longitude, y = latitude, like Order.location.
        assertThat(result.getEnvelopeInternal().getMinX()).isEqualTo(100.0);
        assertThat(result.getEnvelopeInternal().getMaxY()).isEqualTo(2.0);
    }

    @Test
    void gjT1_multiPolygon_isAccepted() throws Exception {
        MultiPolygon result = GeoJsonGeometryParser.parse(
                json("{\"type\":\"MultiPolygon\",\"coordinates\":[" + SQUARE + "," + OTHER_SQUARE + "]}"));

        assertThat(result.getNumGeometries()).isEqualTo(2);
        assertThat(result.getSRID()).isEqualTo(4326);
    }

    @Test
    void gjT1_featureWrappingAPolygon_isAccepted() throws Exception {
        MultiPolygon result = GeoJsonGeometryParser.parse(json(
                "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
                        + SQUARE + "}}"));

        assertThat(result.getNumGeometries()).isEqualTo(1);
        assertThat(result.getSRID()).isEqualTo(4326);
    }

    @Test
    void gjT1_exactly5000Vertices_isAccepted() throws Exception {
        assertThat(GeoJsonGeometryParser.parse(json(circle(5_000))).getNumPoints()).isEqualTo(5_000);
    }

    // ---------- refused (HTTP 400 through DataInvalidException) ----------

    @Test
    void gjT1_lineString_isRejected() throws Exception {
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(
                json("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}")))
                .isInstanceOf(DataInvalidException.class);
    }

    @Test
    void gjT1_selfIntersectingPolygon_isRejected() throws Exception {
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(
                json("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[2,2],[2,0],[0,2],[0,0]]]}")))
                .isInstanceOf(DataInvalidException.class);
    }

    @Test
    void gjT1_5001Vertices_isRejected() throws Exception {
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(json(circle(5_001))))
                .isInstanceOf(DataInvalidException.class);
    }

    @Test
    void outOfRangeOrMalformedCoordinates_areRejected() throws Exception {
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(
                json("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[181,0],[181,1],[0,1],[0,0]]]}")))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(
                json("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,91],[0,1],[0,0]]]}")))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(
                json("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1]]]}")))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(json("{\"type\":\"Polygon\"}")))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> GeoJsonGeometryParser.parse(null))
                .isInstanceOf(DataInvalidException.class);
    }

    // ---------- writer and covers ----------

    @Test
    void writer_roundTripsTheSameShape() throws Exception {
        MultiPolygon parsed = GeoJsonGeometryParser.parse(
                json("{\"type\":\"MultiPolygon\",\"coordinates\":[" + SQUARE + "," + OTHER_SQUARE + "]}"));

        JsonNode written = GeoJsonGeometryParser.toGeoJson(parsed);

        assertThat(written.get("type").asText()).isEqualTo("MultiPolygon");
        assertThat(GeoJsonGeometryParser.parse(written).equalsExact(parsed)).isTrue();
        assertThat(GeoJsonGeometryParser.toGeoJson(null)).isNull();
    }

    @Test
    void covers_countsTheBoundaryAsInside() throws Exception {
        MultiPolygon zone = GeoJsonGeometryParser.parse(json("{\"type\":\"Polygon\",\"coordinates\":" + SQUARE + "}"));

        assertThat(GeoJsonGeometryParser.covers(zone, point(5, 5))).isTrue();
        assertThat(GeoJsonGeometryParser.covers(zone, point(10, 5))).isTrue();
        assertThat(GeoJsonGeometryParser.covers(zone, point(10.001, 5))).isFalse();
        assertThat(GeoJsonGeometryParser.covers(zone, null)).isFalse();
    }
}
