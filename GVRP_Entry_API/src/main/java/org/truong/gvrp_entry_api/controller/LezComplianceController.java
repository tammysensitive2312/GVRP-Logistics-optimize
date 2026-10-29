package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.RouteGeometryDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionComplianceDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.lez.ComplianceReadService;

/**
 * LEZ compliance panel and route geometry (LEZ-001 N8, D1/D2).
 *
 * <p>PLANNER only through {@code SecurityConfig} ({@code /api/v1/**}); the branch
 * always comes from the authenticated principal, so another branch's id answers
 * 404 exactly like a missing one. Rechecking is a {@code POST} because it writes.
 */
@RestController
@RequiredArgsConstructor
public class LezComplianceController {

    private final ComplianceReadService complianceReadService;

    /** Per-route compliance state of a solution; {@code STALE} is computed, never written. */
    @GetMapping("/api/v1/solutions/{id}/compliance")
    public ResponseEntity<SolutionComplianceDTO> compliance(@PathVariable Long id) {
        return ResponseEntity.ok(complianceReadService.compliance(
                id, CurrentUserUtil.getCurrentBranchId()));
    }

    /** Recomputes every route of the solution with the current rules and stores it. */
    @PostMapping("/api/v1/solutions/{id}/compliance/recheck")
    public ResponseEntity<SolutionComplianceDTO> recheck(@PathVariable Long id) {
        return ResponseEntity.ok(complianceReadService.recheck(
                id, CurrentUserUtil.getCurrentBranchId()));
    }

    /** Leg polylines and turn instructions of one route, for the planner map. */
    @GetMapping("/api/v1/routes/{id}/geometry")
    public ResponseEntity<RouteGeometryDTO> geometry(@PathVariable Long id) {
        return ResponseEntity.ok(complianceReadService.geometry(
                id, CurrentUserUtil.getCurrentBranchId()));
    }
}
