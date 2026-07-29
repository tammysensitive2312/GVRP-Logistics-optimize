package org.truong.gvrp_engine_api.distance_matrix;

/**
 * Lưu dày 2 × double[n][n] — cách cũ, GIỮ LẠI cho:
 * - nhánh Pareto (mask = null, job nhỏ),
 * - test đối chiếu (oracle để chứng minh BlockDiagonalCostMatrix trả cùng giá trị),
 * - job không bật cluster-first (N < CLUSTER_FIRST_ORDER_THRESHOLD).
 * <p>
 * Bộ nhớ O(n²) — xem {@link MatrixMemory#requireDenseFits(int)} để biết trần.
 */
public final class DenseCostMatrix implements CostMatrix {

    private final double[][] distanceMeters;
    private final double[][] timeSeconds;

    public DenseCostMatrix(double[][] distanceMeters, double[][] timeSeconds) {
        int n = distanceMeters.length;
        if (timeSeconds.length != n) {
            throw new IllegalArgumentException(String.format(
                    "distance/time lệch kích thước: %d vs %d", n, timeSeconds.length));
        }
        // Ma trận phải VUÔNG: nếu không, lỗi sẽ nổ muộn dưới dạng
        // ArrayIndexOutOfBounds ở giữa vòng lặp solver, rất khó truy về đây.
        for (int i = 0; i < n; i++) {
            if (distanceMeters[i].length != n || timeSeconds[i].length != n) {
                throw new IllegalArgumentException(String.format(
                        "Ma trận không vuông tại hàng %d: distance=%d, time=%d, kỳ vọng n=%d",
                        i, distanceMeters[i].length, timeSeconds[i].length, n));
            }
        }
        this.distanceMeters = distanceMeters;
        this.timeSeconds = timeSeconds;
    }

    /** Cấp phát mới, có guard bộ nhớ fail-loud. */
    public static DenseCostMatrix allocate(int n) {
        MatrixMemory.requireDenseFits(n);
        return new DenseCostMatrix(new double[n][n], new double[n][n]);
    }

    public void put(int i, int j, double distance, double time) {
        distanceMeters[i][j] = distance;
        timeSeconds[i][j] = time;
    }

    @Override
    public double distanceMeters(int i, int j) {
        return distanceMeters[i][j];
    }

    @Override
    public double timeSeconds(int i, int j) {
        return timeSeconds[i][j];
    }

    @Override
    public int size() {
        return distanceMeters.length;
    }

    @Override
    public long allocatedBytes() {
        return MatrixMemory.denseBytes(size());
    }

    @Override
    public String layout() {
        return "DENSE";
    }
}
