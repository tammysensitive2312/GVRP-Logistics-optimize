package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.AlgorithmStartsListener;
import com.graphhopper.jsprit.core.algorithm.listener.IterationStartsListener;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.termination.TimeTermination;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.HardRouteConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.truong.gvrp_engine_api.service.OptimizationResultExtractor.extractRouteDetails;
import static org.truong.gvrp_engine_api.service.OptimizationResultExtractor.extractUnassignedOrders;
import static org.truong.gvrp_engine_api.utils.AppConstant.DEMAND_SCALE;

/**
 * GREEN VRP Optimization Service - REFACTORED & BUG-FREE
 * <p>
 * Version: 2.0
 * Date: 2025-01-17
 * <p>
 * KEY IMPROVEMENTS FROM v1.0:
 * ✅ Fixed: Cost matrix now contains ONLY physical distance/time
 * ✅ Fixed: CO2 cost is vehicle-dependent (not averaged)
 * ✅ Fixed: No more mixed units (meters + VND)
 * ✅ Fixed: No more double-counting of costs
 * ✅ Added: Optional Pareto frontier analysis
 * ✅ Added: Proper separation of solver cost vs business metrics
 * <p>
 * ARCHITECTURE:
 * Cost Layer (Jsprit) → Metrics Layer → Business Layer
 *
 * @author Truong
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class OptimizationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DistanceMatrixService distanceMatrixService;
    private final CallbackService callbackService;

    // ==================== ASYNC ENTRY POINT ====================

    /**
     * Async optimization entry point (for background processing)
     */
    @Async
    public CompletableFuture<Void> optimizeAsync(EngineOptimizationRequest request) {
        try {
            log.info("🚀 Starting optimization for job #{}", request.getJobId());

            LocalDateTime startTime = LocalDateTime.now();
            OptimizationResult result = optimize(request);
            LocalDateTime endTime = LocalDateTime.now();

            java.time.Duration d = java.time.Duration.between(startTime, endTime);
            log.info("✅ Optimization completed for job #{} in {}m {}s",
                    request.getJobId(),
                    d.toMinutesPart(),
                    d.toSecondsPart());

            callbackService.sendCompletionCallback(request.getJobId(), result);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("❌ Optimization failed for job #{}", request.getJobId(), e);
            callbackService.sendFailureCallback(request.getJobId(), e.getMessage());

            return CompletableFuture.failedFuture(e);
        }
    }

    // ==================== MAIN OPTIMIZATION METHOD ====================

    /**
     * Main optimization method
     * <p>
     * Orchestrates the entire optimization process:
     * 1. Prepare context
     * 2. Calculate distance matrix
     * 3. Choose optimization mode (single vs Pareto)
     * 4. Build VRP
     * 5. Solve
     * 6. Extract results
     */
    public OptimizationResult optimize(EngineOptimizationRequest request) {

        // STEP 1: Prepare Context
        OptimizationContext context = prepareContext(request);

        // STEP 2: Calculate Distance Matrix
        DistanceTimeMatrix matrix = calculateDistanceMatrix(context);

        // STEP 3: Validate Config
        OptimizationConfig config = request.getConfig();
        validateConfig(config);

        // STEP 4: Choose optimization mode
        if (config.getEnableParetoAnalysis() != null && config.getEnableParetoAnalysis()) {
            // ADVANCED MODE: Multi-objective Pareto analysis
            return optimizeMultiObjective(context, matrix, config, request);
        } else {
            // DEFAULT MODE: Single weighted optimization
            return optimizeSingleObjective(context, matrix, config, request);
        }
    }

    // ==================== SINGLE OBJECTIVE OPTIMIZATION (DEFAULT) ====================

    /**
     * Single-run weighted optimization - PRODUCTION DEFAULT
     * <p>
     * Fast, clear result, suitable for real-time optimization
     * Uses weighted sum to combine cost + CO2 into single objective
     */
    private OptimizationResult optimizeSingleObjective(
            OptimizationContext context,
            DistanceTimeMatrix matrix,
            OptimizationConfig config,
            EngineOptimizationRequest request) {

        // Build GREEN VRP
        VehicleRoutingProblem vrp = buildGreenVRP(context, matrix, config);

        // Create and run algorithm
        VehicleRoutingAlgorithm algorithm = createAlgorithm(vrp, context, config);
        addProgressListener(algorithm, request.getJobId(), config);

        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // Calculate real metrics
        SolutionMetrics metrics = SolutionMetricsCalculator.calculate(
                bestSolution,
                context,
                matrix
        );

        // Create solution candidate
        SolutionCandidate selected = new SolutionCandidate(
                "WEIGHTED",
                bestSolution,
                metrics,
                config.getCostWeight(),
                config.getCo2Weight()
        );

        // Extract route details
        OptimizationResultExtractor.RouteExtractionResult routeResult = extractRouteDetails(bestSolution, context, matrix);
        List<UnassignedOrder> unassigned = extractUnassignedOrders(bestSolution, context);

        log.info("✅ Single optimization completed");
        log.info("   Cost: {} VND | CO2: {} kg | Vehicles: {} | Orders: {} / {}",
                metrics.getTotalCostVnd(),
                metrics.getTotalCo2Kg(),
                metrics.getVehiclesUsed(),
                metrics.getOrdersServed(),
                metrics.getOrdersServed() + metrics.getOrdersUnserved());

        return OptimizationResult.single(
                request.getJobId(),
                selected,
                matrix,
                routeResult.routes(),
                unassigned
        );
    }

    // ==================== MULTI-OBJECTIVE OPTIMIZATION (ADVANCED) ====================

    /**
     * Multi-run optimization for Pareto frontier analysis
     * <p>
     * Slower but provides trade-off options between cost and CO2
     * Useful for decision-making and policy analysis
     */
    private OptimizationResult optimizeMultiObjective(
            OptimizationContext context,
            DistanceTimeMatrix matrix,
            OptimizationConfig config,
            EngineOptimizationRequest request) {

        log.info("🎯 Running MULTI-OBJECTIVE optimization (Pareto analysis)");
        log.info("   This will run {} optimization scenarios", ObjectivePreset.values().length);

        List<SolutionCandidate> candidates = new ArrayList<>();

        // Run optimization for each preset
        for (ObjectivePreset preset : ObjectivePreset.values()) {

            log.info("   Running {} optimization...", preset.getDisplayName());

            // Override weights for this run
            OptimizationConfig presetConfig = config.clone();
            presetConfig.setCostWeight(preset.costWeight);
            presetConfig.setCo2Weight(preset.co2Weight);

            // Build and solve VRP
            VehicleRoutingProblem vrp = buildGreenVRP(context, matrix, presetConfig);
            VehicleRoutingAlgorithm algorithm = createAlgorithm(vrp, context, presetConfig);

            Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
            VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

            // Calculate metrics
            SolutionMetrics metrics = SolutionMetricsCalculator.calculate(
                    bestSolution,
                    context,
                    matrix
            );

            candidates.add(new SolutionCandidate(
                    preset.name(),
                    bestSolution,
                    metrics,
                    preset.costWeight,
                    preset.co2Weight
            ));

            log.info("   ✓ {} | Cost: {} VND | CO2: {} kg",
                    preset.name(),
                    metrics.getTotalCostVnd(),
                    metrics.getTotalCo2Kg());
        }

        // Build Pareto frontier
        List<SolutionCandidate> paretoFrontier = buildParetoFrontier(candidates);
        log.info("   📊 Pareto frontier size: {} (from {} candidates)",
                paretoFrontier.size(),
                candidates.size());

        // Select best solution based on user preferences
        SolutionCandidate selected = selectFromPareto(paretoFrontier, config);
        log.info("   ✅ Selected: {} (cost={}, CO2={})",
                selected.getPresetName(),
                selected.getCostWeight(),
                selected.getCo2Weight());

        // Extract route details from selected solution
        OptimizationResultExtractor.RouteExtractionResult result = extractRouteDetails(
                selected.getSolution(),
                context,
                matrix
        );
        List<UnassignedOrder> unassigned = extractUnassignedOrders(
                selected.getSolution(),
                context
        );

        return OptimizationResult.pareto(
                request.getJobId(),
                selected,
                paretoFrontier,
                matrix,
                result.routes(),
                unassigned
        );
    }

    // ==================== VRP BUILDER (GREEN) ====================

    /**
     * Build GREEN VRP with vehicle-dependent CO2 costs
     * <p>
     * KEY CHANGE: CO2 cost is now in vehicleType.costPerDistance
     * Not in the cost matrix!
     */
    private VehicleRoutingProblem buildGreenVRP(
            OptimizationContext context,
            DistanceTimeMatrix matrix,
            OptimizationConfig config) {

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        // Get and normalize weights
        double[] weights = config.getEffectiveWeights();
        double costWeight = weights[0];
        double co2Weight = weights[1];

        log.info("Cost weight: {}, CO2 weight: {}", costWeight, co2Weight);

        // 1. Add Vehicles with GREEN cost
        for (var vehicleDTO : context.vehicleDTOs().values()) {
            VehicleTypeImpl greenVehicleType = GreenVRPCostCalculator.buildGreenVehicleType(
                    context.vehicleTypeDTOs().get(vehicleDTO.getVehicleTypeId()),
                    costWeight,
                    co2Weight
            );

            log.info("Vehicle Type {}: Final CostPerKm configured in Jsprit = {}",
                    greenVehicleType.getTypeId(),
                    greenVehicleType.getVehicleCostParams().perDistanceUnit);

            VehicleImpl jspritVehicle = buildJspritVehicle(
                    vehicleDTO,
                    context,
                    greenVehicleType
            );
            vrpBuilder.addVehicle(jspritVehicle);
        }

        // 2. Add Services (Orders)
        for (var orderDTO : context.orderDTOs().values()) {
            Service jspritService = buildJspritService(orderDTO, context);
            vrpBuilder.addJob(jspritService);
        }

        // 3. Set Physical Cost Matrix (ONLY distance + time)
        VehicleRoutingTransportCostsMatrix costs = GreenVRPCostCalculator.buildPhysicalCostMatrix(
                matrix.distanceMatrix(),
                matrix.timeMatrix(),
                context.allLocations()
        );
        vrpBuilder.setRoutingCost(costs);

        // 4. Fleet Size
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        log.info("✅ GREEN VRP built: {} vehicles, {} jobs | Weights: cost={}, CO2={}",
                context.vehicleDTOs().size(),
                context.orderDTOs().size(),
                costWeight,
                co2Weight);

        return vrpBuilder.build();
    }

    // ==================== BUILD JSPRIT VEHICLE ====================

    private VehicleImpl buildJspritVehicle(
            Vehicle vehicleDTO,
            OptimizationContext context,
            VehicleTypeImpl greenVehicleType) {

        // Get depot locations
        var startDepotDTO = context.depotDTOs().get(vehicleDTO.getStartDepotId());
        var endDepotDTO = context.depotDTOs().get(vehicleDTO.getEndDepotId());

        if (startDepotDTO == null || endDepotDTO == null) {
            throw new IllegalArgumentException("Depot not found for vehicle: " + vehicleDTO.getId());
        }

        Location startLocation = context.allLocations().stream()
                .filter(loc -> loc.getId().equals("depot-" + startDepotDTO.getId()))
                .findFirst()
                .orElseThrow();

        Location endLocation = context.allLocations().stream()
                .filter(loc -> loc.getId().equals("depot-" + endDepotDTO.getId()))
                .findFirst()
                .orElseThrow();

        // Build Vehicle
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder
                .newInstance("vehicle-" + vehicleDTO.getId())
                .setStartLocation(startLocation)
                .setEndLocation(endLocation)
                .setType(greenVehicleType)  // ✅ Use GREEN vehicle type
                .setReturnToDepot(true);

        vehicleBuilder.addSkill("STANDARD");

        // Time Windows
        long earliestStart = 8 * 3600;
        VehicleType vt = context.vehicleTypeDTOs().get(vehicleDTO.getVehicleTypeId());
        double maxDurationHours = vt.getMaxDuration() != null ? vt.getMaxDuration() : 12.0;
        long maxDurationSeconds = (long) (maxDurationHours * 3600);
        long latestArrival = earliestStart + maxDurationSeconds;

        vehicleBuilder
                .setEarliestStart(earliestStart)
                .setLatestArrival(latestArrival);

        return vehicleBuilder.build();
    }

    // ==================== BUILD JSPRIT SERVICE ====================

    private Service buildJspritService(
            Order orderDTO,
            OptimizationContext context) {

        Location orderLocation = context.allLocations().stream()
                .filter(loc -> loc.getId().equals("order-" + orderDTO.getId()))
                .findFirst()
                .orElseThrow();

        int scaledDemand = (int) Math.round(orderDTO.getDemand() * DEMAND_SCALE);

        Service.Builder serviceBuilder = Service.Builder
                .newInstance("order-" + orderDTO.getId())
                .setName(orderDTO.getOrderCode())
                .setLocation(orderLocation)
                .addSizeDimension(0, scaledDemand)
                .setServiceTime(orderDTO.getServiceTime() * 60.0);

        // Time Windows
        if (orderDTO.getTimeWindowStart() != null && orderDTO.getTimeWindowEnd() != null) {
            long startSeconds = parseTimeToSeconds(orderDTO.getTimeWindowStart());
            long endSeconds = parseTimeToSeconds(orderDTO.getTimeWindowEnd());

            serviceBuilder.setTimeWindow(
                    TimeWindow.newInstance(startSeconds, endSeconds)
            );
        }

        // Priority
        if (orderDTO.getPriority() != null) {
            serviceBuilder.setPriority(orderDTO.getPriority());
        }

        serviceBuilder.addRequiredSkill("STANDARD");

        return serviceBuilder.build();
    }

    // ==================== CREATE ALGORITHM ====================

    private VehicleRoutingAlgorithm createAlgorithm(
            VehicleRoutingProblem vrp,
            OptimizationContext context,
            OptimizationConfig config) {

        // Build vehicle max distance constraints
        Map<String, Double> vehicleMaxDistances = new HashMap<>();
        for (Vehicle v : context.vehicleDTOs().values()) {
            VehicleType vt = context.vehicleTypeDTOs().get(v.getVehicleTypeId());
            if (vt.getMaxDistance() != null) {
                vehicleMaxDistances.put("vehicle-" + v.getId(), vt.getMaxDistance() * 1000.0);
            }
        }

        int maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : 2000;
        int numThreads = config.getNumThreads() != null ? config.getNumThreads() : 4;

        Jsprit.Builder builder = Jsprit.Builder.newInstance(vrp);
        builder.setProperty(Jsprit.Parameter.ITERATIONS, String.valueOf(maxIterations));
        builder.setProperty(Jsprit.Parameter.THREADS, String.valueOf(numThreads));

        // Add max distance constraints
        if (!vehicleMaxDistances.isEmpty()) {
            StateManager stateManager = new StateManager(vrp);
            ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

            constraintManager.addConstraint(new MaxDistanceConstraint(
                    vrp.getTransportCosts(),
                    vehicleMaxDistances
            ));

            builder.setStateAndConstraintManager(stateManager, constraintManager);

            log.info("✅ Applied MaxDistance constraints for {} vehicles",
                    vehicleMaxDistances.size());
        }

        VehicleRoutingAlgorithm algorithm = builder.buildAlgorithm();

        // Set timeout
//        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
//            long timeoutMs = config.getTimeoutSeconds() * 1000L;
//            log.info("Setting algorithm timeout to: {} ms", timeoutMs);
//
//            TimeTermination timeoutTermination = new TimeTermination(timeoutMs);
//            algorithm.setPrematureAlgorithmTermination(timeoutTermination);
//        } else {
//            log.info("No timeout configured, algorithm will run until max iterations: {}", maxIterations);
//        }

        log.info("=== Jsprit Algorithm Configured ===");
        log.info("Max Iterations: {}", maxIterations);
        log.info("Threads: {}", numThreads);
        log.info("Timeout: {}s", config.getTimeoutSeconds());
        log.info("Constraints applied: MaxDistance");
        log.info("====================================");

        return algorithm;
    }

    // ==================== PARETO FRONTIER ====================

    /**
     * Build Pareto frontier from candidates
     * Returns only non-dominated solutions
     */
    private List<SolutionCandidate> buildParetoFrontier(List<SolutionCandidate> candidates) {
        List<SolutionCandidate> pareto = new ArrayList<>();

        for (SolutionCandidate candidate : candidates) {
            boolean isDominated = false;

            for (SolutionCandidate other : candidates) {
                if (other.dominates(candidate)) {
                    isDominated = true;
                    break;
                }
            }

            if (!isDominated) {
                pareto.add(candidate);
            }
        }

        return pareto;
    }

    /**
     * Select best solution from Pareto frontier based on user weights
     */
    private SolutionCandidate selectFromPareto(
            List<SolutionCandidate> pareto,
            OptimizationConfig config) {

        double[] weights = config.getEffectiveWeights();
        double costWeight = weights[0];
        double co2Weight = weights[1];

        // Normalize objectives
        double minCost = pareto.stream()
                .mapToDouble(c -> c.getMetrics().getTotalCostVnd())
                .min()
                .orElse(1.0);
        double maxCost = pareto.stream()
                .mapToDouble(c -> c.getMetrics().getTotalCostVnd())
                .max()
                .orElse(1.0);
        double minCO2 = pareto.stream()
                .mapToDouble(c -> c.getMetrics().getTotalCo2Kg())
                .min()
                .orElse(1.0);
        double maxCO2 = pareto.stream()
                .mapToDouble(c -> c.getMetrics().getTotalCo2Kg())
                .max()
                .orElse(1.0);

        return pareto.stream()
                .min(Comparator.comparingDouble(c -> {
                    double normCost = (c.getMetrics().getTotalCostVnd() - minCost) / (maxCost - minCost + 1);
                    double normCO2 = (c.getMetrics().getTotalCo2Kg() - minCO2) / (maxCO2 - minCO2 + 1);
                    return costWeight * normCost + co2Weight * normCO2;
                }))
                .orElseThrow(() -> new RuntimeException("No solution in Pareto frontier"));
    }

    // ==================== HELPER METHODS ====================

    private OptimizationContext prepareContext(EngineOptimizationRequest request) {

        Map<Long, Depot> depotDTOs = new HashMap<>();
        request.getDepots().forEach(d -> depotDTOs.put(d.getId(), d));

        Map<Long, Order> orderDTOs = new HashMap<>();
        request.getOrders().forEach(o -> orderDTOs.put(o.getId(), o));

        Map<Long, VehicleType> vehicleTypeDTOs = new HashMap<>();
        request.getVehicleTypes().forEach(vt -> vehicleTypeDTOs.put(vt.getId(), vt));

        Map<Long, Vehicle> vehicleDTOs = new HashMap<>();
        request.getVehicles().forEach(v -> vehicleDTOs.put(v.getId(), v));

        List<Location> allLocations = new ArrayList<>();

        for (var depot : request.getDepots()) {
            Location loc = Location.Builder.newInstance()
                    .setId("depot-" + depot.getId())
                    .setCoordinate(Coordinate.newInstance(depot.getLongitude(), depot.getLatitude()))
                    .setName(depot.getName())
                    .build();
            allLocations.add(loc);
        }

        for (var order : request.getOrders()) {
            Location loc = Location.Builder.newInstance()
                    .setId("order-" + order.getId())
                    .setCoordinate(Coordinate.newInstance(order.getLongitude(), order.getLatitude()))
                    .setName(order.getOrderCode())
                    .build();
            allLocations.add(loc);
        }

        return new OptimizationContext(
                allLocations,
                depotDTOs,
                orderDTOs,
                vehicleTypeDTOs,
                vehicleDTOs
        );
    }

    private DistanceTimeMatrix calculateDistanceMatrix(OptimizationContext context) {
        log.info("Calculating distance matrix...");

        List<OptCoordinates> coordinates = context.allLocations().stream()
                .map(loc -> new OptCoordinates(
                        BigDecimal.valueOf(loc.getCoordinate().getY()),
                        BigDecimal.valueOf(loc.getCoordinate().getX())
                ))
                .toList();

        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(coordinates);

        int n = coordinates.size();
        double[][] distanceMatrix = new double[n][n];
        double[][] timeMatrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                DistanceMatrixEntry entry = ghMatrix.get(i, j);
                distanceMatrix[i][j] = entry.distanceMeters();
                timeMatrix[i][j] = entry.timeSeconds();
            }
        }

        log.info("✅ Distance matrix calculated successfully");

        return new DistanceTimeMatrix(distanceMatrix, timeMatrix, context.allLocations());
    }

    private void validateConfig(OptimizationConfig config) {
        if (config.getDistanceWeight() != null && config.getDistanceWeight() > 0) {
            log.warn("⚠️  distanceWeight is DEPRECATED and will be ignored. " +
                    "Use costWeight and co2Weight instead.");
        }

        double costWeight = config.getCostWeight() != null ? config.getCostWeight() : 0.0;
        double co2Weight = config.getCo2Weight() != null ? config.getCo2Weight() : 0.0;

        if (costWeight + co2Weight == 0) {
            throw new IllegalArgumentException(
                    "Invalid config: costWeight + co2Weight must be > 0"
            );
        }

        log.info("✅ Config validated | Mode: {} | Weights: cost={}, CO2={}",
                config.getEnableParetoAnalysis() != null && config.getEnableParetoAnalysis()
                        ? "PARETO" : "WEIGHTED",
                costWeight,
                co2Weight);
    }

    private void addProgressListener(VehicleRoutingAlgorithm algorithm, Long jobId, OptimizationConfig config) {
        algorithm.addListener(new IterationStartsListener() {
            private final long algorithmStartTime = System.currentTimeMillis();

            @Override
            public void informIterationStarts(int i, VehicleRoutingProblem problem,
                                              Collection<VehicleRoutingProblemSolution> solutions) {
                if (i % 500 == 0 || i == 1) {
                    VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);
                    long elapsed = (System.currentTimeMillis() - algorithmStartTime) / 1000;

                    log.info("--- Job #{} Progress ---", jobId);
                    log.info("Iteration: {} / {}", i, config.getMaxIterations());
                    log.info("Elapsed Time: {}s / {}s (Timeout)", elapsed, config.getTimeoutSeconds());
                    log.info("Current Best Cost: {}", String.format("%.2f", best.getCost()));
                    log.info("Routes: {}", best.getRoutes().size());
                    log.info("Unassigned: {}", best.getUnassignedJobs().size());
                    log.info("-----------------------");
                }
            }
        });
    }

    private long parseTimeToSeconds(String timeStr) {
        try {
            LocalTime time = LocalTime.parse(timeStr, TIME_FORMATTER);
            return time.toSecondOfDay();
        } catch (Exception e) {
            log.warn("Failed to parse time: {}, using default", timeStr);
            return 0;
        }
    }

    // ==================== MAX DISTANCE CONSTRAINT ====================

    static class MaxDistanceConstraint implements HardRouteConstraint {
        private final VehicleRoutingTransportCosts costs;
        private final Map<String, Double> vehicleMaxDistances;

        public MaxDistanceConstraint(VehicleRoutingTransportCosts costs,
                                     Map<String, Double> vehicleMaxDistances) {
            this.costs = costs;
            this.vehicleMaxDistances = vehicleMaxDistances;
        }

        @Override
        public boolean fulfilled(JobInsertionContext iFacts) {
            String vehicleId = iFacts.getRoute().getVehicle().getId();
            Double maxDistance = vehicleMaxDistances.get(vehicleId);

            if (maxDistance == null) return true;

            double totalDistance = 0.0;
            TourActivity prevAct = iFacts.getRoute().getStart();

            for (TourActivity act : iFacts.getRoute().getActivities()) {
                totalDistance += costs.getDistance(
                        prevAct.getLocation(),
                        act.getLocation(),
                        prevAct.getEndTime(),
                        iFacts.getRoute().getVehicle()
                );
                prevAct = act;
            }

            totalDistance += costs.getDistance(
                    prevAct.getLocation(),
                    iFacts.getRoute().getEnd().getLocation(),
                    prevAct.getEndTime(),
                    iFacts.getRoute().getVehicle()
            );

            return totalDistance <= maxDistance;
        }
    }

}