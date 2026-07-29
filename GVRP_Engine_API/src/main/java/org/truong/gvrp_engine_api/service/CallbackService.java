package org.truong.gvrp_engine_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_engine_api.model.OptimizationResult;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final RestTemplate restTemplate;
    private final SolutionConverter solutionConverter;
    private final ResultSpool resultSpool;

    @Value("${spring.optimization.entry.url}")
    private String entryApiBaseUrl;

    @Value("${entry.api-key}")
    private String apiKey;

    public void sendCompletionCallback(Long jobId, OptimizationResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("job_id", jobId);
        payload.put("solution", solutionConverter.convertToSolutionData(result));

        resultSpool.store(jobId, payload);
        deliver(jobId, payload);
    }

    /**
     * Giao payload tới Entry. Không ném ra ngoài — mọi kết cục đều được ghi nhận
     * vào spool để sweeper biết phải làm gì tiếp.
     *
     * <p>Phân loại lỗi là điểm mấu chốt của cơ chế retry:
     * <ul>
     *   <li><b>4xx</b> — Entry từ chối payload. Gửi lại y hệt sẽ lại 4xx, nên chuyển
     *       thẳng sang {@code poison/} và cần người xem, không đốt thêm lượt thử.</li>
     *   <li><b>5xx / timeout / connection refused</b> — Entry đang hỏng hoặc chậm,
     *       payload có thể vẫn hợp lệ. Giữ ở {@code pending/} và retry.</li>
     * </ul>
     *
     * <p>Lưu ý về read timeout: nếu Entry xử lý xong nhưng phản hồi về muộn hơn
     * timeout, engine thấy {@code ResourceAccessException} và sẽ gửi lại — lúc đó
     * chốt idempotency phía Entry ({@code findByJobId}) là thứ chặn dữ liệu trùng.
     *
     * @return true nếu Entry trả 2xx
     */
    public boolean deliver(Long jobId, Map<String, Object> payload) {
        String url = entryApiBaseUrl + "/solutions/callbacks/complete";
        log.info("📤 Gửi completion callback job #{} → {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Callback job #{} thành công ({})", jobId, response.getStatusCode());
                resultSpool.markSent(jobId);
                return true;
            }

            resultSpool.recordFailure(jobId, "HTTP " + response.getStatusCode());
            log.warn("⚠️  Callback job #{} trả về {} (sẽ retry)", jobId, response.getStatusCode());
            return false;

        } catch (HttpClientErrorException e) {
            // 4xx — payload sai, retry vô ích
            resultSpool.markPoisoned(jobId,
                    e.getStatusCode() + " " + e.getResponseBodyAsString());
            return false;

        } catch (RestClientException e) {
            // 5xx, read timeout, connection refused — retry có ý nghĩa
            resultSpool.recordFailure(jobId, e.getMessage());
            log.error("❌ Callback job #{} thất bại (sẽ retry): {}", jobId, e.getMessage());
            return false;
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

    /**
     * Send cancelled callback to Entry API — job bị hủy theo yêu cầu, KHÔNG kèm lời giải.
     */
    public void sendCancelledCallback(Long jobId, String reason) {
        String url = entryApiBaseUrl + "/solutions/callbacks/cancelled";

        log.info("📤 Sending cancelled callback for job #{} to {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("reason", reason);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Cancelled callback sent successfully for job #{}", jobId);
            } else {
                log.warn("⚠️  Unexpected callback response: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send cancelled callback for job #{}: {}",
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
