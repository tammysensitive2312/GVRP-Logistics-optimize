package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.IterationStartsListener;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint;
import com.graphhopper.jsprit.core.problem.constraint.HardRouteConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.truong.gvrp_engine_api.distance_matrix.*;
import org.truong.gvrp_engine_api.model.*;
import org.truong.gvrp_engine_api.clustering.ClusterMergeService;
import org.truong.gvrp_engine_api.clustering.KMeansClusterer;
import org.truong.gvrp_engine_api.clustering.VehicleClusterAssigner;
import org.truong.gvrp_engine_api.job.JobCancelledException;
import org.truong.gvrp_engine_api.job.JobRegistry;

import static org.truong.gvrp_engine_api.utils.AppConstant.CLUSTER_FIRST_ORDER_THRESHOLD;
import static org.truong.gvrp_engine_api.utils.AppConstant.CLUSTER_TARGET_SIZE;

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

    /**
     * Ngưỡng nhận diện cạnh SENTINEL (cặp bị prune hoặc route lỗi trong ma trận).
     * Đặt ở nửa PRUNED_METERS: mọi cạnh THẬT (dù vòng cả nước) đều << ngưỡng này,
     * còn cạnh sentinel (1e9 m) thì > ngưỡng — tách bạch tuyệt đối, không nhầm.
     */
    private static final double SENTINEL_DISTANCE_THRESHOLD = MatrixMask.PRUNED_METERS / 2.0;

    private final DistanceMatrixService distanceMatrixService;
    private final CallbackService callbackService;
    private final JobRegistry jobRegistry;

    // ==================== ASYNC ENTRY POINT ====================

    /**
     * Async optimization entry point (for background processing)
     */
    @Async
    public CompletableFuture<Void> optimizeAsync(EngineOptimizationRequest request) {
        JobRegistry.JobHandle handle = jobRegistry.register(request.getJobId());
        try {
            log.info("🚀 Starting optimization for job #{}", request.getJobId());

            LocalDateTime startTime = LocalDateTime.now();
            OptimizationResult result = optimize(request, handle);
            LocalDateTime endTime = LocalDateTime.now();

            java.time.Duration d = java.time.Duration.between(startTime, endTime);
            log.info("✅ Optimization completed for job #{} in {}m {}s",
                    request.getJobId(),
                    d.toMinutesPart(),
                    d.toSecondsPart());

            jobRegistry.markTerminal(handle, JobRegistry.Status.COMPLETED);
            callbackService.sendCompletionCallback(request.getJobId(), result);

            return CompletableFuture.completedFuture(null);

        } catch (JobCancelledException ce) {
            log.warn("🛑 Optimization cancelled for job #{}: {}", request.getJobId(), ce.getMessage());
            jobRegistry.markTerminal(handle, JobRegistry.Status.CANCELLED);
            callbackService.sendCancelledCallback(request.getJobId(), ce.getMessage());

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("❌ Optimization failed for job #{}", request.getJobId(), e);
            jobRegistry.markTerminal(handle, JobRegistry.Status.FAILED);
            callbackService.sendFailureCallback(request.getJobId(), e.getMessage());

            return CompletableFuture.failedFuture(e);
        }
    }

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
    public OptimizationResult optimize(EngineOptimizationRequest request, JobRegistry.JobHandle handle) {
        OptimizationContext context = prepareContext(request);
        OptimizationConfig config = request.getConfig();
        validateConfig(config);

        if (Boolean.TRUE.equals(config.getEnableParetoAnalysis())) {
            // Pareto: chưa cluster → full matrix (mask = null). Job Pareto thường nhỏ nên chấp nhận.
            DistanceTimeMatrix matrix = calculateDistanceMatrix(context, null, handle);
            return optimizeMultiObjective(context, matrix, config, request, handle);
        } else {
            // 1) Cluster TRƯỚC (chỉ cần toạ độ order)
            Map<String, Integer> clusterAssignment = buildClusterAssignmentIfEligible(context);
            // 2) Dựng mask từ cluster, rồi build matrix thưa
            MatrixMask mask = MatrixMask.fromClusters(context, clusterAssignment);
            DistanceTimeMatrix matrix = calculateDistanceMatrix(context, mask, handle);
            return optimizeSingleObjective(context, matrix, config, request, clusterAssignment, handle);
        }
    }

    /**
     * Chạy pipeline cluster-first (K-means -> merge -> vehicle assignment) NẾU
     * đủ điều kiện, trả về null nếu không (job nhỏ, hoặc không đủ dữ liệu để
     * phân cụm có ý nghĩa).
     * <p>
     * ĐIỀU KIỆN BẬT (TẤT CẢ phải đúng):
     * 1. Số order >= CLUSTER_FIRST_ORDER_THRESHOLD.
     * 2. C = min(ceil(N/S_target), numVehicles) > 1 — nếu <= 1, phân cụm vô
     * nghĩa (không có gì để partition), bỏ qua để tránh tốn preprocessing
     * vô ích.
     * <p>
     * TRẢ VỀ null (KHÔNG throw) khi không đủ điều kiện — đây là nhánh BÌNH
     * THƯỜNG của luồng xử lý (đa số job nhỏ), không phải lỗi.
     */
    private Map<String, Integer> buildClusterAssignmentIfEligible(OptimizationContext context) {

        int orderCount = context.orderDTOs().size();
        if (orderCount < CLUSTER_FIRST_ORDER_THRESHOLD) {
            log.debug("Cluster-first bỏ qua: {} orders < ngưỡng {}", orderCount, CLUSTER_FIRST_ORDER_THRESHOLD);
            return null;
        }

        int numVehicles = context.vehicleDTOs().size();
        int rawClusters = (int) Math.ceil((double) orderCount / CLUSTER_TARGET_SIZE);
        int numClusters = Math.min(Math.max(1, rawClusters), numVehicles);

        if (numClusters <= 1) {
            log.info("Cluster-first bỏ qua: C tính được = {} (numVehicles={}, rawClusters={}) — " +
                            "không đủ điều kiện phân vùng có ý nghĩa",
                    numClusters, numVehicles, rawClusters);
            return null;
        }

        log.info("🔀 Cluster-first ĐƯỢC BẬT: {} orders, C={} cluster, {} vehicle",
                orderCount, numClusters, numVehicles);

        // ===== Bước 1: build order coordinates + demands theo thứ tự ID cố định =====
        // BẮT BUỘC sort theo orderId để đảm bảo index nhất quán xuyên suốt toàn bộ
        // pipeline (KMeans -> merge -> map ngược về jobId)
        List<Long> orderIds = context.orderDTOs().keySet().stream().sorted().toList();

        List<OptCoordinates> orderCoords = orderIds.stream()
                .map(id -> {
                    Order o = context.orderDTOs().get(id);
                    return new OptCoordinates(
                            BigDecimal.valueOf(o.getLatitude()),
                            BigDecimal.valueOf(o.getLongitude()));
                })
                .toList();

        double[] demands = orderIds.stream()
                .mapToDouble(id -> context.orderDTOs().get(id).getDemand())
                .toArray();

        // ===== Bước 2: K-means (seed cố định 42L, đồng bộ toàn project) =====
        KMeansClusterer.ClusterAssignment initialAssignment =
                KMeansClusterer.fit(orderCoords, numClusters);

        // ===== Bước 3: merge cụm nhỏ — ngưỡng = capacity NHỎ NHẤT trong các
        // vehicle type khả dụng, đơn vị demand THÔ (không nhân DEMAND_SCALE) =====
        double minClusterDemandThreshold = context.vehicleTypeDTOs().values().stream()
                .mapToDouble(VehicleType::getCapacity)
                .min()
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy VehicleType nào để tính minClusterDemandThreshold"));

        ClusterMergeService.MergedClusterAssignment merged = ClusterMergeService.merge(
                initialAssignment, orderCoords, demands, minClusterDemandThreshold);

        // ===== Bước 4: build clusterIdByJobId (map ngược orderIds[i] -> jobId) =====
        Map<String, Integer> clusterIdByJobId = new HashMap<>();
        int[] clusterIdByIndex = merged.clusterIdByOrderIndex();
        for (int i = 0; i < orderIds.size(); i++) {
            clusterIdByJobId.put("order-" + orderIds.get(i), clusterIdByIndex[i]);
        }

        // ===== Bước 5: vehicle -> cluster (demand-proportional quota) =====
        List<ClusterMergeService.ClusterCentroid> centroids =
                ClusterMergeService.computeCentroids(merged, orderCoords);
        List<ClusterMergeService.ClusterDemand> clusterDemands =
                ClusterMergeService.computeClusterDemands(merged, demands);

        List<VehicleClusterAssigner.VehicleDepotInfo> vehicleInfos = context.vehicleDTOs().values().stream()
                .map(v -> {
                    Depot startDepot = context.depotDTOs().get(v.getStartDepotId());
                    return new VehicleClusterAssigner.VehicleDepotInfo(
                            String.valueOf(v.getId()), startDepot.getLatitude(), startDepot.getLongitude());
                })
                .toList();

        Map<String, Integer> clusterIdByVehicleIdRaw =
                VehicleClusterAssigner.assign(vehicleInfos, centroids, clusterDemands);

        // Chuẩn hóa key về đúng format Jsprit dùng ("vehicle-{id}") — giữ
        // VehicleClusterAssigner không phụ thuộc quy ước prefix của tầng Jsprit.
        Map<String, Integer> clusterIdByVehicleId = new HashMap<>();
        clusterIdByVehicleIdRaw.forEach((rawId, clusterId) ->
                clusterIdByVehicleId.put("vehicle-" + rawId, clusterId));

        // ===== Kết quả cuối: gộp cả 2 map để truyền 1 tham số duy nhất xuống
        // createAlgorithm() — job key và vehicle key có prefix khác nhau
        // ("order-"/"vehicle-") nên KHÔNG đụng độ khi gộp chung 1 Map. =====
        Map<String, Integer> combined = new HashMap<>(clusterIdByJobId);
        combined.putAll(clusterIdByVehicleId);
        return combined;
    }


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
            EngineOptimizationRequest request,
            Map<String, Integer> clusterAssignment,
            JobRegistry.JobHandle handle) {

        // Build GREEN VRP
        VehicleRoutingProblem vrp = buildGreenVRP(context, matrix, config);

        // Create and run algorithm
        VehicleRoutingAlgorithm algorithm = createAlgorithm(vrp, context, config, clusterAssignment, handle);
        addProgressListener(algorithm, request.getJobId(), config, handle);

        handle.setPhase(JobRegistry.Phase.SOLVING);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        if (handle.isCancelRequested()) {
            throw new JobCancelledException("Job bị hủy trong lúc solve");
        }

        // FAIL-LOUD: chặn kết quả rác (cạnh sentinel lọt vào route) trước khi tính metric/gửi callback
        assertNoSentinelEdgeTraversed(bestSolution, matrix, context, request.getJobId());

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
            EngineOptimizationRequest request,
            JobRegistry.JobHandle handle) {

        log.info("🎯 Running MULTI-OBJECTIVE optimization (Pareto analysis)");
        log.info("   This will run {} optimization scenarios", ObjectivePreset.values().length);

        List<SolutionCandidate> candidates = new ArrayList<>();
        List<ParetoWeightSampler.WeightPoint> weightPoints = ParetoWeightSampler.generate(4, 2.0);

        // Run optimization for each preset
        for (ParetoWeightSampler.WeightPoint point : weightPoints) {
            log.info("Running {} optimization...", point.label());

            // Override weights for this run
            OptimizationConfig presetConfig = config.clone();
            presetConfig.setCostWeight(point.costWeight());
            presetConfig.setCo2Weight(point.co2Weight());

            // Build and solve VRP
            VehicleRoutingProblem vrp = buildGreenVRP(context, matrix, presetConfig);
            // Cluster-first CHƯA áp dụng cho nhánh Pareto (quyết định đã chốt) — truyền
            // null tường minh, KHÔNG phải quên set. Mỗi weight-point trong Pareto vẫn
            // giải trên toàn bộ order set, không phân vùng cluster.
            VehicleRoutingAlgorithm algorithm = createAlgorithm(vrp, context, presetConfig, null, handle);
            addProgressListener(algorithm, request.getJobId(), presetConfig, handle);
            handle.setPhase(JobRegistry.Phase.SOLVING);
            Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
            VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

            if (handle.isCancelRequested()) {
                throw new JobCancelledException("Job bị hủy trong lúc solve (Pareto)");
            }

            // FAIL-LOUD: chặn kết quả rác (cạnh sentinel lọt vào route)
            assertNoSentinelEdgeTraversed(bestSolution, matrix, context, request.getJobId());

            // Calculate metrics
            SolutionMetrics metrics = SolutionMetricsCalculator.calculate(
                    bestSolution,
                    context,
                    matrix
            );

            candidates.add(new SolutionCandidate(
                    point.label(),
                    bestSolution,
                    metrics,
                    point.costWeight(),
                    point.co2Weight()
            ));

            log.info("   ✓ {} | Cost: {} VND | CO2: {} kg",
                    point.label(), metrics.getTotalCostVnd(), metrics.getTotalCo2Kg());
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
        VehicleRoutingTransportCosts costs = GreenVRPCostCalculator.buildPhysicalCostMatrix(
                matrix.costs(),
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
        if (maxDurationHours > 24.0) {
            throw new IllegalStateException(String.format(
                    "VehicleType %d: maxDuration=%.1f h vượt 24h — nghi lệch đơn vị (phút lưu vào cột giờ?). " +
                            "Ràng buộc ca làm sẽ bị vô hiệu nếu bỏ qua.",
                    vt.getId(), maxDurationHours));
        }
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
            OptimizationConfig config,
            Map<String, Integer> clusterAssignment,
            JobRegistry.JobHandle handle
    ) {

        Map<String, Double> vehicleMaxDistances = new HashMap<>();
        for (Vehicle v : context.vehicleDTOs().values()) {
            VehicleType vt = context.vehicleTypeDTOs().get(v.getVehicleTypeId());
            if (vt.getMaxDistance() != null) {
                vehicleMaxDistances.put("vehicle-" + v.getId(), vt.getMaxDistance() * 1000.0);
            }
        }

        int maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : 2000;
        int numThreads = config.getNumThreads() != null ? config.getNumThreads() : 1;

        Jsprit.Builder builder = Jsprit.Builder.newInstance(vrp);
        builder.setProperty(Jsprit.Parameter.ITERATIONS, String.valueOf(maxIterations))
                .setProperty(Jsprit.Parameter.THREADS, String.valueOf(numThreads))
                .setProperty(Jsprit.Parameter.FAST_REGRET, "true")
                .setProperty(Jsprit.Parameter.CONSTRUCTION, String.valueOf(Jsprit.Construction.REGRET_INSERTION));


        StateManager stateManager = new StateManager(vrp);
        ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

        constraintManager.addConstraint(
                new NoPrunedEdgeConstraint(vrp.getTransportCosts(), SENTINEL_DISTANCE_THRESHOLD),
                ConstraintManager.Priority.CRITICAL);
        log.info("✅ Applied NoPrunedEdge constraint (threshold {} m)", SENTINEL_DISTANCE_THRESHOLD);

        if (!vehicleMaxDistances.isEmpty()) {
            constraintManager.addConstraint(new MaxDistanceConstraint(
                    vrp.getTransportCosts(),
                    vehicleMaxDistances
            ));
            log.info("✅ Applied MaxDistance constraints for {} vehicles", vehicleMaxDistances.size());
        }

        if (clusterAssignment != null) {
            constraintManager.addConstraint(new ClusterRouteConstraint(clusterAssignment));
            log.info("✅ Applied ClusterRoute constraint ({} vehicle/job entries)", clusterAssignment.size());
        }

        builder.setStateAndConstraintManager(stateManager, constraintManager);
        VehicleRoutingAlgorithm algorithm = builder.buildAlgorithm();
        algorithm.setPrematureAlgorithmTermination(solution -> handle.isCancelRequested());

        // Set timeout
        if (config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0) {
//            long timeoutMs = config.getTimeoutSeconds() * 1000L;
//            TimeTermination timeoutTermination = new TimeTermination(timeoutMs);
//            algorithm.setPrematureAlgorithmTermination(timeoutTermination);
//            algorithm.addListener(timeoutTermination);
        } else {
            log.info("No timeout configured, algorithm will run until max iterations: {}", maxIterations);
        }

        log.info("=== Jsprit Algorithm Configured ===");
        log.info("Max Iterations: {}", maxIterations);
        log.info("Threads: {}", numThreads);
        log.info("Timeout: {}s", config.getTimeoutSeconds());
        log.info("Constraints applied: MaxDistance");
        log.info("====================================");

        return algorithm;
    }


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
        int locIndex = 0;

        for (var depot : request.getDepots()) {
            Location loc = Location.Builder.newInstance()
                    .setId("depot-" + depot.getId())
                    .setIndex(locIndex++)
                    .setCoordinate(Coordinate.newInstance(depot.getLongitude(), depot.getLatitude()))
                    .setName(depot.getName())
                    .build();
            allLocations.add(loc);
        }

        for (var order : request.getOrders()) {
            Location loc = Location.Builder.newInstance()
                    .setId("order-" + order.getId())
                    .setIndex(locIndex++)
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

    private DistanceTimeMatrix calculateDistanceMatrix(OptimizationContext context, MatrixMask mask,
                                                       JobRegistry.JobHandle handle) {
        log.info("Calculating distance matrix...");
        handle.setPhase(JobRegistry.Phase.BUILDING_MATRIX);

        List<OptCoordinates> coordinates = context.allLocations().stream()
                .map(loc -> new OptCoordinates(
                        BigDecimal.valueOf(loc.getCoordinate().getY()),
                        BigDecimal.valueOf(loc.getCoordinate().getX())
                ))
                .toList();

        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(
                coordinates, mask, handle::isCancelRequested);

        log.info("✅ Distance matrix calculated successfully");
        return new DistanceTimeMatrix(ghMatrix.costs(), context.allLocations());
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

    private void addProgressListener(VehicleRoutingAlgorithm algorithm, Long jobId,
                                     OptimizationConfig config, JobRegistry.JobHandle handle) {
        int maxIter = config.getMaxIterations() != null ? config.getMaxIterations() : 2000;
        algorithm.addListener(new IterationStartsListener() {
            private final long algorithmStartTime = System.currentTimeMillis();

            @Override
            public void informIterationStarts(int i, VehicleRoutingProblem problem,
                                              Collection<VehicleRoutingProblemSolution> solutions) {
                boolean logTick = (i % 500 == 0 || i == 1);
                boolean snapTick = (i % 50 == 0 || i == 1);
                if (!logTick && !snapTick) return;

                VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);
                long elapsed = (System.currentTimeMillis() - algorithmStartTime) / 1000;

                if (snapTick) {
                    handle.updateSnapshot(new JobRegistry.ProgressSnapshot(
                            i, maxIter, best.getCost(),
                            best.getRoutes().size(), best.getUnassignedJobs().size(),
                            elapsed, JobRegistry.Phase.SOLVING, java.time.Instant.now()));
                }

                if (logTick) {
                    log.info("--- Job #{} Progress ---", jobId);
                    log.info("Iteration: {} / {}", i, maxIter);
                    log.info("Elapsed Time: {}s / {}s (Timeout)", elapsed, config.getTimeoutSeconds());
                    log.info("Current Best Cost: {}", String.format("%.2f", best.getCost()));
                    log.info("Routes: {}", best.getRoutes().size());
                    log.info("Unassigned: {}", best.getUnassignedJobs().size());
                    log.info("-----------------------");
                }
            }
        });
    }

    /**
     * FAIL-LOUD: quét mọi cạnh THỰC SỰ đi qua trong lời giải; nếu có cạnh sentinel
     * (>= ngưỡng) thì ném lỗi — KHÔNG cho kết quả rác chảy xuống metric/DB/callback.
     * Với NoPrunedEdgeConstraint đã bật, điều này về lý thuyết không bao giờ xảy ra;
     * đây là lưới an toàn cuối cùng để phát hiện regression prune/route.
     */
    private void assertNoSentinelEdgeTraversed(
            VehicleRoutingProblemSolution solution,
            DistanceTimeMatrix matrix,
            OptimizationContext context,
            Long jobId) {

        List<Location> locs = context.allLocations();

        for (VehicleRoute route : solution.getRoutes()) {
            TourActivity prev = route.getStart();
            for (TourActivity act : route.getActivities()) {
                checkSentinelEdge(prev.getLocation(), act.getLocation(), locs, matrix, jobId);
                prev = act;
            }
            checkSentinelEdge(prev.getLocation(), route.getEnd().getLocation(), locs, matrix, jobId);
        }
    }

    private void checkSentinelEdge(Location from, Location to,
                                   List<Location> locs, DistanceTimeMatrix matrix, Long jobId) {
        // O(1) nhờ index đã set ở prepareContext (trước đây indexOf() quét tuyến tính
        // → ~n² phép so sánh cho mỗi lần kiểm lời giải).
        int i = from.getIndex();
        int j = to.getIndex();
        if (i < 0 || j < 0) return;
        double d = matrix.distance(i, j);
        if (d >= SENTINEL_DISTANCE_THRESHOLD) {
            throw new IllegalStateException(String.format(
                    "Job #%d: lời giải đi qua cạnh SENTINEL %s -> %s (%.0f m) — kết quả " +
                            "KHÔNG hợp lệ, hủy để tránh gửi cost rác. Kiểm tra prune cluster / route lỗi.",
                    jobId, from.getId(), to.getId(), d));
        }
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

    // ==================== CLUSTER ROUTE CONSTRAINT ====================

    /**
     * Chặn Jsprit chèn job thuộc cluster A vào route đang phục vụ cluster B (A != B).
     * <p>
     * TIỀN ĐỀ: vehicleId -> clusterId và jobId -> clusterId đã được tính SẴN
     * TRƯỚC KHI optimize (qua VehicleClusterAssigner + ClusterMergeService),
     * KHÔNG suy luận trong lúc chạy (khác với ý tưởng "route tự khóa cluster
     * từ job đầu tiên" đã bị loại bỏ vì phi-determinism với multi-thread).
     * <p>
     * AN TOÀN KHI THIẾU DỮ LIỆU: nếu vehicle hoặc job không có trong map
     * (lỗi mapping ở tầng trên), constraint KHÔNG chặn (return true) — tránh
     * biến 1 lỗi tiềm ẩn ở tầng khác thành unassigned hàng loạt khó chẩn đoán.
     * Cùng nguyên tắc với MaxDistanceConstraint (maxDistance == null -> true).
     */
    static class ClusterRouteConstraint implements HardRouteConstraint {
        private final Map<String, Integer> clusterIdByEntityId; // key: "order-{id}" hoặc "vehicle-{id}"

        public ClusterRouteConstraint(Map<String, Integer> clusterIdByEntityId) {
            this.clusterIdByEntityId = clusterIdByEntityId;
        }

        @Override
        public boolean fulfilled(JobInsertionContext iFacts) {
            String vehicleId = iFacts.getRoute().getVehicle().getId();
            Integer vehicleCluster = clusterIdByEntityId.get(vehicleId);
            if (vehicleCluster == null) {
                return true; // vehicle không rõ cluster -> không chặn (an toàn)
            }

            String jobId = iFacts.getJob().getId();
            Integer jobCluster = clusterIdByEntityId.get(jobId);
            if (jobCluster == null) {
                return true; // job không rõ cluster -> không chặn (an toàn)
            }

            return jobCluster.equals(vehicleCluster);
        }
    }

    // ==================== NO PRUNED EDGE CONSTRAINT ====================

    /**
     * Chặn solver chèn job nếu cạnh nối tới nó (prev→job HOẶC job→next) là cạnh
     * SENTINEL — tức cặp đã bị prune theo cluster, hoặc GraphHopper route lỗi và
     * bị điền ∞ hữu hạn trong ma trận.
     * <p>
     * VÌ SAO CẦN: sentinel là số HỮU HẠN (1e9 m) nên nếu chỉ để trong ma trận chi
     * phí, solver VẪN có thể chọn nó (đặc biệt khi penalty cho job chưa gán bị
     * "thổi phồng" bởi chính giá trị sentinel trong ma trận) → sinh route triệu km.
     * Constraint này biến cạnh sentinel thành BẤT KHẢ THI, nên order không tới được
     * sẽ rơi về UNASSIGNED thay vì tạo lời giải rác.
     */
    static class NoPrunedEdgeConstraint implements HardActivityConstraint {
        private final VehicleRoutingTransportCosts costs;
        private final double threshold;

        NoPrunedEdgeConstraint(VehicleRoutingTransportCosts costs, double threshold) {
            this.costs = costs;
            this.threshold = threshold;
        }

        @Override
        public ConstraintsStatus fulfilled(JobInsertionContext iFacts,
                                           TourActivity prevAct,
                                           TourActivity newAct,
                                           TourActivity nextAct,
                                           double prevActDepTime) {
            var vehicle = iFacts.getNewVehicle();

            double dPrevNew = costs.getDistance(
                    prevAct.getLocation(), newAct.getLocation(), prevActDepTime, vehicle);
            if (dPrevNew >= threshold) {
                return ConstraintsStatus.NOT_FULFILLED; // cạnh vào job là sentinel
            }

            double dNewNext = costs.getDistance(
                    newAct.getLocation(), nextAct.getLocation(), prevActDepTime, vehicle);
            if (dNewNext >= threshold) {
                return ConstraintsStatus.NOT_FULFILLED; // cạnh ra khỏi job là sentinel
            }

            return ConstraintsStatus.FULFILLED;
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

            // ===== BƯỚC 1: Tính d_current — khoảng cách route hiện tại (chưa có job mới) =====
            double currentDistance = 0.0;
            TourActivity prevAct = iFacts.getRoute().getStart();

            for (TourActivity act : iFacts.getRoute().getActivities()) {
                currentDistance += costs.getDistance(
                        prevAct.getLocation(),
                        act.getLocation(),
                        prevAct.getEndTime(),
                        iFacts.getRoute().getVehicle()
                );
                prevAct = act;
            }

            currentDistance += costs.getDistance(
                    prevAct.getLocation(),
                    iFacts.getRoute().getEnd().getLocation(),
                    prevAct.getEndTime(),
                    iFacts.getRoute().getVehicle()
            );

            // ===== BƯỚC 2: Tính Δd_min — detour tốt nhất khi chèn job mới =====
            // Lấy location của job đang được xem xét chèn
            Location jobLocation = iFacts.getJob().getActivities().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Job " + iFacts.getJob().getId() + " has no activities"))
                    .getLocation();

            double minDetour = Double.MAX_VALUE;
            TourActivity prev = iFacts.getRoute().getStart();

            // Danh sách các "điểm nối tiếp theo" để duyệt mọi vị trí chèn khả dĩ,
            // bao gồm cả vị trí cuối route (trước khi về depot)
            java.util.List<TourActivity> candidates = new java.util.ArrayList<>(
                    iFacts.getRoute().getActivities());
            candidates.add(iFacts.getRoute().getEnd());

            for (TourActivity next : candidates) {
                double dPrevNext = costs.getDistance(
                        prev.getLocation(), next.getLocation(),
                        prev.getEndTime(), iFacts.getRoute().getVehicle());

                double dPrevJob = costs.getDistance(
                        prev.getLocation(), jobLocation,
                        prev.getEndTime(), iFacts.getRoute().getVehicle());

                double dJobNext = costs.getDistance(
                        jobLocation, next.getLocation(),
                        prev.getEndTime(), iFacts.getRoute().getVehicle());

                double detour = dPrevJob + dJobNext - dPrevNext;
                minDetour = Math.min(minDetour, detour);

                prev = next;
            }

            double projectedDistance = currentDistance + minDetour;

            return projectedDistance <= maxDistance;
        }
    }

}