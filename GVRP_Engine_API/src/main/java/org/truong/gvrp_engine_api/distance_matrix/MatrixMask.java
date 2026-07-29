package org.truong.gvrp_engine_api.distance_matrix;

import com.graphhopper.jsprit.core.problem.Location;
import org.truong.gvrp_engine_api.model.OptimizationContext;

import java.util.List;
import java.util.Map;

/**
 * Quyết định cặp (i,j) trong ma trận có cần gọi GraphHopper hay không.
 * Cặp bị loại (pruned) sẽ được điền SENTINEL (∞ hữu hạn), KHÔNG phải 0.
 */
public final class MatrixMask {

    /**
     * ∞ hữu hạn: đủ lớn để solver không bao giờ chọn, đủ nhỏ để không overflow khi cộng dồn.
     */
    public static final double PRUNED_METERS = 1e9;
    public static final double PRUNED_SECONDS = 1e7;

    /**
     * Nhãn depot: nối tới mọi thứ, không bao giờ prune.
     * PUBLIC vì BlockDiagonalCostMatrix phải phân loại được nhãn để cấp phát dải rộng —
     * hai lớp buộc phải đồng thuận về nhãn, nếu lệch thì đọc lệch slot.
     */
    public static final int DEPOT = -1;

    /** Nhãn order chưa được gán cụm: xử lý y như depot (an toàn thì tính). */
    public static final int UNCLUSTERED = -2;

    private final int[] clusterByLoc;
    private final boolean full;

    private MatrixMask(int[] clusterByLoc, boolean full) {
        this.clusterByLoc = clusterByLoc;
        this.full = full;
    }

    public static MatrixMask fromClusters(OptimizationContext context, Map<String, Integer> clusterAssignment) {
        if (clusterAssignment == null || clusterAssignment.isEmpty()) {
            return new MatrixMask(null, true); // full
        }
        List<Location> locs = context.allLocations();
        int[] clusterByLoc = new int[locs.size()];
        for (int k = 0; k < locs.size(); k++) {
            String id = locs.get(k).getId(); // "depot-{id}" hoặc "order-{id}"
            if (id.startsWith("depot-")) {
                clusterByLoc[k] = DEPOT;
            } else {
                clusterByLoc[k] = clusterAssignment.getOrDefault(id, UNCLUSTERED);
            }
        }
        return new MatrixMask(clusterByLoc, false);
    }

    /**
     * Dựng mask trực tiếp từ nhãn cụm — CHỈ dùng cho test, để kiểm bất biến
     * "tập needed() == tập ô block lưu được" mà không cần cả OptimizationContext.
     */
    public static MatrixMask forTesting(int[] clusterByLoc) {
        return new MatrixMask(clusterByLoc, false);
    }

    /** Mask "đầy" = không prune gì cả (nhánh Pareto / job nhỏ) → phải lưu dày. */
    public boolean isFull() {
        return full;
    }

    /**
     * Nhãn cụm theo index location. Trả về mảng NỘI BỘ (không clone) vì nó có thể dài
     * 50 000 phần tử và chỉ được đọc; clone ở đây là 200 KB rác mỗi lần gọi.
     *
     * @return null nếu mask đầy
     */
    public int[] clusterByLoc() {
        return clusterByLoc;
    }

    /**
     * Có cần gọi GraphHopper cho cặp (i -> j) không? (i != j giả định đã xử lý riêng).
     * <p>
     * BẤT BIẾN: tập {(i,j) : needed(i,j)} phải TRÙNG KHỚP tập ô mà
     * {@link BlockDiagonalCostMatrix} có chỗ lưu. Sửa hàm này thì phải sửa cả bố cục
     * block, nếu không sẽ có ô được tính nhưng bị ném đi im lặng.
     */
    public boolean needed(int i, int j) {
        if (full) return true;
        int ci = clusterByLoc[i], cj = clusterByLoc[j];
        // Depot nối tới mọi thứ — KHÔNG BAO GIỜ prune hàng/cột depot (nếu không order thành bất khả đạt).
        if (ci == DEPOT || cj == DEPOT) return true;
        // Order chưa gán cụm: an toàn thì tính.
        if (ci == UNCLUSTERED || cj == UNCLUSTERED) return true;
        // Còn lại: chỉ tính khi cùng cụm.
        return ci == cj;
    }
}