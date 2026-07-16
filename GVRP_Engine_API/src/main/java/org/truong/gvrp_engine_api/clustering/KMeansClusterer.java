package org.truong.gvrp_engine_api.clustering;

import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * K-means++ Clusterer — thuần thuật toán, KHÔNG phụ thuộc Spring.
 * <p>
 * MỤC ĐÍCH:
 * Chia danh sách tọa độ orders thành C cụm địa lý, dùng làm bước tiền xử lý
 * trước khi đưa vào Jsprit (route-planning structure), KHÔNG phải để tính
 * distance matrix (khác mục đích với {@code DistanceMatrixService}).
 * <p>
 * TẠI SAO DÙNG EUCLIDEAN THAY VÌ HAVERSINE:
 * K-means chỉ cần THỨ TỰ TƯƠNG ĐỐI giữa các khoảng cách để gom nhóm, không
 * cần giá trị tuyệt đối chính xác theo mét. Với phạm vi một thành phố
 * (~10-20km bán kính), sai số Euclidean phẳng vs Haversine dưới 1% — nhưng
 * Euclidean nhanh hơn đáng kể khi phải tính hàng triệu lần
 * (N orders × nhiều iteration × C centroid).
 * <p>
 * TẠI SAO DÙNG K-MEANS++ INIT THAY VÌ RANDOM INIT:
 * Vì thuật toán chỉ chạy MỘT LẦN DUY NHẤT cho mỗi job (không có ngân sách
 * thời gian để chạy nhiều lần rồi chọn kết quả tốt nhất, khác với cách
 * ParetoWeightSampler chạy nhiều scenario), nên centroid ban đầu phải được
 * chọn có chủ đích để tránh hội tụ vào local optimum tệ (2 centroid rơi
 * gần nhau, chia đôi cùng một khu vực).
 *
 * @author Truong
 */
@Slf4j
public final class KMeansClusterer {

    /** Ngưỡng dịch chuyển centroid (độ, tương đương lat/lon) để coi là đã hội tụ. */
    private static final double CONVERGENCE_THRESHOLD = 1e-6;

    /** Số iteration tối đa — ngưỡng dừng cứng, tránh vòng lặp vô hạn nếu không hội tụ. */
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    /**
     * Seed mặc định, đồng bộ với RANDOM_SEED=42L đã dùng xuyên suốt project
     * (JspritConvergenceBenchmarkTest, CircuityFactorMeasurementTest, ...).
     * Dùng cho overload không truyền seed tường minh, để đảm bảo:
     * - Reproducibility: chạy lại cùng 1 job phải cho cùng cluster assignment,
     *   cần thiết khi debug production ("tại sao routing job #123 kỳ vậy").
     * - Nhất quán với convention "validate bằng seed cố định trước, ngẫu nhiên
     *   hóa sau nếu cần" đã áp dụng trong toàn bộ project.
     * TODO: cân nhắc seed theo jobId thay vì hằng số cố định nếu muốn mỗi job
     * có cluster assignment riêng nhưng vẫn deterministic khi re-run — để ngỏ,
     * chưa quyết định (xem thảo luận với Truong).
     */
    private static final long DEFAULT_RANDOM_SEED = 42L;

    private KMeansClusterer() {
        // Utility class — không khởi tạo instance
    }

    /**
     * Kết quả gán cụm.
     *
     * @param clusterIdByOrderIndex clusterIdByOrderIndex[i] = cluster ID của order tại index i
     *                              trong danh sách coordinates gốc truyền vào
     * @param numClusters           số cụm ĐÃ YÊU CẦU (k truyền vào fit()) — LƯU Ý: đây KHÔNG
     *                              phải là số cụm thực sự có ít nhất 1 điểm. Với phân bố lệch
     *                              (ví dụ Hà Nội: Old Quarter dày, ngoại ô thưa), một số cụm có
     *                              thể rỗng sau khi hội tụ (xem recomputeCentroids: cụm rỗng giữ
     *                              nguyên centroid cũ thay vì NaN, nhưng vẫn "chiếm 1 slot" trong
     *                              numClusters dù không có điểm nào). TẦNG GỌI PHẢI DÙNG
     *                              nonEmptyClusterIds() thay vì lặp [0, numClusters) để tránh xử
     *                              lý nhầm cụm rỗng như cụm thật (đặc biệt quan trọng cho bước
     *                              merge cluster nhỏ sắp tới, nơi centroid của cụm rỗng là stale
     *                              — không đại diện điểm dữ liệu thực nào).
     */
    public record ClusterAssignment(int[] clusterIdByOrderIndex, int numClusters) {

        /**
         * Trả về tập hợp cluster ID THỰC SỰ có ít nhất 1 điểm được gán.
         * <p>
         * Dùng thay cho việc lặp [0, numClusters) một cách "mù" — vì numClusters
         * chỉ phản ánh số cụm YÊU CẦU ban đầu, không đảm bảo mọi cluster ID trong
         * khoảng đó đều có dữ liệu thực (xem giải thích ở javadoc của record này).
         */
        public Set<Integer> nonEmptyClusterIds() {
            return Arrays.stream(clusterIdByOrderIndex)
                    .boxed()
                    .collect(Collectors.toSet());
        }

        /**
         * True nếu tồn tại cụm rỗng — nghĩa là numClusters() yêu cầu ban đầu
         * KHÔNG khớp số cụm thực sự có điểm. Hữu ích để log cảnh báo hoặc audit
         * khi tích hợp với ClusterMergeService.
         */
        public boolean hasEmptyClusters() {
            return nonEmptyClusterIds().size() < numClusters;
        }
    }

    /**
     * Chạy K-means++ trên danh sách tọa độ, dùng seed mặc định cố định
     * (DEFAULT_RANDOM_SEED) — KHÔNG dùng new Random() không seed, để đảm bảo
     * reproducibility khi debug production.
     *
     * @param coordinates danh sách tọa độ orders (KHÔNG bao gồm depot)
     * @param numClusters số cụm mong muốn C (đã tính theo công thức
     *                    C = max(1, ceil(N / S_target)) ở tầng gọi)
     * @return ClusterAssignment ánh xạ index -> cluster ID
     */
    public static ClusterAssignment fit(List<OptCoordinates> coordinates, int numClusters) {
        return fit(coordinates, numClusters, DEFAULT_MAX_ITERATIONS, new Random(DEFAULT_RANDOM_SEED));
    }

    /**
     * Chạy K-means++ với seed cố định — dùng cho unit test cần kết quả deterministic,
     * giống cách RANDOM_SEED được dùng trong các benchmark test hiện có của project.
     *
     * @param coordinates   danh sách tọa độ orders
     * @param numClusters   số cụm mong muốn C
     * @param maxIterations ngưỡng dừng cứng số vòng lặp
     * @param random        nguồn ngẫu nhiên (truyền seed cố định để test deterministic)
     * @return ClusterAssignment ánh xạ index -> cluster ID
     */
    public static ClusterAssignment fit(
            List<OptCoordinates> coordinates,
            int numClusters,
            int maxIterations,
            Random random) {

        validateInputs(coordinates, numClusters, maxIterations);

        int n = coordinates.size();

        // Edge case: số điểm <= số cụm yêu cầu -> mỗi điểm tự thành 1 cụm,
        // không cần chạy K-means (tránh cụm rỗng / centroid trùng nhau).
        if (n <= numClusters) {
            int[] trivialAssignment = new int[n];
            for (int i = 0; i < n; i++) {
                trivialAssignment[i] = i;
            }
            log.info("⚠️  Số orders ({}) <= số cụm yêu cầu ({}) — gán mỗi order 1 cụm riêng, bỏ qua K-means",
                    n, numClusters);
            return new ClusterAssignment(trivialAssignment, n);
        }

        double[][] points = toPointArray(coordinates);

        // ===== BƯỚC 1: Khởi tạo centroid bằng K-means++ =====
        double[][] centroids = initializeCentroidsPlusPlus(points, numClusters, random);

        int[] assignment = new int[n];
        int iteration = 0;
        boolean converged = false;

        // ===== BƯỚC 2-3: Lặp gán điểm + cập nhật centroid đến khi hội tụ =====
        while (iteration < maxIterations && !converged) {
            iteration++;

            // Gán mỗi điểm vào centroid gần nhất (Euclidean)
            for (int i = 0; i < n; i++) {
                assignment[i] = nearestCentroidIndex(points[i], centroids);
            }

            // Tính lại centroid = trung bình tọa độ các điểm trong cụm
            double[][] newCentroids = recomputeCentroids(points, assignment, numClusters, centroids);

            converged = hasConverged(centroids, newCentroids);
            centroids = newCentroids;
        }

        if (!converged) {
            log.warn("⚠️  K-means KHÔNG hội tụ sau {} iterations (đạt ngưỡng dừng cứng). " +
                    "Kết quả vẫn được dùng nhưng chất lượng cụm có thể chưa tối ưu.", maxIterations);
        } else {
            log.info("✅ K-means hội tụ sau {} iterations ({} orders → {} cụm yêu cầu)",
                    iteration, n, numClusters);
        }

        ClusterAssignment result = new ClusterAssignment(assignment, numClusters);

        if (result.hasEmptyClusters()) {
            log.warn("⚠️  Có {} cụm rỗng trong tổng {} cụm yêu cầu (thực tế chỉ {} cụm có điểm). " +
                            "Tầng gọi PHẢI dùng nonEmptyClusterIds() thay vì lặp [0, numClusters) " +
                            "để tránh xử lý nhầm cụm rỗng, đặc biệt ở bước merge cluster nhỏ.",
                    numClusters - result.nonEmptyClusterIds().size(),
                    numClusters,
                    result.nonEmptyClusterIds().size());
        }

        return result;
    }

    // ==================== K-MEANS++ INITIALIZATION ====================

    /**
     * Khởi tạo centroid theo K-means++.
     * <p>
     * - Centroid đầu tiên: chọn ngẫu nhiên đều (uniform) một điểm bất kỳ.
     * - Centroid tiếp theo: chọn theo xác suất tỉ lệ với BÌNH PHƯƠNG khoảng cách
     *   đến centroid gần nhất đã có (roulette wheel selection). Điểm càng xa
     *   các centroid hiện tại càng có xác suất cao được chọn, nhưng vẫn giữ
     *   tính ngẫu nhiên để tránh luôn bám theo outlier/nhiễu.
     */
    private static double[][] initializeCentroidsPlusPlus(double[][] points, int k, Random random) {
        int n = points.length;
        double[][] centroids = new double[k][2];

        // Centroid đầu tiên: chọn ngẫu nhiên đều
        int firstIndex = random.nextInt(n);
        centroids[0] = points[firstIndex].clone();

        // Khoảng cách bình phương từ mỗi điểm đến centroid gần nhất đã chọn
        double[] minSquaredDistances = new double[n];

        for (int c = 1; c < k; c++) {
            double sumSquaredDistances = 0.0;

            // Cập nhật minSquaredDistances dựa trên centroid vừa thêm (c-1)
            for (int i = 0; i < n; i++) {
                double d = squaredEuclideanDistance(points[i], centroids[c - 1]);
                if (c == 1 || d < minSquaredDistances[i]) {
                    minSquaredDistances[i] = d;
                }
                sumSquaredDistances += minSquaredDistances[i];
            }

            // Roulette wheel: chọn điểm tiếp theo theo xác suất tỉ lệ D(x)^2
            if (sumSquaredDistances == 0.0) {
                // Toàn bộ điểm còn lại trùng centroid đã có -> chọn ngẫu nhiên đều để tránh chia 0
                centroids[c] = points[random.nextInt(n)].clone();
                continue;
            }

            double threshold = random.nextDouble() * sumSquaredDistances;
            double cumulative = 0.0;
            int chosenIndex = n - 1; // fallback an toàn nếu sai số làm tổng không khớp

            for (int i = 0; i < n; i++) {
                cumulative += minSquaredDistances[i];
                if (cumulative >= threshold) {
                    chosenIndex = i;
                    break;
                }
            }

            centroids[c] = points[chosenIndex].clone();
        }

        return centroids;
    }

    // ==================== ASSIGNMENT & UPDATE ====================

    private static int nearestCentroidIndex(double[] point, double[][] centroids) {
        int nearest = 0;
        double minDist = squaredEuclideanDistance(point, centroids[0]);

        for (int c = 1; c < centroids.length; c++) {
            double d = squaredEuclideanDistance(point, centroids[c]);
            if (d < minDist) {
                minDist = d;
                nearest = c;
            }
        }
        return nearest;
    }

    /**
     * Tính lại centroid = trung bình tọa độ các điểm trong cụm.
     * <p>
     * XỬ LÝ CỤM RỖNG: nếu một cụm không có điểm nào (có thể xảy ra khi centroid
     * ban đầu "thắng" 0 điểm sau bước gán), GIỮ NGUYÊN centroid cũ của cụm đó
     * thay vì để NaN — tránh lan truyền lỗi sang các iteration sau. Đây là một
     * chiến lược đơn giản (không phải re-seed cụm rỗng); nếu cụm rỗng xuất hiện
     * thường xuyên trong thực tế, cần nâng cấp sang chiến lược re-seed.
     */
    private static double[][] recomputeCentroids(
            double[][] points, int[] assignment, int k, double[][] previousCentroids) {

        double[][] sums = new double[k][2];
        int[] counts = new int[k];

        for (int i = 0; i < points.length; i++) {
            int c = assignment[i];
            sums[c][0] += points[i][0];
            sums[c][1] += points[i][1];
            counts[c]++;
        }

        double[][] newCentroids = new double[k][2];
        for (int c = 0; c < k; c++) {
            if (counts[c] == 0) {
                log.debug("Cụm {} rỗng sau bước gán — giữ nguyên centroid cũ", c);
                newCentroids[c] = previousCentroids[c].clone();
            } else {
                newCentroids[c][0] = sums[c][0] / counts[c];
                newCentroids[c][1] = sums[c][1] / counts[c];
            }
        }

        return newCentroids;
    }

    private static boolean hasConverged(double[][] oldCentroids, double[][] newCentroids) {
        for (int c = 0; c < oldCentroids.length; c++) {
            double shift = Math.sqrt(squaredEuclideanDistance(oldCentroids[c], newCentroids[c]));
            if (shift > CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    // ==================== HELPERS ====================

    private static double squaredEuclideanDistance(double[] a, double[] b) {
        double dLat = a[0] - b[0];
        double dLon = a[1] - b[1];
        return dLat * dLat + dLon * dLon;
    }

    private static double[][] toPointArray(List<OptCoordinates> coordinates) {
        double[][] points = new double[coordinates.size()][2];
        for (int i = 0; i < coordinates.size(); i++) {
            OptCoordinates c = coordinates.get(i);
            points[i][0] = c.latDouble();
            points[i][1] = c.lonDouble();
        }
        return points;
    }

    private static void validateInputs(List<OptCoordinates> coordinates, int numClusters, int maxIterations) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("coordinates không được rỗng");
        }
        if (numClusters < 1) {
            throw new IllegalArgumentException("numClusters phải >= 1, nhận được: " + numClusters);
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations phải >= 1, nhận được: " + maxIterations);
        }
    }
}