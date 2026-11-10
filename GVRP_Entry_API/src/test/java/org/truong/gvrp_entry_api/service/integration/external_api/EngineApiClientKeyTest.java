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
import org.truong.gvrp_entry_api.exception.EngineUnavailableException;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SEC-ENGINE-001 EN-T9: every Engine call carries {@code X-Engine-Key}; a blank key sends none.
 * The key is added per call, so the shared RestTemplate (also used for Goong and Slack) gets no
 * interceptor.
 */
class EngineApiClientKeyTest {

    private static final String BASE = "http://engine.test/api/v1";
    private static final String KEY = "e".repeat(43);
    private static final String HEADER = EngineApiClientImpl.ENGINE_KEY_HEADER;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EngineApiClientImpl client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        OptimizationJobRepository jobs = mock(OptimizationJobRepository.class);
        when(jobs.findById(any())).thenReturn(Optional.empty());
        client = new EngineApiClientImpl(restTemplate, jobs, new ObjectMapper());
        ReflectionTestUtils.setField(client, "engineBaseUrl", BASE);
        ReflectionTestUtils.setField(client, "engineInboundKey", KEY);
    }

    private static EngineLegsRequestDTO.Point point(double lat, double lng) {
        return new EngineLegsRequestDTO.Point(lat, lng);
    }

    @Test
    void allFiveEngineCallsCarryTheKey() {
        server.expect(requestTo(BASE + "/optimization")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HEADER, KEY))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"external_job_id\":\"engine-1\",\"status\":\"ACCEPTED\"}"));
        server.expect(requestTo(BASE + "/optimization/1/progress")).andExpect(method(HttpMethod.GET))
                .andExpect(header(HEADER, KEY))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/optimization/1/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HEADER, KEY))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        server.expect(requestTo(BASE + "/optimization/evaluate")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HEADER, KEY))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/optimization/legs")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HEADER, KEY))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.submitOptimizationAsync(1L, new EngineOptimizationRequest());
        client.getProgress(1L);
        client.requestCancel(1L);
        client.evaluateRoutes(new EngineEvaluateRequestDTO());
        client.legs(List.of(point(21.0, 105.8), point(21.01, 105.81)), "vi");

        server.verify();
    }

    @Test
    void blankKeySendsNoHeader() {
        ReflectionTestUtils.setField(client, "engineInboundKey", "  ");
        server.expect(requestTo(BASE + "/optimization/1/progress"))
                .andExpect(headerDoesNotExist(HEADER))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        client.getProgress(1L);
        server.verify();
    }

    @Test
    void engine401IsReportedAsUnavailableNotAsBadInput() {
        server.expect(requestTo(BASE + "/optimization/evaluate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"UNAUTHORIZED\"}"));
        assertThrows(EngineUnavailableException.class, () -> client.evaluateRoutes(new EngineEvaluateRequestDTO()));
        server.verify();
    }
}
