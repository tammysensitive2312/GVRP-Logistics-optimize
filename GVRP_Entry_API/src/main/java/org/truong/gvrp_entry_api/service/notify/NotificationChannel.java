package org.truong.gvrp_entry_api.service.notify;

import org.truong.gvrp_entry_api.service.event.JobCompletionEvent;

public interface NotificationChannel {
    void handle(JobCompletionEvent event);

    default boolean supports(JobCompletionEvent event) {
        return true;
    }
}
