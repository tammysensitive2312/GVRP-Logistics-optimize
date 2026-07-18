package org.truong.gvrp_engine_api.clustering;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.clustering.ClusterMergeService.ClusterCentroid;
import org.truong.gvrp_engine_api.clustering.ClusterMergeService.ClusterDemand;
import org.truong.gvrp_engine_api.clustering.VehicleClusterAssigner.VehicleDepotInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho VehicleClusterAssigner — Phương án 1 (demand-proportional quota
 * qua Largest Remainder Method + nearest-greedy assignment trên toàn bộ ma
 * trận cặp (vehicle, cluster)).
 * <p>
 * Không dùng @SpringBootTest — thuần thuật toán, giống style
 * KMeansClustererTest / ClusterMergeServiceTest.
 * <p>
 * LƯU Ý QUAN TRỌNG (rút ra từ 1 lần chạy thất bại thực tế khi review):
 * vehicles.size() == centroids.size() KHÔNG tự động đảm bảo "mỗi cluster có
 * ít nhất 1 vehicle". Largest Remainder Method chỉ đảm bảo sum(quota) == V
 * chính xác — một cluster có tỷ trọng demand quá thấp và không thắng ghế dư
 * vẫn có thể hợp pháp nhận quota=0 dù V==C. Điều kiện "mỗi cluster >= 1" chỉ
 * đúng khi demand đủ cân bằng (D_c >= sum(D)/V với mọi c). Hai test
 * shouldAssignExactlyOneVehiclePerCluster_whenCountsAreEqualAndDemandIsBalanced
 * và shouldAllowZeroQuotaCluster_whenDemandIsSkewedEvenIfCountsAreEqual bên
 * dưới test rõ ràng cả 2 nhánh của sự thật này, để tránh lặp lại nhầm lẫn.
 */
class VehicleClusterAssignerTest {

    private VehicleDepotInfo vehicle(String id, double lat, double lon) {
        return new VehicleDepotInfo(id, lat, lon);
    }

    private ClusterCentroid centroid(int clusterId, double lat, double lon) {
        return new ClusterCentroid(clusterId, lat, lon);
    }

    private ClusterDemand demand(int clusterId, double totalDemand) {
        return new ClusterDemand(clusterId, totalDemand);
    }

    // ==================== QUOTA TỶ LỆ THEO DEMAND ====================

    /**
     * Kịch bản trực tiếp phơi bày lý do Phương án 1 ra đời: TẤT CẢ vehicle
     * xuất phát từ CÙNG 1 TỌA ĐỘ DEPOT (mô phỏng single-depot fleet phổ biến
     * trong project — xem application.properties chỉ có 1 GraphHopper
     * profile, và các test khác như MaxDistanceConstraintTest dùng depot đơn).
     * <p>
     * Với thuật toán nearest-only thuần túy (đã loại bỏ), toàn bộ 4 vehicle
     * sẽ dồn vào ĐÚNG 1 cluster gần depot nhất, cluster kia có 0 vehicle ->
     * unassigned hàng loạt. Phương án 1 PHẢI phân bổ theo quota demand
     * (100:300 = 1:3) bất kể depot trùng nhau.
     */
    @Test
    void shouldDistributeByDemandQuota_evenWhenAllVehiclesShareSameDepot() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("1", 21.0285, 105.8542),
                vehicle("2", 21.0285, 105.8542),
                vehicle("3", 21.0285, 105.8542),
                vehicle("4", 21.0285, 105.8542)
        );

        // 2 cluster: demand 100kg và 300kg -> tỷ lệ 1:3 -> quota kỳ vọng 1 và 3
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.10, 105.90),
                centroid(1, 20.90, 105.70)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 100.0),
                demand(1, 300.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(4, result.size(), "Mọi vehicle phải được gán cluster, kể cả khi depot trùng nhau");

        long countCluster0 = result.values().stream().filter(c -> c == 0).count();
        long countCluster1 = result.values().stream().filter(c -> c == 1).count();

        assertEquals(1, countCluster0, "Cluster 0 (demand=100, tỷ lệ 1/4) phải nhận đúng 1 vehicle");
        assertEquals(3, countCluster1, "Cluster 1 (demand=300, tỷ lệ 3/4) phải nhận đúng 3 vehicle");
    }

    @Test
    void shouldDistributeEvenly_whenDemandsAreEqual() {
        List<VehicleDepotInfo> vehicles = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            vehicles.add(vehicle(String.valueOf(i), 21.0, 105.8));
        }

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 21.50, 106.00),
                centroid(2, 22.00, 106.20)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 100.0),
                demand(1, 100.0),
                demand(2, 100.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(6, result.size());
        for (int c = 0; c < 3; c++) {
            final int clusterId = c;
            long count = result.values().stream().filter(v -> v == clusterId).count();
            assertEquals(2, count, "Demand bằng nhau -> mỗi cluster phải nhận đúng 6/3=2 vehicle");
        }
    }

    /**
     * Kiểm tra Largest Remainder Method xử lý đúng trường hợp làm tròn không
     * chia hết — 3 cluster, 7 vehicle, demand bằng nhau -> raw quota mỗi
     * cluster = 7/3 = 2.333, floor = 2 mỗi cluster (tổng 6), còn dư 1 ghế
     * phải cấp cho cluster có phần dư lớn nhất. Vì demand bằng nhau, phần dư
     * bằng nhau tuyệt đối -> tie-break clusterId nhỏ nhất thắng (cluster 0).
     */
    @Test
    void shouldAssignRemainderSeat_toLowestClusterIdOnTie() {
        List<VehicleDepotInfo> vehicles = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            vehicles.add(vehicle(String.valueOf(i), 21.0, 105.8));
        }

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 21.50, 106.00),
                centroid(2, 22.00, 106.20)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 100.0),
                demand(1, 100.0),
                demand(2, 100.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(7, result.size());
        long countCluster0 = result.values().stream().filter(c -> c == 0).count();
        long countCluster1 = result.values().stream().filter(c -> c == 1).count();
        long countCluster2 = result.values().stream().filter(c -> c == 2).count();

        assertEquals(7, countCluster0 + countCluster1 + countCluster2);
        // Ghế dư (1) phải rơi vào ĐÚNG cluster 0 theo tie-break clusterId tăng dần
        // đã cài trong computeQuota — siết chặt thay vì chỉ kiểm tra số lượng,
        // vì demand bằng nhau tuyệt đối (100.0 cho cả 3) loại bỏ hoàn toàn rủi ro
        // sai lệch floating-point giữa các remainder.
        assertEquals(3, countCluster0, "Cluster 0 phải thắng ghế dư do tie-break clusterId nhỏ nhất");
        assertEquals(2, countCluster1);
        assertEquals(2, countCluster2);
    }

    // ==================== NEAREST-GREEDY TRONG NGÂN SÁCH QUOTA ====================

    /**
     * Khi quota cho phép, vehicle vẫn nên ưu tiên gán vào cluster GẦN NHẤT
     * theo depot — quota chỉ có tác dụng CHẶN khi cluster đã đầy, không đảo
     * lộn thứ tự ưu tiên khoảng cách một cách vô lý.
     */
    @Test
    void shouldPreferNearestCluster_whenQuotaAllows() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("near-c0", 21.001, 105.801),
                vehicle("near-c1", 25.001, 110.001)
        );

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.000, 105.800),
                centroid(1, 25.000, 110.000)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 100.0),
                demand(1, 100.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(0, result.get("near-c0"), "Vehicle gần cluster 0 phải được gán cluster 0");
        assertEquals(1, result.get("near-c1"), "Vehicle gần cluster 1 phải được gán cluster 1");
    }

    /**
     * Vehicle "thua" quota ở cluster gần nhất phải tự động rơi xuống cluster
     * gần thứ nhì còn chỗ — đây là hành vi cốt lõi phân biệt nearest-greedy
     * có quota với nearest-only thuần túy.
     */
    @Test
    void shouldFallBackToSecondNearestCluster_whenNearestClusterQuotaExhausted() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("v1", 21.0001, 105.8001), // gần cluster 0 nhất trong 2 vehicle
                vehicle("v2", 21.0002, 105.8002)  // gần cluster 0 kém hơn v1 một chút
        );

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.000, 105.800),  // rất gần cả 2 vehicle
                centroid(1, 30.000, 110.000)   // rất xa cả 2 vehicle
        );
        // Demand bằng nhau -> quota mỗi cluster = 1 (2 vehicle / 2 cluster)
        List<ClusterDemand> demands = List.of(
                demand(0, 100.0),
                demand(1, 100.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(2, result.size());
        assertEquals(1, result.values().stream().filter(c -> c == 0).count(),
                "Cluster 0 chỉ nhận đúng 1 vehicle dù cả 2 đều gần nó (quota chặn)");
        assertEquals(1, result.values().stream().filter(c -> c == 1).count(),
                "Vehicle còn lại PHẢI rơi xuống cluster 1 dù xa hơn nhiều, vì cluster 0 đã hết quota");
        // Siết chặt: v1 (gần cluster 0 hơn) phải là người thắng, v2 rơi xuống cluster 1
        assertEquals(0, result.get("v1"), "v1 gần cluster 0 hơn v2 -> v1 phải thắng cluster 0");
        assertEquals(1, result.get("v2"), "v2 thua quota ở cluster 0 -> phải rơi xuống cluster 1");
    }

    // ==================== TRƯỜNG HỢP BIÊN ====================

    /**
     * sum=165, sum/3=55 -> mọi demand phải >= 55 để floor(raw) >= 1 cho cả 3
     * cluster (55 vừa đúng biên: raw=1.0 chẵn, floor=1, remainder=0 mỗi
     * cluster -> không cần Largest Remainder can thiệp). Xem lưu ý đầu file
     * về lý do V==C không tự động đảm bảo mỗi cluster >= 1 vehicle.
     */
    @Test
    void shouldAssignExactlyOneVehiclePerCluster_whenCountsAreEqualAndDemandIsBalanced() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("1", 21.00, 105.80),
                vehicle("2", 22.00, 106.00),
                vehicle("3", 23.00, 106.20)
        );
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 22.00, 106.00),
                centroid(2, 23.00, 106.20)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 55.0), demand(1, 55.0), demand(2, 55.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(3, result.size());
        Set<Integer> distinctClusters = new TreeSet<>(result.values());
        assertEquals(3, distinctClusters.size(),
                "Với demand cân bằng (mỗi cluster >= sum/V), vehicles.size() == centroids.size() " +
                        "phải cho mỗi cluster ĐÚNG 1 vehicle — không rỗng, không thừa");
    }

    /**
     * QUAN TRỌNG: vehicles.size() == centroids.size() KHÔNG tự động đảm bảo
     * "mỗi cluster có ít nhất 1 vehicle" — đó chỉ đúng khi demand đủ CÂN BẰNG
     * để floor(n_c^raw) >= 1 với mọi cluster (D_c >= sum(D)/V). Nếu demand
     * lệch mạnh, Largest Remainder Method có thể HỢP PHÁP cho 1 cluster
     * quota=0 dù V==C — đây là hành vi ĐÚNG của thuật toán phân bổ theo tỷ
     * lệ, không phải bug. Test này bắt nguồn trực tiếp từ báo lỗi thực tế đã
     * gặp khi review: demand 50/200/75 (sum=325), V=3 ->
     * cluster 0 raw = 3*50/325 = 0.4615, floor=0, remainder thấp nhất trong 3
     * cluster nên không thắng ghế dư -> quota=0 dù chỉ còn 1 ghế trống cần cấp.
     */
    @Test
    void shouldAllowZeroQuotaCluster_whenDemandIsSkewedEvenIfCountsAreEqual() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("1", 21.00, 105.80),
                vehicle("2", 22.00, 106.00),
                vehicle("3", 23.00, 106.20)
        );
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 22.00, 106.00),
                centroid(2, 23.00, 106.20)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 50.0), demand(1, 200.0), demand(2, 75.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        // Bất biến vẫn phải giữ: MỌI vehicle được gán (tổng quota luôn = V)
        assertEquals(3, result.size(), "Mọi vehicle vẫn phải được gán dù demand lệch mạnh");

        Set<Integer> distinctClusters = new TreeSet<>(result.values());
        assertTrue(distinctClusters.size() <= 3,
                "Số cluster có vehicle không được vượt quá số cluster yêu cầu");
        assertFalse(distinctClusters.contains(0),
                "Cluster 0 (demand=50, tỷ trọng thấp nhất, không thắng ghế dư theo Largest " +
                        "Remainder Method) phải hợp pháp nhận quota=0 trong kịch bản demand lệch này");
    }

    @Test
    void shouldHandleSingleCluster_assigningAllVehiclesToIt() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("1", 21.00, 105.80),
                vehicle("2", 22.00, 106.00)
        );
        List<ClusterCentroid> centroids = List.of(centroid(0, 21.50, 105.90));
        List<ClusterDemand> demands = List.of(demand(0, 500.0));

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(2, result.size());
        assertTrue(result.values().stream().allMatch(c -> c == 0));
    }

    // ==================== VALIDATE INPUT ====================

    @Test
    void shouldThrowException_whenClusterCountExceedsVehicleCount() {
        List<VehicleDepotInfo> vehicles = List.of(vehicle("1", 21.0, 105.8));
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.0, 105.8),
                centroid(1, 22.0, 106.0)
        );
        List<ClusterDemand> demands = List.of(demand(0, 100.0), demand(1, 100.0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VehicleClusterAssigner.assign(vehicles, centroids, demands));
        assertTrue(
                ex.getMessage().toLowerCase().contains("cluster")
                        || ex.getMessage().toLowerCase().contains("vehicle"),
                "Thông báo lỗi phải chỉ rõ nguyên nhân vi phạm ràng buộc centroids <= vehicles");
    }

    @Test
    void shouldThrowException_whenVehiclesIsEmpty() {
        List<ClusterCentroid> centroids = List.of(centroid(0, 21.0, 105.8));
        List<ClusterDemand> demands = List.of(demand(0, 100.0));

        assertThrows(IllegalArgumentException.class,
                () -> VehicleClusterAssigner.assign(List.of(), centroids, demands));
    }

    @Test
    void shouldThrowException_whenCentroidsIsEmpty() {
        List<VehicleDepotInfo> vehicles = List.of(vehicle("1", 21.0, 105.8));
        assertThrows(IllegalArgumentException.class,
                () -> VehicleClusterAssigner.assign(vehicles, List.of(), List.of()));
    }

    @Test
    void shouldThrowException_whenDemandsSizeMismatchesCentroids() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("1", 21.0, 105.8), vehicle("2", 22.0, 106.0)
        );
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.0, 105.8), centroid(1, 22.0, 106.0)
        );
        List<ClusterDemand> demands = List.of(demand(0, 100.0)); // thiếu 1 phần tử

        assertThrows(IllegalArgumentException.class,
                () -> VehicleClusterAssigner.assign(vehicles, centroids, demands));
    }

    @Test
    void shouldThrowException_whenTotalDemandIsZero() {
        List<VehicleDepotInfo> vehicles = List.of(vehicle("1", 21.0, 105.8));
        List<ClusterCentroid> centroids = List.of(centroid(0, 21.0, 105.8));
        List<ClusterDemand> demands = List.of(demand(0, 0.0));

        assertThrows(IllegalArgumentException.class,
                () -> VehicleClusterAssigner.assign(vehicles, centroids, demands));
    }

    // ==================== DETERMINISM ====================

    @Test
    void shouldProduceSameResult_whenCalledMultipleTimesWithSameInput() {
        List<VehicleDepotInfo> vehicles = List.of(
                vehicle("3", 21.05, 105.85),
                vehicle("1", 21.00, 105.80),
                vehicle("2", 21.02, 105.82),
                vehicle("4", 25.00, 110.00)
        );
        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 25.00, 110.00)
        );
        List<ClusterDemand> demands = List.of(demand(0, 150.0), demand(1, 250.0));

        Map<String, Integer> result1 = VehicleClusterAssigner.assign(vehicles, centroids, demands);
        Map<String, Integer> result2 = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(result1, result2,
                "Cùng input phải luôn cho cùng kết quả gán — thuật toán phải deterministic tuyệt đối " +
                        "(greedy trên danh sách đã sort ổn định, tie-break rõ ràng theo clusterId/vehicleId)");
    }

    /**
     * Xáo trộn THỨ TỰ input list (nhưng giữ nguyên nội dung) không được làm
     * thay đổi kết quả — vì assign() tự sort nội bộ theo vehicleId, không
     * phụ thuộc thứ tự truyền vào từ tầng gọi (context.vehicleDTOs().values()
     * là HashMap, thứ tự duyệt không đảm bảo).
     */
    @Test
    void shouldProduceSameResult_regardlessOfInputListOrder() {
        List<VehicleDepotInfo> orderA = List.of(
                vehicle("1", 21.00, 105.80),
                vehicle("2", 21.02, 105.82),
                vehicle("3", 25.00, 110.00)
        );
        List<VehicleDepotInfo> orderB = List.of(
                vehicle("3", 25.00, 110.00),
                vehicle("1", 21.00, 105.80),
                vehicle("2", 21.02, 105.82)
        );

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.00, 105.80),
                centroid(1, 25.00, 110.00)
        );
        List<ClusterDemand> demands = List.of(demand(0, 200.0), demand(1, 100.0));

        Map<String, Integer> resultA = VehicleClusterAssigner.assign(orderA, centroids, demands);
        Map<String, Integer> resultB = VehicleClusterAssigner.assign(orderB, centroids, demands);

        assertEquals(resultA, resultB,
                "Thứ tự phần tử trong list đầu vào không được ảnh hưởng kết quả gán");
    }

    // ==================== BẤT BIẾN: MỌI VEHICLE LUÔN ĐƯỢC GÁN ====================

    /**
     * Stress test nhẹ: nhiều vehicle trùng tọa độ hệt nhau (mô phỏng fleet
     * cùng bãi đỗ), nhiều cluster với demand lệch mạnh — xác nhận bất biến
     * "mọi vehicle đều được gán" giữ vững trong điều kiện khắc nghiệt, không
     * chỉ ở test case đơn giản.
     */
    @Test
    void shouldAssignEveryVehicle_evenWithSkewedDemandAndIdenticalDepots() {
        List<VehicleDepotInfo> vehicles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            vehicles.add(vehicle(String.valueOf(i), 21.0285, 105.8542));
        }

        List<ClusterCentroid> centroids = List.of(
                centroid(0, 21.03, 105.85),
                centroid(1, 21.10, 105.90),
                centroid(2, 20.90, 105.70),
                centroid(3, 21.50, 106.00)
        );
        List<ClusterDemand> demands = List.of(
                demand(0, 970.0),
                demand(1, 10.0),
                demand(2, 10.0),
                demand(3, 10.0)
        );

        Map<String, Integer> result = VehicleClusterAssigner.assign(vehicles, centroids, demands);

        assertEquals(20, result.size(), "Mọi vehicle phải được gán bất kể demand lệch mạnh đến đâu");

        Set<Integer> usedClusters = new TreeSet<>(result.values());
        assertTrue(usedClusters.size() <= 4, "Không có cluster ID lạ ngoài phạm vi [0,4)");
    }
}