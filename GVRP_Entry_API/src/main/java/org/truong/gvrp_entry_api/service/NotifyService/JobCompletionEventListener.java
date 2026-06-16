package org.truong.gvrp_entry_api.service.NotifyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.truong.gvrp_entry_api.util.JobCompletionEvent;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCompletionEventListener {
    private final List<NotificationChannel> channels;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCompleted(JobCompletionEvent event) {
        log.info("Broadcasting JobCompletionEvent for job #{} to {} channels",
                event.getJobId(), channels.size());

        for (NotificationChannel channel : channels) {
            if (!channel.supports(event)) {
                log.debug("Channel {} skipped (supports=false)",
                        channel.getClass().getSimpleName());
                continue;
            }

            try {
                channel.handle(event);
            } catch (Exception e) {
                log.error("❌ Channel {} failed for job #{}: {}",
                        channel.getClass().getSimpleName(),
                        event.getJobId(),
                        e.getMessage()
                );
            }
        }
    }
}
