package org.truong.gvrp_engine_api.distance_matrix;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.truong.gvrp_engine_api.job.JobCancelledException;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

@Slf4j
@Service
public class DistanceMatrixService {

    private final DistanceProvider primaryProvider;

    /**
     * Pool RIÊNG cho matrix build — KHÔNG dùng common ForkJoinPool để tránh
     * giành luồng với request threads / CompletableFuture async của toàn app.
     */
    private final ForkJoinPool matrixPool;

    public DistanceMatrixService(
            @Qualifier(value = "graphHoperDistanceProvider") DistanceProvider primaryProvider,
            @Value("${gvrp.matrix.parallelism:0}") int configuredParallelism) {
        this.primaryProvider = primaryProvider;
        // 0 = tự chọn (số core - 1); đặt >0 trong config để ghim cho benchmark.
        int parallelism = configuredParallelism > 0
                ? configuredParallelism
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.matrixPool = new ForkJoinPool(parallelism);
        log.info("[Matrix] Khởi tạo pool riêng cho matrix build: parallelism={}", parallelism);
    }

    @PreDestroy
    void shutdownPool() {
        matrixPool.shutdown();
        log.info("[Matrix] Đã shutdown pool riêng của matrix build");
    }

    /** Overload giữ tương thích (không hỗ trợ hủy) — dùng cho test / lời gọi cũ. */
    public DistanceMatrix createDistanceMatrix(List<OptCoordinates> coordinates, MatrixMask mask) {
        return createDistanceMatrix(coordinates, mask, () -> false);
    }

    /**
     * Dựng ma trận khoảng cách/thời gian.
     * <p>
     * HAI NHÁNH LƯU TRỮ:
     * <ul>
     *   <li><b>BLOCK</b> (mask có cụm): chỉ cấp phát và chỉ duyệt những ô mà
     *       {@link MatrixMask#needed} trả true. Bộ nhớ O(N·S), vòng lặp O(N·S).</li>
     *   <li><b>DÀY</b> (mask null/đầy): 2 × double[n][n], có guard fail-loud.
     *       Bộ nhớ O(n²) — n = 50 010 cần 37 GiB, không khả thi trên máy local.</li>
     * </ul>
     * Bản cũ luôn cấp phát dày rồi ghi sentinel vào 99.66% ô: prune giảm số lần gọi
     * GraphHopper nhưng KHÔNG giảm một byte nào, và vòng lặp vẫn chạy đủ n² vòng.
     *
     * @param cancelled cờ hủy hợp tác — kiểm ở đầu mỗi hàng; nếu bật thì ném
     *                  JobCancelledException để thoát sớm (cho phép cancel job
     *                  ngay trong lúc dựng ma trận, vốn có thể mất nhiều phút).
     */
    public DistanceMatrix createDistanceMatrix(List<OptCoordinates> coordinates, MatrixMask mask,
                                               BooleanSupplier cancelled) {
        int n = coordinates.size();
        boolean blockLayout = mask != null && !mask.isFull() && mask.clusterByLoc() != null;

        return blockLayout
                ? buildBlock(coordinates, mask, cancelled, n)
                : buildDense(coordinates, cancelled, n);
    }

    // ==================== NHÁNH BLOCK — O(N·S) bộ nhớ và thời gian ====================

    private DistanceMatrix buildBlock(List<OptCoordinates> coordinates, MatrixMask mask,
                                      BooleanSupplier cancelled, int n) {
        long t0 = System.nanoTime();

        BlockDiagonalCostMatrix matrix = BlockDiagonalCostMatrix.allocate(mask.clusterByLoc());

        long stored = matrix.storedCells();
        long densePairs = (long) n * n;
        log.info("[Matrix] Bố cục BLOCK {}x{} | {} cụm, dải rộng {} | lưu {} / {} ô ({}% ô dày là rác) "
                        + "| {} thay vì {} (giảm {}×) | threads={}",
                n, n, matrix.clusterCount(), matrix.wideCount(),
                stored, densePairs,
                String.format("%.2f", 100.0 * matrix.savedFraction()),
                MatrixMemory.humanBytes(matrix.allocatedBytes()),
                MatrixMemory.humanBytes(MatrixMemory.denseBytes(n)),
                MatrixMemory.denseBytes(n) / Math.max(1, matrix.allocatedBytes()),
                matrixPool.getParallelism());

        LongAdder computed = new LongAdder();
        LongAdder failed = new LongAdder();
        AtomicInteger rowsDone = new AtomicInteger();
        int logEvery = Math.max(1, n / 10);

        matrixPool.submit(() ->
                IntStream.range(0, n).parallel().forEach(i -> {
                    if (cancelled.getAsBoolean()) {
                        throw new JobCancelledException("Job bị hủy trong lúc dựng ma trận (matrix build)");
                    }
                    // CHỈ duyệt các ô có chỗ lưu. Bản cũ duyệt cả n cột cho mỗi hàng
                    // (n=50 010 -> 2.5 tỉ vòng + 2.5 tỉ atomic increment bị tranh chấp).
                    int[] targets = matrix.targetsFor(i);
                    for (int j : targets) {
                        if (i == j) continue;
                        try {
                            DistanceMatrixEntry e = primaryProvider.fetch(coordinates.get(i), coordinates.get(j));
                            matrix.put(i, j, e.distanceMeters(), e.timeSeconds());
                            computed.increment();
                        } catch (Exception e) {
                            log.warn("[Matrix] Route {}->{} lỗi, điền SENTINEL (KHÔNG dùng ZERO để tránh route rác): {}",
                                    i, j, e.getMessage());
                            matrix.put(i, j, MatrixMask.PRUNED_METERS, MatrixMask.PRUNED_SECONDS);
                            failed.increment();
                        }
                    }
                    int done = rowsDone.incrementAndGet();
                    if (done % logEvery == 0 || done == n) {
                        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                        log.info("[Matrix] Tiến độ {}/{} hàng ({}%) | computed={} failed={} | {} ms",
                                done, n, 100 * done / n, computed.sum(), failed.sum(), elapsedMs);
                    }
                })
        ).join();

        logSummary(n, t0, computed.sum(), failed.sum(), densePairs - computed.sum() - failed.sum(),
                matrix);
        warnIfSuspicious(failed.sum(), computed.sum(), matrix.clusterCount());
        return new DistanceMatrix(coordinates, matrix);
    }

    // ==================== NHÁNH DÀY — chỉ cho job nhỏ / Pareto ====================

    private DistanceMatrix buildDense(List<OptCoordinates> coordinates,
                                      BooleanSupplier cancelled, int n) {
        long t0 = System.nanoTime();
        long densePairs = (long) n * n;

        log.info("[Matrix] Bố cục DÀY {}x{} = {} cặp | {} | threads={} "
                        + "| KHÔNG có mask cụm nên không thể lưu block",
                n, n, densePairs, MatrixMemory.humanBytes(MatrixMemory.denseBytes(n)),
                matrixPool.getParallelism());

        // Guard TRƯỚC khi cấp phát: thà chết ngay với con số rõ ràng hơn là GC-thrash 20 phút.
        DenseCostMatrix matrix = DenseCostMatrix.allocate(n);

        LongAdder computed = new LongAdder();
        LongAdder failed = new LongAdder();
        AtomicInteger rowsDone = new AtomicInteger();
        int logEvery = Math.max(1, n / 10);

        matrixPool.submit(() ->
                IntStream.range(0, n).parallel().forEach(i -> {
                    if (cancelled.getAsBoolean()) {
                        throw new JobCancelledException("Job bị hủy trong lúc dựng ma trận (matrix build)");
                    }
                    for (int j = 0; j < n; j++) {
                        if (i == j) {
                            matrix.put(i, j, 0.0, 0.0);
                        } else {
                            try {
                                DistanceMatrixEntry e = primaryProvider.fetch(coordinates.get(i), coordinates.get(j));
                                matrix.put(i, j, e.distanceMeters(), e.timeSeconds());
                                computed.increment();
                            } catch (Exception e) {
                                log.warn("[Matrix] Route {}->{} lỗi, điền SENTINEL (KHÔNG dùng ZERO để tránh route rác): {}",
                                        i, j, e.getMessage());
                                matrix.put(i, j, MatrixMask.PRUNED_METERS, MatrixMask.PRUNED_SECONDS);
                                failed.increment();
                            }
                        }
                    }
                    int done = rowsDone.incrementAndGet();
                    if (done % logEvery == 0 || done == n) {
                        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                        log.info("[Matrix] Tiến độ {}/{} hàng ({}%) | computed={} failed={} | {} ms",
                                done, n, 100 * done / n, computed.sum(), failed.sum(), elapsedMs);
                    }
                })
        ).join();

        logSummary(n, t0, computed.sum(), failed.sum(), 0, matrix);
        warnIfSuspicious(failed.sum(), computed.sum(), 0);
        return new DistanceMatrix(coordinates, matrix);
    }

    // ==================== LOG ====================

    private void logSummary(int n, long t0, long computed, long failed, long notStored,
                            CostMatrix matrix) {
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        double throughput = computed / Math.max(1e-3, elapsedMs / 1000.0);
        double msPerLocation = elapsedMs / (double) Math.max(1, n);

        // ms/location là hằng số đo được ~76-91 ms trên dải 10k-18k order (prep TUYẾN TÍNH).
        // In ra để mỗi job tự cập nhật hằng số, thay vì phải suy ngược từ mốc thời gian.
        log.info("[Matrix] XONG {} {}x{} trong {} ms | computed={} ({} route/s) | không-lưu={} | failed={} "
                        + "| {} | {} ms/location (hằng số chiếu quy mô)",
                matrix.layout(), n, n, elapsedMs, computed,
                String.format("%.0f", throughput), notStored, failed,
                MatrixMemory.humanBytes(matrix.allocatedBytes()),
                String.format("%.1f", msPerLocation));
    }

    private void warnIfSuspicious(long failed, long computed, int clusterCount) {
        if (failed > 0) {
            log.warn("[Matrix] Có {} cặp route lỗi ({}%) — kiểm tra tọa độ/map/subnetwork",
                    failed, String.format("%.2f", 100.0 * failed / Math.max(1, computed)));
        }
        if (clusterCount == 1) {
            log.warn("[Matrix] Chỉ có 1 cụm — block-diagonal suy biến về ma trận dày, "
                    + "không tiết kiệm được bộ nhớ. Kiểm tra CLUSTER_TARGET_SIZE và VehicleClusterAssigner.");
        }
    }
}
