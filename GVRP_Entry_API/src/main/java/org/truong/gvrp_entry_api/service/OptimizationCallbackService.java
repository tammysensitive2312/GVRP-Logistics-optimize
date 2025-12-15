package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationCallbackService {

    private final OptimizationJobRepository jobRepository;
    private final SolutionRepository solutionRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final EmailService emailService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Handle optimization completion callback
     * 🔧 CHANGES:
     * - Uncommented saveSolution() logic
     * - Uncommented job status update
     * - Added error handling
     */
    @Transactional
    public void handleCompletion(EngineCallbackRequest.CompletionCallback callback) {

        try {
            // 1. Load job
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

            // 2. Validate job can receive completion
            if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
                log.warn("⚠️  Job #{} is not in PROCESSING state (current: {}). Ignoring callback.",
                        job.getId(), job.getStatus());
                return;
            }

            // 🔧 CHANGE: Uncommented this block
            // 3. Save solution
            Solution solution = saveSolution(job, callback);

            // 4. Update job
            job.setStatus(OptimizationJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());

            // 🔧 CHANGE: Add null check for externalJobId
            if (callback.getExternalJobId() != null) {
                job.setExternalJobId(callback.getExternalJobId());
            }

            job.setSolution(solution);
            jobRepository.save(job);

            // 5. Send success email
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        emailService.sendOptimizationSuccessEmail(
                                job.getCreatedBy().getId(),
                                job.getId(),
                                solution.getId());
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to trigger async email: {}", e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            log.error("❌ Failed to process completion callback for job #{}: {}",
                    callback.getJobId(), e.getMessage(), e);
            throw e; // Re-throw to return 500 to Engine API
        }
    }

    /**
     * Handle optimization failure callback
     *
     * 🔧 CHANGES:
     * - Added error handling
     * - Made email sending optional
     */
    @Transactional
    public void handleFailure(EngineCallbackRequest.FailureCallback callback) {

        log.warn("📥 Processing failure callback for job #{}", callback.getJobId());

        try {
            // 1. Load job
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

            // 2. Validate job can receive failure
            if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
                log.warn("⚠️  Job #{} is not in PROCESSING state (current: {}). Ignoring callback.",
                        job.getId(), job.getStatus());
                return;
            }

            // 3. Update job
            job.setStatus(OptimizationJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());

            // 🔧 CHANGE: Add null check
            if (callback.getExternalJobId() != null) {
                job.setExternalJobId(callback.getExternalJobId());
            }

            job.setErrorMessage(callback.getErrorMessage());
            jobRepository.save(job);

            log.info("✅ Job #{} marked as FAILED: {}", job.getId(), callback.getErrorMessage());

            // 4. Send failure email
            // 🔧 CHANGE: Made optional
            try {
                Exception error = new RuntimeException(callback.getErrorMessage());
                emailService.sendOptimizationFailureEmail(job.getCreatedBy(), job, error);
            } catch (Exception e) {
                log.warn("⚠️  Failed to send failure email: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Failed to process failure callback for job #{}: {}",
                    callback.getJobId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handle progress update callback (optional)
     *
     * 🔧 CHANGES:
     * - Added error handling
     */
    @Transactional
    public void handleProgressUpdate(EngineCallbackRequest.ProgressCallback callback) {

        try {
            // 1. Load job
            OptimizationJob job = jobRepository.findById(callback.getJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

            // 2. Update progress (if you have progress field)
            // 🔧 TODO: Add progress field to OptimizationJob entity if needed
            // job.setProgress(callback.getProgress());
            // jobRepository.save(job);

            log.debug("📊 Job #{} progress: {}%", job.getId(), callback.getProgress());

            // 3. Optional: Send WebSocket notification to frontend
            // 🔧 TODO: Implement WebSocket if needed
            // webSocketService.sendProgressUpdate(job.getBranch().getId(), callback);

        } catch (Exception e) {
            log.warn("⚠️  Failed to process progress update for job #{}: {}",
                    callback.getJobId(), e.getMessage());
            // Don't throw - progress updates are optional
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Save solution with routes and segments
     *
     * 🔧 CHANGES:
     * - Uncommented entire method
     * - Added null checks
     * - Added error handling
     */
    private Solution saveSolution(OptimizationJob job, EngineCallbackRequest.CompletionCallback callback) {

        EngineCallbackRequest.SolutionData solutionData = callback.getSolution();

        log.debug("💾 Saving solution for job #{}...", job.getId());

        // 1. Create solution entity
        Solution solution = Solution.builder()
                .job(job)
                .branch(job.getBranch())
                .type(SolutionType.ENGINE_GENERATED)
                .totalDistance(solutionData.getTotalDistance())
                .totalCost(solutionData.getTotalCost())
                .totalCO2(solutionData.getTotalCO2())
                .totalTime(solutionData.getTotalTime())
                .totalVehiclesUsed(solutionData.getTotalVehiclesUsed())
                .servedOrders(solutionData.getServedOrders())
                .unservedOrders(solutionData.getUnservedOrders())
                .status(calculateSolutionStatus(callback))
                .build();

        solution = solutionRepository.save(solution);

        log.debug("✓ Created solution #{}", solution.getId());

        // 2. Save routes
        List<Route> routes = new ArrayList<>();

        // 🔧 CHANGE: Add null check
        if (solutionData.getRoutes() != null) {
            for (EngineCallbackRequest.RouteData routeData : solutionData.getRoutes()) {
                try {
                    Route route = saveRoute(solution, routeData);
                    routes.add(route);
                } catch (Exception e) {
                    log.error("❌ Failed to save route for vehicle {}: {}",
                            routeData.getVehicleId(), e.getMessage(), e);
                    // Continue with other routes
                }
            }
        }

        solution.setRoutes(routes);

        log.debug("✓ Saved {} routes for solution #{}", routes.size(), solution.getId());

        return solution;
    }

    private SolutionStatus calculateSolutionStatus(EngineCallbackRequest.CompletionCallback callback) {

        EngineCallbackRequest.SolutionData solutionData = callback.getSolution();

        Integer servedOrders = solutionData.getServedOrders();
        Integer unservedOrders = solutionData.getUnservedOrders();
        List<EngineCallbackRequest.RouteData> routes = solutionData.getRoutes();

        if (servedOrders == null || unservedOrders == null || routes == null) {
            // If the core metrics are null, treat this as a data error.
            // You can choose either INFEASIBLE or log WARN and return PARTIAL_SUCCESS.
            // Here we choose INFEASIBLE to force the Engine's output data to be checked..status()
            return SolutionStatus.INFEASIBLE;
        }

        if (routes.isEmpty() || servedOrders == 0) {
            return SolutionStatus.INFEASIBLE;
        }

        if (servedOrders > 0 && unservedOrders == 0) {
            return SolutionStatus.SUCCESS;
        }

        if (servedOrders > 0 && unservedOrders > 0) {
            return SolutionStatus.PARTIAL_SUCCESS;
        }

        return SolutionStatus.INFEASIBLE;
    }

    /**
     * Save route with segments
     *
     * 🔧 CHANGES:
     * - Uncommented entire method
     * - Added null checks
     * - Fixed field mapping
     */
    private Route saveRoute(Solution solution, EngineCallbackRequest.RouteData routeData) {

        log.debug("💾 Saving route for vehicle #{}...", routeData.getVehicleId());

        // 1. Load vehicle
        Vehicle vehicle = vehicleRepository.findById(routeData.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found", "Vehicle"));

        // 2. Create route
        Route route = Route.builder()
                .solution(solution)
                .vehicle(vehicle)

                // 🔧 CHANGE: Add null check and default value
                .routeOrder(routeData.getRouteOrder() != null ? routeData.getRouteOrder() : 1)

                .distance(routeData.getDistance())

                // 🔧 CHANGE: Map duration to serviceTime (assuming this is the field name)
                .serviceTime(routeData.getDuration())

                .co2Emission(routeData.getCo2Emission())
                .orderCount(routeData.getOrderCount())
                .loadUtilization(routeData.getLoadUtilization())
                .build();

        route = routeRepository.save(route);

        log.debug("✓ Created route #{}", route.getId());

        // 3. Save segments
        List<RouteStop> segments = new ArrayList<>();

        // 🔧 CHANGE: Add null check
        if (routeData.getStops() != null) {
            for (EngineCallbackRequest.StopData stopData : routeData.getStops()) {
                try {
                    RouteStop stop = saveStop(route, stopData);
                    segments.add(stop);
                } catch (Exception e) {
                    log.error("❌ Failed to save segment #{}: {}",
                            stopData.getSequenceNumber(), e.getMessage(), e);
                    // Continue with other segments
                }
            }
        }

        route.setSegments(segments);

        log.debug("✓ Saved {} segments for route #{}", segments.size(), route.getId());

        return route;
    }


    private RouteStop saveStop(Route route, EngineCallbackRequest.StopData stopData) {

        // Load order if type = ORDER/CUSTOMER
        Order order = null;
        if (("ORDER".equals(stopData.getType()) || "CUSTOMER".equals(stopData.getType()))
                && stopData.getOrderId() != null) {
            order = orderRepository.findById(stopData.getOrderId()).orElse(null);
        }

        // Parse times
        LocalTime arrivalTime = parseTime(stopData.getArrivalTime());
        LocalTime departureTime = parseTime(stopData.getDepartureTime());

        // Map LocationType
        LocationType locationType = mapLocationType(stopData.getType());

        // Create stop
        RouteStop stop = RouteStop.builder()
                .route(route)
                .sequenceNumber(stopData.getSequenceNumber())
                .type(locationType)
                .order(order)
                .locationId(stopData.getLocationId())
                .locationName(stopData.getLocationName())
                .address(stopData.getAddress())
                // .location(createPoint(stopData.getLongitude(), stopData.getLatitude()))
                .arrivalTime(arrivalTime)
                .departureTime(departureTime)
                .serviceTime(stopData.getWaitTime())
                .waitTime(stopData.getWaitTime())
                .demand(stopData.getDemand())
                .loadAfter(stopData.getLoadAfter())
                .build();

        return routeStopRepository.save(stop);
    }

    private LocationType mapLocationType(String type) {
        if ("CUSTOMER".equals(type)) {
            return LocationType.ORDER;
        }
        try {
            return LocationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown location type: {}, defaulting to ORDER", type);
            return LocationType.ORDER;
        }
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }


    /**
     * Parse time string (HH:mm:ss) to LocalTime
     *
     * 🔧 CHANGES:
     * - Added more flexible parsing (handles HH:mm format too)
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            // Try HH:mm:ss format first
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (Exception e) {
            try {
                // 🔧 CHANGE: Try HH:mm format as fallback
                return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception e2) {
                log.warn("⚠️  Failed to parse time: {}", timeStr);
                return null;
            }
        }
    }
}