package org.truong.gvrp_engine_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_engine_api.model.EngineOptimizationRequest;
import org.truong.gvrp_engine_api.model.EngineOptimizationResponse;
import org.truong.gvrp_engine_api.service.OptimizationService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/optimization")
@RequiredArgsConstructor
public class OptimizationController {
    private final OptimizationService optimizationService;

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
            response.setJobId(request.getJobId());
            response.setStatus("ACCEPTED");
            response.setMessage("Optimization job accepted and processing");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ Validation error: {}", e.getMessage());

            EngineOptimizationResponse errorResponse = new EngineOptimizationResponse();
            errorResponse.setJobId(request.getJobId());
            errorResponse.setStatus("REJECTED");
            errorResponse.setMessage(e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("❌ Unexpected error processing job #{}", request.getJobId(), e);

            EngineOptimizationResponse errorResponse = new EngineOptimizationResponse();
            errorResponse.setJobId(request.getJobId());
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "GVRP Engine API");
        health.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(health);
    }
}
