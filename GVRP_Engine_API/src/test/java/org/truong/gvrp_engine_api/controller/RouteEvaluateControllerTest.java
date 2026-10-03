package org.truong.gvrp_engine_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.truong.gvrp_engine_api.model.RouteEvaluateRequest;
import org.truong.gvrp_engine_api.model.RouteEvaluateResponse;
import org.truong.gvrp_engine_api.service.RouteEvaluationService;

import java.util.List;

import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller envelope tests for {@code POST /api/v1/optimization/evaluate}.
 *
 * <p>Uses standalone MockMvc with a stubbed service — no Spring context, no Mockito.
 */
class RouteEvaluateControllerTest {

    private static MockMvc mockMvc(RouteEvaluationService service) {
        OptimizationController controller = new OptimizationController(
                null, null, null, null, service);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static RouteEvaluationService okService() {
        return new RouteEvaluationService(null) {
            @Override
            public RouteEvaluateResponse evaluate(RouteEvaluateRequest request) {
                return new RouteEvaluateResponse(true, List.of(), List.of(), null);
            }
        };
    }

    @Test
    void evaluate_ok_returnsResponse() throws Exception {
        mockMvc(okService())
                .perform(post("/api/v1/optimization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true));
    }

    @Test
    void evaluate_malformed_returns400() throws Exception {
        RouteEvaluationService failing = new RouteEvaluationService(null) {
            @Override
            public RouteEvaluateResponse evaluate(RouteEvaluateRequest request) {
                throw new IllegalArgumentException("Routes list cannot be empty");
            }
        };

        mockMvc(failing)
                .perform(post("/api/v1/optimization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void evaluate_oversized_returns413() throws Exception {
        RouteEvaluationService tooLarge = new RouteEvaluationService(null) {
            @Override
            public RouteEvaluateResponse evaluate(RouteEvaluateRequest request) {
                throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Too many stops");
            }
        };

        mockMvc(tooLarge)
                .perform(post("/api/v1/optimization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routes\":[]}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void evaluate_unexpected_returns500() throws Exception {
        RouteEvaluationService broken = new RouteEvaluationService(null) {
            @Override
            public RouteEvaluateResponse evaluate(RouteEvaluateRequest request) {
                throw new RuntimeException("boom");
            }
        };

        mockMvc(broken)
                .perform(post("/api/v1/optimization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routes\":[]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}
