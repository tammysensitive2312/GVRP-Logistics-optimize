package org.truong.gvrp_engine_api.clustering;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Tiền xử lý: gán mỗi vehicle vào ĐÚNG 1 cluster, theo Phương án 1 —
 * demand-proportional quota + nearest-greedy assignment (đã chốt với Truong
 * sau khi phát hiện thuật toán nearest-only thuần túy sụp đổ với topology
 * single/few-depot: mọi vehicle chung 1 depot sẽ dồn hết vào 1 cluster).
 * <p>
 * THUẬT TOÁN (2 bước):
 * 1. Tính quota n_c cho mỗi cluster, TỶ LỆ THUẬN với tổng demand của cluster
 *    đó, dùng Largest Remainder Method (Hamilton's method) để đảm bảo
 *    sum(n_c) = |V| CHÍNH XÁC — không lệch do làm tròn từng cluster riêng lẻ.
 * 2. Gán vehicle vào cluster theo greedy nearest-first trên TOÀN BỘ ma trận
 *    cặp (vehicle, cluster), nhưng bị CHẶN khi cluster đã đủ quota — vehicle
 *    "thua" quota ở cluster gần nhất sẽ tự động rơi xuống cluster gần thứ nhì
 *    còn chỗ, nhờ duyệt cặp theo thứ tự khoảng cách tăng dần toàn cục (không
 *    phải riêng theo từng vehicle).
 * <p>
 * TẠI SAO KHÔNG DÙNG CAPACITY VEHICLE ĐỂ TÍNH QUOTA (GIỚI HẠN ĐÃ BIẾT):
 * Quota hiện tính theo SỐ LƯỢNG vehicle, không theo capacity — giả định fleet
 * tương đối đồng nhất. Với fleet dị chủng (VehicleType.capacity chênh lệch
 * lớn), quota theo count có thể không phản ánh đúng năng lực chuyên chở thực
 * tế của mỗi cluster. Đây là giới hạn CHẤP NHẬN ĐƯỢC ở giai đoạn này — nâng
 * cấp lên quota theo tổng capacity nếu benchmark cho thấy unassigned lệch
 * bất thường ở các branch có fleet dị chủng mạnh.
 * <p>
 * RÀNG BUỘC BẮT BUỘC: centroids.size() <= vehicles.size(). Tầng gọi
 * (OptimizationService) PHẢI cap C = min(ceil(N/S_target), |V|) TRƯỚC KHI
 * chạy KMeansClusterer — class này CHỦ ĐỘNG throw nếu vi phạm, không tự ý
 * hạ số cluster (sẽ làm sai lệch kết quả phân cụm địa lý đã tính ở bước trước).
 *
 * @author Truong
 */
@Slf4j
public final class VehicleClusterAssigner {

    private VehicleClusterAssigner() {
        // Utility class — không khởi tạo instance
    }

    /**
     * @param vehicleId ID vehicle KHÔNG có prefix "vehicle-" (tầng gọi tự thêm khi cần)
     * @param depotLat  vĩ độ startDepot
     * @param depotLon  kinh độ startDepot
     */
    public record VehicleDepotInfo(String vehicleId, double depotLat, double depotLon) {}

    /**
     * Gán mỗi vehicle vào 1 cluster, theo quota tỷ lệ demand + nearest-greedy.
     *
     * @param vehicles  danh sách vehicle cần gán (KHÔNG rỗng)
     * @param centroids centroid các cluster sau merge (từ
     *                  {@link ClusterMergeService#computeCentroids})
     * @param demands   tổng demand mỗi cluster (từ
     *                  {@link ClusterMergeService#computeClusterDemands}) —
     *                  PHẢI cùng số cluster và cùng clusterId với centroids
     * @return Map vehicleId -> clusterId, LUÔN đủ entry cho mọi vehicle
     * @throws IllegalArgumentException nếu centroids.size() > vehicles.size()
     */
    public static Map<String, Integer> assign(
            List<VehicleDepotInfo> vehicles,
            List<ClusterMergeService.ClusterCentroid> centroids,
            List<ClusterMergeService.ClusterDemand> demands) {

        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("vehicles không được rỗng");
        }
        if (centroids == null || centroids.isEmpty()) {
            throw new IllegalArgumentException("centroids không được rỗng");
        }
        if (centroids.size() > vehicles.size()) {
            throw new IllegalArgumentException(String.format(
                    "Số cluster (%d) vượt quá số vehicle (%d) — vi phạm ràng buộc " +
                            "bắt buộc của hard-partition assignment. Tầng gọi phải giới hạn " +
                            "C = min(ceil(N/S_target), |V|) TRƯỚC KHI phân cụm.",
                    centroids.size(), vehicles.size()));
        }
        if (demands == null || demands.size() != centroids.size()) {
            throw new IllegalArgumentException("demands phải có cùng số cluster với centroids");
        }

        Map<Integer, Integer> residualQuota = new HashMap<>(computeQuota(demands, vehicles.size()));

        List<VehicleDepotInfo> sortedVehicles = new ArrayList<>(vehicles);
        sortedVehicles.sort(Comparator
                .comparing(VehicleClusterAssigner::extractNumericId)
                .thenComparing(VehicleDepotInfo::vehicleId));

        record Pair(VehicleDepotInfo vehicle, ClusterMergeService.ClusterCentroid centroid, double distSquared) {}

        // Ma trận đầy đủ |V| x |C| — chấp nhận được vì đây là preprocessing
        // 1 lần/job, KHÔNG phải hot path bên trong vòng lặp Jsprit.
        List<Pair> pairs = new ArrayList<>(sortedVehicles.size() * centroids.size());
        for (VehicleDepotInfo v : sortedVehicles) {
            for (ClusterMergeService.ClusterCentroid c : centroids) {
                double dLat = v.depotLat() - c.lat();
                double dLon = v.depotLon() - c.lon();
                pairs.add(new Pair(v, c, dLat * dLat + dLon * dLon));
            }
        }

        pairs.sort((a, b) -> {
            int cmp = Double.compare(a.distSquared(), b.distSquared());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.centroid().clusterId(), b.centroid().clusterId());
            if (cmp != 0) return cmp;
            return a.vehicle().vehicleId().compareTo(b.vehicle().vehicleId());
        });

        Map<String, Integer> result = new HashMap<>();
        Set<String> assignedVehicleIds = new HashSet<>();

        for (Pair pair : pairs) {
            if (assignedVehicleIds.contains(pair.vehicle().vehicleId())) {
                continue; // vehicle đã được gán ở cặp gần hơn
            }
            int clusterId = pair.centroid().clusterId();
            Integer remaining = residualQuota.get(clusterId);
            if (remaining == null || remaining <= 0) {
                continue; // cluster đã đủ quota
            }

            result.put(pair.vehicle().vehicleId(), clusterId);
            assignedVehicleIds.add(pair.vehicle().vehicleId());
            residualQuota.put(clusterId, remaining - 1);
        }

        // Bất biến bắt buộc: sum(quota) == |V| (computeQuota đảm bảo) trên ma
        // trận cặp đầy đủ -> MỌI vehicle phải được gán. Vi phạm là dấu hiệu
        // sai số floating-point tích lũy trong computeQuota — phải biết ngay,
        // KHÔNG âm thầm bỏ sót vehicle (sẽ gây unassigned không rõ nguyên nhân).
        if (result.size() != vehicles.size()) {
            throw new IllegalStateException(String.format(
                    "Bất biến vi phạm: chỉ %d/%d vehicle được gán cluster — kiểm tra lại computeQuota().",
                    result.size(), vehicles.size()));
        }

        long distinctClustersUsed = result.values().stream().distinct().count();
        log.info("✅ Gán {} vehicle vào {}/{} cluster (quota theo demand)",
                result.size(), distinctClustersUsed, centroids.size());

        return result;
    }

    /**
     * Largest Remainder Method (Hamilton's method): đảm bảo sum(quota) == |V|
     * CHÍNH XÁC, không lệch do làm tròn từng cluster riêng lẻ.
     */
    private static Map<Integer, Integer> computeQuota(
            List<ClusterMergeService.ClusterDemand> demands, int vehicleCount) {

        double totalDemand = demands.stream()
                .mapToDouble(ClusterMergeService.ClusterDemand::totalDemand)
                .sum();
        if (totalDemand <= 0) {
            throw new IllegalArgumentException("Tổng demand phải > 0, nhận được: " + totalDemand);
        }

        Map<Integer, Integer> floorQuota = new TreeMap<>();
        Map<Integer, Double> remainder = new TreeMap<>();
        int assignedSeats = 0;

        for (ClusterMergeService.ClusterDemand d : demands) {
            double raw = vehicleCount * (d.totalDemand() / totalDemand);
            int floor = (int) Math.floor(raw);
            floorQuota.put(d.clusterId(), floor);
            remainder.put(d.clusterId(), raw - floor);
            assignedSeats += floor;
        }

        int remainingSeats = vehicleCount - assignedSeats;

        List<Integer> byRemainderDesc = new ArrayList<>(remainder.keySet());
        byRemainderDesc.sort((a, b) -> {
            int cmp = Double.compare(remainder.get(b), remainder.get(a));
            return cmp != 0 ? cmp : Integer.compare(a, b); // tie-break: clusterId tăng dần
        });

        // remainingSeats về lý thuyết luôn < số cluster (mỗi remainder < 1),
        // nhưng dùng modulo phòng vệ sai số floating-point tích lũy.
        for (int i = 0; i < remainingSeats; i++) {
            int clusterId = byRemainderDesc.get(i % byRemainderDesc.size());
            floorQuota.merge(clusterId, 1, Integer::sum);
        }

        log.debug("Quota theo demand: {}", floorQuota);
        return floorQuota;
    }

    private static Long extractNumericId(VehicleDepotInfo v) {
        try {
            return Long.parseLong(v.vehicleId());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE; // non-numeric id -> đẩy về cuối, vẫn deterministic
        }
    }
}