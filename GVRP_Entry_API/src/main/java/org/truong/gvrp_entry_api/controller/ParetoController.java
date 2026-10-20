package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.ParetoFrontierDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.ParetoService;

/**
 * Pareto frontier of a solution's job (PARETO-001).
 *
 * <p>Under {@code /api/v1/solutions/**}, so the existing security rules make both
 * endpoints PLANNER-only without a {@code SecurityConfig} change (no permitAll).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/solutions")
@RequiredArgsConstructor
public class ParetoController {

    private final ParetoService paretoService;

    /** Candidates of the job behind the solution's revision chain. */
    @GetMapping("/{id}/pareto")
    public ResponseEntity<ParetoFrontierDTO> getFrontier(@PathVariable Long id) {
        return ResponseEntity.ok(paretoService.getFrontier(id, CurrentUserUtil.getCurrentBranchId()));
    }

    /** Apply a frontier candidate as a new {@code PARETO_ALTERNATIVE} revision. */
    @PostMapping("/{id}/pareto/{candidateId}/apply")
    public ResponseEntity<SolutionDetailResponseDTO> apply(
            @PathVariable Long id, @PathVariable Long candidateId) {
        return ResponseEntity.ok(paretoService.apply(
                id, candidateId, CurrentUserUtil.getCurrentBranchId()));
    }
}
