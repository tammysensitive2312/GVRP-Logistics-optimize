package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.request.CustomizeRoutesRequest;
import org.truong.gvrp_entry_api.dto.response.RouteEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.service.RouteCustomizationService;
import org.truong.gvrp_entry_api.service.SolutionService;

@Slf4j
@RestController
@RequestMapping("/api/v1/solutions")
@RequiredArgsConstructor
public class SolutionController {
    private final SolutionService solutionService;
    private final RouteCustomizationService routeCustomizationService;

    @GetMapping("/{id}")
    public ResponseEntity<SolutionDetailResponseDTO> getSolutionById(@PathVariable Long id) {
        SolutionDetailResponseDTO response = solutionService.getSolutionDetail(id);
        return ResponseEntity.ok(response);
    }

    /** Id of the newest revision in the chain (recovery after 409). */
    @GetMapping("/{id}/latest-revision")
    public ResponseEntity<java.util.Map<String, Long>> latestRevision(@PathVariable Long id) {
        return ResponseEntity.ok(java.util.Map.of(
                "id", routeCustomizationService.latestRevisionId(id)));
    }

    /** Preview of manual sequences: no persistence. */
    @PostMapping("/{id}/evaluate")
    public ResponseEntity<RouteEvaluateResponseDTO> evaluateRoutes(
            @PathVariable Long id, @RequestBody CustomizeRoutesRequest request) {
        return ResponseEntity.ok(routeCustomizationService.evaluate(id, request));
    }

    /** Save manual sequences as a new solution revision (re-evaluated server-side). */
    @PostMapping("/{id}/revisions")
    public ResponseEntity<SolutionDetailResponseDTO> saveRevision(
            @PathVariable Long id, @RequestBody CustomizeRoutesRequest request) {
        return ResponseEntity.ok(routeCustomizationService.saveRevision(id, request));
    }
}
