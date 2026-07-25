package org.truong.gvrp_engine_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_engine_api.job.JobRegistry;
import org.truong.gvrp_engine_api.model.EngineOptimizationRequest;
import org.truong.gvrp_engine_api.model.EngineOptimizationResponse;
import org.truong.gvrp_engine_api.service.CallbackService;
import org.truong.gvrp_engine_api.service.OptimizationService;
import org.truong.gvrp_engine_api.service.ResultSpool;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/optimization")
@RequiredArgsConstructor
public class OptimizationController {
    private final OptimizationService optimizationService;
    private final JobRegistry jobRegistry;
    private final ResultSpool resultSpool;
    private final CallbackService callbackService;

    @PostMapping
    public ResponseEntity<EngineOptimizationResponse> optimize(
            @Valid @RequestBody EngineOptimizationRequest request) {

        try {
            // Validate request
            validateRequest(request);

            // Submit to async processing
            optimizationService.optimizeAsync(request);

            // Return accepted response
            EngineOptimizationResponse response = new EngineOptimizationResponse();
            response.setExternalJobId("engine-" + request.getJobId());
            response.setStatus("ACCEPTED");
            response.setMessage("Optimization job accepted and processing");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ Validation error: {}", e.getMessage());

            EngineOptimizationResponse errorResponse = new EngineOptimizationResponse();
            errorResponse.setExternalJobId("engine-" + request.getJobId());
            errorResponse.setStatus("REJECTED");
            errorResponse.setMessage(e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("❌ Unexpected error processing job #{}", request.getJobId(), e);

            EngineOptimizationResponse errorResponse = new EngineOptimizationResponse();
            errorResponse.setExternalJobId("engine-" + request.getJobId());
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage("Internal server error: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    private void validateRequest(EngineOptimizationRequest request) {
        if (request.getJobId() == null) {
            throw new IllegalArgumentException("Job ID is required");
        }

        if (request.getOrders() == null || request.getOrders().isEmpty()) {
            throw new IllegalArgumentException("Orders list cannot be empty");
        }

        if (request.getVehicles() == null || request.getVehicles().isEmpty()) {
            throw new IllegalArgumentException("Vehicles list cannot be empty");
        }

        if (request.getDepots() == null || request.getDepots().isEmpty()) {
            throw new IllegalArgumentException("Depots list cannot be empty");
        }

        if (request.getVehicleTypes() == null || request.getVehicleTypes().isEmpty()) {
            throw new IllegalArgumentException("Vehicle types list cannot be empty");
        }

        // Validate coordinates
        for (var order : request.getOrders()) {
            if (order.getLatitude() == null || order.getLongitude() == null) {
                throw new IllegalArgumentException("Order " + order.getOrderCode() + " missing coordinates");
            }
        }

        for (var depot : request.getDepots()) {
            if (depot.getLatitude() == null || depot.getLongitude() == null) {
                throw new IllegalArgumentException("Depot " + depot.getName() + " missing coordinates");
            }
        }
    }

    /**
     * Lấy tiến độ hiện tại của job (poll). Trả 404 nếu job không tồn tại
     * (chưa chạy / đã bị evict sau TTL).
     */
    @GetMapping("/{jobId}/progress")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable Long jobId) {
        JobRegistry.JobHandle h = jobRegistry.get(jobId);
        if (h == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("status", h.status());
        body.put("phase", h.phase());
        body.put("startedAt", h.startedAt());
        body.put("finishedAt", h.finishedAt());

        JobRegistry.ProgressSnapshot snap = h.snapshot();
        if (snap != null) {
            body.put("iteration", snap.iteration());
            body.put("maxIterations", snap.maxIterations());
            body.put("percent", snap.maxIterations() > 0
                    ? (100 * snap.iteration() / snap.maxIterations()) : 0);
            body.put("bestCost", snap.bestCost());
            body.put("routes", snap.routes());
            body.put("unassigned", snap.unassigned());
            body.put("elapsedSeconds", snap.elapsedSeconds());
            body.put("updatedAt", snap.updatedAt());
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Yêu cầu hủy job. 202 nếu đã ghi nhận (solver sẽ dừng ở ranh giới vòng kế),
     * 404 nếu không tồn tại, 409 nếu job đã kết thúc.
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long jobId) {
        boolean accepted = jobRegistry.requestCancel(jobId);
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);

        if (accepted) {
            body.put("status", "CANCEL_REQUESTED");
            return ResponseEntity.accepted().body(body);
        }

        JobRegistry.JobHandle h = jobRegistry.get(jobId);
        if (h == null) {
            return ResponseEntity.notFound().build();
        }
        body.put("status", h.status());
        body.put("message", "Job đã kết thúc, không thể hủy");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Kéo kết quả đã lưu trong spool — dùng khi callback thất bại vĩnh viễn, hoặc để
     * đối chiếu với dữ liệu Entry đã ghi. Tìm cả trong pending/, poison/ và sent/.
     *
     * <p>Khác với {@code /progress} (đọc JobRegistry trong RAM, mất sau restart),
     * endpoint này đọc từ đĩa nên sống sót qua restart.
     */
    @GetMapping("/{jobId}/result")
    public ResponseEntity<Map<String, Object>> result(@PathVariable Long jobId) {
        return resultSpool.load(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Ép gửi lại callback ngay, không chờ sweeper. Dùng sau khi vừa vá xong Entry
     * và muốn nạp lại kết quả mà không phải chạy lại job.
     */
    @PostMapping("/{jobId}/resend")
    public ResponseEntity<Map<String, Object>> resend(@PathVariable Long jobId) {
        Optional<Map<String, Object>> payload = resultSpool.load(jobId);
        if (payload.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        boolean delivered = callbackService.deliver(jobId, payload.get());

        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("delivered", delivered);
        return ResponseEntity
                .status(delivered ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                .body(body);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "GVRP Engine API");
        health.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(health);
    }
}
