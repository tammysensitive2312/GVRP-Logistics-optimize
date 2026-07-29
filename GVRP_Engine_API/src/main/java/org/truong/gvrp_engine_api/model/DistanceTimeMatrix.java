package org.truong.gvrp_engine_api.model;

import com.graphhopper.jsprit.core.problem.Location;
import org.truong.gvrp_engine_api.distance_matrix.CostMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DenseCostMatrix;

import java.util.List;

/**
 * Ma trận + danh sách location, dùng xuyên suốt tầng service.
 * <p>
 * Trước đây record này giữ trực tiếp {@code double[][]} nên MỌI consumer đều buộc
 * bố cục phải là dày n×n. Giờ nó giữ {@link CostMatrix} để bố cục block-diagonal
 * (O(N·S)) dùng được mà không phải sửa logic nghiệp vụ nào.
 */
public record DistanceTimeMatrix(
        CostMatrix costs,
        List<Location> locations
) {
    /**
     * Tương thích ngược: dựng từ hai mảng dày (test và nhánh không cluster).
     */
    public DistanceTimeMatrix(double[][] distanceMatrix, double[][] timeMatrix,
                              List<Location> locations) {
        this(new DenseCostMatrix(distanceMatrix, timeMatrix), locations);
    }

    /** Khoảng cách (m) từ i -> j. Sentinel nếu cặp bị prune. */
    public double distance(int fromIndex, int toIndex) {
        return costs.distanceMeters(fromIndex, toIndex);
    }

    /** Thời gian (s) từ i -> j. Sentinel nếu cặp bị prune. */
    public double time(int fromIndex, int toIndex) {
        return costs.timeSeconds(fromIndex, toIndex);
    }
}
