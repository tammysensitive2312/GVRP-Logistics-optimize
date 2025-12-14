package org.truong.gvrp_engine_api.distance_matrix;

import java.time.Duration;

public record DistanceMatrixEntry(Duration time, Distance distance) {

    public static final DistanceMatrixEntry ZERO =
            new DistanceMatrixEntry(Duration.ZERO, Distance.ZERO);

    public double timeSeconds() {
        return time.getSeconds();
    }

    public double distanceMeters() {
        return distance.metersDouble();
    }
}
