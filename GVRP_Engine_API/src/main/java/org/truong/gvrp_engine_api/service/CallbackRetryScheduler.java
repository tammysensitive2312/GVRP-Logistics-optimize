package org.truong.gvrp_engine_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Quét {@code .data/results/pending/} và gửi lại những callback chưa giao được.
 *
 * <p>Chạy được là nhờ {@code @EnableScheduling} trên lớp Application. Pool scheduler
 * lấy từ {@code spring.task.scheduling.pool.size} trong application.properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackRetryScheduler {

    private final ResultSpool spool;
    private final CallbackService callbackService;

    @Value("${gvrp.result-spool.max-attempts:20}")
    private int maxAttempts;

    @Scheduled(
            initialDelayString = "${gvrp.result-spool.retry.initial-delay-ms:60000}",
            fixedDelayString = "${gvrp.result-spool.retry.interval-ms:120000}")
    public void resendPending() {
        List<Long> pending = spool.listPending();
        if (pending.isEmpty()) {
            return;
        }
        log.info("🔁 {} callback đang chờ gửi lại: {}", pending.size(), pending);

        for (Long jobId : pending) {
            ResultSpool.Meta meta = spool.meta(jobId);

            if (meta.attempts() >= maxAttempts) {
                spool.markPoisoned(jobId,
                        "Vượt " + maxAttempts + " lần thử. Lỗi cuối: " + meta.lastError());
                continue;
            }
            if (!dueForRetry(meta)) {
                continue;
            }

            var payload = spool.load(jobId);
            if (payload.isEmpty()) {
                // File gz hỏng/không đọc được. Nếu chỉ bỏ qua thì deliver() không chạy
                // → recordFailure() không chạy → attempts đứng yên ở 0 → guard
                // maxAttempts bên trên không bao giờ kích hoạt → job kẹt trong
                // pending/ và bị quét lại mỗi 2 phút mãi mãi. Phải chủ động poison.
                spool.markPoisoned(jobId, "Không đọc được payload trong spool");
                continue;
            }

            log.info("🔁 Gửi lại job #{} (lần thử {})", jobId, meta.attempts() + 1);
            callbackService.deliver(jobId, payload.get());
        }
    }

    /**
     * Backoff mũ: chờ {@code 2^attempts} phút, trần 60 phút.
     *
     * <p>Dịch chuyển {@code 1L << min(attempts, 6)} chặn ở 64 để không tràn khi
     * attempts lớn; {@code Math.min(60, ...)} là trần thực tế.
     */
    private boolean dueForRetry(ResultSpool.Meta meta) {
        if (meta.lastAttemptAt() == null) {
            return true;
        }
        long waitMinutes = Math.min(60L, 1L << Math.min(meta.attempts(), 6));
        return Duration.between(meta.lastAttemptAt(), Instant.now()).toMinutes() >= waitMinutes;
    }
}
