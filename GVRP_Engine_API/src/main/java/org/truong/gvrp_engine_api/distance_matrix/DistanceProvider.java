package org.truong.gvrp_engine_api.distance_matrix;

public interface DistanceProvider {

    /**
     * Fetch distance and time between two coordinates
     */
    DistanceMatrixEntry fetch(OptCoordinates from, OptCoordinates to);

}
