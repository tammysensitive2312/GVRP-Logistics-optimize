package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.BulkEditJobRequest;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.OptimizationJobService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class OptimizationJobController {
    private final OptimizationJobService jobService;

    @PostMapping("/plan")
    public ResponseEntity<OptimizationJobDTO> submitRoutePlanning(
            @Valid @RequestBody RoutePlanningRequest request) {

        Long userId = CurrentUserUtil.getCurrentUserId();
        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OptimizationJobDTO job = jobService.submitJob(request, branchId, userId);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping
    public ResponseEntity<List<OptimizationJobDTO>> getJobHistory(
            @RequestParam(defaultValue = "10") int limit) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();
        List<OptimizationJobDTO> jobs = jobService.getJobHistory(branchId, limit);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OptimizationJobDTO> getJobById(
            @PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        OptimizationJobDTO job = jobService.getJobById(id, branchId);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> getJobProgress(@PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        Map<String, Object> progress = jobService.getJobProgress(id, branchId);
        if (progress == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(progress);
    }

    @PutMapping
    public ResponseEntity<Void> cancelJob(
            @Valid @RequestBody BulkEditJobRequest request
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        jobService.cancelJobs(request.getIds(), branchId);
        return ResponseEntity.noContent().build();
    }

}
