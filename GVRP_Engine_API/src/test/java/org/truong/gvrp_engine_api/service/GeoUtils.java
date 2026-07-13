package org.truong.gvrp_engine_api.service;

import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

public final class GeoUtils {

    private static final double EARTH_RADIUS = 6_371_000; // meters

    private GeoUtils() {
        // Utility class
    }

    public static double haversine(OptCoordinates a, OptCoordinates b) {

        double lat1 = Math.toRadians(a.latDouble());
        double lon1 = Math.toRadians(a.lonDouble());

        double lat2 = Math.toRadians(b.latDouble());
        double lon2 = Math.toRadians(b.lonDouble());

        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        double sinLat = Math.sin(deltaLat / 2);
        double sinLon = Math.sin(deltaLon / 2);

        double haversine =
                sinLat * sinLat
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * sinLon
                        * sinLon;

        double angularDistance =
                2 * Math.atan2(
                        Math.sqrt(haversine),
                        Math.sqrt(1 - haversine));

        return EARTH_RADIUS * angularDistance;
    }
}