package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.IterationEndsListener;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BENCHMARK GIAI ĐOẠN 2 — Xác nhận hội tụ thực sự ở FULL iterations.
 * <p>
 * KHÁC VỚI BẢN TRƯỚC (JspritPerformanceBenchmarkTest gốc):
 * - Bản trước quét cả ma trận 9 tổ hợp ở 50 iterations để tìm ứng viên nhanh —
 *   kết quả: threads scale gần tuyến tính, FAST_REGRET giảm ~70% thời gian,
 *   nhưng KHÔNG kết luận được về CHẤT LƯỢNG vì 50 iterations chưa hội tụ
 *   (unassigned dao động 365-394/1000, tức ~37-39%, ở MỌI cấu hình).
 * <p>
 * - Bản này CHỈ chạy 2 cấu hình để so sánh công bằng tại điểm hội tụ thực sự:
 *   1. regret_insertion, fastRegret=true,  threads=4  (ứng viên "nhanh nhất")
 *   2. regret_insertion, fastRegret=false, threads=4  (baseline "chất lượng cao")
 * <p>
 * - Thêm IterationEndsListener để log tiến độ (cost, unassigned) theo mốc %
 *   của MAX_ITERATIONS, giúp quan sát TRỰC TIẾP xu hướng hội tụ theo thời
 *   gian thực thay vì đợi im lặng đến khi xong mới biết kết quả cuối.
 * <p>
 * QUAN TRỌNG:
 * - Test @Disabled mặc định — bỏ khi chạy thật.
 * - MAX_ITERATIONS = 2000, KHÔNG set timeout (giữ nguyên bài học từ Timeout bug).
 * - Ước tính thời gian chạy dựa trên số liệu 50-iteration đã đo:
 *   regret+fastRegret=true+threads=4:  127s/50 iter ≈ 2.54s/iter → ~85 phút cho 2000 iter
 *   regret+fastRegret=false+threads=4: 430s/50 iter ≈ 8.6s/iter  → ~287 phút (~4.8h) cho 2000 iter
 *   ĐÂY LÀ NGOẠI SUY THÔ (giả định thời gian/iteration không đổi theo thời gian,
 *   điều này CHƯA được xác nhận — có thể route càng phức tạp về sau, mỗi
 *   iteration càng chậm dần, hoặc ngược lại nếu ruin size giảm dần theo
 *   adaptive strategy của Jsprit). Progress log sẽ cho biết ngoại suy này
 *   đúng sai đến đâu khi chạy thật.
 * - Vì baseline fastRegret=false có thể mất gần 5 tiếng, cân nhắc chạy
 *   qua đêm hoặc dùng ENABLE_SLOW_BASELINE=false để tạm tắt nếu muốn xem
 *   riêng ứng viên nhanh trước.
 */
@SpringBootTest
public class JspritConvergenceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(JspritConvergenceBenchmarkTest.class);

    // ==================== CẤU HÌNH BENCHMARK ====================

    private static final int NUM_ORDERS = 1000;
    private static final int NUM_VEHICLES = 5;

    /** FULL iterations — mục tiêu là quan sát điểm hội tụ thực sự, không cắt ngang. */
    private static final int MAX_ITERATIONS = 2000;

    private static final long RANDOM_SEED = 42L;

    private static final double CENTER_LAT = 21.0285;
    private static final double CENTER_LON = 105.8542;
    private static final double SPREAD_DEGREES = 0.05;

    /**
     * Đặt false nếu muốn tạm bỏ qua baseline "chất lượng cao" (fastRegret=false)
     * vì nó có thể chạy rất lâu (~4-5 tiếng theo ngoại suy thô) — chỉ chạy
     * ứng viên nhanh (fastRegret=true) trước để có kết quả sớm.
     */
    private static final boolean ENABLE_SLOW_BASELINE = true;

    /** Log tiến độ mỗi khi đạt thêm N% của MAX_ITERATIONS (10% = log 10 lần/run). */
    private static final int PROGRESS_LOG_EVERY_PERCENT = 10;

    @Autowired
    private DistanceMatrixService distanceMatrixService;

    private static List<OptCoordinates> sharedCoordinates;
    private static double[][] sharedDistanceMatrix;
    private static double[][] sharedTimeMatrix;

    @BeforeAll
    static void logBenchmarkHeader() {
        log.info("================================================================");
        log.info(" JSPRIT CONVERGENCE BENCHMARK (FULL ITERATIONS)");
        log.info(" Orders={} | Vehicles={} | MaxIterations={} | Seed={}",
                NUM_ORDERS, NUM_VEHICLES, MAX_ITERATIONS, RANDOM_SEED);
        log.info(" ⚠️  Ước tính thời gian: ứng viên nhanh ~85 phút, baseline chậm ~4-5 giờ");
        log.info(" ⚠️  Đây là ngoại suy THÔ từ benchmark 50-iteration trước — có thể sai lệch");
        log.info("================================================================");
    }

    private void ensureSharedMatrixBuilt() {
        if (sharedDistanceMatrix != null) {
            return;
        }

        log.info("🗺️  Building shared distance matrix via REAL GraphHopper ({} locations)...",
                NUM_ORDERS + 1);

        Random random = new Random(RANDOM_SEED);
        List<OptCoordinates> coordinates = new ArrayList<>();

        coordinates.add(new OptCoordinates(
                BigDecimal.valueOf(CENTER_LAT),
                BigDecimal.valueOf(CENTER_LON)
        ));

        for (int i = 0; i < NUM_ORDERS; i++) {
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * 2 * SPREAD_DEGREES;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * 2 * SPREAD_DEGREES;
            coordinates.add(new OptCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon)));
        }

        long start = System.currentTimeMillis();
        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(coordinates);
        long elapsed = System.currentTimeMillis() - start;

        int n = coordinates.size();
        double[][] distanceMatrix = new double[n][n];
        double[][] timeMatrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                DistanceMatrixEntry entry = ghMatrix.get(i, j);
                distanceMatrix[i][j] = entry.distanceMeters();
                timeMatrix[i][j] = entry.timeSeconds();
            }
        }

        sharedCoordinates = coordinates;
        sharedDistanceMatrix = distanceMatrix;
        sharedTimeMatrix = timeMatrix;

        log.info("✅ Shared distance matrix built in {} ms ({} locations, {} cells)",
                elapsed, n, n * n);
    }

    // ==================== BENCHMARK CHÍNH ====================

    @Test
//    @Disabled("Benchmark thủ công dài hơi (tới ~5 tiếng cho baseline chậm). "
//            + "Bỏ @Disabled khi sẵn sàng chạy qua đêm hoặc để nền lâu dài.")
    void benchmarkConvergenceAtFullIterations() {
        ensureSharedMatrixBuilt();

        List<BenchmarkResult> results = new ArrayList<>();

        // Ứng viên 1: cấu hình nhanh nhất từ benchmark 50-iteration trước
        log.info("=== [1/2] Ứng viên NHANH: regret_insertion, fastRegret=true, threads=4 ===");
        results.add(runSingleConfiguration(Jsprit.Construction.REGRET_INSERTION, true, 4));

        // Ứng viên 2: baseline "chất lượng cao" — chỉ chạy nếu ENABLE_SLOW_BASELINE
        if (ENABLE_SLOW_BASELINE) {
            log.info("=== [2/2] Baseline CHẤT LƯỢNG: regret_insertion, fastRegret=false, threads=4 ===");
            results.add(runSingleConfiguration(Jsprit.Construction.REGRET_INSERTION, false, 4));
        } else {
            log.info("=== [2/2] Bỏ qua baseline chậm (ENABLE_SLOW_BASELINE=false) ===");
        }

        printResultsTable(results);

        for (BenchmarkResult r : results) {
            assertTrue(Double.isFinite(r.jspritCost),
                    "Cấu hình " + r.label + " trả về cost không hữu hạn: " + r.jspritCost);
            assertFalse(r.jspritCost < 0,
                    "Cấu hình " + r.label + " trả về cost âm bất thường: " + r.jspritCost);
        }

        // So sánh trực tiếp nếu chạy cả 2 — đây là câu trả lời thật cho câu hỏi
        // "chênh lệch 3.59% ở 50 iterations còn tồn tại ở full iterations không?"
        if (results.size() == 2) {
            BenchmarkResult fast = results.get(0);
            BenchmarkResult slow = results.get(1);
            double costDiffPercent = (fast.jspritCost - slow.jspritCost) / slow.jspritCost * 100.0;

            log.info("================================================================");
            log.info(" SO SÁNH TRỰC TIẾP TẠI FULL {} ITERATIONS", MAX_ITERATIONS);
            log.info("================================================================");
            log.info(" Ứng viên nhanh : {} ms | cost={} | unassigned={}",
                    fast.elapsedMillis, fast.jspritCost, fast.unassignedCount);
            log.info(" Baseline chậm  : {} ms | cost={} | unassigned={}",
                    slow.elapsedMillis, slow.jspritCost, slow.unassignedCount);
            log.info(" Chênh lệch cost: {}% (dương = ứng viên nhanh tệ hơn)",
                    String.format("%.2f", costDiffPercent));
            log.info(" Chênh lệch thời gian: {}x (baseline chậm hơn bao nhiêu lần)",
                    String.format("%.2f", (double) slow.elapsedMillis / fast.elapsedMillis));
            log.info("================================================================");
        }
    }

    /**
     * Chạy một cấu hình Jsprit cụ thể, có progress listener log tiến độ hội tụ
     * theo mốc % iterations thay vì im lặng tới khi xong.
     */
    private BenchmarkResult runSingleConfiguration(
            Jsprit.Construction construction,
            boolean fastRegret,
            int threads) {

        VehicleRoutingProblem vrp = buildVrp();

        Jsprit.Builder builder = Jsprit.Builder.newInstance(vrp);
        builder.setProperty(Jsprit.Parameter.ITERATIONS, String.valueOf(MAX_ITERATIONS));
        builder.setProperty(Jsprit.Parameter.THREADS, String.valueOf(threads));
        builder.setProperty(Jsprit.Parameter.CONSTRUCTION, construction.toString());
        builder.setProperty(Jsprit.Parameter.FAST_REGRET, String.valueOf(fastRegret));

        VehicleRoutingAlgorithm algorithm = builder.buildAlgorithm();

        String label = String.format("%s | fastRegret=%s | threads=%d", construction, fastRegret, threads);

        // Progress listener: log mỗi khi đạt thêm PROGRESS_LOG_EVERY_PERCENT% iterations,
        // kèm thời gian đã trôi qua và cost/unassigned hiện tại — cho phép quan sát
        // TRỰC TIẾP xu hướng hội tụ (cost có đang giảm dần đều, hay đã bão hòa sớm?)
        long algorithmStartTime = System.currentTimeMillis();
        int logStepIterations = Math.max(1, MAX_ITERATIONS * PROGRESS_LOG_EVERY_PERCENT / 100);

        algorithm.addListener((IterationEndsListener) (iteration, problem, solutions) -> {
            if (iteration % logStepIterations == 0 || iteration == MAX_ITERATIONS) {
                VehicleRoutingProblemSolution currentBest = Solutions.bestOf(solutions);
                long elapsedSoFar = System.currentTimeMillis() - algorithmStartTime;
                double percentDone = 100.0 * iteration / MAX_ITERATIONS;

                log.info("    [{}] {}% ({}/{}) | {}s trôi qua | cost={} | unassigned={}",
                        label,
                        String.format("%.0f", percentDone),
                        iteration, MAX_ITERATIONS,
                        elapsedSoFar / 1000,
                        String.format("%.0f", currentBest.getCost()),
                        currentBest.getUnassignedJobs().size());
            }
        });

        long start = System.currentTimeMillis();
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        long elapsed = System.currentTimeMillis() - start;

        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        log.info("    → HOÀN TẤT [{}]: {} ms | jspritCost={} | vehiclesUsed={} | unassigned={}",
                label, elapsed, best.getCost(), best.getRoutes().size(), best.getUnassignedJobs().size());

        return new BenchmarkResult(
                label,
                construction,
                fastRegret,
                threads,
                elapsed,
                best.getCost(),
                best.getRoutes().size(),
                best.getUnassignedJobs().size()
        );
    }

    /**
     * Build VRP đơn giản hóa — giống hệt bản benchmark 50-iteration trước,
     * giữ nguyên để đảm bảo so sánh công bằng giữa 2 lần benchmark.
     */
    private VehicleRoutingProblem buildVrp() {
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < sharedCoordinates.size(); i++) {
            OptCoordinates coord = sharedCoordinates.get(i);
            String id = (i == 0) ? "depot-0" : "order-" + i;
            locations.add(Location.Builder.newInstance()
                    .setId(id)
                    .setCoordinate(Coordinate.newInstance(coord.lonDouble(), coord.latDouble()))
                    .build());
        }

        VehicleRoutingTransportCostsMatrix.Builder costBuilder =
                VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);
        for (int i = 0; i < locations.size(); i++) {
            for (int j = 0; j < locations.size(); j++) {
                costBuilder.addTransportDistance(
                        locations.get(i).getId(), locations.get(j).getId(), sharedDistanceMatrix[i][j]);
                costBuilder.addTransportTime(
                        locations.get(i).getId(), locations.get(j).getId(), sharedTimeMatrix[i][j]);
            }
        }
        vrpBuilder.setRoutingCost(costBuilder.build());

        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("benchmark-type")
                .addCapacityDimension(0, 100_000)
                .setCostPerDistance(1.0)
                .setCostPerTransportTime(0.1)
                .setFixedCost(0.0)
                .build();

        Location depotLocation = locations.get(0);
        for (int v = 0; v < NUM_VEHICLES; v++) {
            VehicleImpl vehicle = VehicleImpl.Builder.newInstance("vehicle-" + v)
                    .setStartLocation(depotLocation)
                    .setEndLocation(depotLocation)
                    .setType(vehicleType)
                    .setReturnToDepot(true)
                    .setEarliestStart(8 * 3600)
                    .setLatestArrival(20 * 3600)
                    .build();
            vrpBuilder.addVehicle(vehicle);
        }

        for (int i = 1; i < locations.size(); i++) {
            Service service = Service.Builder.newInstance("order-" + i)
                    .setLocation(locations.get(i))
                    .addSizeDimension(0, 10)
                    .setServiceTime(300.0)
                    .build();
            vrpBuilder.addJob(service);
        }

        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        return vrpBuilder.build();
    }

    // ==================== IN KẾT QUẢ ====================

    private void printResultsTable(List<BenchmarkResult> results) {
        log.info("================================================================");
        log.info(" KẾT QUẢ BENCHMARK (paste vào Excel/Sheets, phân tách bởi dấu phẩy)");
        log.info("================================================================");
        log.info("construction,fast_regret,threads,elapsed_ms,jsprit_cost,vehicles_used,unassigned");

        for (BenchmarkResult r : results) {
            log.info("{},{},{},{},{},{},{}",
                    r.construction, r.fastRegret, r.threads,
                    r.elapsedMillis, r.jspritCost, r.vehiclesUsed, r.unassignedCount);
        }

        log.info("================================================================");
    }

    // ==================== DATA HOLDER ====================

    private static class BenchmarkResult {
        final String label;
        final Jsprit.Construction construction;
        final boolean fastRegret;
        final int threads;
        final long elapsedMillis;
        final double jspritCost;
        final int vehiclesUsed;
        final int unassignedCount;

        BenchmarkResult(String label, Jsprit.Construction construction, boolean fastRegret,
                        int threads, long elapsedMillis, double jspritCost,
                        int vehiclesUsed, int unassignedCount) {
            this.label = label;
            this.construction = construction;
            this.fastRegret = fastRegret;
            this.threads = threads;
            this.elapsedMillis = elapsedMillis;
            this.jspritCost = jspritCost;
            this.vehiclesUsed = vehiclesUsed;
            this.unassignedCount = unassignedCount;
        }
    }
}