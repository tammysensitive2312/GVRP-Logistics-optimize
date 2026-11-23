package org.truong.gvrp_entry_api.service.integration.external_api;

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
import org.truong.gvrp_entry_api.dto.request.EngineEvaluateRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineLegsRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.response.EngineEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.EngineLegsResponseDTO;
import org.truong.gvrp_entry_api.dto.response.EngineOptimizationResponse;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.EngineUnavailableException;
import org.truong.gvrp_entry_api.exception.ErrorDetails;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class EngineApiClientImpl implements EngineApiClient{
    private final RestTemplate restTemplate;
    private final OptimizationJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.optimization.engine.url}")
    private String engineBaseUrl;

    /**
     * Entry -> Engine service key (SEC-ENGINE-001), sent only on Engine calls. The shared
     * RestTemplate also serves Goong and Slack, so the key is never added by an interceptor.
     */
    @Value("${engine.inbound-key:}")
    private String engineInboundKey;

    /** Header the Engine's ServiceKeyFilter checks. */
    static final String ENGINE_KEY_HEADER = "X-Engine-Key";

    private HttpHeaders engineHeaders(boolean json) {
        HttpHeaders headers = new HttpHeaders();
        if (json) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (engineInboundKey != null && !engineInboundKey.isBlank()) {
            headers.set(ENGINE_KEY_HEADER, engineInboundKey.trim());
        } else {
            log.error("engine.inbound-key (GVRP_ENGINE_INBOUND_KEY) is not set; the Engine will refuse this call");
        }
        return headers;
    }

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
            // Prepare HTTP request
            HttpEntity<EngineOptimizationRequest> httpRequest =
                    new HttpEntity<>(engineRequest, engineHeaders(true));

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

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProgress(Long jobId) {
        String url = engineBaseUrl + "/optimization/" + jobId + "/progress";
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(engineHeaders(false)), Map.class);
            return (Map<String, Object>) resp.getBody();
        } catch (HttpClientErrorException.NotFound nf) {
            return null;
        } catch (Exception e) {
            log.warn("Không lấy được progress job #{} từ engine: {}", jobId, e.getMessage());
            return null;
        }
    }

    @Override
    public void requestCancel(Long jobId) {
        String url = engineBaseUrl + "/optimization/" + jobId + "/cancel";
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(engineHeaders(false)), Void.class);
            log.info("Đã gửi yêu cầu cancel job #{} xuống engine", jobId);
        } catch (Exception e) {
            // best-effort: DB đã set CANCELLED; engine không tới được thì chỉ cảnh báo
            log.warn("Không gửi được cancel job #{} xuống engine: {}", jobId, e.getMessage());
        }
    }

    @Override
    public EngineEvaluateResponseDTO evaluateRoutes(EngineEvaluateRequestDTO request) {
        String url = engineBaseUrl + "/optimization/evaluate";
        try {
            ResponseEntity<EngineEvaluateResponseDTO> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, engineHeaders(true)), EngineEvaluateResponseDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EngineUnavailableException(
                        "Engine returned " + response.getStatusCode() + " for route evaluation.", null);
            }
            return response.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw engineKeyRejected("route evaluation", e);
        } catch (HttpClientErrorException e) {
            // Engine rejected OUR payload — surface as a 400 with the engine message.
            throw new DataInvalidException(List.of(
                    ErrorDetails.validation("Engine rejected route sequences: " + e.getResponseBodyAsString())));
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new EngineUnavailableException(
                    "Cannot reach optimization engine for route evaluation. Retry later; unsaved edits are kept.",
                    e);
        }
    }

    @Override
    public EngineLegsResponseDTO legs(List<EngineLegsRequestDTO.Point> points, String locale) {
        String url = engineBaseUrl + "/optimization/legs";
        EngineLegsRequestDTO request = new EngineLegsRequestDTO();
        request.setPoints(points);
        request.setLocale(locale);
        try {
            ResponseEntity<EngineLegsResponseDTO> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, engineHeaders(true)), EngineLegsResponseDTO.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new EngineUnavailableException(
                        "Engine returned " + response.getStatusCode() + " for leg geometry.", null);
            }
            return response.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw engineKeyRejected("leg geometry", e);
        } catch (HttpClientErrorException e) {
            throw new DataInvalidException(List.of(
                    ErrorDetails.validation("Engine rejected leg points: " + e.getResponseBodyAsString())));
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new EngineUnavailableException(
                    "Cannot reach optimization engine for leg geometry. Retry later; the route was not sent.",
                    e);
        }
    }

    /**
     * A 401 from the Engine is a key misconfiguration between the two services, not a fault in the
     * caller's data: report it as the Engine being unavailable rather than as a 400.
     */
    private EngineUnavailableException engineKeyRejected(String operation, Exception cause) {
        log.error("Engine refused the service key for {} (check engine.inbound-key / GVRP_ENGINE_INBOUND_KEY)",
                operation);
        return new EngineUnavailableException(
                "Optimization engine refused this server's credentials for " + operation + ".", cause);
    }

    private void updateJobStatus(Long jobId, OptimizationJobStatus status, String errorMessage, String externalJobId) {        try {
            OptimizationJob job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException(AppConstant.JOB));

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
