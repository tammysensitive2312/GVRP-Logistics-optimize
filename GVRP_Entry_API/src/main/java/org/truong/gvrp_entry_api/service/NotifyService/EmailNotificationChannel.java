package org.truong.gvrp_entry_api.service.NotifyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.service.EmailService;
import org.truong.gvrp_entry_api.util.JobCompletionEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    @Autowired
    EmailService emailService;

    @Override
    public void handle(JobCompletionEvent event) {
        log.info("📧 Sending success email for job #{}", event.getJobId());
        emailService.sendOptimizationSuccessEmail(
                event.getUserId(),
                event.getJobId(),
                event.getSolutionId()
        );
    }
}
