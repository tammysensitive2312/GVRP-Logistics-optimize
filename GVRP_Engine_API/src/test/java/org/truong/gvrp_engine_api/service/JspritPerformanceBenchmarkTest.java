package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
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
 * BENCHMARK — không phải correctness test.
 * <p>
 * Mục tiêu: đo thời gian solve (KHÔNG tính thời gian build distance matrix,
 * vì đó chỉ tính 1 lần/job, không phải biến số đang khảo sát) theo ma trận
 * cấu hình:
 * <p>
 * - FAST_REGRET:      true / false
 * - THREADS:          1 / 2 / 4
 * - CONSTRUCTION:     BEST_INSERTION / REGRET_INSERTION
 * <p>
 * => 2 x 3 x 2 = 12 tổ hợp, mỗi tổ hợp chạy trên cùng 1 bộ dữ liệu
 * (cùng seed random) để đảm bảo so sánh công bằng.
 * <p>
 * QUAN TRỌNG:
 * - Test này @Disabled mặc định vì tốn thời gian thật (gọi GraphHopper +
 *   Jsprit 12 lần liên tiếp). Bỏ @Disabled khi muốn chạy benchmark thủ công.
 * - Cần GraphHopper đã import/load xong (graphhopper.osm.file trỏ đúng file
 *   .osm.pbf như trong application.properties). Lần chạy đầu sẽ chậm hơn do
 *   GraphHopper build graph — hãy chạy 1 lần "làm nóng" trước khi đo, hoặc
 *   đảm bảo .cache/routing-graph đã tồn tại từ lần chạy trước.
 * - maxIterations CỐ ĐỊNH và KHÔNG set timeout, để đo đúng chi phí/iteration
 *   thay vì bị cắt ngang giữa chừng (áp dụng đúng bài học từ Timeout bug đã
 *   fix trước đó — nếu vô tình bật timeout, kết quả benchmark sẽ vô nghĩa
 *   vì các cấu hình có thể bị cắt ở các iteration khác nhau).
 * - Kết quả in ra dạng bảng CSV-like trong log để dễ paste vào Excel/Sheets.
 */
@SpringBootTest
public class JspritPerformanceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(JspritPerformanceBenchmarkTest.class);

    // ==================== CẤU HÌNH BENCHMARK ====================

    /** Số orders giả lập. Đổi giá trị này để benchmark ở các quy mô khác nhau. */
    private static final int NUM_ORDERS = 1000;

    /** Số vehicles giả lập. */
    private static final int NUM_VEHICLES = 5;

    /** Số iterations CỐ ĐỊNH cho mọi tổ hợp — không set timeout. */
    private static final int MAX_ITERATIONS = 50  ;

    /** Seed cố định để mọi cấu hình chạy trên CÙNG một bộ dữ liệu. */
    private static final long RANDOM_SEED = 42L;

    /** Tâm khu vực sinh tọa độ giả lập — quanh Hà Nội. */
    private static final double CENTER_LAT = 21.0285;
    private static final double CENTER_LON = 105.8542;

    /** Bán kính lan tỏa tọa độ giả lập, tính theo độ (~ 0.05 độ ~ 5-6km). */
    private static final double SPREAD_DEGREES = 0.05;

    @Autowired
    private DistanceMatrixService distanceMatrixService;

    // Dữ liệu dùng chung cho mọi tổ hợp — build 1 lần duy nhất
    private static List<OptCoordinates> sharedCoordinates;
    private static double[][] sharedDistanceMatrix;
    private static double[][] sharedTimeMatrix;

    @BeforeAll
    static void logBenchmarkHeader() {
        log.info("================================================================");
        log.info(" JSPRIT PERFORMANCE BENCHMARK");
        log.info(" Orders={} | Vehicles={} | MaxIterations={} | Seed={}",
                NUM_ORDERS, NUM_VEHICLES, MAX_ITERATIONS, RANDOM_SEED);
        log.info("================================================================");
    }

    /**
     * Chuẩn bị distance matrix DÙNG CHUNG cho toàn bộ 12 tổ hợp.
     * Build 1 lần duy nhất bằng GraphHopper thật (giống production),
     * để loại trừ biến số "thời gian build matrix" ra khỏi phép so sánh.
     */
    private void ensureSharedMatrixBuilt() {
        if (sharedDistanceMatrix != null) {
            return; // đã build rồi, tái sử dụng
        }

        log.info("🗺️  Building shared distance matrix via REAL GraphHopper ({} locations)...",
                NUM_ORDERS + 1);

        Random random = new Random(RANDOM_SEED);
        List<OptCoordinates> coordinates = new ArrayList<>();

        // Depot tại tâm
        coordinates.add(new OptCoordinates(
                BigDecimal.valueOf(CENTER_LAT),
                BigDecimal.valueOf(CENTER_LON)
        ));

        // N orders random quanh tâm
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
//    @Disabled("Benchmark thủ công — bỏ @Disabled khi cần chạy đo hiệu năng. "
//            + "Thời gian chạy ước tính: 12 tổ hợp x vài chục giây - vài phút/tổ hợp.")
    void benchmarkAllConfigurations() {
        ensureSharedMatrixBuilt();

        List<BenchmarkResult> results = new ArrayList<>();

        boolean[] fastRegretOptions = {false, true};
        int[] threadOptions = {4};
        Jsprit.Construction[] constructionOptions = {
                Jsprit.Construction.BEST_INSERTION,
                Jsprit.Construction.REGRET_INSERTION
        };

        int totalRuns = fastRegretOptions.length * threadOptions.length * constructionOptions.length;
        int runIndex = 0;

        for (Jsprit.Construction construction : constructionOptions) {
            for (boolean fastRegret : fastRegretOptions) {

                // FAST_REGRET chỉ có ý nghĩa với REGRET_INSERTION — với BEST_INSERTION
                // tham số này không được Jsprit sử dụng, nên bỏ qua tổ hợp trùng lặp
                // để không tốn thời gian chạy 2 lần cùng một cấu hình thực chất.
                if (construction == Jsprit.Construction.BEST_INSERTION && fastRegret) {
                    log.info("⏭️  Bỏ qua tổ hợp trùng lặp: BEST_INSERTION không dùng FAST_REGRET");
                    continue;
                }

                for (int threads : threadOptions) {
                    runIndex++;
                    log.info("--- Run {}/{}: construction={}, fastRegret={}, threads={} ---",
                            runIndex, totalRuns, construction, fastRegret, threads);

                    BenchmarkResult result = runSingleConfiguration(construction, fastRegret, threads);
                    results.add(result);

                    log.info("    → {} ms | jspritCost={} | vehiclesUsed={} | unassigned={}",
                            result.elapsedMillis, result.jspritCost,
                            result.vehiclesUsed, result.unassignedCount);
                }
            }
        }

        printResultsTable(results);

        // Sanity check tối thiểu — không phải mục tiêu chính của benchmark,
        // chỉ đảm bảo không có cấu hình nào crash / trả về cost bất thường
        // (Jsprit trả Double.MAX_VALUE hoặc NaN nếu solution rỗng/lỗi).
        for (BenchmarkResult r : results) {
            assertTrue(Double.isFinite(r.jspritCost),
                    "Cấu hình " + r.label + " trả về cost không hữu hạn: " + r.jspritCost);
            assertFalse(r.jspritCost < 0,
                    "Cấu hình " + r.label + " trả về cost âm bất thường: " + r.jspritCost);
        }
    }

    /**
     * Chạy một cấu hình Jsprit cụ thể trên bộ dữ liệu chung, đo thời gian solve.
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

        long start = System.currentTimeMillis();
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        long elapsed = System.currentTimeMillis() - start;

        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        String label = String.format("%s | fastRegret=%s | threads=%d",
                construction, fastRegret, threads);

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
     * Build VRP đơn giản hóa — KHÔNG dùng GreenVRPCostCalculator/multi-objective
     * vì benchmark này chỉ khảo sát tốc độ solve thuần túy của Jsprit theo
     * construction/thread/fast-regret, không phải chất lượng multi-objective.
     * Dùng 1 loại vehicle đồng nhất, cost matrix thuần vật lý (mét).
     */
    private VehicleRoutingProblem buildVrp() {
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        // Xây dựng Location list khớp với index trong sharedDistanceMatrix
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < sharedCoordinates.size(); i++) {
            OptCoordinates coord = sharedCoordinates.get(i);
            String id = (i == 0) ? "depot-0" : "order-" + i;
            locations.add(Location.Builder.newInstance()
                    .setId(id)
                    .setCoordinate(Coordinate.newInstance(coord.lonDouble(), coord.latDouble()))
                    .build());
        }

        // Cost matrix — dùng lại matrix đã build 1 lần
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

        // Vehicle type đồng nhất, capacity dư dả để tránh nhiễu do infeasibility
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

        // Orders — demand nhỏ đồng nhất, không time window để tách biệt biến số
        // (mục tiêu benchmark là tốc độ construction/regret, không phải feasibility)
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

        results.stream()
                .min((a, b) -> Long.compare(a.elapsedMillis, b.elapsedMillis))
                .ifPresent(fastest -> log.info("🏆 NHANH NHẤT: {} ({} ms)", fastest.label, fastest.elapsedMillis));

        results.stream()
                .min((a, b) -> Double.compare(a.jspritCost, b.jspritCost))
                .ifPresent(best -> log.info("💰 COST THẤP NHẤT: {} (cost={})", best.label, best.jspritCost));
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