package org.truong.gvrp_entry_api.service.integration.external_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_entry_api.dto.request.EngineEvaluateRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineLegsRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies {@link EngineApiClientImpl} parses the unified Engine error envelope cleanly
 * without leaking raw JSON strings or HTML error pages.
 */
class EngineApiClientErrorContractTest {

    private static final String BASE = "http://engine.test/api/v1";

    private MockRestServiceServer server;
    private OptimizationJobRepository jobRepository;
    private EngineApiClientImpl client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        jobRepository = mock(OptimizationJobRepository.class);
        client = new EngineApiClientImpl(restTemplate, jobRepository, new ObjectMapper());
        ReflectionTestUtils.setField(client, "engineBaseUrl", BASE);
        ReflectionTestUtils.setField(client, "engineInboundKey", "k".repeat(40));
    }

    @Test
    void evaluateRoutesParses400UnifiedEnvelopeCleanly() {
        server.expect(requestTo(BASE + "/optimization/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"0040001\",\"message\":\"Routes list cannot be empty\"}]}"));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> client.evaluateRoutes(new EngineEvaluateRequestDTO()));

        assertEquals(1, ex.getErrors().size());
        ErrorDetail detail = ex.getErrors().get(0);
        assertEquals("0040001", detail.getCode());
        assertEquals("Engine rejected route sequences: Routes list cannot be empty", detail.getMessage());
        assertFalse(detail.getMessage().contains("{"));
        server.verify();
    }

    @Test
    void evaluateRoutesMaps413UnifiedEnvelopeToPayloadTooLargeCode() {
        server.expect(requestTo(BASE + "/optimization/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"0041301\",\"message\":\"Too many stops: 600 (max 500)\"}]}"));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> client.evaluateRoutes(new EngineEvaluateRequestDTO()));

        assertEquals(1, ex.getErrors().size());
        ErrorDetail detail = ex.getErrors().get(0);
        assertEquals("0041301", detail.getCode());
        assertEquals("Engine rejected route sequences: Too many stops: 600 (max 500)", detail.getMessage());
        server.verify();
    }

    @Test
    void legsParses400UnifiedEnvelopeCleanly() {
        List<EngineLegsRequestDTO.Point> points = List.of(
                new EngineLegsRequestDTO.Point(21.0, 105.8));

        server.expect(requestTo(BASE + "/optimization/legs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"0040001\",\"message\":\"At least 2 points are required\"}]}"));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> client.legs(points, "vi"));

        assertEquals("0040001", ex.getErrors().get(0).getCode());
        assertEquals("Engine rejected leg points: At least 2 points are required", ex.getErrors().get(0).getMessage());
        assertFalse(ex.getErrors().get(0).getMessage().contains("{"));
        server.verify();
    }

    @Test
    void legsFallsBackSafelyOnNonJsonBody() {
        List<EngineLegsRequestDTO.Point> points = List.of(
                new EngineLegsRequestDTO.Point(21.0, 105.8),
                new EngineLegsRequestDTO.Point(21.01, 105.81));

        server.expect(requestTo(BASE + "/optimization/legs"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Bad Request</body></html>"));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> client.legs(points, "vi"));

        assertEquals("0040001", ex.getErrors().get(0).getCode());
        assertEquals("Engine rejected leg points: HTTP 400", ex.getErrors().get(0).getMessage());
        assertFalse(ex.getErrors().get(0).getMessage().contains("<html>"));
        server.verify();
    }

    @Test
    void getProgressReturnsNullOn404UnifiedEnvelope() {
        server.expect(requestTo(BASE + "/optimization/99/progress"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"0040402\",\"message\":\"Job not found.\"}]}"));

        assertNull(client.getProgress(99L));
        server.verify();
    }

    @Test
    void submitOptimizationAsyncStoresCleanExtractedMessageOn413() {
        OptimizationJob job = new OptimizationJob();
        job.setId(99L);
        job.setStatus(OptimizationJobStatus.PENDING);
        when(jobRepository.findById(99L)).thenReturn(Optional.of(job));

        server.expect(requestTo(BASE + "/optimization"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"0041301\",\"message\":\"Too many orders: 600 (max 500)\"}]}"));

        client.submitOptimizationAsync(99L, new EngineOptimizationRequest());

        assertEquals(OptimizationJobStatus.FAILED, job.getStatus());
        assertEquals("Engine API error: Too many orders: 600 (max 500)", job.getErrorMessage());
        verify(jobRepository).save(job);
        server.verify();
    }

    @Test
    void submitOptimizationAsyncFallsBackSafelyOnNonJson5xx() {
        OptimizationJob job = new OptimizationJob();
        job.setId(100L);
        job.setStatus(OptimizationJobStatus.PENDING);
        when(jobRepository.findById(100L)).thenReturn(Optional.of(job));

        server.expect(requestTo(BASE + "/optimization"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Bad Gateway</body></html>"));

        client.submitOptimizationAsync(100L, new EngineOptimizationRequest());

        assertEquals(OptimizationJobStatus.FAILED, job.getStatus());
        assertEquals("Engine API error: HTTP 502", job.getErrorMessage());
        assertFalse(job.getErrorMessage().contains("<html>"));
        verify(jobRepository).save(job);
        server.verify();
    }
}
