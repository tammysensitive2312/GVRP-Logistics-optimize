package org.truong.gvrp_engine_api.clustering;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.clustering.KMeansClusterer.ClusterAssignment;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho KMeansClusterer.
 * <p>
 * Không dùng @SpringBootTest vì KMeansClusterer thuần thuật toán,
 * không phụ thuộc Spring — giống style GeoUtilsTest, MaxDistanceConstraintTest.
 */
class KMeansClustererTest {

    private static final long FIXED_SEED = 42L;

    // ==================== HELPER: TẠO TỌA ĐỘ GIẢ LẬP ====================

    private OptCoordinates coord(double lat, double lon) {
        return new OptCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    /**
     * Sinh 2 "khối" điểm tách biệt rõ ràng trên bản đồ — khối A quanh Hoàn Kiếm,
     * khối B quanh Hà Đông (cách xa ~10km). Dùng để kiểm tra K-means gom đúng cụm.
     */
    private List<OptCoordinates> buildTwoWellSeparatedBlobs(int pointsPerBlob, long seed) {
        Random random = new Random(seed);
        List<OptCoordinates> coordinates = new ArrayList<>();

        double blobACenterLat = 21.0285, blobACenterLon = 105.8542; // Hoàn Kiếm
        double blobBCenterLat = 20.9700, blobBCenterLon = 105.7500; // Hà Đông, cách xa

        double spread = 0.005; // ~500m, đủ nhỏ để không lẫn sang blob kia

        for (int i = 0; i < pointsPerBlob; i++) {
            coordinates.add(coord(
                    blobACenterLat + (random.nextDouble() - 0.5) * 2 * spread,
                    blobACenterLon + (random.nextDouble() - 0.5) * 2 * spread));
        }
        for (int i = 0; i < pointsPerBlob; i++) {
            coordinates.add(coord(
                    blobBCenterLat + (random.nextDouble() - 0.5) * 2 * spread,
                    blobBCenterLon + (random.nextDouble() - 0.5) * 2 * spread));
        }

        return coordinates;
    }

    // ==================== CORRECTNESS CƠ BẢN ====================

    @Test
    void shouldGroupWellSeparatedBlobsIntoDistinctClusters() {
        // Given: 2 khối điểm tách biệt rõ ràng, mỗi khối 50 điểm
        List<OptCoordinates> coordinates = buildTwoWellSeparatedBlobs(50, FIXED_SEED);

        // When
        ClusterAssignment result = KMeansClusterer.fit(
                coordinates, 2, 100, new Random(FIXED_SEED));

        // Then: tất cả điểm trong blob A (index 0-49) phải cùng 1 cluster,
        // tất cả điểm trong blob B (index 50-99) phải cùng 1 cluster KHÁC
        int clusterOfBlobA = result.clusterIdByOrderIndex()[0];
        int clusterOfBlobB = result.clusterIdByOrderIndex()[50];

        assertNotEquals(clusterOfBlobA, clusterOfBlobB,
                "Hai khối điểm cách xa nhau phải rơi vào 2 cụm khác nhau");

        for (int i = 0; i < 50; i++) {
            assertEquals(clusterOfBlobA, result.clusterIdByOrderIndex()[i],
                    "Điểm index " + i + " trong blob A phải cùng cụm với các điểm khác trong blob A");
        }
        for (int i = 50; i < 100; i++) {
            assertEquals(clusterOfBlobB, result.clusterIdByOrderIndex()[i],
                    "Điểm index " + i + " trong blob B phải cùng cụm với các điểm khác trong blob B");
        }
    }

    // ==================== EDGE CASE: N <= NUM_CLUSTERS ====================

    @Test
    void shouldAssignEachPointItsOwnCluster_whenPointCountLessThanOrEqualClusterCount() {
        // Given: chỉ có 3 orders nhưng yêu cầu 5 cụm
        List<OptCoordinates> coordinates = List.of(
                coord(21.0, 105.8),
                coord(21.1, 105.9),
                coord(21.2, 106.0)
        );

        // When
        ClusterAssignment result = KMeansClusterer.fit(coordinates, 5);

        // Then: mỗi order tự thành 1 cụm riêng, numClusters trả về = số orders (3), không phải 5
        assertEquals(3, result.numClusters());
        Set<Integer> distinctClusterIds = new HashSet<>();
        for (int clusterId : result.clusterIdByOrderIndex()) {
            distinctClusterIds.add(clusterId);
        }
        assertEquals(3, distinctClusterIds.size(),
                "Mỗi order phải có cluster ID riêng biệt khi n <= numClusters");
    }

    @Test
    void shouldHandleExactlyEqualCase_whenPointCountEqualsClusterCount() {
        // Given: n == numClusters (biên chính xác, không phải n < numClusters)
        List<OptCoordinates> coordinates = List.of(
                coord(21.0, 105.8),
                coord(21.1, 105.9)
        );

        // When
        ClusterAssignment result = KMeansClusterer.fit(coordinates, 2);

        // Then
        assertEquals(2, result.numClusters());
        assertNotEquals(
                result.clusterIdByOrderIndex()[0],
                result.clusterIdByOrderIndex()[1],
                "n == numClusters vẫn phải cho mỗi điểm 1 cụm riêng"
        );
    }

    // ==================== DETERMINISM ====================

    @Test
    void shouldProduceSameResult_whenGivenSameSeed() {
        // Given: cùng dữ liệu, cùng seed
        List<OptCoordinates> coordinates = buildTwoWellSeparatedBlobs(30, FIXED_SEED);

        // When: chạy 2 lần độc lập với cùng seed
        ClusterAssignment result1 = KMeansClusterer.fit(
                coordinates, 2, 100, new Random(FIXED_SEED));
        ClusterAssignment result2 = KMeansClusterer.fit(
                coordinates, 2, 100, new Random(FIXED_SEED));

        // Then: kết quả phải giống hệt nhau (deterministic)
        assertArrayEquals(
                result1.clusterIdByOrderIndex(),
                result2.clusterIdByOrderIndex(),
                "Cùng seed phải cho ra cùng kết quả gán cụm — cần thiết để test ổn định, không flaky"
        );
    }

    // ==================== VALIDATE INPUT ====================

    @Test
    void shouldThrowException_whenCoordinatesIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> KMeansClusterer.fit(List.of(), 3));
    }

    @Test
    void shouldThrowException_whenCoordinatesIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> KMeansClusterer.fit(null, 3));
    }

    @Test
    void shouldThrowException_whenNumClustersIsZero() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8));
        assertThrows(IllegalArgumentException.class,
                () -> KMeansClusterer.fit(coordinates, 0));
    }

    @Test
    void shouldThrowException_whenNumClustersIsNegative() {
        List<OptCoordinates> coordinates = List.of(coord(21.0, 105.8));
        assertThrows(IllegalArgumentException.class,
                () -> KMeansClusterer.fit(coordinates, -1));
    }

    @Test
    void shouldThrowException_whenMaxIterationsIsZero() {
        List<OptCoordinates> coordinates = buildTwoWellSeparatedBlobs(10, FIXED_SEED);
        assertThrows(IllegalArgumentException.class,
                () -> KMeansClusterer.fit(coordinates, 2, 0, new Random(FIXED_SEED)));
    }

    // ==================== TOÀN VẸN KẾT QUẢ ====================

    @Test
    void shouldAssignValidClusterIdToEveryPoint_noIndexLeftBehind() {
        // Given: 200 điểm ngẫu nhiên, 7 cụm — quy mô đủ lớn để dễ lộ bug bỏ sót index
        Random dataRandom = new Random(FIXED_SEED);
        List<OptCoordinates> coordinates = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            double lat = 21.0 + dataRandom.nextDouble() * 0.1;
            double lon = 105.8 + dataRandom.nextDouble() * 0.1;
            coordinates.add(coord(lat, lon));
        }

        // When
        ClusterAssignment result = KMeansClusterer.fit(
                coordinates, 7, 100, new Random(FIXED_SEED));

        // Then: mọi index đều có cluster ID hợp lệ trong [0, numClusters)
        assertEquals(200, result.clusterIdByOrderIndex().length,
                "Số phần tử trong mảng gán cụm phải khớp số orders đầu vào");

        for (int i = 0; i < result.clusterIdByOrderIndex().length; i++) {
            int clusterId = result.clusterIdByOrderIndex()[i];
            assertTrue(clusterId >= 0 && clusterId < result.numClusters(),
                    "Cluster ID tại index " + i + " phải nằm trong [0, " + result.numClusters() + "), nhận được: " + clusterId);
        }

        // Với 200 điểm ngẫu nhiên trải đều và chỉ 7 cụm (tỉ lệ điểm/cụm cao),
        // không nên có cụm rỗng — xác nhận nonEmptyClusterIds() khớp numClusters()
        // trong trường hợp dữ liệu "khỏe mạnh" này.
        assertEquals(result.numClusters(), result.nonEmptyClusterIds().size(),
                "Với 200 điểm/7 cụm phân bố đều, không nên có cụm rỗng");
    }

    // ==================== CỤM RỖNG KHÔNG GÂY NaN / CRASH ====================

    @Test
    void shouldNotProduceNaNOrCrash_whenManyDuplicateCoordinatesExist() {
        // Given: kịch bản dễ gây cụm rỗng — rất nhiều điểm TRÙNG HỆT tọa độ nhau,
        // yêu cầu số cụm gần bằng số điểm phân biệt thực tế. Đây là trường hợp
        // dễ khiến K-means++ chọn trùng centroid hoặc một số centroid "thắng" 0 điểm.
        List<OptCoordinates> coordinates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            coordinates.add(coord(21.0285, 105.8542)); // toàn bộ trùng 1 điểm
        }

        // When / Then: không được ném exception, không crash
        assertDoesNotThrow(() -> {
            ClusterAssignment result = KMeansClusterer.fit(
                    coordinates, 10, 50, new Random(FIXED_SEED));

            // Toàn vẹn: vẫn phải gán đủ 100 index, mỗi cluster ID hợp lệ
            assertEquals(100, result.clusterIdByOrderIndex().length);
            for (int clusterId : result.clusterIdByOrderIndex()) {
                assertTrue(clusterId >= 0 && clusterId < result.numClusters());
            }
        }, "Toàn bộ điểm trùng tọa độ không được gây NaN hoặc crash trong K-means++");
    }

    /**
     * Kịch bản CHÍNH XÁC gây cụm rỗng: 100 điểm trùng hệt tọa độ, yêu cầu 10 cụm.
     * Vì mọi điểm giống hệt nhau, khoảng cách tới mọi centroid đều bằng 0 —
     * bước gán sẽ luôn chọn centroid đầu tiên tìm thấy (index 0 trong mảng
     * centroids) cho MỌI điểm, khiến 9/10 cụm còn lại rỗng ngay từ vòng lặp đầu.
     * Đây chính là bug mà numClusters() (báo "10 cụm") sẽ che giấu nếu không
     * dùng nonEmptyClusterIds().
     */
    @Test
    void shouldReportOnlyOneNonEmptyCluster_whenAllPointsAreIdentical() {
        // Given
        List<OptCoordinates> coordinates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            coordinates.add(coord(21.0285, 105.8542));
        }

        // When
        ClusterAssignment result = KMeansClusterer.fit(
                coordinates, 10, 50, new Random(FIXED_SEED));

        // Then: numClusters() vẫn báo 10 (số yêu cầu ban đầu) — đây là hành vi
        // ĐÃ BIẾT và được document rõ, KHÔNG phải bug.
        assertEquals(10, result.numClusters(),
                "numClusters() phải phản ánh đúng số cụm YÊU CẦU ban đầu, không tự ý thay đổi");

        // Nhưng nonEmptyClusterIds() phải phản ánh ĐÚNG thực tế: chỉ 1 cụm có điểm,
        // vì mọi điểm giống hệt nhau nên chỉ 1 centroid "thắng" tất cả.
        assertEquals(1, result.nonEmptyClusterIds().size(),
                "Khi mọi điểm trùng tọa độ, chỉ 1 cụm thực sự có dữ liệu — " +
                        "nonEmptyClusterIds() phải phản ánh đúng điều này, không phải 10");

        // Và hasEmptyClusters() phải báo true — đây là tín hiệu cảnh báo quan trọng
        // cho ClusterMergeService biết cần xử lý cụm rỗng trước khi merge.
        assertTrue(result.hasEmptyClusters(),
                "hasEmptyClusters() phải trả về true khi numClusters() không khớp " +
                        "số cụm thực sự có điểm — đây là tín hiệu bắt buộc cho tầng gọi");
    }

    @Test
    void shouldReportNoEmptyClusters_whenBlobsAreWellSeparatedAndMatchRequestedCount() {
        // Given: kịch bản "khỏe mạnh" — 2 khối tách biệt rõ, yêu cầu đúng 2 cụm.
        // Test này để đối chứng với test cụm rỗng ở trên: xác nhận
        // hasEmptyClusters() = false trong trường hợp bình thường, không phải
        // lúc nào cũng trả về true (tránh false positive).
        List<OptCoordinates> coordinates = buildTwoWellSeparatedBlobs(50, FIXED_SEED);

        // When
        ClusterAssignment result = KMeansClusterer.fit(
                coordinates, 2, 100, new Random(FIXED_SEED));

        // Then
        assertFalse(result.hasEmptyClusters(),
                "Với dữ liệu tách biệt rõ ràng và số cụm yêu cầu hợp lý, " +
                        "không nên có cụm rỗng — hasEmptyClusters() phải là false");
        assertEquals(2, result.nonEmptyClusterIds().size(),
                "Cả 2 cụm yêu cầu đều phải có điểm thực sự trong kịch bản này");
    }

    @Test
    void shouldConverge_withinReasonableIterations_forTypicalDataset() {
        // Given: dữ liệu điển hình — 3 khối tách biệt
        List<OptCoordinates> coordinates = new ArrayList<>();
        coordinates.addAll(buildTwoWellSeparatedBlobs(40, FIXED_SEED));
        // Thêm khối thứ 3 ở vị trí khác
        Random random = new Random(FIXED_SEED + 1);
        for (int i = 0; i < 40; i++) {
            coordinates.add(coord(
                    21.10 + (random.nextDouble() - 0.5) * 0.01,
                    106.00 + (random.nextDouble() - 0.5) * 0.01));
        }

        // When / Then: phải hội tụ trong ngưỡng mặc định mà không throw,
        // và trả về đúng 3 cụm được yêu cầu (không bị giảm do edge case)
        ClusterAssignment result = KMeansClusterer.fit(
                coordinates, 3, 100, new Random(FIXED_SEED));

        assertEquals(3, result.numClusters());
        assertEquals(120, result.clusterIdByOrderIndex().length);
    }
}