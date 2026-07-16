package org.truong.gvrp_engine_api.clustering;

import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Merge các cụm nhỏ (Phương án C đã chốt) — xử lý TRƯỚC khi đưa cluster
 * assignment vào HardRouteConstraint, để giảm rủi ro unassigned-do-cluster-lệch
 * mà không phải nới lỏng hard constraint (giữ nguyên lợi ích performance).
 * <p>
 * THUẬT TOÁN:
 * 1. Tính totalDemand + centroid cho mỗi cụm KHÔNG rỗng (dựa trên
 *    {@link KMeansClusterer.ClusterAssignment#nonEmptyClusterIds()}).
 * 2. Lặp: tìm cụm có totalDemand < minClusterDemandThreshold VÀ có demand
 *    NHỎ NHẤT (greedy, deterministic). Merge nó vào cụm khác gần nhất về
 *    mặt centroid (Euclidean, cùng cách đo với KMeansClusterer).
 * 3. Dùng Union-Find để xử lý merge chain: nếu cụm gộp sau merge VẪN còn
 *    nhỏ, nó tiếp tục là ứng viên merge ở vòng lặp sau — không bị bỏ sót.
 * 4. Dừng khi không còn cụm nào dưới ngưỡng, hoặc chỉ còn 1 cụm.
 * 5. Remap cluster ID về dạng liền mạch 0..(M-1) — output numClusters()
 *    LUÔN khớp thực tế (khác với KMeansClusterer.ClusterAssignment, nơi
 *    numClusters() chỉ phản ánh số cụm YÊU CẦU ban đầu).
 * <p>
 * TẠI SAO minClusterDemandThreshold LÀ THAM SỐ, KHÔNG PHẢI HẰNG SỐ:
 * Ngưỡng này phụ thuộc dữ liệu nghiệp vụ (capacity nhỏ nhất trong các
 * vehicle type khả dụng cho job hiện tại) — chỉ tầng gọi (OptimizationService)
 * mới biết, không phải hằng số thuật toán thuần túy như CONVERGENCE_THRESHOLD
 * trong KMeansClusterer.
 * <p>
 * TẠI SAO CENTROID TÍNH THEO SỐ ĐIỂM, KHÔNG THEO DEMAND:
 * Centroid ở đây dùng để tìm "cụm hàng xóm gần nhất về địa lý" — nếu weight
 * theo demand, 1 order khổng lồ có thể kéo lệch centroid khỏi vị trí địa lý
 * thực, làm sai bước tìm hàng xóm.
 *
 * @author Truong
 */
@Slf4j
public final class ClusterMergeService {

    private ClusterMergeService() {
        // Utility class — không khởi tạo instance
    }

    /**
     * Kết quả sau merge.
     *
     * @param clusterIdByOrderIndex ánh xạ index order -> cluster ID (đã remap liền mạch)
     * @param numClusters           số cụm THỰC SỰ sau merge — KHÔNG như
     *                              KMeansClusterer.ClusterAssignment, giá trị này LUÔN
     *                              khớp đúng số cụm không rỗng, vì đã remap liền mạch
     *                              0..(numClusters-1). Tầng gọi có thể lặp
     *                              [0, numClusters) an toàn, không cần nonEmptyClusterIds().
     */
    public record MergedClusterAssignment(int[] clusterIdByOrderIndex, int numClusters) {}

    /**
     * Merge các cụm nhỏ trong kết quả K-means, dựa trên tổng demand.
     *
     * @param initialAssignment        kết quả từ {@link KMeansClusterer#fit}
     * @param coordinates               danh sách tọa độ orders — PHẢI cùng thứ tự index
     *                                  với coordinates đã truyền vào KMeansClusterer.fit()
     * @param demands                   demands[i] = nhu cầu (kg) của order tại index i,
     *                                  PHẢI cùng độ dài và cùng thứ tự index với coordinates
     * @param minClusterDemandThreshold ngưỡng demand tối thiểu — cụm có tổng demand nhỏ hơn
     *                                  giá trị này sẽ bị merge vào cụm gần nhất. Giá trị này
     *                                  do tầng gọi tính (ví dụ: capacity nhỏ nhất trong các
     *                                  vehicle type khả dụng), tính bằng đơn vị demand THÔ
     *                                  (chưa nhân DEMAND_SCALE — xem AppConstant.DEMAND_SCALE,
     *                                  không áp dụng ở tầng này).
     * @return MergedClusterAssignment với cluster ID đã remap liền mạch, không cụm rỗng
     */
    public static MergedClusterAssignment merge(
            KMeansClusterer.ClusterAssignment initialAssignment,
            List<OptCoordinates> coordinates,
            double[] demands,
            double minClusterDemandThreshold) {

        validateInputs(initialAssignment, coordinates, demands, minClusterDemandThreshold);

        // ===== BƯỚC 1: Khởi tạo Union-Find + tính stats ban đầu cho mỗi cụm =====
        Set<Integer> activeClusterIds = new TreeSet<>(initialAssignment.nonEmptyClusterIds());
        Map<Integer, Integer> parent = new HashMap<>();
        for (int id : activeClusterIds) {
            parent.put(id, id);
        }

        Map<Integer, ClusterStats> stats = computeInitialStats(initialAssignment, coordinates, demands, activeClusterIds);

        int initialClusterCount = activeClusterIds.size();
        int mergeCount = 0;

        // ===== BƯỚC 2: Lặp merge cụm nhỏ nhất cho đến khi không còn cụm dưới ngưỡng =====
        while (activeClusterIds.size() > 1) {
            Integer smallest = findSmallestClusterBelowThreshold(activeClusterIds, stats, minClusterDemandThreshold);
            if (smallest == null) {
                break; // không còn cụm nào dưới ngưỡng -> dừng
            }

            int nearestTarget = findNearestOtherCluster(smallest, activeClusterIds, stats);

            ClusterStats sourceStatsBeforeMerge = stats.get(smallest);
            double sourceDemand = sourceStatsBeforeMerge.totalDemand;

            mergeInto(smallest, nearestTarget, parent, stats, activeClusterIds);
            mergeCount++;

            log.info("🔗 Merge cụm {} (demand={} < ngưỡng={}) vào cụm {} — demand cụm gộp mới: {}",
                    smallest, sourceDemand, minClusterDemandThreshold,
                    nearestTarget, stats.get(nearestTarget).totalDemand);
        }

        log.info("✅ Hoàn tất merge: {} cụm ban đầu -> {} cụm sau merge ({} lần merge)",
                initialClusterCount, activeClusterIds.size(), mergeCount);

        // ===== BƯỚC 3: Remap cluster ID về dạng liền mạch 0..(M-1) =====
        return remapToContiguousIds(initialAssignment, parent, activeClusterIds);
    }

    // ==================== TÍNH STATS BAN ĐẦU ====================

    private static Map<Integer, ClusterStats> computeInitialStats(
            KMeansClusterer.ClusterAssignment assignment,
            List<OptCoordinates> coordinates,
            double[] demands,
            Set<Integer> activeClusterIds) {

        Map<Integer, ClusterStats> stats = new HashMap<>();
        for (int id : activeClusterIds) {
            stats.put(id, new ClusterStats());
        }

        int[] clusterIdByIndex = assignment.clusterIdByOrderIndex();
        for (int i = 0; i < clusterIdByIndex.length; i++) {
            int clusterId = clusterIdByIndex[i];
            ClusterStats s = stats.get(clusterId);
            s.sumLat += coordinates.get(i).latDouble();
            s.sumLon += coordinates.get(i).lonDouble();
            s.count++;
            s.totalDemand += demands[i];
        }

        return stats;
    }

    // ==================== TÌM CỤM NHỎ NHẤT DƯỚI NGƯỠNG ====================

    /**
     * Tìm cụm có totalDemand < threshold VÀ nhỏ nhất trong số đó (greedy).
     * Tie-break theo cluster ID tăng dần để đảm bảo deterministic.
     *
     * @return cluster ID cần merge, hoặc null nếu không còn cụm nào dưới ngưỡng
     */
    private static Integer findSmallestClusterBelowThreshold(
            Set<Integer> activeClusterIds, Map<Integer, ClusterStats> stats, double threshold) {

        Integer best = null;
        double bestDemand = Double.MAX_VALUE;

        // activeClusterIds là TreeSet -> duyệt theo thứ tự ID tăng dần,
        // đảm bảo tie-break nhất quán khi nhiều cụm có cùng demand nhỏ nhất.
        for (int id : activeClusterIds) {
            double demand = stats.get(id).totalDemand;
            if (demand < threshold && demand < bestDemand) {
                best = id;
                bestDemand = demand;
            }
        }

        return best;
    }

    // ==================== TÌM CỤM GẦN NHẤT ĐỂ MERGE VÀO ====================

    private static int findNearestOtherCluster(
            int sourceId, Set<Integer> activeClusterIds, Map<Integer, ClusterStats> stats) {

        ClusterStats source = stats.get(sourceId);
        double sourceLat = source.centroidLat();
        double sourceLon = source.centroidLon();

        int nearest = -1;
        double nearestDistSquared = Double.MAX_VALUE;

        for (int candidateId : activeClusterIds) {
            if (candidateId == sourceId) {
                continue;
            }
            ClusterStats candidate = stats.get(candidateId);
            double dLat = candidate.centroidLat() - sourceLat;
            double dLon = candidate.centroidLon() - sourceLon;
            double distSquared = dLat * dLat + dLon * dLon;

            if (distSquared < nearestDistSquared) {
                nearestDistSquared = distSquared;
                nearest = candidateId;
            }
        }

        return nearest;
    }

    // ==================== THỰC HIỆN MERGE (UNION-FIND) ====================

    private static void mergeInto(
            int sourceId, int targetId,
            Map<Integer, Integer> parent,
            Map<Integer, ClusterStats> stats,
            Set<Integer> activeClusterIds) {

        ClusterStats source = stats.get(sourceId);
        ClusterStats target = stats.get(targetId);

        target.sumLat += source.sumLat;
        target.sumLon += source.sumLon;
        target.count += source.count;
        target.totalDemand += source.totalDemand;

        // QUAN TRỌNG: parent map KHÔNG bị xóa entry — cần giữ nguyên để
        // find() có thể truy vết từ bất kỳ cluster ID gốc nào (kể cả những
        // ID đã bị merge từ lâu) về đúng root hiện tại ở bước remap cuối cùng.
        parent.put(sourceId, targetId);

        activeClusterIds.remove(sourceId);
        stats.remove(sourceId);
    }

    /**
     * Tìm root hiện tại của 1 cluster ID gốc, có path compression để tăng tốc
     * các lần tra cứu sau (không ảnh hưởng correctness, chỉ tối ưu hằng số).
     */
    private static int find(int id, Map<Integer, Integer> parent) {
        int root = id;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }

        // Path compression
        int current = id;
        while (parent.get(current) != root) {
            int next = parent.get(current);
            parent.put(current, root);
            current = next;
        }

        return root;
    }

    // ==================== REMAP VỀ ID LIỀN MẠCH ====================

    private static MergedClusterAssignment remapToContiguousIds(
            KMeansClusterer.ClusterAssignment initialAssignment,
            Map<Integer, Integer> parent,
            Set<Integer> finalActiveClusterIds) {

        // finalActiveClusterIds là TreeSet -> duyệt theo thứ tự tăng dần để
        // đánh số liền mạch 0..(M-1) một cách deterministic (không phụ thuộc
        // thứ tự HashMap).
        Map<Integer, Integer> rootToContiguousId = new TreeMap<>();
        int nextId = 0;
        for (int root : finalActiveClusterIds) {
            rootToContiguousId.put(root, nextId++);
        }

        int[] originalClusterIdByIndex = initialAssignment.clusterIdByOrderIndex();
        int[] remapped = new int[originalClusterIdByIndex.length];

        for (int i = 0; i < originalClusterIdByIndex.length; i++) {
            int originalClusterId = originalClusterIdByIndex[i];
            int root = find(originalClusterId, parent);
            remapped[i] = rootToContiguousId.get(root);
        }

        return new MergedClusterAssignment(remapped, finalActiveClusterIds.size());
    }

    // ==================== VALIDATE ====================

    private static void validateInputs(
            KMeansClusterer.ClusterAssignment initialAssignment,
            List<OptCoordinates> coordinates,
            double[] demands,
            double minClusterDemandThreshold) {

        if (initialAssignment == null) {
            throw new IllegalArgumentException("initialAssignment không được null");
        }
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("coordinates không được rỗng");
        }
        if (demands == null) {
            throw new IllegalArgumentException("demands không được null");
        }
        if (coordinates.size() != demands.length) {
            throw new IllegalArgumentException(String.format(
                    "coordinates.size() (%d) phải khớp demands.length (%d) — cùng thứ tự index",
                    coordinates.size(), demands.length));
        }
        if (coordinates.size() != initialAssignment.clusterIdByOrderIndex().length) {
            throw new IllegalArgumentException(String.format(
                    "coordinates.size() (%d) phải khớp initialAssignment.clusterIdByOrderIndex().length (%d)",
                    coordinates.size(), initialAssignment.clusterIdByOrderIndex().length));
        }
        if (minClusterDemandThreshold <= 0) {
            throw new IllegalArgumentException(
                    "minClusterDemandThreshold phải > 0, nhận được: " + minClusterDemandThreshold);
        }
        for (int i = 0; i < demands.length; i++) {
            if (demands[i] < 0) {
                throw new IllegalArgumentException(
                        "demands[" + i + "] âm bất thường: " + demands[i]);
            }
        }
    }

    // ==================== STATS HOLDER (MUTABLE — cập nhật trong quá trình merge) ====================

    private static class ClusterStats {
        double sumLat = 0.0;
        double sumLon = 0.0;
        int count = 0;
        double totalDemand = 0.0;

        double centroidLat() {
            return sumLat / count;
        }

        double centroidLon() {
            return sumLon / count;
        }
    }
}