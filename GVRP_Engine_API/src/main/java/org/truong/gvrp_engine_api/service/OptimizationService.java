package org.truong.gvrp_engine_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.IterationStartsListener;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.termination.TimeTermination;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.HardRouteConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
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
import org.springframework.stereotype.Service;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DistanceMatrixService distanceMatrixService;
    private final CallbackService callbackService;

    /**
     * Async optimization entry point
     */
    @Async
    public void optimizeAsync(EngineOptimizationRequest request) {
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

        } catch (Exception e) {
            log.error("❌ Optimization failed for job #{}", request.getJobId(), e);

            callbackService.sendFailureCallback(request.getJobId(), e.getMessage());
        }
    }

    /**
     * Main optimization method
     */
    public OptimizationResult optimize(EngineOptimizationRequest request) {

        // STEP 1: Prepare Context
        OptimizationContext context = prepareContext(request);

        // STEP 2: Calculate Distance Matrix
        DistanceTimeMatrix matrix = calculateDistanceMatrix(context);

        // STEP 3: Build VRP
        VehicleRoutingProblem vrp = buildVehicleRoutingProblem(context, matrix, request.getConfig());

        // STEP 4: create Algorithm and Solve
        VehicleRoutingAlgorithm algorithm = createAlgorithm(vrp, context, request.getConfig());

        // Add progress listener
        algorithm.addListener(new IterationStartsListener() {
            @Override
            public void informIterationStarts(int i, VehicleRoutingProblem problem,
                                              Collection<VehicleRoutingProblemSolution> solutions) {
                if (i % 100 == 0) {
                    VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);
                    log.info("Iteration {}: Cost = {}, Unassigned = {}",
                            i, String.format("%.2f", best.getCost()),
                            best.getUnassignedJobs().size());
                }
            }
        });

        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // STEP 5: Extract Result
        OptimizationResult result = extractResult(bestSolution, context, matrix, request);

        log.info("Optimization Completed");

        return result;
    }

    // ============================================
    // STEP 1: PREPARE CONTEXT
    // ============================================

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

        // Convert Jsprit Locations to OptCoordinates
        List<OptCoordinates> coordinates = context.allLocations.stream()
                .map(loc -> new OptCoordinates(
                        BigDecimal.valueOf(loc.getCoordinate().getY()),
                        BigDecimal.valueOf(loc.getCoordinate().getX())
                ))
                .toList();

        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(coordinates);

        // Convert to double[][] format for Jsprit
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

        log.info("✓ Distance matrix calculated successfully");

        return new DistanceTimeMatrix(distanceMatrix, timeMatrix, context.allLocations);
    }

    private VehicleRoutingProblem buildVehicleRoutingProblem(
            OptimizationContext context,
            DistanceTimeMatrix matrix,
            OptimizationConfig config) {

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        // 1. Add Vehicles
        for (var vehicleDTO : context.vehicleDTOs.values()) {
            VehicleImpl jspritVehicle = buildJspritVehicle(vehicleDTO, context);
            vrpBuilder.addVehicle(jspritVehicle);
        }

        // 2. Add Services (Orders)
        for (var orderDTO : context.orderDTOs.values()) {
            com.graphhopper.jsprit.core.problem.job.Service jspritService = buildJspritService(orderDTO, context, config);
            vrpBuilder.addJob(jspritService);
        }

        // 3. Set Transport Costs (Green VRP)
        VehicleRoutingTransportCostsMatrix costs = buildGreenTransportCosts(
                matrix, context, config);
        vrpBuilder.setRoutingCost(costs);

        // 4. Fleet Size
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        log.info("VRP built: {} vehicles, {} jobs",
                context.vehicleDTOs.size(), context.orderDTOs.size());

        return vrpBuilder.build();
    }

    private VehicleImpl buildJspritVehicle(
            Vehicle vehicleDTO,
            OptimizationContext context) {

        // Get vehicle type
        var vehicleTypeDTO = context.vehicleTypeDTOs.get(vehicleDTO.getVehicleTypeId());
        if (vehicleTypeDTO == null) {
            throw new IllegalArgumentException("Vehicle type not found: " + vehicleDTO.getVehicleTypeId());
        }

        // Create Vehicle Type
        VehicleTypeImpl.Builder typeBuilder = VehicleTypeImpl.Builder
                .newInstance("type-" + vehicleTypeDTO.getId())
                .addCapacityDimension(0, vehicleTypeDTO.getCapacity())
                .setCostPerDistance(vehicleTypeDTO.getCostPerKm() / 1000.0) // per meter
                .setCostPerTransportTime(vehicleTypeDTO.getCostPerHour() / 3600.0) // per second
                .setFixedCost(vehicleTypeDTO.getFixedCost())
                ;

        VehicleTypeImpl vehicleType = typeBuilder.build();

        // Get depot locations
        var startDepotDTO = context.depotDTOs.get(vehicleDTO.getStartDepotId());
        var endDepotDTO = context.depotDTOs.get(vehicleDTO.getEndDepotId());

        if (startDepotDTO == null || endDepotDTO == null) {
            throw new IllegalArgumentException("Depot not found for vehicle: " + vehicleDTO.getId());
        }

        Location startLocation = context.allLocations.stream()
                .filter(loc -> loc.getId().equals("depot-" + startDepotDTO.getId()))
                .findFirst()
                .orElseThrow();

        Location endLocation = context.allLocations.stream()
                .filter(loc -> loc.getId().equals("depot-" + endDepotDTO.getId()))
                .findFirst()
                .orElseThrow();

        // Build Vehicle
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder
                .newInstance("vehicle-" + vehicleDTO.getId())
                .setStartLocation(startLocation)
                .setEndLocation(endLocation)
                .setType(vehicleType)
                .setReturnToDepot(true);

        // Add Skills based on vehicle type
        // TODO: Parse from vehicle_features JSON if needed
        // For now, assume all trucks can handle all orders
        vehicleBuilder.addSkill("STANDARD");

        // Time Windows (8:00 - 18:00) = 8*3600 to 18*3600 seconds

        long earliestStart = 8 * 3600;

        double maxDurationHours = vehicleTypeDTO.getMaxDuration() != null
                ? vehicleTypeDTO.getMaxDuration()
                : 12.0;
        long maxDurationSeconds = (long) (maxDurationHours * 3600);
        long latestArrival = earliestStart + maxDurationSeconds;

        vehicleBuilder
                .setEarliestStart(earliestStart)
                .setLatestArrival(latestArrival);

        VehicleImpl vehicle = vehicleBuilder.build();

        log.info("Vehicle created: {} (Type: {}), Capacity: {}, Route: {} -> {}",
                vehicleDTO.getVehicleLicensePlate(),
                vehicleTypeDTO.getTypeName(),
                vehicleTypeDTO.getCapacity(),
                startDepotDTO.getName(),
                endDepotDTO.getName()
        );

        return vehicle;
    }

    private com.graphhopper.jsprit.core.problem.job.Service buildJspritService(
            Order orderDTO,
            OptimizationContext context,
            OptimizationConfig config) {

        Location orderLocation = context.allLocations.stream()
                .filter(loc -> loc.getId().equals("order-" + orderDTO.getId()))
                .findFirst()
                .orElseThrow();

        com.graphhopper.jsprit.core.problem.job.Service.Builder serviceBuilder = com.graphhopper.jsprit.core.problem.job.Service.Builder
                .newInstance("order-" + orderDTO.getId())
                .setName(orderDTO.getOrderCode())
                .setLocation(orderLocation)
                .addSizeDimension(0, orderDTO.getDemand().intValue())
                .setServiceTime(orderDTO.getServiceTime() * 60.0); // convert minutes to seconds

        // Time Windows
        if (orderDTO.getTimeWindowStart() != null && orderDTO.getTimeWindowEnd() != null) {
            long startSeconds = parseTimeToSeconds(orderDTO.getTimeWindowStart());
            long endSeconds = parseTimeToSeconds(orderDTO.getTimeWindowEnd());

            serviceBuilder.setTimeWindow(
                    TimeWindow.newInstance(startSeconds, endSeconds)
            );

            log.debug("Order {} time window: {} - {} ({} - {}s)",
                    orderDTO.getOrderCode(),
                    orderDTO.getTimeWindowStart(),
                    orderDTO.getTimeWindowEnd(),
                    startSeconds,
                    endSeconds
            );
        }

        // Priority (higher number = higher priority in Jsprit)
        if (orderDTO.getPriority() != null) {
            serviceBuilder.setPriority(orderDTO.getPriority());
        }

        // Required Skills
        serviceBuilder.addRequiredSkill("STANDARD");

        com.graphhopper.jsprit.core.problem.job.Service service = serviceBuilder.build();

//        log.info("Service created: {} (Demand: {}kg, Service: {}min, Priority: {})",
//                orderDTO.getOrderCode(),
//                orderDTO.getDemand(),
//                orderDTO.getServiceTime(),
//                orderDTO.getPriority()
//        );

        return service;
    }

    private long parseTimeToSeconds(String timeStr) {
        // Parse "09:00:00" to seconds from midnight
        try {
            LocalTime time = LocalTime.parse(timeStr, TIME_FORMATTER);
            return time.toSecondOfDay();
        } catch (Exception e) {
            log.warn("Failed to parse time: {}, using default", timeStr);
            return 0;
        }
    }

    private VehicleRoutingTransportCostsMatrix buildGreenTransportCosts(
            DistanceTimeMatrix matrix,
            OptimizationContext context,
            OptimizationConfig config) {

        VehicleRoutingTransportCostsMatrix.Builder matrixBuilder =
                VehicleRoutingTransportCostsMatrix.Builder.newInstance(true); // symmetric

        List<Location> locations = context.allLocations;

        // Calculate average emission factor from vehicle types
        double avgEmissionFactor = context.vehicleTypeDTOs.values().stream()
                .mapToDouble(vt -> vt.getEmissionFactor() != null ? vt.getEmissionFactor() : 200.0)
                .average()
                .orElse(200.0);

        log.info("Average emission factor: {} g CO2/km", avgEmissionFactor);

        // Weights from config
        double distanceWeight = config.getDistanceWeight() != null ? config.getDistanceWeight() : 0.2;
        double costWeight = config.getCostWeight() != null ? config.getCostWeight() : 0.7;
        double co2Weight = config.getCo2Weight() != null ? config.getCo2Weight() : 0.1;

//        log.debug("Objective weights: Distance={}, Cost={}, CO2={}",
//                distanceWeight, costWeight, co2Weight);

        for (int i = 0; i < locations.size(); i++) {
            for (int j = 0; j < locations.size(); j++) {
                Location from = locations.get(i);
                Location to = locations.get(j);

                double distanceMeters = matrix.distanceMatrix[i][j];
                double timeSeconds = matrix.timeMatrix[i][j];

                // GREEN VRP COST CALCULATION
                double distanceKm = distanceMeters / 1000.0;

                // CO2 emissions (kg)
                double co2Kg = (distanceKm * avgEmissionFactor) / 1000.0;

                // CO2 cost (VND) - assume 10,000 VND per kg CO2
                double co2CostVND = co2Kg * 10000.0;

                // Multi-objective cost function
                // Cost = w1 * distance + w2 * monetary_cost + w3 * co2_cost
                double totalCost = (distanceWeight * distanceMeters) +
                        (costWeight * distanceMeters) + // base transport cost
                        (co2Weight * co2CostVND);

                matrixBuilder.addTransportDistance(from.getId(), to.getId(), totalCost);
                matrixBuilder.addTransportTime(from.getId(), to.getId(), timeSeconds);
            }
        }

        return matrixBuilder.build();
    }


    private VehicleRoutingAlgorithm createAlgorithm(
            VehicleRoutingProblem vrp,
            OptimizationContext context,
            OptimizationConfig config) {

        Map<String, Double> vehicleMaxDistances = new HashMap<>();
        for (Vehicle v : context.vehicleDTOs.values()) {
            VehicleType vt = context.vehicleTypeDTOs.get(v.getVehicleTypeId());
            if (vt.getMaxDistance() != null) {
                vehicleMaxDistances.put("vehicle-" + v.getId(), vt.getMaxDistance() * 1000.0); // đổi sang mét
            }
        }

        int maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : 1000;
        int numThreads = config.getNumThreads() != null ? config.getNumThreads() : 4;

        Jsprit.Builder builder = Jsprit.Builder.newInstance(vrp);
        builder.setProperty(Jsprit.Parameter.ITERATIONS, String.valueOf(maxIterations));
        builder.setProperty(Jsprit.Parameter.THREADS, String.valueOf(numThreads));

        if (!vehicleMaxDistances.isEmpty()) {
            StateManager stateManager = new StateManager(vrp);
            ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

            constraintManager.addConstraint(new MaxDistanceConstraint(vrp.getTransportCosts(), vehicleMaxDistances));

            builder.setStateAndConstraintManager(stateManager, constraintManager);

            log.info("✅ Applied MaxDistance constraints for {} vehicles", vehicleMaxDistances.size());
        }

        VehicleRoutingAlgorithm algorithm = builder.buildAlgorithm();

        log.info("Creating algorithm: maxIterations={}, threads={}", maxIterations, numThreads);

        // Set timeout if specified
        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
            algorithm.setPrematureAlgorithmTermination(
                    new TimeTermination(config.getTimeoutSeconds() * 1000)); // milliseconds
        }

        return algorithm;
    }

    private OptimizationResult extractResult(
            VehicleRoutingProblemSolution solution,
            OptimizationContext context,
            DistanceTimeMatrix matrix,
            EngineOptimizationRequest request) {

        OptimizationResult result = new OptimizationResult();
        result.setJobId(request.getJobId());

        // Unassigned orders
        List<UnassignedOrder> unassignedOrders = new ArrayList<>();
        for (var job : solution.getUnassignedJobs()) {
            String orderId = job.getId().replace("order-", "");
            var orderDTO = context.orderDTOs.get(Long.parseLong(orderId));

            if (orderDTO != null) {
                UnassignedOrder unassigned = new UnassignedOrder();
                unassigned.setOrderId(orderDTO.getId());
                unassigned.setOrderCode(orderDTO.getOrderCode());
                unassigned.setReason("No suitable vehicle found or capacity exceeded");
                unassignedOrders.add(unassigned);

                log.warn("⚠️  Unassigned Order: {}", orderDTO.getOrderCode());
            }
        }
        result.setUnassignedOrders(unassignedOrders);

        // Routes
        List<RouteDetail> routes = new ArrayList<>();
        double totalDistance = 0;
        double totalTime = 0;
        double totalCO2 = 0;
        int totalOrdersServed = 0;

        int routeNumber = 0;
        for (VehicleRoute route : solution.getRoutes()) {
            routeNumber++;

            String vehicleId = route.getVehicle().getId().replace("vehicle-", "");
            var vehicleDTO = context.vehicleDTOs.get(Long.parseLong(vehicleId));

            if (vehicleDTO == null) continue;

            var vehicleTypeDTO = context.vehicleTypeDTOs.get(vehicleDTO.getVehicleTypeId());

            RouteDetail routeDetail = new RouteDetail();
            routeDetail.setVehicleId(vehicleDTO.getId());
            routeDetail.setVehicleLicensePlate(vehicleDTO.getVehicleLicensePlate());
            routeDetail.setVehicleType(vehicleTypeDTO.getTypeName());
            routeDetail.setEmissionFactor(vehicleTypeDTO.getEmissionFactor());

            List<Stop> stops = new ArrayList<>();

            // ========================================
            // LOG CHI TIẾT LỘ TRÌNH BẮT ĐẦU TẠI ĐÂY
            // ========================================

            log.info("╔════════════════════════════════════════════════════════════════════════════╗");
            log.info("║  📍 ROUTE #{}: {}                                                    ",
                    routeNumber, vehicleDTO.getVehicleLicensePlate());
            log.info("╠════════════════════════════════════════════════════════════════════════════╣");

            // Start at depot
            var startDepot = context.depotDTOs.get(vehicleDTO.getStartDepotId());
            Stop startStop = new Stop();
            startStop.setType("DEPOT");
            startStop.setLocationId("depot-" + startDepot.getId());
            startStop.setLocationName(startDepot.getName());
            startStop.setArrivalTime(formatTime(route.getStart().getEndTime()));
            startStop.setDepartureTime(formatTime(route.getStart().getEndTime()));
            startStop.setLoadAfter(0.0);
            stops.add(startStop);

            log.info("║  🏢 START: {} at {}",
                    startDepot.getName(),
                    formatTime(route.getStart().getEndTime()));
            log.info("║     📍 Location: ({}, {})",
                    startDepot.getLatitude(),
                    startDepot.getLongitude());
            log.info("╠────────────────────────────────────────────────────────────────────────────╣");

            double routeDistance = 0;
            double routeTime = 0;
            double routeLoad = 0;
            int orderCount = 0;

            TourActivity prevActivity = route.getStart();

            // Customer stops
            for (TourActivity activity : route.getActivities()) {
                if (activity instanceof TourActivity.JobActivity jobActivity) {
                    com.graphhopper.jsprit.core.problem.job.Service service = (com.graphhopper.jsprit.core.problem.job.Service) jobActivity.getJob();

                    String orderId = service.getId().replace("order-", "");
                    var orderDTO = context.orderDTOs.get(Long.parseLong(orderId));

                    if (orderDTO != null) {
                        orderCount++;

                        Stop stop = new Stop();
                        stop.setType("CUSTOMER");
                        stop.setSequenceNumber(orderCount);
                        stop.setOrderId(orderDTO.getId());
                        stop.setOrderCode(orderDTO.getOrderCode());
                        stop.setLocationId("order-" + orderDTO.getId());
                        stop.setLocationName(orderDTO.getOrderCode());
                        stop.setDemand(orderDTO.getDemand());
                        stop.setArrivalTime(formatTime(activity.getArrTime()));
                        stop.setDepartureTime(formatTime(activity.getEndTime()));
                        stop.setServiceTime(activity.getOperationTime() / 60.0); // minutes

                        // Calculate segment distance
                        double segmentDistance = getTransportDistance(
                                prevActivity.getLocation(),
                                activity.getLocation(),
                                matrix,
                                context
                        );

                        double segmentTime = getTransportTime(
                                prevActivity.getLocation(),
                                activity.getLocation(),
                                matrix,
                                context
                        );

                        // Wait time
                        double waitTime = Math.max(0,
                                (activity.getArrTime() - prevActivity.getEndTime() - segmentTime) / 60.0);
                        stop.setWaitTime(waitTime);

                        routeLoad += orderDTO.getDemand();
                        stop.setLoadAfter(routeLoad);

                        stops.add(stop);

                        // ✅ LOG CHI TIẾT MỖI STOP
                        log.info("║  #{} 📦 {}",
                                orderCount,
                                orderDTO.getOrderCode());
                        log.info("║     📍 Location: ({}, {})",
                                orderDTO.getLatitude(),
                                orderDTO.getLongitude());

                        log.info("║     🚚 Demand: {}kg → Load after: {}kg / {}kg capacity",
                                orderDTO.getDemand(),
                                routeLoad,
                                vehicleTypeDTO.getCapacity());
                        log.info("║     ⏱️  Arrival: {} | Departure: {} (Service: {}min)",
                                formatTime(activity.getArrTime()),
                                formatTime(activity.getEndTime()),
                                stop.getServiceTime());
                        log.info("║     🛣️  From previous: {}km in {}min{}",
                                segmentDistance / 1000.0,
                                segmentTime / 60.0,
                                waitTime > 0 ? String.format(" (Wait: %.0fmin)", waitTime) : "");

                        // Time window info
                        if (orderDTO.getTimeWindowStart() != null && orderDTO.getTimeWindowEnd() != null) {
                            log.info("║     🕐 Time Window: {} - {}{}",
                                    orderDTO.getTimeWindowStart(),
                                    orderDTO.getTimeWindowEnd(),
                                    activity.getArrTime() > parseTimeToSeconds(orderDTO.getTimeWindowEnd().toString())
                                            ? " ⚠️ LATE" : " ✓");
                        }

                        log.info("╠────────────────────────────────────────────────────────────────────────────╣");

                        routeDistance += segmentDistance;
                        routeTime += segmentTime;
                        routeTime += activity.getOperationTime(); // service time

                        prevActivity = activity;
                    }
                }
            }

            // Return to depot
            var endDepot = context.depotDTOs.get(vehicleDTO.getEndDepotId());

            double returnDistance = getTransportDistance(
                    prevActivity.getLocation(),
                    route.getEnd().getLocation(),
                    matrix,
                    context
            );
            routeDistance += returnDistance;

            double returnTime = getTransportTime(
                    prevActivity.getLocation(),
                    route.getEnd().getLocation(),
                    matrix,
                    context
            );
            routeTime += returnTime;

            Stop endStop = new Stop();
            endStop.setType("DEPOT");
            endStop.setLocationId("depot-" + endDepot.getId());
            endStop.setLocationName(endDepot.getName());
            endStop.setArrivalTime(formatTime(route.getEnd().getArrTime()));
            endStop.setDepartureTime(null);
            endStop.setLoadAfter(0.0);
            stops.add(endStop);

            log.info("║  🏢 END: {} at {}",
                    endDepot.getName(),
                    formatTime(route.getEnd().getArrTime()));
            log.info("║     📍 Location: ({}, {})",
                    endDepot.getLatitude(),
                    endDepot.getLongitude());
            log.info("║     🛣️  From last stop: {}km in {}min",
                    returnDistance / 1000.0,
                    returnTime / 60.0);
            log.info("╠════════════════════════════════════════════════════════════════════════════╣");

            routeDetail.setStops(stops);
            routeDetail.setTotalDistance(routeDistance / 1000.0); // km
            routeDetail.setTotalTime(routeTime / 3600.0); // hours
            routeDetail.setOrderCount(orderCount);
            routeDetail.setTotalLoad(routeLoad);
            routeDetail.setLoadUtilization((routeLoad / vehicleTypeDTO.getCapacity()) * 100.0);

            // Calculate CO2 emissions
            double co2Kg = (routeDetail.getTotalDistance() * vehicleTypeDTO.getEmissionFactor()) / 1000.0;
            routeDetail.setTotalCO2(co2Kg);

            routes.add(routeDetail);

            totalDistance += routeDetail.getTotalDistance();
            totalTime += routeDetail.getTotalTime();
            totalCO2 += co2Kg;
            totalOrdersServed += orderCount;

            // ✅ LOG SUMMARY CHO ROUTE
            log.info("║  📊 ROUTE SUMMARY:");
            log.info("║     • Orders delivered: {}", orderCount);
            log.info("║     • Total distance: {} km", routeDetail.getTotalDistance());
            log.info("║     • Total time: {} hours ({} minutes)",
                    routeDetail.getTotalTime(),
                    routeDetail.getTotalTime() * 60);
            log.info("║     • Total load: {}kg ({}% capacity)",
                    routeLoad,
                    routeDetail.getLoadUtilization());
            log.info("║     • CO2 emissions: {} kg", co2Kg);
            log.info("║     • Average speed: {} km/h",
                    routeDetail.getTotalDistance() / routeDetail.getTotalTime());
            log.info("╚════════════════════════════════════════════════════════════════════════════╝");
            log.info("");
        }

        result.setRoutes(routes);
        result.setTotalCost(solution.getCost());
        result.setTotalDistance(totalDistance);
        result.setTotalTime(totalTime);
        result.setTotalCO2(totalCO2);
        result.setTotalVehiclesUsed(routes.size());
        result.setTotalOrdersServed(totalOrdersServed);
        result.setTotalOrdersUnassigned(unassignedOrders.size());

        // ✅ OVERALL SUMMARY
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║  🎯 OPTIMIZATION RESULT SUMMARY                                            ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║  💰 Total Cost: {} VND", result.getTotalCost());
        log.info("║  🛣️  Total Distance: {} km", result.getTotalDistance());
        log.info("║  ⏱️  Total Time: {} hours", result.getTotalTime());
        log.info("║  🌱 Total CO2: {} kg", result.getTotalCO2());
        log.info("║  🚚 Vehicles Used: {}/{}", result.getTotalVehiclesUsed(), context.vehicleDTOs.size());
        log.info("║  📦 Orders Served: {}/{}", result.getTotalOrdersServed(), context.orderDTOs.size());
        log.info("║  ⚠️  Unassigned Orders: {}", result.getTotalOrdersUnassigned());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");

        return result;
    }

    private double getTransportDistance(Location from, Location to, DistanceTimeMatrix matrix, OptimizationContext context) {
        int fromIndex = context.allLocations.indexOf(from);
        int toIndex = context.allLocations.indexOf(to);
        return matrix.distanceMatrix[fromIndex][toIndex];
    }

    private double getTransportTime(Location from, Location to, DistanceTimeMatrix matrix, OptimizationContext context) {
        int fromIndex = context.allLocations.indexOf(from);
        int toIndex = context.allLocations.indexOf(to);
        return matrix.timeMatrix[fromIndex][toIndex];
    }

    private String formatTime(double seconds) {
        long totalSeconds = (long) seconds;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    static class MaxDistanceConstraint implements HardRouteConstraint {
        private final VehicleRoutingTransportCosts costs;
        private final Map<String, Double> vehicleMaxDistances; // Map<VehicleId, MaxDistanceMeters>

        public MaxDistanceConstraint(VehicleRoutingTransportCosts costs, Map<String, Double> vehicleMaxDistances) {
            this.costs = costs;
            this.vehicleMaxDistances = vehicleMaxDistances;
        }

        @Override
        public boolean fulfilled(JobInsertionContext iFacts) {
            String vehicleId = iFacts.getRoute().getVehicle().getId();
            Double maxDistance = vehicleMaxDistances.get(vehicleId);

            // Nếu xe không có giới hạn khoảng cách, cho qua
            if (maxDistance == null) return true;

            double totalDistance = 0.0;
            TourActivity prevAct = iFacts.getRoute().getStart();

            // Tính toán lại khoảng cách nếu thêm job mới vào route
            // Lưu ý: Đây là cách tính đơn giản (O(n)). Với dữ liệu lớn cần StateManager.
            for (TourActivity act : iFacts.getRoute().getActivities()) {
                totalDistance += costs.getDistance(prevAct.getLocation(), act.getLocation(), prevAct.getEndTime(), iFacts.getRoute().getVehicle());
                prevAct = act;
            }
            // Cộng thêm đoạn về đích
            totalDistance += costs.getDistance(prevAct.getLocation(), iFacts.getRoute().getEnd().getLocation(), prevAct.getEndTime(), iFacts.getRoute().getVehicle());

            return totalDistance <= maxDistance;
        }
    }


    record OptimizationContext(
            List<Location> allLocations,
            Map<Long, Depot> depotDTOs,
            Map<Long, Order> orderDTOs,
            Map<Long, VehicleType> vehicleTypeDTOs,
            Map<Long, Vehicle> vehicleDTOs
    ) {}

    record DistanceTimeMatrix(
            double[][] distanceMatrix,
            double[][] timeMatrix,
            List<Location> locations
    ) {}
}
