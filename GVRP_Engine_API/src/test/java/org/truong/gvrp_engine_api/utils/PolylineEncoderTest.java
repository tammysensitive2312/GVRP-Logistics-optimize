package org.truong.gvrp_engine_api.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EB-T3: the encoder must agree with the published Google vectors, because Entry
 * and the clients decode with independent implementations.
 */
class PolylineEncoderTest {

    /** The published example from Google's "Encoded Polyline Algorithm Format". */
    private static final List<double[]> GOOGLE_POINTS = List.of(
            new double[]{38.5, -120.2},
            new double[]{40.7, -120.95},
            new double[]{43.252, -126.453});

    private static final String GOOGLE_ENCODED = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    void encodesThePublishedExample() {
        assertEquals(GOOGLE_ENCODED, PolylineEncoder.encode(GOOGLE_POINTS));
    }

    @Test
    void decodesThePublishedExample() {
        List<double[]> decoded = PolylineEncoder.decode(GOOGLE_ENCODED);

        assertEquals(3, decoded.size());
        for (int i = 0; i < GOOGLE_POINTS.size(); i++) {
            assertEquals(GOOGLE_POINTS.get(i)[0], decoded.get(i)[0], 1e-5);
            assertEquals(GOOGLE_POINTS.get(i)[1], decoded.get(i)[1], 1e-5);
        }
    }

    @Test
    void roundTripsAndKeepsEmptyInputEmpty() {
        List<double[]> points = List.of(
                new double[]{21.027_764, 105.834_160},
                new double[]{21.028_500, 105.854_000});
        List<double[]> decoded = PolylineEncoder.decode(PolylineEncoder.encode(points));

        assertEquals(2, decoded.size());
        assertEquals(21.027_764, decoded.get(0)[0], 1e-5);
        assertEquals(105.834_160, decoded.get(0)[1], 1e-5);
        assertEquals("", PolylineEncoder.encode(List.of()));
        assertEquals(List.of(), PolylineEncoder.decode(""));
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> PolylineEncoder.encode(List.of(new double[]{Double.NaN, 105.0})));
    }
}
