package org.truong.gvrp_entry_api.integration.external_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.response.EngineOptimizationResponse;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;


@Slf4j
@Service
@RequiredArgsConstructor
public class EngineApiClientImpl implements EngineApiClient{
    private final RestTemplate restTemplate;
    private final OptimizationJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.optimization.engine.url}")
    private String engineBaseUrl;

    @Override
    @Async
    public void submitOptimizationAsync(Long jobId, EngineOptimizationRequest engineRequest) {
        log.info("Submitting Engine API: jobId={}", jobId);

        engineRequest.setJobId(jobId);

        if (engineRequest.getConfig() != null) {
            log.info("Engine config: iterations={}, timeout={}s, costWeight={}, co2Weight={}",
                    engineRequest.getConfig().getMaxIterations(),
                    engineRequest.getConfig().getTimeoutSeconds(),
                    determineGoal(engineRequest.getConfig()));
        }

        try {
            String payload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(engineRequest);

            log.info("📤 ===== ENGINE PAYLOAD =====");
            log.info("\n{}", payload);
            log.info("============================");

        } catch (Exception e) {
            log.warn("Cannot log payload", e);
        }

        try {
            // Prepare HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<EngineOptimizationRequest> httpRequest =
                    new HttpEntity<>(engineRequest, headers);

            String url = engineBaseUrl + "/api/engine/optimize";

            ResponseEntity<EngineOptimizationResponse> response = restTemplate.postForEntity(
                    url,
                    httpRequest,
                    EngineOptimizationResponse.class
            );

            // Handle response
            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("✓ Engine accepted request: jobId={}", jobId);
                updateJobStatus(jobId, OptimizationJobStatus.PROCESSING, null);
            } else {
                log.warn("Unexpected response status: {}", response.getStatusCode());
                updateJobStatus(jobId, OptimizationJobStatus.FAILED,
                        "Engine returned unexpected status: " + response.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            log.error("Failed to connect to Engine API: jobId={}", jobId, e);
            updateJobStatus(jobId, OptimizationJobStatus.FAILED,
                    "Cannot connect to optimization engine. Please check if engine is running.");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // HTTP error (4xx, 5xx)
            log.error("Engine API error: jobId={}, status={}", jobId, e.getStatusCode(), e);
            updateJobStatus(jobId, OptimizationJobStatus.FAILED,
                    "Engine API error: " + e.getMessage());
        }
        catch (Exception e) {
            log.error("Failed to submit to Engine API: jobId={}", jobId, e);
            updateJobStatus(jobId, OptimizationJobStatus.FAILED,
                    "Failed to communicate with optimization engine: " + e.getMessage());
        }
    }

    private void updateJobStatus(Long jobId, OptimizationJobStatus status, String errorMessage) {
        try {
            OptimizationJob job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

            job.setStatus(status);

            if (status == OptimizationJobStatus.PROCESSING) {
                job.setStartedAt(java.time.LocalDateTime.now());
            } else if (status == OptimizationJobStatus.FAILED) {
                job.setCompletedAt(java.time.LocalDateTime.now());
                job.setErrorMessage(errorMessage);
            }

            jobRepository.save(job);

            log.info("Job status updated: jobId={}, status={}", jobId, status);

        } catch (Exception e) {
            log.error("Failed to update job status: jobId={}", jobId, e);
        }
    }

    private String determineGoal(EngineOptimizationRequest.OptimizationConfig config) {
        if (config.getCo2Weight() > 0.5) return "MINIMIZE_CO2";
        if (config.getDistanceWeight() > 0.5) return "MINIMIZE_DISTANCE";
        if (config.getCostWeight() > 0.5) return "MINIMIZE_COST";
        return "BALANCED";
    }
}
