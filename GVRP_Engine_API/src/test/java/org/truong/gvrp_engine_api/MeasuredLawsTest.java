package org.truong.gvrp_engine_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import org.truong.gvrp_engine_api.distance_matrix.MatrixMemory;
import org.truong.gvrp_engine_api.model.ObjectivePreset;
import org.truong.gvrp_engine_api.model.VehicleType;
import org.truong.gvrp_engine_api.service.GreenVRPCostCalculator;
import org.truong.gvrp_engine_api.utils.AppConstant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NGHIỆM THU MỌI CON SỐ ĐANG ĐƯỢC KHẲNG ĐỊNH TRONG CLAUDE.md.
 * <p>
 * <b>Mục đích khác với test thông thường.</b> Test bình thường bắt lỗi CODE. Test này bắt
 * lỗi <b>PHÁT BIỂU</b> — cụ thể là bắt Claude (hoặc bất kỳ ai) viết một công thức/hằng số
 * sai vào tài liệu rồi cả nhóm xây quyết định lên trên nó.
 * <p>
 * Lý do tồn tại: trong phiên 2026-07-28, công thức runtime từng được viết là
 * {@code T ≈ 3.94e-8·N·R} — <b>sai 1000×</b>. Cắm số job #30 vào là lộ ngay (3.5 s so với
 * 3 419.6 s đo được), nhưng không ai cắm. Test này cắm hộ, mỗi lần {@code mvn test}.
 * <p>
 * <b>Cách dùng:</b> bạn KHÔNG cần hiểu các công thức. Chỉ cần biết test đỏ nghĩa là một
 * con số trong CLAUDE.md không còn đúng, và thông báo lỗi nói rõ phải sửa ở đâu.
 * <p>
 * <b>GIỚI HẠN — đọc kỹ, đừng tin quá mức.</b> Phần lớn test ở đây làm số học trên
 * {@link #JOBS}, tức một hằng số CHÉP TAY từ log. Chúng khoá <i>tài liệu</i>, KHÔNG khoá
 * <i>hành vi engine</i>: sửa code solver thế nào chúng cũng không đỏ. Chỉ những test dưới
 * đây thực sự chạm code production và do đó bắt được hồi quy thật:
 * <ul>
 *   <li>{@link #memoryCeilingTableMatchesCode}, {@link #denseMemoryTableMatchesCode},
 *       {@link #job33ImpliesHeapLowerBound}, {@link #blockLayoutNumbersMatchCode} → MatrixMemory</li>
 *   <li>{@link #breakEvenTableMatchesFormula}, {@link #weightAsymmetryIsStillPresent}
 *       → GreenVRPCostCalculator (gọi thật, KHÔNG copy công thức)</li>
 *   <li>{@link #presetsMatchDocumentation}, {@link #appConstantsMatchDocumentation},
 *       {@link #clusterTargetSizeIsFarBelowIntOverflowCeiling} → hằng số cấu hình</li>
 * </ul>
 * Hồi quy hành vi thuộc trách nhiệm của {@code BlockDiagonalCostMatrixTest} và các test
 * khác, không phải file này.
 * <p>
 * <b>Khi thêm job mới:</b> thêm một dòng vào {@link #JOBS} rồi chạy lại. Nếu job mới không
 * tuân luật, test đỏ — đó chính là tín hiệu cần biết, không phải thứ cần tắt.
 */
class MeasuredLawsTest {

    /**
     * Số liệu THÔ đọc trực tiếp từ log engine — đây là bản ghi gốc, không phải giá trị
     * đã qua xử lý. Mọi luật bên dưới phải suy ra được từ đúng những con số này.
     *
     * @param jspritSeconds thời gian pha search do jsprit tự in ("took X seconds")
     * @param prepSeconds   wall time TRỪ pha search (prepareContext + K-means + matrix);
     *                      job 30/31/32 là giá trị suy ra sau khi bù giờ bị
     *                      {@code toMinutesPart()} ăn mất, job 33 đo trực tiếp từ dòng
     *                      "Starting optimization" nên là số CHẮC CHẮN nhất.
     */
    record JobLog(
            int id, int orders, int vehicles, int fleetCap,
            double jspritSeconds, double prepSeconds,
            double distanceKm, double timeHours,
            long fuelCostVnd, long timeCostVnd, long fixedCostVnd,
            double co2Kg, long co2CostVnd, long totalCostVnd,
            double loadUtilPct, double timeUtilPct
    ) {
    }

    private static final List<JobLog> JOBS = List.of(
            new JobLog(30, 12_054, 7_280, 10_000, 3_419.576, 934,
                    112_917.24455917809, 29_632.12611111124,
                    903_337_956L, 148_160_630L, 728_000_000L,
                    20_325.10402065216, 2_032_510_402L, 1_779_498_587L, 3.4, 40.7),
            new JobLog(31, 12_054, 4_813, 5_000, 2_292.859, 1_102,
                    78_673.93682991523, 20_545.38944444441,
                    629_391_494L, 102_726_947L, 481_300_000L,
                    14_161.308629384728, 1_416_130_862L, 1_213_418_441L, 5.2, 42.7),
            new JobLog(32, 10_054, 2_890, 5_000, 1_150.343, 807,
                    50_644.00221231669, 13_434.700555555564,
                    405_152_017L, 67_173_502L, 289_000_000L,
                    9_115.92039821703, 911_592_039L, 761_325_520L, 7.1, 46.5),
            new JobLog(33, 18_054, 2_946, 5_000, 2_870.448, 1_382,
                    47_870.14992515209, 16_491.324444444406,
                    382_961_199L, 82_456_622L, 294_600_000L,
                    8_616.626986527372, 861_662_698L, 760_017_821L, 12.6, 56.0)
    );

    private static JobLog job(int id) {
        return JOBS.stream().filter(j -> j.id() == id).findFirst().orElseThrow();
    }

    // ======================================================================
    // 1. THAM SỐ SUY NGƯỢC — phải ra SỐ TRÒN khớp DB. Bốn phép chia độc lập
    //    cùng ra số tròn thì gần như không thể là trùng hợp.
    // ======================================================================

    @Test
    @DisplayName("Suy ngược tham số Truck 5T từ log: 8000 VND/km, 5000 VND/h, fixed 100k, maxDuration 10h")
    void derivedVehicleParamsAreRoundNumbers() {
        for (JobLog j : JOBS) {
            assertEquals(8_000.0, j.fuelCostVnd() / j.distanceKm(), 0.01,
                    "job " + j.id() + ": cost_per_km suy ra phải là 8000 (giá trị DB của Truck 5T)");
            assertEquals(5_000.0, j.timeCostVnd() / j.timeHours(), 0.01,
                    "job " + j.id() + ": cost_per_hour suy ra phải là 5000");

            assertEquals(0, j.fixedCostVnd() % j.vehicles(),
                    "job " + j.id() + ": fixedCost phải chia HẾT cho số xe (mỗi xe một lần fixed)");
            assertEquals(100_000L, j.fixedCostVnd() / j.vehicles(),
                    "job " + j.id() + ": fixed_cost/xe suy ra phải là 100 000");

            // maxDuration = (giờ mỗi xe) / (time util). timeUtil trong log chỉ có 1 chữ số
            // thập phân nên sai số ~0.003 là do làm tròn của log, không phải do công thức.
            double maxDuration = (j.timeHours() / j.vehicles()) / (j.timeUtilPct() / 100.0);
            assertEquals(10.0, maxDuration, 0.005,
                    "job " + j.id() + ": maxDuration suy ra phải là 10h — nếu lệch, "
                            + "nghi lại lệch đơn vị phút/giờ như bug cũ");
        }
    }

    @Test
    @DisplayName("Cost báo cáo = fuel + time + fixed, KHÔNG bao gồm CO2 cost")
    void reportedCostExcludesCo2() {
        for (JobLog j : JOBS) {
            long sum = j.fuelCostVnd() + j.timeCostVnd() + j.fixedCostVnd();
            // KHÔNG dùng assertEquals(long, long, 2, msg): JUnit không có overload
            // (long,long,long,String) nên nó rơi vào (float,float,float,String). Ở mốc
            // 1.78e9 thì ULP của float là 128, tức delta 2 trở thành vô nghĩa và thông
            // báo lỗi sẽ in ra 128 thay vì 1. So sánh nguyên trực tiếp.
            assertTrue(Math.abs(sum - j.totalCostVnd()) <= 2, String.format(
                    "job %d: tổng cost phải bằng đúng 3 thành phần, lệch %d VND "
                            + "(cho phép ≤2 do (long) cast từng phần)",
                    j.id(), sum - j.totalCostVnd()));

            // CO2 cost LỚN HƠN tổng cost mà lại không nằm trong đó — khiếm khuyết đã biết,
            // ghi lại thành assertion để không ai tưởng nó đã được cộng vào.
            assertTrue(j.co2CostVnd() > j.totalCostVnd(),
                    "job " + j.id() + ": CO2 cost đang lớn hơn total cost — nếu điều này "
                            + "không còn đúng thì mô hình đã đổi, cập nhật CLAUDE.md");
        }
    }

    @Test
    @DisplayName("CO2 cộng tuyến HOÀN HẢO với distance (0.18 kg/km) — Pareto front là một ĐIỂM")
    void co2IsPerfectlyCollinearWithDistance() {
        for (JobLog j : JOBS) {
            assertEquals(0.18, j.co2Kg() / j.distanceKm(), 1e-5,
                    "job " + j.id() + ": CO2/km phải là 0.180 = emissionFactor 180 g/km. "
                            + "180.0 là hardcode VehicleFeaturesDTO.defaultFeatures() (PETROL_CAR), "
                            + "KHÔNG phải giá trị DB (12.3) hay DIESEL_TRUCK (280) — bug đã biết.");
            assertEquals(AppConstant.CARBON_PRICE_PER_KG, j.co2CostVnd() / j.co2Kg(), 1.0,
                    "job " + j.id() + ": giá carbon suy ra phải khớp AppConstant");
        }

        // Cộng tuyến tuyệt đối: tỉ lệ CO2/distance GIỐNG NHAU ở mọi job, dù fleet, mật độ
        // và số order khác nhau hoàn toàn ⇒ CO2 không mang thông tin gì ngoài distance.
        double first = JOBS.get(0).co2Kg() / JOBS.get(0).distanceKm();
        for (JobLog j : JOBS) {
            assertEquals(first, j.co2Kg() / j.distanceKm(), 1e-9,
                    "job " + j.id() + ": nếu tỉ lệ này bắt đầu KHÁC nhau giữa các job thì "
                            + "mô hình CO2 đã phụ thuộc tải/tốc độ — tin tốt, hãy cập nhật CLAUDE.md");
        }
    }

    // ======================================================================
    // 2. LUẬT RUNTIME — đây là chỗ đã từng sai 1000×
    // ======================================================================

    /** Hằng số trong CLAUDE.md: T ≈ 3.94e-5 · N · R giây (2000 iters, 1 thread). */
    private static final double RUNTIME_K = 3.94e-5;

    @Test
    @DisplayName("T ≈ 3.94e-5·N·R tái tạo job 30/31/32 trong sai số 1.5%")
    void runtimeLawReproducesControlledJobs() {
        for (int id : new int[]{30, 31, 32}) {
            JobLog j = job(id);
            double predicted = RUNTIME_K * j.orders() * j.vehicles();
            double errPct = 100.0 * (predicted - j.jspritSeconds()) / j.jspritSeconds();
            assertTrue(Math.abs(errPct) < 1.5, String.format(
                    "job %d: luật dự đoán %.1f s, đo được %.1f s -> lệch %+.2f%% (giới hạn 1.5%%)",
                    id, predicted, j.jspritSeconds(), errPct));
        }
    }

    @Test
    @DisplayName("Job 33 lệch +37% và CHƯA giải thích được — hằng số 3.94e-5 chỉ là CẬN DƯỚI")
    void job33AnomalyIsStillOpen() {
        JobLog j = job(33);
        double k33 = j.jspritSeconds() / ((double) j.orders() * j.vehicles());
        double excessPct = 100.0 * (k33 - RUNTIME_K) / RUNTIME_K;

        assertTrue(excessPct > 30.0, String.format(
                "Job 33 lẽ ra lệch >30%%, thực tế %+.1f%%. Nếu con số này ĐÃ GIẢM thì nguyên nhân "
                        + "đã bị vô tình sửa — tìm ra vì sao rồi cập nhật CLAUDE.md, đừng bỏ qua.",
                excessPct));
    }

    @Test
    @DisplayName("Độ TÁN của K giữa job 30/31/32 là 1.6% (con số CLAUDE.md trích) — khác với dung sai 1.5%")
    void runtimeConstantSpreadIsOnePointSixPercent() {
        double min = Double.MAX_VALUE, max = 0;
        for (int id : new int[]{30, 31, 32}) {
            JobLog j = job(id);
            double k = j.jspritSeconds() / ((double) j.orders() * j.vehicles());
            min = Math.min(min, k);
            max = Math.max(max, k);
        }
        double spreadPct = 100.0 * (max / min - 1.0);
        assertEquals(1.6, spreadPct, 0.15, String.format(
                "độ tán (max/min − 1) = %.3f%%. LƯU Ý: đây KHÁC con số 1.5%% ở "
                        + "runtimeLawReproducesControlledJobs — cái đó là độ lệch so với K cố định "
                        + "(max thật 1.11%%). Đừng lẫn hai đại lượng.", spreadPct));
    }

    /**
     * TÀI LIỆU HOÁ lỗi 1000×, không phải phép kiểm độc lập.
     * <p>
     * Reviewer đã chỉ ra đúng: tỉ số này bị {@link #runtimeLawReproducesControlledJobs}
     * bao hàm về mặt đại số ($\text{ratio} = 1000/(1+err)$ với $|err| < 1.5\%$ ⇒ ratio luôn
     * thuộc [985, 1015]). Giữ lại vì giá trị của nó là *lời nhắc*: đây chính xác là phép
     * thay số ngược lẽ ra phải làm trước khi công bố công thức. Cửa sổ đặt sát để không
     * giả vờ chặt hơn thực tế.
     */
    @Test
    @DisplayName("TÀI LIỆU: hằng số 3.94e-8 (lỗi cũ) dự đoán 3.5 s so với 3 419.6 s đo được")
    void theThousandFoldMistakeStaysDocumented() {
        JobLog j = job(30);
        double wrong = 3.94e-8 * j.orders() * j.vehicles();
        double ratio = j.jspritSeconds() / wrong;

        assertEquals(989.0, ratio, 5.0, String.format(
                "Số mũ sai lệch %.1f× — dự đoán %.2f s so với %.1f s đo được. "
                        + "Bậc của K phải là 1e-5: N·R ~ 1e8 và T ~ 1e3 s ⇒ K ~ 1e-5.",
                ratio, wrong, j.jspritSeconds()));
    }

    @Test
    @DisplayName("Kiểm chứng phụ: T30/T31 ≈ R30/R31 (runtime tuyến tính theo số route)")
    void runtimeIsLinearInRouteCount() {
        JobLog a = job(30), b = job(31);
        assertEquals(a.orders(), b.orders(),
                "phép kiểm này chỉ hợp lệ khi hai job cùng số order");

        double timeRatio = a.jspritSeconds() / b.jspritSeconds();
        double routeRatio = a.vehicles() / (double) b.vehicles();
        assertEquals(routeRatio, timeRatio, 0.05, String.format(
                "T ratio %.3f phải xấp xỉ R ratio %.3f — đây là bằng chứng độc lập cho "
                        + "dạng N·R, tính bằng cách khác với hằng số K", timeRatio, routeRatio));
    }

    // ======================================================================
    // 3. LUẬT PREP — tuyến tính
    // ======================================================================

    @Test
    @DisplayName("Prep ≈ 81 ms/order, TUYẾN TÍNH trên dải 10k–18k (mọi job trong 70–95 ms)")
    void prepIsLinearInOrderCount() {
        for (JobLog j : JOBS) {
            double msPerOrder = j.prepSeconds() / j.orders() * 1000.0;
            assertTrue(msPerOrder > 70 && msPerOrder < 95, String.format(
                    "job %d: prep %.1f ms/order, ngoài dải 70–95. Nếu con số này TĂNG theo N "
                            + "thì prep đã thành bậc hai và chiếu 50k (~4070 s) không còn đúng.",
                    j.id(), msPerOrder));
        }
    }

    @Test
    @DisplayName("Chiếu 50k: prep ~1.13 h — không phải nút thắt")
    void prepProjectionFor50k() {
        double avgMsPerOrder = JOBS.stream()
                .mapToDouble(j -> j.prepSeconds() / j.orders() * 1000.0)
                .average().orElseThrow();
        double projectedHours = avgMsPerOrder / 1000.0 * 50_000 / 3600.0;
        assertEquals(1.13, projectedHours, 0.10, String.format(
                "prep(50k) chiếu ra %.2f h (từ %.1f ms/order)", projectedHours, avgMsPerOrder));
    }

    // ======================================================================
    // 4. TRẦN BỘ NHỚ — công thức thật trong MatrixMemory, không phải số chép tay
    // ======================================================================

    @Test
    @DisplayName("Bảng n_max theo -Xmx trong CLAUDE.md khớp MatrixMemory.maxDenseSize()")
    void memoryCeilingTableMatchesCode() {
        long GiB = 1L << 30;
        assertEquals(17_947, MatrixMemory.maxDenseSize(8 * GiB));
        assertEquals(21_981, MatrixMemory.maxDenseSize(12 * GiB));
        assertEquals(25_381, MatrixMemory.maxDenseSize(16 * GiB));
        assertEquals(50_763, MatrixMemory.maxDenseSize(64 * GiB));
    }

    @Test
    @DisplayName("Bảng bộ nhớ dày trong CLAUDE.md: 1.51 / 4.86 / 37.27 GiB")
    void denseMemoryTableMatchesCode() {
        double GiB = 1L << 30;
        assertEquals(1.51, MatrixMemory.denseBytes(10_064) / GiB, 0.01);
        assertEquals(4.86, MatrixMemory.denseBytes(18_064) / GiB, 0.01);
        assertEquals(37.27, MatrixMemory.denseBytes(50_010) / GiB, 0.01);
    }

    @Test
    @DisplayName("Job 33 chạy được ở n=18 064 ⇒ heap thực ≥ 8.11 GiB (BẤT ĐẲNG THỨC, không phải 12 GiB)")
    void job33ImpliesHeapLowerBound() {
        int n = job(33).orders() + 10;

        // Suy ngược: job này KHÔNG treo ⇒ denseBytes(n) ≤ heap × 0.6 ⇒ heap ≥ denseBytes/0.6.
        // Đây là CẬN DƯỚI. Mọi heap từ ~8.2 GiB trở lên đều thoả — 12 GiB không phải sàn.
        double impliedMinHeapGiB = MatrixMemory.denseBytes(n)
                / MatrixMemory.HEAP_SAFETY_FRACTION / (double) (1L << 30);
        assertEquals(8.10, impliedMinHeapGiB, 0.05, String.format(
                "heap tối thiểu suy ra = %.3f GiB", impliedMinHeapGiB));

        assertTrue(MatrixMemory.maxDenseSize(8L << 30) < n,
                "heap đúng 8g lẽ ra KHÔNG đủ cho n=" + n + " (trần 17 947)");
        assertTrue(MatrixMemory.maxDenseSize(12L << 30) > n,
                "heap 12g phải đủ cho n=" + n);

        // n của job 50k vượt trần cả ở heap 32g — đây là lý do phải dùng block-diagonal.
        assertTrue(MatrixMemory.maxDenseSize(32L << 30) < 50_010,
                "n=50 010 phải vượt trần ngay cả ở heap 32g");
    }

    @Test
    @DisplayName("Bố cục BLOCK ở quy mô 50k: 135.8 MB, giảm 294×, trần mới ~2.1 triệu order")
    void blockLayoutNumbersMatchCode() {
        int depots = 10, orders = 50_000, s = AppConstant.CLUSTER_TARGET_SIZE;
        int n = orders + depots;

        int c = (int) Math.ceil(orders / (double) s);
        int[] sizes = new int[c];
        for (int k = 0; k < orders; k++) sizes[k % c]++;

        long blockBytes = MatrixMemory.blockBytes(n, sizes, depots);
        long denseBytes = MatrixMemory.denseBytes(n);

        assertEquals(135.8, blockBytes / 1e6, 0.3,
                "CLAUDE.md ghi 135.8 MB cho N=50 000, S=" + s);
        assertEquals(294, denseBytes / blockBytes, 2,
                "CLAUDE.md ghi giảm 294×");
        assertEquals(99.66, 100.0 * (1.0 - MatrixMemory.blockCells(n, sizes, depots) / ((double) n * n)),
                0.02, "CLAUDE.md ghi 99.66% ô của ma trận dày là rác");

        // Trần mới: N_max = 0.6·H / (16·S). CLAUDE.md ghi ~2.1 triệu order ở heap 8 GiB.
        double nMaxMillions = MatrixMemory.HEAP_SAFETY_FRACTION * (8L << 30)
                / (16.0 * s) / 1e6;
        assertEquals(2.1, nMaxMillions, 0.1, String.format(
                "trần block ở heap 8 GiB = %.3f triệu order", nMaxMillions));
    }

    @Test
    @DisplayName("Chiếu runtime 50k trong CLAUDE.md: 4.38 h (R=8000) và 1.64 h (R=3000)")
    void runtimeProjectionsFor50k() {
        assertEquals(4.38, RUNTIME_K * 50_000 * 8_000 / 3_600.0, 0.02, "R=8000, 1 thread");
        assertEquals(1.64, RUNTIME_K * 50_000 * 3_000 / 3_600.0, 0.02, "R=3000, 1 thread");
    }

    // ======================================================================
    // 5. HÀM MỤC TIÊU & BẢNG BREAK-EVEN
    // ======================================================================

    /**
     * `Truck 5T` đúng như DB — chính vehicle type của cả 4 job.
     * `emissionFactor = 180` là giá trị THỰC SỰ tới engine (hardcode
     * `VehicleFeaturesDTO.defaultFeatures()` phía Entry, không phải default 200 của engine).
     */
    private static VehicleType truck5T() {
        VehicleType t = new VehicleType();
        t.setId(1002L);
        t.setTypeName("Truck 5T");
        t.setCapacity(5_000);
        t.setFixedCost(100_000.0);
        t.setCostPerKm(8_000.0);
        t.setCostPerHour(5_000.0);
        t.setMaxDistance(500.0);
        t.setMaxDuration(10.0);
        t.setEmissionFactor(180.0);
        return t;
    }

    /**
     * Break-even detour để mở một xe mới, tính từ CHÍNH code production
     * ({@code GreenVRPCostCalculator.buildGreenVehicleType} + {@code normalizeWeights}),
     * KHÔNG phải từ bản copy công thức.
     * <p>
     * Lý do quan trọng: nếu ai vá khiếm khuyết "weights nhân bất đối xứng" trong
     * `buildGreenVehicleType`, một bản copy sẽ vẫn xanh và test mất hết ý nghĩa. Đọc qua
     * `getVehicleCostParams()` — chữ ký đã tra từ source jsprit thật (`VehicleCostParams`
     * có field public final `fix`, `perDistanceUnit`, `perTransportTimeUnit`;
     * `VehicleTypeImpl` KHÔNG có `getFixedCost()`).
     */
    private static double breakEvenMeters(double costWeight, double co2Weight) {
        double[] w = GreenVRPCostCalculator.normalizeWeights(costWeight, co2Weight);
        VehicleTypeImpl.VehicleCostParams p = GreenVRPCostCalculator
                .buildGreenVehicleType(truck5T(), w[0], w[1])
                .getVehicleCostParams();
        return p.fix / p.perDistanceUnit;
    }

    @Test
    @DisplayName("Bảng break-even detour trong CLAUDE.md: 12500 / 6364 / 3846 / 0.556 m — tính từ code thật")
    void breakEvenTableMatchesFormula() {
        // Weight lấy từ chính ObjectivePreset, không phải literal — đổi preset thì test theo.
        assertEquals(12_500, breakEvenMeters(
                        ObjectivePreset.COST_FOCUSED.costWeight, ObjectivePreset.COST_FOCUSED.co2Weight), 1,
                "COST_FOCUSED: mở xe mới rẻ hơn nếu detour > 12.5 km");
        assertEquals(6_364, breakEvenMeters(0.7, 0.3), 1,
                "weights MẶC ĐỊNH (0.7, 0.3) trong OptimizationConfig.getEffectiveWeights()");
        assertEquals(3_846, breakEvenMeters(
                        ObjectivePreset.BALANCED.costWeight, ObjectivePreset.BALANCED.co2Weight), 1,
                "BALANCED");
        // CLAUDE.md ghi "0.56 m"; giá trị chính xác 0.5555 m.
        assertEquals(0.556, breakEvenMeters(
                        ObjectivePreset.ECO_FOCUSED.costWeight, ObjectivePreset.ECO_FOCUSED.co2Weight), 0.005,
                "ECO_FOCUSED: solver mở xe mới để tiết kiệm nửa mét ⇒ nghiệm suy biến 1 đơn/xe");
    }

    @Test
    @DisplayName("Weights nhân BẤT ĐỐI XỨNG: đọc trực tiếp cost params mà buildGreenVehicleType sinh ra")
    void weightAsymmetryIsStillPresent() {
        double wc = 0.5, we = 0.5;
        VehicleTypeImpl.VehicleCostParams p = GreenVRPCostCalculator
                .buildGreenVehicleType(truck5T(), wc, we).getVehicleCostParams();

        // Đây là phát biểu TRỰC TIẾP về hành vi code, không qua tỉ số dẫn xuất nào.
        // fixed và time chỉ nhân costWeight:
        assertEquals(100_000.0 * wc, p.fix, 1e-9,
                "fixedCost chỉ nhân costWeight — nếu đã nhân cả hai weight thì khiếm khuyết "
                        + "bất đối xứng ĐÃ ĐƯỢC VÁ, hãy cập nhật CLAUDE.md và test này");
        assertEquals((5_000.0 / 3_600.0) * wc, p.perTransportTimeUnit, 1e-9,
                "timeCost chỉ nhân costWeight");

        // ...còn distance nhân CẢ HAI (fuel×w_c + co2×w_e):
        double fuelPerM = 8_000.0 / 1_000.0;
        double co2PerM = (180.0 / 1e6) * AppConstant.CARBON_PRICE_PER_KG;
        assertEquals(fuelPerM * wc + co2PerM * we, p.perDistanceUnit, 1e-9,
                "distance nhân cả costWeight và co2Weight — chính đây là chỗ bất đối xứng");

        // Hệ quả: đẩy co2Weight → 1 làm fixed bay hơi trong khi distance phình lên.
        double swing = breakEvenMeters(1.0, 0.0) / breakEvenMeters(
                ObjectivePreset.ECO_FOCUSED.costWeight, ObjectivePreset.ECO_FOCUSED.co2Weight);
        assertTrue(swing > 10_000, String.format(
                "break-even biến động %.0f× giữa COST_FOCUSED và ECO_FOCUSED (kỳ vọng >10 000×)",
                swing));
    }

    @Test
    @DisplayName("Preset trong code khớp bảng trong CLAUDE.md")
    void presetsMatchDocumentation() {
        assertEquals(1.0, ObjectivePreset.COST_FOCUSED.costWeight);
        assertEquals(0.0, ObjectivePreset.COST_FOCUSED.co2Weight);
        assertEquals(0.5, ObjectivePreset.BALANCED.costWeight);
        assertEquals(0.5, ObjectivePreset.BALANCED.co2Weight);
        assertEquals(1e-4, ObjectivePreset.ECO_FOCUSED.costWeight);
        assertEquals(1.0, ObjectivePreset.ECO_FOCUSED.co2Weight);
        // PURE_ECO.costWeight ĐƯỢC KHAI BẰNG chính AppConstant.EPSILON, nên so hai cái đó
        // là tautology. Assert giá trị số cụ thể mới bắt được thay đổi.
        assertEquals(0.0001, ObjectivePreset.PURE_ECO.costWeight,
                "PURE_ECO dùng EPSILON = 0.0001; đổi EPSILON thì break-even đổi theo");
        assertEquals(1.0, ObjectivePreset.PURE_ECO.co2Weight);
    }

    @Test
    @DisplayName("Job 31 DOMINATE job 30 trên cả 3 trục ⇒ job 30 bị bỏ dở, không phải kinh tế mô hình")
    void job31DominatesJob30() {
        JobLog a = job(30), b = job(31);
        assertEquals(a.orders(), b.orders(), "bắt buộc cùng bộ order mới so được");

        assertTrue(b.distanceKm() < a.distanceKm(), "distance");
        assertTrue(b.vehicles() < a.vehicles(), "vehicles");
        assertTrue(b.totalCostVnd() < a.totalCostVnd(), "cost");

        // Hàm mục tiêu solver thật (weights 0.7/0.3): 11.0·d_m + 0.9722·t_s + 70000·R
        double objA = a.distanceKm() * 1000 * 11.0 + a.timeHours() * 3600 * 0.9722 + a.vehicles() * 70_000.0;
        double objB = b.distanceKm() * 1000 * 11.0 + b.timeHours() * 3600 * 0.9722 + b.vehicles() * 70_000.0;
        double gapPct = 100.0 * (objA - objB) / objA;

        assertTrue(gapPct > 25, String.format(
                "job 30 lẽ ra tệ hơn >25%%, thực tế %.1f%%. Lời giải job 31 dùng ít xe hơn nên "
                        + "FEASIBLE trong instance job 30 ⇒ phần này là thất bại tìm kiếm "
                        + "(2000 iterations không đủ khi R≈7000), không phải tối ưu kinh tế.",
                gapPct));
    }

    @Test
    @DisplayName("Fleet cap job 31 SÁT TRẦN (96.26%) ⇒ nghiệm bị ép; job 30 (72.8%) thì không")
    void job31FleetCapIsBinding() {
        // fleetCap giờ là trường của JobLog, không còn hardcode literal.
        double usage31 = 100.0 * job(31).vehicles() / job(31).fleetCap();
        assertEquals(96.26, usage31, 0.05, String.format(
                "job 31 dùng %.2f%% cap %d — sát trần ⇒ cap đang BIND", usage31, job(31).fleetCap()));

        double usage30 = 100.0 * job(30).vehicles() / job(30).fleetCap();
        assertEquals(72.80, usage30, 0.05, String.format(
                "job 30 dùng %.2f%% cap %d ⇒ cap KHÔNG bind, nên nó tệ hơn KHÔNG phải "
                        + "vì bị giới hạn xe mà vì 2000 iterations không đủ",
                usage30, job(30).fleetCap()));
    }

    // ======================================================================
    // 6. CHỈ SỐ DỄ ĐỌC SAI
    // ======================================================================

    @Test
    @DisplayName("Load util = 2.05% × (orders/vehicle) ⇒ do MẬT ĐỘ ĐƠN, không phải chất lượng nghiệm")
    void loadUtilIsDeterminedByOrderDensityAlone() {
        for (JobLog j : JOBS) {
            double ordersPerVehicle = j.orders() / (double) j.vehicles();
            double perOrder = j.loadUtilPct() / ordersPerVehicle;
            assertEquals(2.05, perOrder, 0.05, String.format(
                    "job %d: load util / (đơn mỗi xe) = %.3f%%, phải là ~2.05%% ở mọi job. "
                            + "Hệ quả: load util KHÔNG đo chất lượng nghiệm, đừng báo cáo nó làm KPI chính.",
                    j.id(), perOrder));
        }
    }

    @Test
    @DisplayName("Vận tốc trung bình 2.90–3.83 km/h — chậm hơn người đi bộ")
    void averageSpeedIsImplausiblyLowForTrucks() {
        // Dải THẬT là 2.90–3.83. CLAUDE.md từng ghi "2.9–3.8" — cận trên đó SAI, job 30
        // (3.811) và job 31 (3.829) đều vượt 3.8. Ngưỡng đặt 2.8/3.9 để sát phổ thật.
        for (JobLog j : JOBS) {
            double kmh = j.distanceKm() / j.timeHours();
            assertTrue(kmh > 2.8 && kmh < 3.9, String.format(
                    "job %d: %.3f km/h, ngoài dải đo được 2.90–3.83", j.id(), kmh));
        }
        // KHÔNG assert "87–90% thời lượng là service + chờ": JobLog không có service time
        // nên con số đó KHÔNG derive được từ dữ liệu ở đây. Nó là suy luận định tính từ
        // vận tốc, và phải được trình bày như vậy — không phải như số đo.
    }

    @Test
    @DisplayName("Ràng buộc chặn là THỜI GIAN không phải TẢI: sàn capacity 250 xe vs sàn thời gian 2055 xe")
    void bindingConstraintIsTimeNotCapacity() {
        JobLog j = job(31);

        // capacity triệt tiêu trong phép chia (totalDemand/capacity với
        // totalDemand = R·loadUtil·capacity), nên sàn tải ĐƠN GIẢN là "số xe đầy tương đương".
        // Viết thẳng ra để không ai tưởng assertion này đang kiểm giá trị capacity.
        double capacityFloor = j.vehicles() * (j.loadUtilPct() / 100.0);
        double timeFloor = j.timeHours() / 10.0;   // maxDuration = 10 h

        assertEquals(250, capacityFloor, 5,
                "sàn theo capacity = số xe đầy tương đương (CLAUDE.md ghi 250)");
        assertEquals(2_055, timeFloor, 10, "sàn theo thời gian");
        // Tỉ lệ thực đo là 8.21×; ngưỡng đặt ở 7× để không đỏ vì làm tròn 1 chữ số
        // thập phân của loadUtil trong log.
        assertTrue(timeFloor > 7 * capacityFloor, String.format(
                "sàn thời gian (%.0f xe) phải cao hơn sàn tải (%.0f xe) nhiều bậc — thực tế %.2f× "
                        + "⇒ tối ưu theo tải không giúp gì, đừng báo cáo load util làm KPI chính",
                timeFloor, capacityFloor, timeFloor / capacityFloor));
    }

    // ======================================================================
    // 7. HẰNG SỐ CẤU HÌNH ĐANG ĐƯỢC TÀI LIỆU TRÍCH DẪN
    // ======================================================================

    @Test
    @DisplayName("Hằng số trong AppConstant khớp giá trị CLAUDE.md đang trích")
    void appConstantsMatchDocumentation() {
        assertEquals(150, AppConstant.CLUSTER_TARGET_SIZE,
                "CLAUDE.md trích S=150 khắp nơi (bảng bộ nhớ block, tỉ lệ prune, chiếu 50k). "
                        + "Đổi giá trị này thì phải cập nhật toàn bộ những con số đó.");
        assertEquals(1_000, AppConstant.CLUSTER_FIRST_ORDER_THRESHOLD);
        assertEquals(10, AppConstant.DEMAND_SCALE);
        assertEquals(100_000.0, AppConstant.CARBON_PRICE_PER_KG,
                "SỐ GIẢ do người dùng đặt tạm. Nếu bạn cố ý đổi sang giá thật, hãy cập nhật "
                        + "bảng break-even trong CLAUDE.md rồi sửa kỳ vọng ở test này — "
                        + "đừng chỉ tắt assertion.");
    }

    @Test
    @DisplayName("S_target=150 cách trần cụm 46 340 rất xa (309×) ⇒ ClusterMergeService gộp thoải mái")
    void clusterTargetSizeIsFarBelowIntOverflowCeiling() {
        // 46 340 = ⌊√Integer.MAX_VALUE⌋ là số học thuần (luôn đúng), nên KHÔNG assert nó.
        // Điều đáng kiểm là khoảng cách giữa cấu hình THẬT và cái trần đó.
        int ceiling = (int) Math.sqrt(Integer.MAX_VALUE);
        int margin = ceiling / AppConstant.CLUSTER_TARGET_SIZE;
        assertTrue(margin > 100, String.format(
                "S_target=%d cách trần %d chỉ %d× — quá gần, ClusterMergeService gộp mạnh "
                        + "là tràn offset int trong BlockDiagonalCostMatrix",
                AppConstant.CLUSTER_TARGET_SIZE, ceiling, margin));
    }
}
