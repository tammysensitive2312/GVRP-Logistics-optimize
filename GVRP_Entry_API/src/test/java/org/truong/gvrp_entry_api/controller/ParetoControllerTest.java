package org.truong.gvrp_entry_api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.dto.response.ParetoCandidateDTO;
import org.truong.gvrp_entry_api.dto.response.ParetoFrontierDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.service.ParetoService;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envelope tests for the Pareto frontier endpoints (no Spring context) — N-T12.
 * Standalone MockMvc uses the default camelCase Jackson mapper, so single-word
 * JSON keys are asserted.
 */
@ExtendWith(MockitoExtension.class)
class ParetoControllerTest {

    @Mock
    private ParetoService paretoService;

    @InjectMocks
    private ParetoController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** The branch comes from the request attribute set by the JWT filter. */
    private static final Long BRANCH = 1L;

    @Test
    void getFrontier_ok_returnsCandidates() throws Exception {
        when(paretoService.getFrontier(9L, BRANCH)).thenReturn(ParetoFrontierDTO.builder()
                .jobId(5L).solutionId(9L).latestSolutionId(9L).activeCandidateId(100L)
                .clustered(false).clusterCount(0)
                .candidates(List.of(ParetoCandidateDTO.builder()
                        .id(100L).index(0).applicable(false).notApplicableReason("ALREADY_ACTIVE")
                        .build()))
                .build());

        mockMvc().perform(get("/api/v1/solutions/9/pareto")
                        .requestAttr("branchId", BRANCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].id").value(100))
                .andExpect(jsonPath("$.candidates[0].applicable").value(false));

        verify(paretoService).getFrontier(9L, BRANCH);
    }

    @Test
    void apply_ok_returnsDetail() throws Exception {
        when(paretoService.apply(9L, 100L, BRANCH)).thenReturn(
                SolutionDetailResponseDTO.builder().id(10L).paretoCandidateId(100L).build());

        mockMvc().perform(post("/api/v1/solutions/9/pareto/100/apply")
                        .requestAttr("branchId", BRANCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(paretoService).apply(9L, 100L, BRANCH);
    }

    @Test
    void getFrontier_otherBranch_returns400NotFoundEnvelope() throws Exception {
        when(paretoService.getFrontier(9L, BRANCH)).thenThrow(new DataInvalidException(
                List.of(ErrorDetail.builder()
                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                        .resource(AppConstant.PARETO_CANDIDATE)
                        .build())));

        // Design maps "not found" to 404, but GlobalExceptionHandler maps
        // DataInvalidException to 400 — recorded as a deviation.
        mockMvc().perform(get("/api/v1/solutions/9/pareto")
                        .requestAttr("branchId", BRANCH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040402"));
    }

    @Test
    void apply_staleBase_returns409() throws Exception {
        when(paretoService.apply(9L, 100L, BRANCH)).thenThrow(
                new RevisionConflictException("stale", 10L));

        mockMvc().perform(post("/api/v1/solutions/9/pareto/100/apply")
                        .requestAttr("branchId", BRANCH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("0040901"));
    }

    @Test
    void apply_notApplicable_returns422() throws Exception {
        when(paretoService.apply(9L, 100L, BRANCH)).thenThrow(
                new UnprocessablePlanException("not an applicable frontier plan"));

        mockMvc().perform(post("/api/v1/solutions/9/pareto/100/apply")
                        .requestAttr("branchId", BRANCH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("0042201"));
    }
}
