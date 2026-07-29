package org.truong.gvrp_engine_api.distance_matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lưu ma trận khoảng cách/thời gian theo BLOCK CỤM thay vì dày n×n.
 * <p>
 * <b>Vì sao đúng:</b> {@code ClusterRouteConstraint} chặn route xuyên cụm và
 * {@code NoPrunedEdgeConstraint} chặn cạnh sentinel, nên các ô "khác cụm" KHÔNG BAO GIỜ
 * nằm trên một cạnh hợp lệ. Chúng chỉ cần đọc ra sentinel — không cần lưu.
 * Tập ô cần lưu trùng KHỚP TUYỆT ĐỐI với tập ô mà {@link MatrixMask#needed(int, int)}
 * trả về {@code true}:
 * <pre>
 *   needed(i,j) = (i hoặc j là DEPOT)              -> dải rộng
 *              ∨ (i hoặc j là UNCLUSTERED)         -> dải rộng
 *              ∨ (clusterOf[i] == clusterOf[j])    -> block cụm
 * </pre>
 * DEPOT và UNCLUSTERED được gộp thành một nhóm "WIDE" vì mask xử lý chúng y hệt nhau:
 * cần cả hàng và cột đầy đủ.
 * <p>
 * <b>Bộ nhớ:</b> {@code 8 × (2·ΣS_c² + 4·W·n)}. Với N = 50 000, S = 150, W = 10:
 * <pre>
 *   dày  : 16 × 50 010²             = 37.27 GiB
 *   block: 8 × (2·334·150² + 4·10·50 010) = 136 MB      (giảm 294×)
 * </pre>
 * Độ phức tạp chuyển từ O(n²) sang O(N·S) — TUYẾN TÍNH theo số order.
 * <p>
 * <b>Bố cục:</b> mỗi cụm là một mảng PHẲNG {@code double[S_c × S_c]} (không phải
 * {@code double[S_c][S_c]}) để tránh S_c object mảng con và giữ cache locality.
 * <p>
 * <b>Thread-safety khi ghi:</b> {@link #put} an toàn khi mỗi thread độc quyền một
 * chỉ số hàng {@code i}, vì mọi slot đều được khoá bởi {@code (i,j)}: block ghi tại
 * {@code [localI·S + localJ]}, dải rộng ghi tại {@code wideRow[w][j]} (thread sở hữu i)
 * hoặc {@code wideCol[w][i]} (cũng chỉ index i của thread đó). Không có slot nào bị
 * hai thread cùng ghi. Sau khi build xong thì hoàn toàn chỉ-đọc.
 */
public final class BlockDiagonalCostMatrix implements CostMatrix {

    /** Nhóm WIDE: cần hàng + cột đầy đủ (depot, hoặc order chưa gán cụm). */
    private static final int WIDE = -1;

    private final int n;

    /** clusterOf[i] >= 0: id cụm đã nén; WIDE: thuộc dải rộng. */
    private final int[] clusterOf;

    /** Vị trí của i trong nhóm của nó (trong cụm, hoặc trong danh sách WIDE). */
    private final int[] localIdx;

    private final int[] clusterSize;
    private final double[][] blockDist;   // [c][localI * S_c + localJ]
    private final double[][] blockTime;

    private final double[][] wideRowDist; // [w][j] : wide -> j
    private final double[][] wideRowTime;
    private final double[][] wideColDist; // [w][i] : i -> wide
    private final double[][] wideColTime;

    /** Các j cần tính khi i thuộc cụm c = members(c) ∪ WIDE. */
    private final int[][] targetsByCluster;

    /** Dùng khi i thuộc WIDE: mọi j. */
    private final int[] allTargets;

    private final int wideCount;

    private BlockDiagonalCostMatrix(int n, int[] clusterOf, int[] localIdx, int[] clusterSize,
                                    double[][] blockDist, double[][] blockTime,
                                    double[][] wideRowDist, double[][] wideRowTime,
                                    double[][] wideColDist, double[][] wideColTime,
                                    int[][] targetsByCluster, int[] allTargets, int wideCount) {
        this.n = n;
        this.clusterOf = clusterOf;
        this.localIdx = localIdx;
        this.clusterSize = clusterSize;
        this.blockDist = blockDist;
        this.blockTime = blockTime;
        this.wideRowDist = wideRowDist;
        this.wideRowTime = wideRowTime;
        this.wideColDist = wideColDist;
        this.wideColTime = wideColTime;
        this.targetsByCluster = targetsByCluster;
        this.allTargets = allTargets;
        this.wideCount = wideCount;
    }

    // ==================== BUILD ====================

    /**
     * Cấp phát cấu trúc block từ nhãn cụm của {@link MatrixMask}.
     *
     * @param clusterByLoc nhãn cụm theo index location; {@link MatrixMask#DEPOT} và
     *                     {@link MatrixMask#UNCLUSTERED} được gộp vào dải rộng.
     */
    public static BlockDiagonalCostMatrix allocate(int[] clusterByLoc) {
        int n = clusterByLoc.length;

        // 1) Nén id cụm về 0..C-1 (KMeans có thể trả id thưa/không liên tục)
        Map<Integer, Integer> compact = new HashMap<>();
        List<List<Integer>> members = new ArrayList<>();
        List<Integer> wide = new ArrayList<>();

        int[] clusterOf = new int[n];
        int[] localIdx = new int[n];

        for (int i = 0; i < n; i++) {
            int raw = clusterByLoc[i];
            if (raw == MatrixMask.DEPOT || raw == MatrixMask.UNCLUSTERED) {
                clusterOf[i] = WIDE;
                localIdx[i] = wide.size();
                wide.add(i);
            } else {
                Integer c = compact.get(raw);
                if (c == null) {
                    c = members.size();
                    compact.put(raw, c);
                    members.add(new ArrayList<>());
                }
                clusterOf[i] = c;
                localIdx[i] = members.get(c).size();
                members.get(c).add(i);
            }
        }

        int cCount = members.size();
        int w = wide.size();
        int[] clusterSize = new int[cCount];
        for (int c = 0; c < cCount; c++) {
            clusterSize[c] = members.get(c).size();
        }

        // 2) GUARD: dải rộng là phần O(W·n) — nếu W lớn thì block cũng nổ như dày.
        //    Nói rõ con số để biết phải sửa VehicleClusterAssigner, không phải tăng -Xmx.
        long wideBytes = 4L * MatrixMemory.BYTES_PER_CELL * w * n;
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (wideBytes > maxHeap * 0.25) {
            long unclustered = 0;
            for (int i = 0; i < n; i++) {
                if (clusterByLoc[i] == MatrixMask.UNCLUSTERED) unclustered++;
            }
            throw new IllegalStateException(String.format(
                    "Dải rộng có %d thành viên (trong đó %d order CHƯA GÁN CỤM) × n=%d "
                            + "-> %s, vượt 25%% heap (%s). Mỗi thành viên dải rộng tốn 4·8·n = %s. "
                            + "Nguyên nhân gần như chắc chắn là VehicleClusterAssigner để sót order, "
                            + "KHÔNG phải thiếu bộ nhớ.",
                    w, unclustered, n, MatrixMemory.humanBytes(wideBytes),
                    MatrixMemory.humanBytes(maxHeap),
                    MatrixMemory.humanBytes(4L * MatrixMemory.BYTES_PER_CELL * n)));
        }

        // 3) Cấp phát: block phẳng + dải rộng 2 chiều
        double[][] blockDist = new double[cCount][];
        double[][] blockTime = new double[cCount][];
        for (int c = 0; c < cCount; c++) {
            int s = clusterSize[c];
            // GUARD TRÀN INT: offset trong block là localI·s + localJ, tính bằng int.
            // s > 46 340 thì s² > Integer.MAX_VALUE -> NegativeArraySizeException hoặc
            // (tệ hơn) offset âm/quấn vòng đọc lệch slot mà KHÔNG có lỗi nào. Đây đúng
            // là loại "sai âm thầm" phải chặn ở chỗ dữ liệu vô lý.
            if ((long) s * s > Integer.MAX_VALUE) {
                throw new IllegalStateException(String.format(
                        "Cụm %d có %d phần tử -> s² = %d vượt Integer.MAX_VALUE (%d). "
                                + "Trần an toàn cho một cụm là %d. Nguyên nhân gần như chắc chắn là "
                                + "ClusterMergeService gộp quá mạnh hoặc CLUSTER_TARGET_SIZE quá lớn "
                                + "(hiện đang kỳ vọng ~150), KHÔNG phải thiếu bộ nhớ.",
                        c, s, (long) s * s, Integer.MAX_VALUE, (int) Math.sqrt(Integer.MAX_VALUE)));
            }
            blockDist[c] = new double[s * s];
            blockTime[c] = new double[s * s];
        }
        double[][] wideRowDist = new double[w][n];
        double[][] wideRowTime = new double[w][n];
        double[][] wideColDist = new double[w][n];
        double[][] wideColTime = new double[w][n];

        // 4) Danh sách đích cần tính cho mỗi cụm = members(c) ∪ WIDE
        int[] wideArr = wide.stream().mapToInt(Integer::intValue).toArray();
        int[][] targetsByCluster = new int[cCount][];
        for (int c = 0; c < cCount; c++) {
            List<Integer> m = members.get(c);
            int[] t = new int[m.size() + wideArr.length];
            for (int k = 0; k < m.size(); k++) t[k] = m.get(k);
            System.arraycopy(wideArr, 0, t, m.size(), wideArr.length);
            targetsByCluster[c] = t;
        }
        int[] allTargets = new int[n];
        for (int i = 0; i < n; i++) allTargets[i] = i;

        return new BlockDiagonalCostMatrix(n, clusterOf, localIdx, clusterSize,
                blockDist, blockTime, wideRowDist, wideRowTime, wideColDist, wideColTime,
                targetsByCluster, allTargets, w);
    }

    /**
     * Các chỉ số j cần tính cho hàng i. Chỉ trả về đúng những ô có chỗ lưu — nhờ đó
     * vòng lặp fill là O(N·S) chứ không phải O(n²) như bản cũ (n = 50 010 nghĩa là
     * 2.5 TỈ vòng, mỗi ô pruned còn gọi một atomic increment bị tranh chấp).
     */
    public int[] targetsFor(int i) {
        int c = clusterOf[i];
        return c == WIDE ? allTargets : targetsByCluster[c];
    }

    /**
     * Ghi một ô. Cặp không có chỗ lưu (khác cụm) bị BỎ QUA im lặng — đúng ngữ nghĩa,
     * vì getter sẽ trả sentinel cho chúng.
     */
    public void put(int i, int j, double distance, double time) {
        if (i == j) return;
        int ci = clusterOf[i];
        if (ci == WIDE) {
            int w = localIdx[i];
            wideRowDist[w][j] = distance;
            wideRowTime[w][j] = time;
            return;
        }
        int cj = clusterOf[j];
        if (cj == WIDE) {
            int w = localIdx[j];
            wideColDist[w][i] = distance;
            wideColTime[w][i] = time;
            return;
        }
        if (ci != cj) return;
        int off = localIdx[i] * clusterSize[ci] + localIdx[j];
        blockDist[ci][off] = distance;
        blockTime[ci][off] = time;
    }

    // ==================== READ ====================
    // Thứ tự nhánh PHẢI khớp put() và MatrixMask.needed(), nếu không sẽ đọc lệch slot.

    @Override
    public double distanceMeters(int i, int j) {
        if (i == j) return 0.0;
        int ci = clusterOf[i];
        if (ci == WIDE) return wideRowDist[localIdx[i]][j];
        int cj = clusterOf[j];
        if (cj == WIDE) return wideColDist[localIdx[j]][i];
        if (ci != cj) return MatrixMask.PRUNED_METERS;
        return blockDist[ci][localIdx[i] * clusterSize[ci] + localIdx[j]];
    }

    @Override
    public double timeSeconds(int i, int j) {
        if (i == j) return 0.0;
        int ci = clusterOf[i];
        if (ci == WIDE) return wideRowTime[localIdx[i]][j];
        int cj = clusterOf[j];
        if (cj == WIDE) return wideColTime[localIdx[j]][i];
        if (ci != cj) return MatrixMask.PRUNED_SECONDS;
        return blockTime[ci][localIdx[i] * clusterSize[ci] + localIdx[j]];
    }

    @Override
    public int size() {
        return n;
    }

    @Override
    public long allocatedBytes() {
        return MatrixMemory.blockBytes(n, clusterSize, wideCount);
    }

    @Override
    public String layout() {
        return String.format("BLOCK_DIAGONAL(C=%d, W=%d)", clusterSize.length, wideCount);
    }

    // ==================== THÔNG TIN CHO LOG / TEST ====================

    public int clusterCount() {
        return clusterSize.length;
    }

    public int wideCount() {
        return wideCount;
    }

    public int[] clusterSizes() {
        return clusterSize.clone();
    }

    /** Số ô có chỗ lưu — phải bằng số ô mà MatrixMask.needed() trả true. */
    public long storedCells() {
        return MatrixMemory.blockCells(n, clusterSize, wideCount);
    }

    /** Tỉ lệ ô của ma trận dày mà ta KHÔNG cần lưu. */
    public double savedFraction() {
        return 1.0 - storedCells() / ((double) n * n);
    }
}
