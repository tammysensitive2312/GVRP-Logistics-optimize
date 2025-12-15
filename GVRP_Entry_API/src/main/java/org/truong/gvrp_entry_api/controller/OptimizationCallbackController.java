package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.service.OptimizationCallbackService;

@Slf4j
@RestController
@RequestMapping("/api/v1/solutions/callbacks")
@RequiredArgsConstructor
public class OptimizationCallbackController {

    private final OptimizationCallbackService callbackService;

    /**
     * Callback khi optimization hoàn thành thành công
     * POST /api/solutions/callbacks/complete
     */
    @PostMapping("/complete")
    public ResponseEntity<Void> onOptimizationComplete(
            @Valid @RequestBody EngineCallbackRequest.CompletionCallback request) {

        log.info("=== Optimization Completion Callback ===");
        log.info("Job ID: {}", request.getJobId());
        log.info("Routes: {}", request.getSolution().getRoutes().size());

        callbackService.handleCompletion(request);

        log.info("✓ Callback processed successfully");

        return ResponseEntity.ok().build();
    }

    /**
     * Callback khi optimization thất bại
     * POST /api/solutions/callbacks/failed
     */
    @PostMapping("/failed")
    public ResponseEntity<Void> onOptimizationFailed(
            @Valid @RequestBody EngineCallbackRequest.FailureCallback request) {

        log.warn("=== Optimization Failure Callback ===");
        log.warn("Job ID: {}", request.getJobId());
        log.warn("Error: {}", request.getErrorMessage());

        callbackService.handleFailure(request);

        log.info("✓ Failure callback processed");

        return ResponseEntity.ok().build();
    }

    /**
     * Callback để update progress (optional)
     * POST /api/solutions/callbacks/progress
     */
    @PostMapping("/progress")
    public ResponseEntity<Void> onProgressUpdate(
            @Valid @RequestBody EngineCallbackRequest.ProgressCallback request) {

        log.debug("Progress update: Job #{}, Progress: {}%",
                request.getJobId(), request.getProgress());

        callbackService.handleProgressUpdate(request);

        return ResponseEntity.ok().build();
    }
}