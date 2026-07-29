package org.truong.gvrp_engine_api.distance_matrix;

/**
 * Tra cứu khoảng cách/thời gian giữa hai location theo INDEX nguyên — phẳng, O(1),
 * không phụ thuộc cách lưu trữ bên dưới.
 * <p>
 * VÌ SAO CẦN INTERFACE NÀY (số học, không phải sở thích kiến trúc):
 * <pre>
 * Lưu dày 2 × double[n][n]:  M(n) = 16 n²  bytes
 *   n = 18 064  ->   4.86 GiB
 *   n = 50 010  ->  37.27 GiB   <-- job 50k treo ở đây (GC thrash, KHÔNG có OOM)
 *   n_max(heap) = sqrt(heap × 0.6 / 16)  ->  -Xmx8g cho n_max ≈ 17 947
 * </pre>
 * Nhưng với cluster-first ({@link MatrixMask}), số ô THỰC SỰ có thể được đọc chỉ là
 * {@code N·S + 2·D·n} (S = CLUSTER_TARGET_SIZE, D = số depot) vì
 * {@code ClusterRouteConstraint} chặn route xuyên cụm và {@code NoPrunedEdgeConstraint}
 * chặn cạnh sentinel. Ở N = 50 000, S = 150, D = 10: 8.5M ô = <b>136 MB</b> thay vì
 * 37.27 GiB — giảm 294×, và độ phức tạp bộ nhớ từ O(n²) xuống O(N·S) (TUYẾN TÍNH).
 * <p>
 * Mask cũ prune 99.66% số cặp nhưng tiết kiệm 0 byte, vì ô bị prune vẫn chiếm đủ
 * 8 byte của nó trong mảng dày. Prune giảm TÍNH TOÁN; muốn giảm BỘ NHỚ phải đổi
 * CÁCH LƯU — đó là việc của {@link BlockDiagonalCostMatrix}.
 * <p>
 * HỢP ĐỒNG QUAN TRỌNG: với cặp (i,j) không có chỗ lưu (khác cụm), implementation
 * phải TRẢ VỀ sentinel {@link MatrixMask#PRUNED_METERS} / {@link MatrixMask#PRUNED_SECONDS},
 * KHÔNG được throw. Lý do: {@code NoPrunedEdgeConstraint} và
 * {@code assertNoSentinelEdgeTraversed} dựa vào việc ĐỌC ĐƯỢC sentinel để chặn cạnh
 * xuyên cụm; nếu getter throw thì hai lưới an toàn đó chết thay vì hoạt động.
 * <p>
 * Thread-safety: chỉ-đọc sau khi build xong → an toàn cho Jsprit đa luồng.
 */
public interface CostMatrix {

    /** Khoảng cách (mét) từ i -> j. Sentinel nếu cặp bị prune. */
    double distanceMeters(int i, int j);

    /** Thời gian (giây) từ i -> j. Sentinel nếu cặp bị prune. */
    double timeSeconds(int i, int j);

    /** Số location (cạnh của ma trận logic n×n). */
    int size();

    /** Bộ nhớ THỰC SỰ đã cấp phát (bytes) — để log và guard, không phải n². */
    long allocatedBytes();

    /** Tên cách lưu, cho log đối soát benchmark. */
    String layout();
}
