package org.truong.gvrp_engine_api.distance_matrix;

import java.time.Duration;
import java.util.List;

/**
 * Ma trận khoảng cách/thời gian lưu dạng PRIMITIVE double[][] thay cho
 * Map<String,Entry> — để KHÔNG tạo ~n² object + chuỗi key ở quy mô lớn
 * (nguyên nhân OOM ở 6000+ orders). Ô bị prune / route lỗi mang giá trị
 * sentinel (MatrixMask.PRUNED_*), KHÔNG phải 0.
 */
public record DistanceMatrix(
        List<OptCoordinates> coordinates,
        double[][] distanceMeters,
        double[][] timeSeconds
) {
    /** Khoảng cách (m) từ i -> j. */
    public double distanceMeters(int fromIndex, int toIndex) {
        return distanceMeters[fromIndex][toIndex];
    }

    /** Thời gian (s) từ i -> j. */
    public double timeSeconds(int fromIndex, int toIndex) {
        return timeSeconds[fromIndex][toIndex];
    }

    /**
     * Tương thích ngược: dựng một DistanceMatrixEntry TẠM (không lưu trữ).
     * Đường nóng nên đọc thẳng distanceMeters()/timeSeconds() thay vì gọi hàm này.
     */
    public DistanceMatrixEntry get(int fromIndex, int toIndex) {
        return new DistanceMatrixEntry(
                Duration.ofSeconds((long) timeSeconds[fromIndex][toIndex]),
                Distance.ofMeters(distanceMeters[fromIndex][toIndex]));
    }
}
