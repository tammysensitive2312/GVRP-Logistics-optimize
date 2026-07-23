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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class OptimizationJobController {
    private final OptimizationJobService jobService;
    /**
     * Submit optimization job
     * POST /api/jobs/plan
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
     * GET /api/jobs/current
     */
    @GetMapping("/current")
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
    @GetMapping
    public ResponseEntity<List<OptimizationJobDTO>> getJobHistory(
            @RequestParam(defaultValue = "10") int limit) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();
        List<OptimizationJobDTO> jobs = jobService.getJobHistory(branchId, limit);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get job by ID
     * GET /api/jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OptimizationJobDTO> getJobById(
            @PathVariable Long id,
            Authentication authentication) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OptimizationJobDTO job = jobService.getJobById(id, branchId);

        return ResponseEntity.ok(job);
    }

    /**
     * Get real-time progress of a running job (poll) — proxy xuống engine.
     * GET /api/v1/jobs/{id}/progress
     * 200 kèm tiến độ; 204 nếu engine không còn giữ job (chưa chạy / đã kết thúc & evict).
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> getJobProgress(@PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        Map<String, Object> progress = jobService.getJobProgress(id, branchId);
        if (progress == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * Cancel running job
     * DELETE /api/jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelJob(
            @PathVariable Long id) {

        log.info("Cancelling job #{}", id);

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        jobService.cancelJob(id, branchId);

        log.info("✓ Job #{} cancelled", id);

        return ResponseEntity.noContent().build();
    }

}
