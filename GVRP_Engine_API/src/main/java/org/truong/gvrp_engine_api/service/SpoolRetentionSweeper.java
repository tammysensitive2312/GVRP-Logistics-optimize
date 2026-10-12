package org.truong.gvrp_engine_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Quét định kỳ, xóa payload spool đã hết hạn giữ trong {@code sent/} và
 * {@code poison/} để đĩa không phình. {@code pending/} không bao giờ bị đụng
 * (thuộc về {@link CallbackRetryScheduler}).
 *
 * <p>Mặc định 3h sáng mỗi ngày; retention cấu hình được, khớp chính sách
 * 7/30 ngày đã chốt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpoolRetentionSweeper {

    private final ResultSpool spool;

    @Value("${gvrp.result-spool.sent-retention-days:7}")
    private int sentRetentionDays;

    @Value("${gvrp.result-spool.poison-retention-days:30}")
    private int poisonRetentionDays;

    @Scheduled(cron = "${gvrp.result-spool.sweep-cron:0 0 3 * * *}")
    public void sweep() {
        int removed = spool.sweepDelivered(
                Duration.ofDays(sentRetentionDays),
                Duration.ofDays(poisonRetentionDays));
        log.info("🧹 Spool retention sweep: {} payload quá hạn đã dọn "
                + "(sent>{}d, poison>{}d)", removed,
                sentRetentionDays, poisonRetentionDays);
    }
}
