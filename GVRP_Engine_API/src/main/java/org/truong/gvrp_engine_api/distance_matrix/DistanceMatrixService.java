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

    /**
     * Create distance matrix for all coordinates
     */
    /** Overload giữ tương thích (không hỗ trợ hủy) — dùng cho test / lời gọi cũ. */
    public DistanceMatrix createDistanceMatrix(List<OptCoordinates> coordinates, MatrixMask mask) {
        return createDistanceMatrix(coordinates, mask, () -> false);
    }

    /**
     * @param cancelled cờ hủy hợp tác — kiểm ở đầu mỗi hàng; nếu bật thì ném
     *                  JobCancelledException để thoát sớm (cho phép cancel job
     *                  ngay trong lúc dựng ma trận, vốn có thể mất nhiều phút).
     */
    public DistanceMatrix createDistanceMatrix(List<OptCoordinates> coordinates, MatrixMask mask,
                                               BooleanSupplier cancelled) {
        int n = coordinates.size();
        long totalPairs = (long) n * (n - 1);
        long t0 = System.nanoTime();

        // (1) LOG MỞ ĐẦU: quy mô + cấu hình đang chạy
        log.info("[Matrix] Bắt đầu dựng {}x{} = {} cặp | mask={} | threads={} (pool riêng)",
                n, n, totalPairs,
                (mask == null ? "OFF (full)" : "ON (cluster-block)"),
                matrixPool.getParallelism());

        double[][] dist = new double[n][n];           // primitive — không tạo object mỗi ô
        double[][] time = new double[n][n];
        AtomicInteger pruned   = new AtomicInteger(); // cặp bị bỏ qua theo mask
        AtomicInteger computed = new AtomicInteger(); // cặp gọi GraphHopper thật
        AtomicInteger failed   = new AtomicInteger(); // cặp route lỗi -> sentinel
        AtomicInteger rowsDone = new AtomicInteger(); // đếm hàng xong -> tính tiến độ
        int logEvery = Math.max(1, n / 10);           // log mỗi ~10% số hàng

        // Chạy stream song song TRONG pool riêng (submit + join), không phải common pool.
        matrixPool.submit(() ->
            IntStream.range(0, n).parallel().forEach(i -> {
                if (cancelled.getAsBoolean()) {
                    throw new JobCancelledException("Job bị hủy trong lúc dựng ma trận (matrix build)");
                }
                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        dist[i][j] = 0.0;
                        time[i][j] = 0.0;
                    } else if (mask != null && !mask.needed(i, j)) {
                        dist[i][j] = MatrixMask.PRUNED_METERS;   // sentinel double — KHÔNG tạo object
                        time[i][j] = MatrixMask.PRUNED_SECONDS;
                        pruned.incrementAndGet();
                    } else {
                        try {
                            DistanceMatrixEntry e = primaryProvider.fetch(coordinates.get(i), coordinates.get(j));
                            dist[i][j] = e.distanceMeters();
                            time[i][j] = e.timeSeconds();
                            computed.incrementAndGet();
                        } catch (Exception e) {
                            log.warn("[Matrix] Route {}->{} lỗi, điền SENTINEL (KHÔNG dùng ZERO để tránh route rác): {}",
                                    i, j, e.getMessage());
                            dist[i][j] = MatrixMask.PRUNED_METERS;
                            time[i][j] = MatrixMask.PRUNED_SECONDS;
                            failed.incrementAndGet();
                        }
                    }
                }
                // (2) LOG TIẾN ĐỘ: theo mốc %, kèm thời gian đã trôi
                int done = rowsDone.incrementAndGet();
                if (done % logEvery == 0 || done == n) {
                    long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                    log.info("[Matrix] Tiến độ {}/{} hàng ({}%) | computed={} pruned={} failed={} | {} ms",
                            done, n, 100 * done / n, computed.get(), pruned.get(), failed.get(), elapsedMs);
                }
            })
        ).join();

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long calls = computed.get();
        double throughput = calls / Math.max(1e-3, elapsedMs / 1000.0);

        // (3) LOG TỔNG KẾT: 1 dòng chốt hạ hiệu quả prune + tốc độ
        log.info("[Matrix] XONG {}x{} trong {} ms | computed={} ({} route/s) | pruned={} ({}%) | failed={}",
                n, n, elapsedMs, calls, String.format("%.0f", throughput),
                pruned.get(), 100 * pruned.get() / Math.max(1, totalPairs), failed.get());

        // (4) LOG CẢNH BÁO: bắt bất thường sớm, tránh ra route rác âm thầm
        if (failed.get() > 0) {
            log.warn("[Matrix] Có {} cặp route lỗi ({}%) — kiểm tra tọa độ/map/subnetwork",
                    failed.get(), String.format("%.2f", 100.0 * failed.get() / Math.max(1, calls)));
        }
        if (mask != null && pruned.get() == 0) {
            log.warn("[Matrix] mask BẬT nhưng prune=0 — cluster có thể chưa gán đúng, nên kiểm tra");
        }

        return new DistanceMatrix(coordinates, dist, time);
    }

}
