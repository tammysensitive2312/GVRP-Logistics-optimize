package org.truong.gvrp_entry_api.service.NotifyService;

import org.truong.gvrp_entry_api.util.JobCompletionEvent;

public interface NotificationChannel {
    void handle(JobCompletionEvent event);

    default boolean supports(JobCompletionEvent event) {
        return true;
    }
}
