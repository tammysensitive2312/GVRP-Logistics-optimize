package org.truong.gvrp_entry_api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.dto.response.RouteDetailResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteViolationDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.EngineUnavailableException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.service.RouteCustomizationService;
import org.truong.gvrp_entry_api.service.SolutionService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envelope tests for the customization endpoints (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class SolutionCustomizationControllerTest {

    @Mock
    private SolutionService solutionService;
    @Mock
    private RouteCustomizationService routeCustomizationService;

    @InjectMocks
    private SolutionController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String BODY = """
            {"routes":[{"vehicleId":7,"stopOrderIds":[11,12]}]}""";

    @Test
    void evaluate_ok_returnsPreview() throws Exception {
        when(routeCustomizationService.evaluate(eq(9L), any())).thenReturn(
                RouteEvaluateResponseDTO.builder()
                        .feasible(true).violations(List.of()).routes(List.of())
                        .warnings(List.of()).build());

        mockMvc().perform(post("/api/v1/solutions/9/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true));
    }

    @Test
    void evaluate_badSequences_returns400() throws Exception {
        when(routeCustomizationService.evaluate(eq(9L), any())).thenThrow(
                new DataInvalidException(List.of(ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode()).message("bad").build())));

        mockMvc().perform(post("/api/v1/solutions/9/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_engineDown_returns502() throws Exception {
        when(routeCustomizationService.evaluate(eq(9L), any())).thenThrow(
                new EngineUnavailableException("down", null));

        mockMvc().perform(post("/api/v1/solutions/9/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void saveRevision_ok_returnsDetail() throws Exception {
        when(routeCustomizationService.saveRevision(eq(9L), any())).thenReturn(
                SolutionDetailResponseDTO.builder().id(10L).build());

        mockMvc().perform(post("/api/v1/solutions/9/revisions")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void saveRevision_hardViolation_returns422() throws Exception {
        when(routeCustomizationService.saveRevision(eq(9L), any())).thenThrow(
                new UnprocessablePlanException("OVER_CAPACITY route=0"));

        mockMvc().perform(post("/api/v1/solutions/9/revisions")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("0042201"));
    }

    @Test
    void saveRevision_staleBase_returns409() throws Exception {
        when(routeCustomizationService.saveRevision(eq(9L), any())).thenThrow(
                new RevisionConflictException("stale", 10L));

        mockMvc().perform(post("/api/v1/solutions/9/revisions")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void previewDtos_serialize() {
        // Documents that these DTOs construct cleanly for preview mapping.
        assertNotNull(RouteDetailResponseDTO.builder().build());
        assertNotNull(RouteViolationDTO.builder().build());
    }
}
