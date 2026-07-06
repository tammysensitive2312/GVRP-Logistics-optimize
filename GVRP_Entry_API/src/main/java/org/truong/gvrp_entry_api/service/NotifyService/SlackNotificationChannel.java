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

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationChannel implements NotificationChannel {
    private final RestTemplate restTemplate;

    @Override
    public void handle(JobCompletionEvent event) {
        String branchWebhookURL = event.getBranchWebhookURL();
        String messageContent = buildMessage(event);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("text", messageContent);

            HttpEntity<Map<String, String>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    branchWebhookURL, requestEntity, String.class);

            if (!"ok".equals(response.getBody())) {
                log.warn("⚠️ Slack webhook failed for job #{}: {}",
                        event.getJobId(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Slack notification error for job #{}: {}",
                    event.getJobId(), e.getMessage());
        }
    }

    private String buildMessage(JobCompletionEvent event) {
        int totalOrders = event.getServedOrdersCount()
                + event.getUnservedOrdersCount();

        String statusIcon = switch (event.getSolutionStatus()) {
            case SUCCESS -> "✅";
            case PARTIAL_SUCCESS -> "⚠️";
            case INFEASIBLE -> "❌";
            default -> "ℹ️";
        };

        String statusText = switch (event.getSolutionStatus()) {
            case SUCCESS -> "success";
            case PARTIAL_SUCCESS -> "partial success";
            case INFEASIBLE -> "infeasible";
            default -> "end";
        };

        String unassignedLine = event.getUnservedOrdersCount() > 0
                ? String.format(" | ❌ %d unassigned orders",
                event.getUnservedOrdersCount())
                : "";

        String completedAt = event.getCompletedAt() != null
                ? event.getCompletedAt()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))
                : "N/A";

        return String.format(
                """
                %s Job #%d %s — Branch %s
                📦 %d/%d on route orders%s
                🚚 %d xe | 🛣️ %.1f km | 💰 %,.0f VND | 🌱 %.1f kg CO2
                ⏱️ Completed at: %s
                <@%s>
                """,
                statusIcon,
                event.getJobId(),
                statusText,
                event.getBranchName(),
                event.getServedOrdersCount(),
                totalOrders,
                unassignedLine,
                event.getTotalVehiclesUsed(),
                event.getTotalDistance(),
                event.getTotalCost(),
                event.getTotalCO2(),
                completedAt,
                event.getSlackUserId()
        );
    }

    @Override
    public boolean supports(JobCompletionEvent event) {
        return event.getBranchWebhookURL() != null
                && !event.getBranchWebhookURL().isBlank();
    }
}
