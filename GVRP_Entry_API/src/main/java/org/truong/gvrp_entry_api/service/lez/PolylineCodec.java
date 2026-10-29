package org.truong.gvrp_entry_api.service.lez;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder of Google encoded polylines, precision 1e5, points in
 * {@code [lat, lon]} order (LEZ-001, design §16.2-3).
 *
 * <p>Server side mirror of the engine's {@code PolylineEncoder}: Entry needs the
 * decoded line to test a leg against a zone polygon. The client has its own
 * small decoder for rendering.
 */
public final class PolylineCodec {

    static final double PRECISION = 1e5;

    private PolylineCodec() {
    }

    /**
     * Decodes a polyline into {@code {latitude, longitude}} pairs.
     *
     * @throws IllegalArgumentException when the text is truncated or malformed
     */
    public static List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        int index = 0;
        long lat = 0;
        long lon = 0;
        while (index < encoded.length()) {
            Decoded dLat = decodeValue(encoded, index);
            Decoded dLon = decodeValue(encoded, dLat.nextIndex());
            index = dLon.nextIndex();
            lat += dLat.delta();
            lon += dLon.delta();
            points.add(new double[]{lat / PRECISION, lon / PRECISION});
        }
        return points;
    }

    private static Decoded decodeValue(String encoded, int startIndex) {
        int index = startIndex;
        int shift = 0;
        long result = 0;
        int current;
        do {
            if (index >= encoded.length()) {
                throw new IllegalArgumentException("Encoded polyline is truncated");
            }
            current = encoded.charAt(index++) - 63;
            if (current < 0) {
                throw new IllegalArgumentException("Encoded polyline contains an invalid character");
            }
            result |= (long) (current & 0x1f) << shift;
            shift += 5;
        } while (current >= 0x20);
        long delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new Decoded(delta, index);
    }

    private record Decoded(long delta, int nextIndex) {
    }
}
