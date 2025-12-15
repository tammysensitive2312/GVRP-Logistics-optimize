package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.OptimizationJobService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/solutions")
@RequiredArgsConstructor
public class SolutionPlanningController {
    private final OptimizationJobService jobService;
    /**
     * Submit optimization job
     * POST /api/solutions/plan
     */
    @PostMapping("/plan")
    public ResponseEntity<OptimizationJobDTO> submitRoutePlanning(
            @Valid @RequestBody RoutePlanningRequest request) {

//        log.info("=== Route Planning Request ===");
//        log.info("User: {}", CurrentUserUtil.getCurrentUsername());
//        log.info("Orders: {}, Vehicles: {}",
//                request.getOrderIds().size(),
//                request.getVehicleIds().size());

        Long userId = CurrentUserUtil.getCurrentUserId();
        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OptimizationJobDTO job = jobService.submitJob(request, branchId, userId);

//        log.info("✓ Job #{} submitted", job.getId());

        return ResponseEntity.accepted().body(job);
    }

    /**
     * Get current running job
     * GET /api/solutions/jobs/current
     */
    @GetMapping("/jobs/current")
    public ResponseEntity<OptimizationJobDTO> getCurrentJob() {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        return jobService.getCurrentRunningJob(branchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Get job history
     * GET /api/solutions/jobs/history?limit=10
     */
    @GetMapping("/jobs/history")
    public ResponseEntity<List<OptimizationJobDTO>> getJobHistory(
            @RequestParam(defaultValue = "10") int limit) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();
        List<OptimizationJobDTO> jobs = jobService.getJobHistory(branchId, limit);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get job by ID
     * GET /api/solutions/jobs/{id}
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<OptimizationJobDTO> getJobById(
            @PathVariable Long id,
            Authentication authentication) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OptimizationJobDTO job = jobService.getJobById(id, branchId);

        return ResponseEntity.ok(job);
    }

    /**
     * Cancel running job
     * DELETE /api/solutions/jobs/{id}
     */
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> cancelJob(
            @PathVariable Long id) {

        log.info("Cancelling job #{}", id);

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        jobService.cancelJob(id, branchId);

        log.info("✓ Job #{} cancelled", id);

        return ResponseEntity.noContent().build();
    }

}
