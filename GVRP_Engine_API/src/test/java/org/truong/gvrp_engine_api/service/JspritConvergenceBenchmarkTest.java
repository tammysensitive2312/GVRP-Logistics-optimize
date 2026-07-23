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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BENCHMARK GIAI ĐOẠN 2 — Xác nhận hội tụ thực sự ở FULL iterations, CHẠY NHIỀU LẦN.
 * <p>
 * THAY ĐỔI SO VỚI BẢN 1-LẦN-CHẠY TRƯỚC:
 * - Một lần chạy duy nhất mỗi cấu hình không đủ để phân biệt "cấu hình A tốt hơn B"
 *   khỏi nhiễu ngẫu nhiên (Jsprit dùng random trong construction + ruin strategy,
 *   nên 2 lần chạy CÙNG cấu hình vẫn cho cost khác nhau). Bản này chạy
 *   {@link #RUNS_PER_CONFIG} lần mỗi cấu hình để có mean/stddev đáng tin cậy.
 * - MỌI kết quả (mỗi run, mỗi mốc % iteration) được ghi ra CSV NGAY SAU KHI CÓ,
 *   không đợi toàn bộ benchmark chạy xong mới ghi — vì tổng thời gian chạy có
 *   thể lên tới nhiều giờ, nếu bị gián đoạn (crash, dừng thủ công) giữa chừng,
 *   các run đã hoàn tất trước đó vẫn phải giữ được dữ liệu, không mất trắng.
 * <p>
 * OUTPUT (trong build/benchmark-reports/):
 * - convergence_summary_<timestamp>.csv : 1 dòng = 1 run hoàn tất
 *   (config, run_id, elapsed_ms, jsprit_cost, vehicles_used, unassigned)
 *   → dùng để tính mean/stddev, so sánh 2 cấu hình một cách có ý nghĩa thống kê.
 * - convergence_progress_<timestamp>.csv : 1 dòng = 1 mốc % iteration của 1 run
 *   (config, run_id, iteration, percent_done, elapsed_ms, cost, unassigned)
 *   → dùng để vẽ đường cong hội tụ, xác nhận cost đã plateau hay còn đang giảm
 *   ở gần iteration cuối (bằng chứng trực tiếp cho việc đã hội tụ thật hay chưa).
 * <p>
 * QUAN TRỌNG:
 * - MAX_ITERATIONS = 2000, KHÔNG set timeout (giữ nguyên bài học từ Timeout bug).
 * - RUNS_PER_CONFIG mặc định = 5 — đủ để tính stddev có ý nghĩa mà không quá tốn
 *   thời gian. Có thể giảm xuống 3 nếu cần kết quả sớm, tăng lên nếu variance cao.
 * - Ước tính thời gian (dựa trên benchmark 1-lần trước, ~85 phút/run nhanh,
 *   ~vài giờ/run chậm): với RUNS_PER_CONFIG=5, tổng thời gian có thể RẤT dài
 *   cho baseline chậm — cân nhắc ENABLE_SLOW_BASELINE=false trước, chạy riêng
 *   ứng viên nhanh 5 lần để có kết quả sớm, sau đó chạy baseline chậm qua đêm.
 */
@SpringBootTest
public class JspritConvergenceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(JspritConvergenceBenchmarkTest.class);

    // ==================== CẤU HÌNH BENCHMARK ====================

    private static final int NUM_ORDERS = 1000;
    private static final int NUM_VEHICLES = 5;

    /** FULL iterations — mục tiêu là quan sát điểm hội tụ thực sự, không cắt ngang. */
    private static final int MAX_ITERATIONS = 2000;

    /**
     * Số lần chạy LẶP LẠI mỗi cấu hình. Jsprit có random nội bộ (construction,
     * ruin strategy) nên 1 lần chạy không đủ để phân biệt "tốt hơn thật sự"
     * khỏi "may mắn lần này". Cần >= 3 để tính stddev có ý nghĩa tối thiểu,
     * khuyến nghị 5 nếu thời gian cho phép.
     */
    private static final int RUNS_PER_CONFIG = 5;

    /** Tọa độ bài toán CỐ ĐỊNH giữa mọi run — chỉ Jsprit's internal random thay đổi. */
    private static final long COORDINATE_SEED = 42L;

    private static final double CENTER_LAT = 21.0285;
    private static final double CENTER_LON = 105.8542;
    private static final double SPREAD_DEGREES = 0.05;

    /**
     * Đặt false nếu muốn tạm bỏ qua baseline "chất lượng cao" (fastRegret=false)
     * vì nó có thể chạy rất lâu — chỉ chạy ứng viên nhanh (fastRegret=true)
     * RUNS_PER_CONFIG lần trước để có kết quả sớm.
     */
    private static final boolean ENABLE_SLOW_BASELINE = true;

    /** Log tiến độ mỗi khi đạt thêm N% của MAX_ITERATIONS (10% = log 10 lần/run). */
    private static final int PROGRESS_LOG_EVERY_PERCENT = 10;

    @Autowired
    private DistanceMatrixService distanceMatrixService;

    private static List<OptCoordinates> sharedCoordinates;
    private static double[][] sharedDistanceMatrix;
    private static double[][] sharedTimeMatrix;

    // File output — khởi tạo 1 lần, ghi append xuyên suốt toàn bộ benchmark
    private static Path summaryCsvPath;
    private static Path progressCsvPath;

    @BeforeAll
    static void setupOutputFiles() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        Path outputDir = Paths.get("build", "benchmark-reports");
        Files.createDirectories(outputDir);

        summaryCsvPath = outputDir.resolve("convergence_summary_" + timestamp + ".csv");
        progressCsvPath = outputDir.resolve("convergence_progress_" + timestamp + ".csv");

        try (FileWriter w = new FileWriter(summaryCsvPath.toFile())) {
            w.write("config,run_id,construction,fast_regret,threads,elapsed_ms,jsprit_cost,vehicles_used,unassigned\n");
        }
        try (FileWriter w = new FileWriter(progressCsvPath.toFile())) {
            w.write("config,run_id,iteration,percent_done,elapsed_ms,cost,unassigned\n");
        }

        log.info("================================================================");
        log.info(" JSPRIT CONVERGENCE BENCHMARK (FULL ITERATIONS, MULTIPLE RUNS)");
        log.info(" Orders={} | Vehicles={} | MaxIterations={} | RunsPerConfig={} | CoordSeed={}",
                NUM_ORDERS, NUM_VEHICLES, MAX_ITERATIONS, RUNS_PER_CONFIG, COORDINATE_SEED);
        log.info(" 📄 Summary CSV : {}", summaryCsvPath.toAbsolutePath());
        log.info(" 📄 Progress CSV: {}", progressCsvPath.toAbsolutePath());
        log.info(" ⚠️  Dữ liệu được ghi NGAY SAU MỖI RUN — an toàn nếu benchmark bị gián đoạn giữa chừng");
        log.info("================================================================");
    }

    private void ensureSharedMatrixBuilt() {
        if (sharedDistanceMatrix != null) {
            return;
        }

        log.info("🗺️  Building shared distance matrix via REAL GraphHopper ({} locations)...",
                NUM_ORDERS + 1);

        Random random = new Random(COORDINATE_SEED);
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
        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(coordinates, null); // null = full matrix (không prune)
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
    void benchmarkConvergenceAtFullIterations() {
        ensureSharedMatrixBuilt();

        List<BenchmarkResult> fastResults = new ArrayList<>();
        List<BenchmarkResult> slowResults = new ArrayList<>();

        String fastConfigLabel = "fastRegret=true";
        log.info("=== [1/2] Ứng viên NHANH: regret_insertion, fastRegret=true, threads=4 — {} runs ===",
                RUNS_PER_CONFIG);
        for (int runId = 1; runId <= RUNS_PER_CONFIG; runId++) {
            BenchmarkResult r = runSingleConfiguration(
                    fastConfigLabel, runId, Jsprit.Construction.REGRET_INSERTION, true, 4);
            fastResults.add(r);
            appendSummaryRow(r);
        }

        if (ENABLE_SLOW_BASELINE) {
            String slowConfigLabel = "fastRegret=false";
            log.info("=== [2/2] Baseline CHẤT LƯỢNG: regret_insertion, fastRegret=false, threads=4 — {} runs ===",
                    RUNS_PER_CONFIG);
            for (int runId = 1; runId <= RUNS_PER_CONFIG; runId++) {
                BenchmarkResult r = runSingleConfiguration(
                        slowConfigLabel, runId, Jsprit.Construction.REGRET_INSERTION, false, 4);
                slowResults.add(r);
                appendSummaryRow(r);
            }
        } else {
            log.info("=== [2/2] Bỏ qua baseline chậm (ENABLE_SLOW_BASELINE=false) ===");
        }

        for (BenchmarkResult r : fastResults) {
            assertTrue(Double.isFinite(r.jspritCost), "Run " + r.runId + " (" + r.configLabel + ") trả về cost không hữu hạn: " + r.jspritCost);
            assertFalse(r.jspritCost < 0, "Run " + r.runId + " (" + r.configLabel + ") trả về cost âm bất thường: " + r.jspritCost);
        }
        for (BenchmarkResult r : slowResults) {
            assertTrue(Double.isFinite(r.jspritCost), "Run " + r.runId + " (" + r.configLabel + ") trả về cost không hữu hạn: " + r.jspritCost);
            assertFalse(r.jspritCost < 0, "Run " + r.runId + " (" + r.configLabel + ") trả về cost âm bất thường: " + r.jspritCost);
        }

        printAggregateStatistics("fastRegret=true", fastResults);
        if (!slowResults.isEmpty()) {
            printAggregateStatistics("fastRegret=false", slowResults);
            printComparison(fastResults, slowResults);
        }

        log.info("================================================================");
        log.info(" ✅ Benchmark hoàn tất. Dữ liệu đầy đủ đã lưu tại:");
        log.info("    {}", summaryCsvPath.toAbsolutePath());
        log.info("    {}", progressCsvPath.toAbsolutePath());
        log.info("================================================================");
    }

    /**
     * Chạy một cấu hình Jsprit cụ thể (1 run), có progress listener log tiến độ
     * hội tụ theo mốc % iterations — ghi từng dòng progress ra CSV ngay lập tức.
     */
    private BenchmarkResult runSingleConfiguration(
            String configLabel,
            int runId,
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

        String fullLabel = String.format("%s | run=%d/%d | %s | threads=%d",
                configLabel, runId, RUNS_PER_CONFIG, construction, threads);

        long algorithmStartTime = System.currentTimeMillis();
        int logStepIterations = Math.max(1, MAX_ITERATIONS * PROGRESS_LOG_EVERY_PERCENT / 100);

        algorithm.addListener((IterationEndsListener) (iteration, problem, solutions) -> {
            if (iteration % logStepIterations == 0 || iteration == MAX_ITERATIONS) {
                VehicleRoutingProblemSolution currentBest = Solutions.bestOf(solutions);
                long elapsedSoFar = System.currentTimeMillis() - algorithmStartTime;
                double percentDone = 100.0 * iteration / MAX_ITERATIONS;

                log.info("    [{}] {}% ({}/{}) | {}s trôi qua | cost={} | unassigned={}",
                        fullLabel,
                        String.format("%.0f", percentDone),
                        iteration, MAX_ITERATIONS,
                        elapsedSoFar / 1000,
                        String.format("%.0f", currentBest.getCost()),
                        currentBest.getUnassignedJobs().size());

                appendProgressRow(configLabel, runId, iteration, percentDone,
                        elapsedSoFar, currentBest.getCost(), currentBest.getUnassignedJobs().size());
            }
        });

        long start = System.currentTimeMillis();
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        long elapsed = System.currentTimeMillis() - start;

        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        log.info("    → HOÀN TẤT [{}]: {} ms | jspritCost={} | vehiclesUsed={} | unassigned={}",
                fullLabel, elapsed, best.getCost(), best.getRoutes().size(), best.getUnassignedJobs().size());

        return new BenchmarkResult(
                configLabel,
                runId,
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
     * Build VRP đơn giản hóa — giống hệt bản benchmark trước, giữ nguyên để
     * đảm bảo so sánh công bằng giữa các run và giữa các lần benchmark khác nhau.
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

    // ==================== GHI FILE (APPEND NGAY SAU MỖI SỰ KIỆN) ====================

    /**
     * Ghi 1 dòng summary ngay sau khi 1 run hoàn tất — KHÔNG đợi toàn bộ
     * benchmark xong. Dùng "synchronized" vì @Test có thể chạy tuần tự ở đây,
     * nhưng an toàn hơn nếu sau này benchmark được song song hóa giữa các run.
     */
    private static synchronized void appendSummaryRow(BenchmarkResult r) {
        try (FileWriter w = new FileWriter(summaryCsvPath.toFile(), true)) {
            w.write(String.format(Locale.US, "%s,%d,%s,%s,%d,%d,%.4f,%d,%d%n",
                    r.configLabel, r.runId, r.construction, r.fastRegret, r.threads,
                    r.elapsedMillis, r.jspritCost, r.vehiclesUsed, r.unassignedCount));
        } catch (IOException e) {
            // Non-fatal nhưng LOUD: mất 1 dòng summary không nên làm sập benchmark
            // đang chạy nhiều giờ, nhưng phải cảnh báo rõ vì đây là dữ liệu quý.
            log.error("❌ Không ghi được summary row cho run {} ({}): {}",
                    r.runId, r.configLabel, e.getMessage(), e);
        }
    }

    private static synchronized void appendProgressRow(
            String configLabel, int runId, int iteration, double percentDone,
            long elapsedMs, double cost, int unassigned) {
        try (FileWriter w = new FileWriter(progressCsvPath.toFile(), true)) {
            w.write(String.format(Locale.US, "%s,%d,%d,%.1f,%d,%.4f,%d%n",
                    configLabel, runId, iteration, percentDone, elapsedMs, cost, unassigned));
        } catch (IOException e) {
            log.error("❌ Không ghi được progress row cho run {} ({}) tại iteration {}: {}",
                    runId, configLabel, iteration, e.getMessage(), e);
        }
    }

    // ==================== THỐNG KÊ TỔNG HỢP ====================

    private void printAggregateStatistics(String configLabel, List<BenchmarkResult> results) {
        if (results.isEmpty()) {
            return;
        }

        double[] costs = results.stream().mapToDouble(r -> r.jspritCost).toArray();
        long[] elapsedMs = results.stream().mapToLong(r -> r.elapsedMillis).toArray();
        int[] unassigned = results.stream().mapToInt(r -> r.unassignedCount).toArray();

        double meanCost = mean(costs);
        double stdCost = stdDev(costs, meanCost);
        double meanElapsed = mean(toDoubleArray(elapsedMs));
        double meanUnassigned = mean(toDoubleArray(unassigned));

        log.info("----------------------------------------------------------------");
        log.info(" Thống kê [{}] qua {} lần chạy:", configLabel, results.size());
        log.info("   Cost:       mean={} | stddev={} | min={} | max={}",
                String.format(Locale.US, "%.2f", meanCost),
                String.format(Locale.US, "%.2f", stdCost),
                String.format(Locale.US, "%.2f", min(costs)),
                String.format(Locale.US, "%.2f", max(costs)));
        log.info("   Elapsed:    mean={} ms", String.format(Locale.US, "%.0f", meanElapsed));
        log.info("   Unassigned: mean={} (min={}, max={})",
                String.format(Locale.US, "%.1f", meanUnassigned),
                (int) min(toDoubleArray(unassigned)), (int) max(toDoubleArray(unassigned)));
        log.info("----------------------------------------------------------------");
    }

    private void printComparison(List<BenchmarkResult> fastResults, List<BenchmarkResult> slowResults) {
        double meanFastCost = mean(fastResults.stream().mapToDouble(r -> r.jspritCost).toArray());
        double meanSlowCost = mean(slowResults.stream().mapToDouble(r -> r.jspritCost).toArray());
        double meanFastElapsed = mean(toDoubleArray(fastResults.stream().mapToLong(r -> r.elapsedMillis).toArray()));
        double meanSlowElapsed = mean(toDoubleArray(slowResults.stream().mapToLong(r -> r.elapsedMillis).toArray()));

        double costDiffPercent = (meanFastCost - meanSlowCost) / meanSlowCost * 100.0;
        double speedupFactor = meanSlowElapsed / meanFastElapsed;

        log.info("================================================================");
        log.info(" SO SÁNH TỔNG HỢP (trung bình qua {} lần chạy mỗi cấu hình)", RUNS_PER_CONFIG);
        log.info("================================================================");
        log.info(" fastRegret=true : mean cost={} | mean elapsed={} ms",
                String.format(Locale.US, "%.2f", meanFastCost), String.format(Locale.US, "%.0f", meanFastElapsed));
        log.info(" fastRegret=false: mean cost={} | mean elapsed={} ms",
                String.format(Locale.US, "%.2f", meanSlowCost), String.format(Locale.US, "%.0f", meanSlowElapsed));
        log.info(" Chênh lệch cost trung bình: {}% (dương = fastRegret=true tệ hơn)",
                String.format(Locale.US, "%.2f", costDiffPercent));
        log.info(" Tốc độ: fastRegret=true nhanh hơn {}x", String.format(Locale.US, "%.2f", speedupFactor));
        log.info("================================================================");
        log.info(" ⚠️  Nếu stddev của mỗi cấu hình (xem thống kê ở trên) LỚN so với");
        log.info("    chênh lệch mean giữa 2 cấu hình, kết luận 'A tốt hơn B' chưa");
        log.info("    đủ vững — hai phân phối có thể overlap đáng kể. Xem thêm CSV.");
        log.info("================================================================");
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stdDev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / values.length);
    }

    private static double min(double[] values) {
        double m = Double.MAX_VALUE;
        for (double v : values) m = Math.min(m, v);
        return m;
    }

    private static double max(double[] values) {
        double m = -Double.MAX_VALUE;
        for (double v : values) m = Math.max(m, v);
        return m;
    }

    private static double[] toDoubleArray(long[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }

    private static double[] toDoubleArray(int[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }

    // ==================== DATA HOLDER ====================

    private static class BenchmarkResult {
        final String configLabel;
        final int runId;
        final Jsprit.Construction construction;
        final boolean fastRegret;
        final int threads;
        final long elapsedMillis;
        final double jspritCost;
        final int vehiclesUsed;
        final int unassignedCount;

        BenchmarkResult(String configLabel, int runId, Jsprit.Construction construction, boolean fastRegret,
                        int threads, long elapsedMillis, double jspritCost,
                        int vehiclesUsed, int unassignedCount) {
            this.configLabel = configLabel;
            this.runId = runId;
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