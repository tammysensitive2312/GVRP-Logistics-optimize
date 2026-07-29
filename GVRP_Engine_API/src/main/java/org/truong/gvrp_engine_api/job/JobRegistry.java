package org.truong.gvrp_engine_api.job;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sổ đăng ký job đang chạy — trạng thái SỐNG, tạm thời trong bộ nhớ engine.
 * <p>
 * Là backbone cho hai tính năng: get-progress (poll) và cancel. Khác với DB bền
 * vững ở Entry API (cập nhật qua callback), registry này giữ dữ liệu real-time
 * (iteration hiện tại, best cost, cờ hủy) của tiến trình đang chạy tại engine.
 * <p>
 * Thread-safety: ConcurrentHashMap + AtomicReference/AtomicBoolean, không cần khóa.
 * Bộ nhớ: job đã kết thúc được evict theo TTL (lazy sweep khi register job mới).
 */
@Slf4j
@Component
public class JobRegistry {

    public enum Status {RUNNING, COMPLETED, FAILED, CANCELLED}

    public enum Phase {BUILDING_MATRIX, SOLVING}

    private static final Duration TERMINAL_TTL = Duration.ofMinutes(30);

    public record ProgressSnapshot(
            int iteration,
            int maxIterations,
            double bestCost,
            int routes,
            int unassigned,
            long elapsedSeconds,
            Phase phase,
            Instant updatedAt) {
    }

    @Setter
    public static final class JobHandle {
        private final long jobId;
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private final AtomicReference<ProgressSnapshot> snapshot = new AtomicReference<>();
        private volatile Status status = Status.RUNNING;
        private volatile Phase phase = Phase.BUILDING_MATRIX;
        private final Instant startedAt = Instant.now();
        private volatile Instant finishedAt;

        private JobHandle(long jobId) {
            this.jobId = jobId;
        }

        public long jobId() {
            return jobId;
        }

        public boolean isCancelRequested() {
            return cancelRequested.get();
        }

        public Status status() {
            return status;
        }

        public Phase phase() {
            return phase;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public Instant finishedAt() {
            return finishedAt;
        }

        public ProgressSnapshot snapshot() {
            return snapshot.get();
        }

        public void updateSnapshot(ProgressSnapshot s) {
            this.snapshot.set(s);
        }
    }

    private final Map<Long, JobHandle> jobs = new ConcurrentHashMap<>();

    public JobHandle register(long jobId) {
        evictExpired();
        JobHandle h = new JobHandle(jobId);
        jobs.put(jobId, h);
        log.info("[JobRegistry] Đăng ký job #{} (RUNNING)", jobId);
        return h;
    }

    public JobHandle get(long jobId) {
        return jobs.get(jobId);
    }

    /**
     * Yêu cầu hủy. Trả false nếu job không tồn tại hoặc đã kết thúc (idempotent).
     */
    public boolean requestCancel(long jobId) {
        JobHandle h = jobs.get(jobId);
        if (h == null || h.status != Status.RUNNING) {
            return false;
        }
        h.cancelRequested.set(true);
        log.info("[JobRegistry] Nhận yêu cầu hủy job #{}", jobId);
        return true;
    }

    /**
     * Đánh dấu trạng thái kết thúc + mốc thời gian (để TTL evict).
     */
    public void markTerminal(JobHandle h, Status terminal) {
        if (h == null) return;
        h.status = terminal;
        h.finishedAt = Instant.now();
        log.info("[JobRegistry] Job #{} -> {}", h.jobId, terminal);
    }

    /**
     * Xóa các job đã kết thúc quá TTL để bộ nhớ không phình.
     */
    private void evictExpired() {
        Instant now = Instant.now();
        jobs.entrySet().removeIf(e -> {
            Instant f = e.getValue().finishedAt;
            return f != null && Duration.between(f, now).compareTo(TERMINAL_TTL) > 0;
        });
    }
}
