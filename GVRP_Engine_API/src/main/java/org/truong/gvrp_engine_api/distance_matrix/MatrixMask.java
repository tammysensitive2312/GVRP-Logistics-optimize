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

    /** ∞ hữu hạn: đủ lớn để solver không bao giờ chọn, đủ nhỏ để không overflow khi cộng dồn. */
    public static final double PRUNED_METERS  = 1e9;
    public static final double PRUNED_SECONDS = 1e7;

    private static final int DEPOT       = -1; // location là depot
    private static final int UNCLUSTERED = -2; // order chưa gán cụm (an toàn: coi như cần tính)

    private final int[] clusterByLoc; // theo index trong context.allLocations()
    private final boolean full;       // true => tính tất cả (không cluster)

    private MatrixMask(int[] clusterByLoc, boolean full) {
        this.clusterByLoc = clusterByLoc;
        this.full = full;
    }

    /** clusterAssignment key: "order-{id}" / "vehicle-{id}" -> clusterId (từ buildClusterAssignmentIfEligible). */
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

    /** Có cần gọi GraphHopper cho cặp (i -> j) không? (i != j giả định đã xử lý riêng). */
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