package org.truong.gvrp_entry_api.service.NotifyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_entry_api.util.JobCompletionEvent;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationChannel implements NotificationChannel {
    private final RestTemplate restTemplate;

    @Override
    public boolean supports(JobCompletionEvent event) {
        return event.getBranchWebhookURL() != null
                && !event.getBranchWebhookURL().isBlank();
    }

    @Override
    public void handle(JobCompletionEvent event) {
        Long jobId = event.getJobId();
        String status = event.getSolutionStatus().toString();
        String slackUserId = event.getSlackUserId();
        String branchWebhookURL = event.getBranchWebhookURL();
        String messageContent = String.format("Optimization for job %d has been completed with status %s. Please check. <@%s>",
                jobId, status, slackUserId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new HashMap<>();
            body.put("text", messageContent);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(branchWebhookURL, requestEntity, String.class);

        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }
}
