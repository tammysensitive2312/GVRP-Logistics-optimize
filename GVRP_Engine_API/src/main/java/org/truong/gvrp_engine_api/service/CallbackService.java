package org.truong.gvrp_engine_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_engine_api.model.OptimizationResult;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SolutionConverter solutionConverter;

    @Value("${spring.optimization.entry.url}")
    private String entryApiBaseUrl;

    @Value("${entry.api-key}")
    private String apiKey;

    public void sendCompletionCallback(Long jobId, OptimizationResult result) {
        String url = entryApiBaseUrl + "/solutions/callbacks/complete";

        log.info("📤 Sending completion callback for job #{} to {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("solution", solutionConverter.convertToSolutionData(result));
            log.warn("Completion callback solution payload: {}",
                    objectMapper.writeValueAsString(payload ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Completion callback sent successfully for job #{}", jobId);
            } else {
                log.warn("⚠️  Unexpected callback response: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send completion callback for job #{}: {}",
                    jobId, e.getMessage(), e);
            // Don't throw - callback failure shouldn't break optimization
        }
    }

    /**
     * Send error callback to Entry API
     */
    public void sendFailureCallback(Long jobId, String errorMessage) {
        String url = entryApiBaseUrl + "/solutions/callbacks/failed";

        log.info("📤 Sending failure callback for job #{} to {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("external_job_id", "engine-" + jobId);
            payload.put("error_message", errorMessage);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Failure callback sent successfully for job #{}", jobId);
            } else {
                log.warn("⚠️  Unexpected callback response: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send failure callback for job #{}: {}",
                    jobId, e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        return headers;
    }

}
