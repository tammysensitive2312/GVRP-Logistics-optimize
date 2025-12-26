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

        OptimizationJobStatus finalStatus = OptimizationJobStatus.PROCESSING;
        String externalJobId = null;
        String errorMessage = null;

        engineRequest.setJobId(jobId);

        if (engineRequest.getConfig() != null) {
            log.info("Engine config: iterations={}, timeout={}s, goal={}",
                    engineRequest.getConfig().getMaxIterations(),
                    engineRequest.getConfig().getTimeoutSeconds(),
                    determineGoal(engineRequest.getConfig()));
        }

        try {
            String payload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(engineRequest);

            log.debug("📤 ===== ENGINE PAYLOAD =====");
            log.debug("\n{}", payload);
            log.debug("============================");

        } catch (Exception e) {
            log.warn("Cannot log payload", e);
        }

        try {
            // Prepare HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<EngineOptimizationRequest> httpRequest =
                    new HttpEntity<>(engineRequest, headers);

            String url = engineBaseUrl + "/optimization";

            ResponseEntity<EngineOptimizationResponse> response = restTemplate.postForEntity(
                    url,
                    httpRequest,
                    EngineOptimizationResponse.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("Engine accepted job successfully");
                // Lấy external_job_id từ body trả về của Engine
                if (response.getBody() != null) {
                    externalJobId = response.getBody().getExternalJobId();
                }
                finalStatus = OptimizationJobStatus.PROCESSING;
            } else {
                finalStatus = OptimizationJobStatus.FAILED;
                errorMessage = "Engine returned: " + response.getStatusCode();
            }

        } catch (ResourceAccessException e) {
            log.error("Failed to connect to Engine API: jobId={}", jobId, e);
            errorMessage = "Cannot connect to optimization engine. Please check if engine is running.";
            finalStatus = OptimizationJobStatus.FAILED;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // HTTP error (4xx, 5xx)
            log.error("Engine API error: jobId={}, status={}", jobId, e.getStatusCode(), e);
            errorMessage = "Engine API error: " + e.getMessage();
            finalStatus = OptimizationJobStatus.FAILED;
        }
        catch (Exception e) {
            log.error("Error communicating with Engine", e);
            finalStatus = OptimizationJobStatus.FAILED;
            errorMessage = e.getMessage();
        } finally {
            updateJobStatus(jobId, finalStatus, errorMessage, externalJobId);
        }
    }

    private void updateJobStatus(Long jobId, OptimizationJobStatus status, String errorMessage, String externalJobId) {
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

            if (externalJobId != null) {
                job.setExternalJobId(externalJobId);
            }

            jobRepository.save(job);

            log.info("Job status updated: jobId={}, status={}", jobId, status);

        } catch (Exception e) {
            log.error("Failed to update job status: jobId={}", jobId, e);
        }
    }

    private String determineGoal(EngineOptimizationRequest.OptimizationConfig config) {
        if (config.getCo2Weight() > 0.5) return "MINIMIZE_CO2";
        if (config.getCo2Weight() == 0.5) return "MINIMIZE_DISTANCE";
        if (config.getCostWeight() > 0.5) return "MINIMIZE_COST";
        return "BALANCED";
    }
}
