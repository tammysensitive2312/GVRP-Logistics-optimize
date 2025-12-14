package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.*;

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
    private final RouteSegmentRepository routeSegmentRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final EmailService emailService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Handle optimization completion callback
     */
    @Transactional
    public void handleCompletion(EngineCallbackRequest.CompletionCallback callback) {

        log.info("Processing completion callback for job #{}", callback.getJobId());

        // 1. Load job
        OptimizationJob job = jobRepository.findById(callback.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

        // 2. Validate job can receive completion
        if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
            log.warn("Job #{} is not in PROCESSING state (current: {}). Ignoring callback.",
                    job.getId(), job.getStatus());
            return;
        }

        // 3. Save solution
//        Solution solution = saveSolution(job, callback);

        // 4. Update job
//        job.setStatus(OptimizationJobStatus.COMPLETED);
//        job.setCompletedAt(LocalDateTime.now());
//        job.setExternalJobId(callback.getExternalJobId());
//        job.setSolution(solution);
//        jobRepository.save(job);
//
//        log.info("✓ Job #{} completed. Solution #{} saved with {} routes",
//                job.getId(), solution.getId(), solution.getRoutes().size());
//
//        // 5. Send success email
//        emailService.sendOptimizationSuccessEmail(job.getCreatedBy(), job, solution);
    }

    /**
     * Handle optimization failure callback
     */
    @Transactional
    public void handleFailure(EngineCallbackRequest.FailureCallback callback) {

        log.warn("Processing failure callback for job #{}", callback.getJobId());

        // 1. Load job
        OptimizationJob job = jobRepository.findById(callback.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

        // 2. Validate job can receive failure
        if (job.getStatus() != OptimizationJobStatus.PROCESSING) {
            log.warn("Job #{} is not in PROCESSING state. Ignoring callback.", job.getId());
            return;
        }

        // 3. Update job
        job.setStatus(OptimizationJobStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        job.setExternalJobId(callback.getExternalJobId());
        job.setErrorMessage(callback.getErrorMessage());
        jobRepository.save(job);

        log.info("✓ Job #{} marked as FAILED: {}", job.getId(), callback.getErrorMessage());

        // 4. Send failure email
        Exception error = new RuntimeException(callback.getErrorMessage());
        emailService.sendOptimizationFailureEmail(job.getCreatedBy(), job, error);
    }

    /**
     * Handle progress update callback (optional)
     */
    @Transactional
    public void handleProgressUpdate(EngineCallbackRequest.ProgressCallback callback) {

        // 1. Load job
        OptimizationJob job = jobRepository.findById(callback.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found", "OptimizationJob"));

        // 2. Update progress (if you have progress field)
        // job.setProgress(callback.getProgress());
        // jobRepository.save(job);

        log.debug("Job #{} progress: {}%", job.getId(), callback.getProgress());

        // 3. Optional: Send WebSocket notification to frontend
        // webSocketService.sendProgressUpdate(job.getBranch().getId(), callback);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Save solution with routes and segments
     */
//    private Solution saveSolution(OptimizationJob job, EngineCallbackRequest.CompletionCallback callback) {
//
//        EngineCallbackRequest.SolutionData solutionData = callback.getSolution();
//
//        // 1. Create solution entity
//        Solution solution = Solution.builder()
//                .job(job)
//                .branch(job.getBranch())
//                .type(SolutionType.ENGINE_GENERATED)
//                .totalDistance(solutionData.getTotalDistance())
//                .totalCost(solutionData.getTotalCost())
//                .totalCO2(solutionData.getTotalCO2())
//                .totalTime(solutionData.getTotalTime())
//                .totalServiceTime(solutionData.getTotalServiceTime())
//                .totalVehiclesUsed(solutionData.getTotalVehiclesUsed())
//                .servedOrders(solutionData.getServedOrders())
//                .unservedOrders(solutionData.getUnservedOrders())
//                .numberOfRoutes(solutionData.getRoutes().size())
//                .build();
//
//        solution = solutionRepository.save(solution);
//
//        log.debug("Created solution #{}", solution.getId());
//
//        // 2. Save routes
//        List<Route> routes = new ArrayList<>();
//
//        for (EngineCallbackRequest.RouteData routeData : solutionData.getRoutes()) {
//            Route route = saveRoute(solution, routeData);
//            routes.add(route);
//        }
//
//        solution.setRoutes(routes);
//
//        log.debug("Saved {} routes for solution #{}", routes.size(), solution.getId());
//
//        return solution;
//    }

    /**
     * Save route with segments
     */
//    private Route saveRoute(Solution solution, EngineCallbackRequest.RouteData routeData) {
//
//        // 1. Load vehicle
//        Vehicle vehicle = vehicleRepository.findById(routeData.getVehicleId())
//                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found", "Vehicle"));
//
//        // 2. Create route
//        Route route = Route.builder()
//                .solution(solution)
//                .vehicle(vehicle)
//                .routeOrder(routeData.getRouteOrder())
//                .distance(routeData.getDistance())
//                .serviceTime(routeData.getDuration())
//                .co2Emission(routeData.getCo2Emission())
//                .orderCount(routeData.getOrderCount())
//                .loadUtilization(routeData.getLoadUtilization())
//                .build();
//
//        route = routeRepository.save(route);
//
//        // 3. Save segments
//        List<RouteSegment> segments = new ArrayList<>();
//
//        for (EngineCallbackRequest.StopData stopData : routeData.getStops()) {
//            RouteSegment segment = saveSegment(route, stopData);
//            segments.add(segment);
//        }
//
//        route.setSegments(segments);
//
//        return route;
//    }

    /**
     * Save route segment
     */
//    private RouteSegment saveSegment(Route route, EngineCallbackRequest.StopData stopData) {
//
//        // Load order if type = ORDER
//        Order order = null;
//        if ("ORDER".equals(stopData.getType()) && stopData.getOrderId() != null) {
//            order = orderRepository.findById(stopData.getOrderId())
//                    .orElse(null);
//        }
//
//        // Parse times
//        LocalTime arrivalTime = parseTime(stopData.getArrivalTime());
//        LocalTime departureTime = parseTime(stopData.getDepartureTime());
//
//        // Create segment
//        RouteSegment segment = RouteSegment.builder()
//                .route(route)
//                .sequenceNumber(stopData.getSequenceNumber())
//                .type(LocationType.valueOf(stopData.getType()))
//                .order(order)
//                .locationId(stopData.getLocationId())
//                .locationName(stopData.getLocationName())
//                .address(stopData.getAddress())
//                // TODO: Create Point from lon/lat
//                // .location(createPoint(stopData.getLongitude(), stopData.getLatitude()))
//                .arrivalTime(arrivalTime)
//                .departureTime(departureTime)
//                .serviceTime(stopData.getServiceTime())
//                .waitTime(stopData.getWaitTime())
//                .demand(stopData.getDemand())
//                .loadAfter(stopData.getLoadAfter())
//                .build();
//
//        return routeSegmentRepository.save(segment);
//    }

    /**
     * Parse time string (HH:mm:ss) to LocalTime
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return null;
        }
    }
}
