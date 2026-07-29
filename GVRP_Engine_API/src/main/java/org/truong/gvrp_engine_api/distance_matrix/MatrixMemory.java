package org.truong.gvrp_engine_api.distance_matrix;

/**
 * Số học bộ nhớ ma trận — tách riêng để TEST ĐƯỢC và để guard trích dẫn được
 * chính con số đã dùng khi quyết định.
 * <p>
 * Mọi công thức ở đây đều là số học trên kích thước bài toán, KHÔNG phải phỏng đoán:
 * <pre>
 *   dày:   M(n)     = 8 × 2 × n²                    (2 ma trận double)
 *   block: M(N,S,D) = 8 × (2·ΣS_c² + 4·W·n)         (block cụm + dải rộng 2 chiều)
 *   trần:  n_max    = sqrt(heap × safety / 16)
 * </pre>
 */
public final class MatrixMemory {

    /** Số ma trận double được lưu song song: distance + time. */
    public static final int MATRICES = 2;

    /** Bytes mỗi ô double. */
    public static final int BYTES_PER_CELL = 8;

    /**
     * Tỉ lệ heap tối đa được phép dùng cho ma trận. 0.6 vì Jsprit còn cần chỗ cho
     * N Service + R VehicleRoute + StateManager + các solution đang so sánh; vượt
     * ngưỡng này thì full-GC bắt đầu chạy liên tục và job "treo" mà không hề có
     * OutOfMemoryError (đúng triệu chứng job 50k).
     */
    public static final double HEAP_SAFETY_FRACTION = 0.6;

    private MatrixMemory() {
    }

    /** Bộ nhớ nếu lưu dày 2 × double[n][n]. */
    public static long denseBytes(int n) {
        return (long) BYTES_PER_CELL * MATRICES * n * n;
    }

    /** n lớn nhất còn an toàn với heap hiện tại nếu lưu dày. */
    public static int maxDenseSize(long maxHeapBytes) {
        return (int) Math.sqrt(maxHeapBytes * HEAP_SAFETY_FRACTION
                / (double) (BYTES_PER_CELL * MATRICES));
    }

    /** Trần an toàn hiện tại của JVM đang chạy. */
    public static int maxDenseSize() {
        return maxDenseSize(Runtime.getRuntime().maxMemory());
    }

    /**
     * Số ô thực sự cần lưu khi dùng block-diagonal.
     *
     * @param n           tổng số location
     * @param clusterSizes kích thước từng cụm (chỉ order đã gán cụm)
     * @param wideCount   số location cần hàng+cột đầy đủ (depot + order chưa gán cụm)
     */
    public static long blockCells(int n, int[] clusterSizes, int wideCount) {
        long cells = 0;
        for (int s : clusterSizes) {
            cells += (long) s * s;
        }
        // dải rộng: mỗi thành viên cần 1 hàng (n) + 1 cột (n)
        cells += 2L * wideCount * n;
        return cells;
    }

    public static long blockBytes(int n, int[] clusterSizes, int wideCount) {
        return (long) BYTES_PER_CELL * MATRICES * blockCells(n, clusterSizes, wideCount);
    }

    /**
     * GiB nhị phân (để so trực tiếp với -Xmx), MB thập phân (để so với con số báo cáo).
     * Ngưỡng và mẫu số phải cùng hệ, nếu không thì 1 MiB in ra thành "1.05 MB" và
     * đối soát log với tài liệu sẽ lệch.
     */
    public static String humanBytes(long bytes) {
        if (bytes >= (1L << 30)) return String.format("%.2f GiB", bytes / (double) (1L << 30));
        if (bytes >= 1_000_000L) return String.format("%.1f MB", bytes / 1e6);
        return bytes + " B";
    }

    /**
     * FAIL-LOUD cho nhánh lưu dày. Ném IllegalStateException kèm ĐẦY ĐỦ chuỗi số học
     * dẫn tới kết luận, và nói rõ đây là trần O(n²) chứ không phải rò rỉ — để lần sau
     * đọc log là biết ngay không cần tăng -Xmx.
     */
    public static void requireDenseFits(int n) {
        long required = denseBytes(n);
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (required > maxHeap * HEAP_SAFETY_FRACTION) {
            throw new IllegalStateException(String.format(
                    "Ma trận DÀY %dx%d cần %s (16·n²), heap tối đa %s, trần an toàn n ≈ %d. "
                            + "Đây là trần thuật toán O(n²), KHÔNG phải rò rỉ — tăng -Xmx không cứu được "
                            + "(job 50k cần ~64 GiB heap). Bật cluster-first để dùng "
                            + "BlockDiagonalCostMatrix: bộ nhớ chuyển sang O(N·S) tuyến tính.",
                    n, n, humanBytes(required), humanBytes(maxHeap), maxDenseSize(maxHeap)));
        }
    }
}
