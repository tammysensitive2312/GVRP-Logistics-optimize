package org.truong.gvrp_engine_api.distance_matrix;

import java.time.Duration;
import java.util.List;

/**
 * Ma trận khoảng cách/thời gian. Việc LƯU TRỮ được uỷ cho {@link CostMatrix}:
 * <ul>
 *   <li>{@link BlockDiagonalCostMatrix} — O(N·S), dùng khi có cluster-first;</li>
 *   <li>{@link DenseCostMatrix} — O(n²), chỉ cho job nhỏ / nhánh Pareto.</li>
 * </ul>
 * Ô bị prune / route lỗi mang giá trị sentinel ({@link MatrixMask#PRUNED_METERS} /
 * {@link MatrixMask#PRUNED_SECONDS}), KHÔNG phải 0 — để {@code NoPrunedEdgeConstraint}
 * chặn được cạnh xuyên cụm thay vì coi nó là cạnh miễn phí.
 */
public record DistanceMatrix(
        List<OptCoordinates> coordinates,
        CostMatrix costs
) {
    /** Khoảng cách (m) từ i -> j. */
    public double distanceMeters(int fromIndex, int toIndex) {
        return costs.distanceMeters(fromIndex, toIndex);
    }

    /** Thời gian (s) từ i -> j. */
    public double timeSeconds(int fromIndex, int toIndex) {
        return costs.timeSeconds(fromIndex, toIndex);
    }

    public int size() {
        return costs.size();
    }

    /**
     * Tương thích ngược: dựng một DistanceMatrixEntry TẠM (không lưu trữ).
     * Đường nóng nên đọc thẳng distanceMeters(i,j)/timeSeconds(i,j) thay vì gọi hàm này.
     */
    public DistanceMatrixEntry get(int fromIndex, int toIndex) {
        return new DistanceMatrixEntry(
                Duration.ofSeconds((long) costs.timeSeconds(fromIndex, toIndex)),
                Distance.ofMeters(costs.distanceMeters(fromIndex, toIndex)));
    }
}
