package org.truong.gvrp_engine_api.clustering;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.clustering.ClusterMergeService.MergedClusterAssignment;
import org.truong.gvrp_engine_api.clustering.KMeansClusterer.ClusterAssignment;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class ClusterMergeServiceTest {

    private OptCoordinates coord(double lat, double lon) {
        return new OptCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    // ==================== KHÔNG CẦN MERGE ====================

    @Test
    void shouldNotMergeAnything_whenAllClustersAlreadyAboveThreshold() {
        // Given: 3 cụm, mỗi cụm có 2 orders demand=100 -> totalDemand=200/cụm,
        // ngưỡng = 150 -> không cụm nào dưới ngưỡng
        List<OptCoordinates> coordinates = List.of(
                coord(21.00, 105.80), coord(21.001, 105.801), // cụm 0
                coord(21.10, 105.90), coord(21.101, 105.901), // cụm 1
                coord(21.20, 106.00), coord(21.201, 106.001)  // cụm 2
        );
        int[] clusterIds = {0, 0, 1, 1, 2, 2};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 3);
        double[] demands = {100, 100, 100, 100, 100, 100};

        // When
        MergedClusterAssignment result = ClusterMergeService.merge(initial, coordinates, demands, 150.0);

        // Then: vẫn 3 cụm, không có merge nào xảy ra
        assertEquals(3, result.numClusters());

        // Cấu trúc nhóm phải giữ nguyên: index 0,1 cùng cụm; 2,3 cùng cụm; 4,5 cùng cụm;
        // và 3 nhóm này phải khác nhau (dù ID cụ thể có thể bị remap)
        assertEquals(result.clusterIdByOrderIndex()[0], result.clusterIdByOrderIndex()[1]);
        assertEquals(result.clusterIdByOrderIndex()[2], result.clusterIdByOrderIndex()[3]);
        assertEquals(result.clusterIdByOrderIndex()[4], result.clusterIdByOrderIndex()[5]);

        Set<Integer> distinctGroups = new TreeSet<>();
        for (int id : result.clusterIdByOrderIndex()) distinctGroups.add(id);
        assertEquals(3, distinctGroups.size(), "3 nhóm gốc phải vẫn tách biệt nhau");
    }

    // ==================== MERGE CƠ BẢN: 1 CỤM NHỎ MERGE VÀO HÀNG XÓM ====================

    @Test
    void shouldMergeSmallClusterIntoNearestNeighbor() {
        // Given: 3 cụm.
        // Cụm 0 (index 0,1): gần cụm 1 nhất, demand nhỏ = 50 (< ngưỡng 150) -> phải merge
        // Cụm 1 (index 2,3): demand lớn = 300, ở gần cụm 0
        // Cụm 2 (index 4,5): demand lớn = 300, ở XA cụm 0 (để đảm bảo cụm 0 merge vào cụm 1, không phải cụm 2)
        List<OptCoordinates> coordinates = List.of(
                coord(21.000, 105.800), coord(21.0001, 105.8001), // cụm 0 — nhỏ
                coord(21.001, 105.801), coord(21.0011, 105.8011), // cụm 1 — lớn, RẤT gần cụm 0
                coord(25.000, 110.000), coord(25.001, 110.001)    // cụm 2 — lớn, RẤT xa cụm 0
        );
        int[] clusterIds = {0, 0, 1, 1, 2, 2};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 3);
        double[] demands = {25, 25, 150, 150, 150, 150}; // cụm 0 = 50, cụm 1 = 300, cụm 2 = 300

        // When
        MergedClusterAssignment result = ClusterMergeService.merge(initial, coordinates, demands, 100.0);

        // Then: còn lại 2 cụm (cụm 0 đã merge vào cụm 1)
        assertEquals(2, result.numClusters());

        // Index 0,1 (cụm 0 cũ) phải giờ CÙNG cụm với index 2,3 (cụm 1 cũ)
        assertEquals(result.clusterIdByOrderIndex()[0], result.clusterIdByOrderIndex()[2],
                "Cụm nhỏ (index 0,1) phải merge vào cụm 1 (gần nhất), không phải cụm 2 (xa)");

        // Index 4,5 (cụm 2) phải KHÁC cụm với nhóm trên — không bị merge vì đã đủ lớn
        assertNotEquals(result.clusterIdByOrderIndex()[0], result.clusterIdByOrderIndex()[4],
                "Cụm 2 đã đủ lớn (300 > ngưỡng 100), không được bị gộp vào nhóm khác");
    }

    // ==================== MERGE CHAIN: CỤM GỘP VẪN NHỎ, TIẾP TỤC MERGE ====================

    @Test
    void shouldContinueMergingChain_whenMergedClusterStillBelowThreshold() {
        // Given: 3 cụm nhỏ liên tiếp về mặt địa lý, mỗi cụm demand=40, ngưỡng=100.
        // Merge lần 1: 2 cụm gần nhau nhất gộp -> demand=80, VẪN dưới ngưỡng 100.
        // Merge lần 2: cụm gộp (80) phải tiếp tục merge với cụm còn lại -> demand=120, đạt ngưỡng.
        // Đây là test trực tiếp cho Union-Find xử lý merge chain — nếu thuật toán
        // chỉ làm 1-pass đơn giản (không lặp lại), test này sẽ FAIL vì dừng ở 2 cụm.
        List<OptCoordinates> coordinates = List.of(
                coord(21.000, 105.800), // cụm 0
                coord(21.001, 105.801), // cụm 1 — gần cụm 0 nhất
                coord(21.100, 105.900)  // cụm 2 — xa hơn 2 cụm kia
        );
        int[] clusterIds = {0, 1, 2};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 3);
        double[] demands = {40, 40, 40};

        // When
        MergedClusterAssignment result = ClusterMergeService.merge(initial, coordinates, demands, 100.0);

        // Then: PHẢI merge hết về 1 cụm duy nhất, vì mọi cụm/cụm-gộp đều dưới ngưỡng 100
        // cho đến khi gộp cả 3 lại (40+40+40=120 > 100)
        assertEquals(1, result.numClusters(),
                "Merge chain phải tiếp tục đến khi cụm gộp vượt ngưỡng — " +
                        "nếu dừng ở 2 cụm, Union-Find chưa xử lý đúng merge chain");

        assertEquals(result.clusterIdByOrderIndex()[0], result.clusterIdByOrderIndex()[1]);
        assertEquals(result.clusterIdByOrderIndex()[1], result.clusterIdByOrderIndex()[2]);
    }

    // ==================== TẤT CẢ CỤM ĐỀU NHỎ -> GỘP VỀ 1 ====================

    @Test
    void shouldMergeEverythingIntoOneCluster_whenTotalDemandBelowThreshold() {
        // Given: 4 cụm, tổng demand toàn bộ (4x30=120) vẫn dưới ngưỡng rất cao (500)
        List<OptCoordinates> coordinates = List.of(
                coord(21.00, 105.80), coord(21.10, 105.90),
                coord(21.20, 106.00), coord(21.30, 106.10)
        );
        int[] clusterIds = {0, 1, 2, 3};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 4);
        double[] demands = {30, 30, 30, 30};

        // When
        MergedClusterAssignment result = ClusterMergeService.merge(initial, coordinates, demands, 500.0);

        // Then: chỉ còn đúng 1 cụm
        assertEquals(1, result.numClusters());
        int expectedGroup = result.clusterIdByOrderIndex()[0];
        for (int id : result.clusterIdByOrderIndex()) {
            assertEquals(expectedGroup, id, "Toàn bộ orders phải về cùng 1 cụm khi ngưỡng quá cao");
        }
    }

    // ==================== TOÀN VẸN: REMAP LIỀN MẠCH, KHÔNG CỤM RỖNG ====================

    @Test
    void shouldAlwaysProduceContiguousIdsWithNoEmptyClusters() {
        // Given: kịch bản có merge (tương tự test merge cơ bản)
        List<OptCoordinates> coordinates = List.of(
                coord(21.000, 105.800), coord(21.0001, 105.8001),
                coord(21.001, 105.801), coord(21.0011, 105.8011),
                coord(25.000, 110.000), coord(25.001, 110.001)
        );
        int[] clusterIds = {0, 0, 1, 1, 2, 2};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 3);
        double[] demands = {25, 25, 150, 150, 150, 150};

        // When
        MergedClusterAssignment result = ClusterMergeService.merge(initial, coordinates, demands, 100.0);

        // Then: mọi cluster ID trong output phải nằm trong [0, numClusters) VÀ
        // mọi giá trị trong khoảng đó phải THỰC SỰ xuất hiện (không có "lỗ hổng"),
        // khác với KMeansClusterer.ClusterAssignment nơi numClusters() có thể "nói dối".
        Set<Integer> distinctIds = new TreeSet<>();
        for (int id : result.clusterIdByOrderIndex()) {
            assertTrue(id >= 0 && id < result.numClusters());
            distinctIds.add(id);
        }
        assertEquals(result.numClusters(), distinctIds.size(),
                "Sau merge, numClusters() phải khớp CHÍNH XÁC số cụm thực sự có điểm — " +
                        "không được có cụm rỗng trong output cuối cùng");
    }

    // ==================== DETERMINISM ====================

    @Test
    void shouldProduceSameResult_whenCalledMultipleTimesWithSameInput() {
        List<OptCoordinates> coordinates = List.of(
                coord(21.000, 105.800), coord(21.001, 105.801), coord(21.100, 105.900)
        );
        int[] clusterIds = {0, 1, 2};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 3);
        double[] demands = {40, 40, 40};

        MergedClusterAssignment result1 = ClusterMergeService.merge(initial, coordinates, demands, 100.0);
        MergedClusterAssignment result2 = ClusterMergeService.merge(initial, coordinates, demands, 100.0);

        assertArrayEquals(result1.clusterIdByOrderIndex(), result2.clusterIdByOrderIndex(),
                "Cùng input phải luôn cho cùng kết quả merge — thuật toán phải deterministic " +
                        "(greedy + tie-break theo ID, không có yếu tố ngẫu nhiên)");
    }

    // ==================== VALIDATE INPUT ====================

    @Test
    void shouldThrowException_whenCoordinatesAndDemandsLengthMismatch() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8), coord(21.1, 105.9));
        int[] clusterIds = {0, 0};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 1);
        double[] demands = {10}; // length mismatch: 1 vs 2

        assertThrows(IllegalArgumentException.class,
                () -> ClusterMergeService.merge(initial, coordinates, demands, 100.0));
    }

    @Test
    void shouldThrowException_whenThresholdIsZeroOrNegative() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8));
        int[] clusterIds = {0};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 1);
        double[] demands = {10};

        assertThrows(IllegalArgumentException.class,
                () -> ClusterMergeService.merge(initial, coordinates, demands, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> ClusterMergeService.merge(initial, coordinates, demands, -5.0));
    }

    @Test
    void shouldThrowException_whenDemandIsNegative() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8), coord(21.1, 105.9));
        int[] clusterIds = {0, 1};
        ClusterAssignment initial = new ClusterAssignment(clusterIds, 2);
        double[] demands = {10, -5};

        assertThrows(IllegalArgumentException.class,
                () -> ClusterMergeService.merge(initial, coordinates, demands, 100.0));
    }

    @Test
    void shouldThrowException_whenInitialAssignmentIsNull() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8));
        double[] demands = {10};

        assertThrows(IllegalArgumentException.class,
                () -> ClusterMergeService.merge(null, coordinates, demands, 100.0));
    }

    // ==================== TÍCH HỢP VỚI KMEANSCLUSTERER (CỤM RỖNG THỰC SỰ) ====================

    @Test
    void shouldHandleEmptyClustersFromKMeansCorrectly_integrationWithRealClusterer() {
        // Given: dùng chính KMeansClusterer để tạo ra tình huống có cụm rỗng thật
        // (100 điểm trùng tọa độ, yêu cầu 10 cụm -> 9 cụm rỗng như đã thấy ở log trước)
        List<OptCoordinates> coordinates = new ArrayList<>();
        double[] demands = new double[100];
        for (int i = 0; i < 100; i++) {
            coordinates.add(coord(21.0285, 105.8542));
            demands[i] = 5.0; // mỗi order 5kg -> tổng 500kg dồn vào 1 cụm duy nhất
        }

        ClusterAssignment kmeansResult = KMeansClusterer.fit(coordinates, 10, 50, new java.util.Random(42L));
        assertTrue(kmeansResult.hasEmptyClusters(), "Tiền đề: KMeansClusterer phải tạo cụm rỗng trong kịch bản này");

        // When: merge với ngưỡng thấp (10kg) -> không cụm nào (thực) cần merge
        // vì cụm duy nhất có điểm đã có demand = 500kg >> 10kg
        MergedClusterAssignment result = ClusterMergeService.merge(kmeansResult, coordinates, demands, 10.0);

        // Then: KHÔNG được crash vì cụm rỗng, và kết quả chỉ có 1 cụm thực
        // (đúng với nonEmptyClusterIds() = 1 mà KMeansClusterer đã báo)
        assertEquals(1, result.numClusters(),
                "ClusterMergeService phải chỉ xử lý cụm KHÔNG rỗng (dùng nonEmptyClusterIds() nội bộ), " +
                        "không được bị ảnh hưởng bởi 9 cụm rỗng từ KMeansClusterer");
    }
}