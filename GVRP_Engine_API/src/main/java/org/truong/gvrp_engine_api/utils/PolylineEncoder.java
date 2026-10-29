package org.truong.gvrp_engine_api.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Google encoded polyline algorithm, precision 1e5, points in {@code [lat, lon]}
 * order (design §16.2-3).
 *
 * <p>Julian Bui's reference algorithm: each coordinate is multiplied by 1e5,
 * delta-encoded, zig-zag encoded and written in 5-bit chunks with 63 added
 * ({@code _p~iF~ps|U_ulLnnqC_mqNvxq`@} is the published example).
 */
public final class PolylineEncoder {

    /** Five decimal places, as used by the Google algorithm and the client decoders. */
    static final double PRECISION = 1e5;

    private PolylineEncoder() {
    }

    /**
     * Encodes a list of {@code {latitude, longitude}} pairs.
     *
     * @throws IllegalArgumentException when a point is missing or not finite
     */
    public static String encode(List<double[]> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        long lastLat = 0;
        long lastLon = 0;
        for (double[] point : points) {
            if (point == null || point.length < 2
                    || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
                throw new IllegalArgumentException("A polyline point needs finite latitude and longitude");
            }
            long lat = Math.round(point[0] * PRECISION);
            long lon = Math.round(point[1] * PRECISION);
            encodeSigned(lat - lastLat, out);
            encodeSigned(lon - lastLon, out);
            lastLat = lat;
            lastLon = lon;
        }
        return out.toString();
    }

    /** Decodes an encoded polyline into {@code {latitude, longitude}} pairs. */
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

    private static void encodeSigned(long value, StringBuilder out) {
        long zigzag = value < 0 ? ~(value << 1) : (value << 1);
        while (zigzag >= 0x20) {
            out.append((char) ((0x20 | (zigzag & 0x1f)) + 63));
            zigzag >>= 5;
        }
        out.append((char) (zigzag + 63));
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
            result |= (long) (current & 0x1f) << shift;
            shift += 5;
        } while (current >= 0x20);
        long delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new Decoded(delta, index);
    }

    private record Decoded(long delta, int nextIndex) {
    }
}
