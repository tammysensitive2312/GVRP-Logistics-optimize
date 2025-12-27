package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.service.SolutionService;

@Slf4j
@RestController
@RequestMapping("/api/v1/solutions")
@RequiredArgsConstructor
public class SolutionController {
    private final SolutionService solutionService;

    @GetMapping("/{id}")
    public ResponseEntity<SolutionDetailResponseDTO> getSolutionById(@PathVariable Long id) {
        SolutionDetailResponseDTO response = solutionService.getSolutionDetail(id);
        return ResponseEntity.ok(response);
    }
}
