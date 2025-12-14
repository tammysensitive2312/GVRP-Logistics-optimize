package org.truong.gvrp_engine_api.distance_matrix;

import java.util.List;
import java.util.Map;

public record DistanceMatrix(
        List<OptCoordinates> coordinates,
        Map<String, DistanceMatrixEntry> entries  // Key = "fromIndex-toIndex"
) {

    public DistanceMatrixEntry get(int fromIndex, int toIndex) {
        String key = fromIndex + "-" + toIndex;
        return entries.getOrDefault(key, DistanceMatrixEntry.ZERO);
    }
}
