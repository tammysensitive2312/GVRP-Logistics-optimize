package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.*;
import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
import org.truong.gvrp_entry_api.entity.*;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;
import org.truong.gvrp_entry_api.exception.JobCancellationException;
import org.truong.gvrp_entry_api.exception.JobLimitException;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.mapper.*;
import org.truong.gvrp_entry_api.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.truong.gvrp_entry_api.util.AppConstant.maxConcurrentJobs;

@Service
@Slf4j
@RequiredArgsConstructor
public class OptimizationJobService {

    private final OptimizationJobRepository jobRepository;
    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;


    private final DepotMapper depotMapper;
    private final OrderMapper orderMapper;
    private final VehicleMapper vehicleMapper;
    private final VehicleTypeMapper vehicleTypeMapper;
    private final OptimizationJobMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final OptimizationConfigMapper optimizationConfigMapper;

    private final EngineApiClient engineApiClient;
    private final EmailService emailService;

    /**
     * VRP-002 Step 8-11: Submit optimization job
     * @param request RoutePlanningRequest
     * @param branchId Branch ID
     * @param userId User ID
     * @return OptimizationJobDTO
     */
    @Transactional
    public OptimizationJobDTO submitJob(
            RoutePlanningRequest request,
            Long branchId,
            Long userId) {

//        log.info("Submitting optimization job for branch {} by user {}", branchId, userId);

        // Step 1: Check concurrent job limit
        long runningJobsCount = jobRepository.countByBranchIdAndStatus(branchId, OptimizationJobStatus.PROCESSING);

        if (runningJobsCount >= maxConcurrentJobs) {
            log.warn("Branch {} has reached the limit of running jobs {}", branchId, maxConcurrentJobs);
            throw new JobLimitException("The limit for " + maxConcurrentJobs + " jobs has been reached.");
        }

        // Step 2: Validate orders BEFORE creating job
        List<Order> orders = validateAndLoadOrders(request.getOrderIds(), branchId);

        // Step 3: Validate vehicles BEFORE creating job
        List<Vehicle> vehicles = validateAndLoadVehicles(request.getVehicleIds(), branchId);

        Set<Depot> depots = vehicles.stream()
                .flatMap(v -> Stream.of(v.getStartDepot(), v.getEndDepot()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<VehicleType> vehicleTypes = vehicles.stream()
                .map(Vehicle::getVehicleType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Create job entity
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found", "Branch"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found", "User"));

        OptimizationJob job = OptimizationJob.builder()
                .branch(branch)
                .createdBy(user)
                .status(OptimizationJobStatus.PENDING)
                .inputData(serializeInputData(request))
                .estimatedDurationMinutes(estimateDuration(orders.size(), vehicles.size()))
                .build();

        job = jobRepository.save(job);
        log.info("Created optimization job #{} for branch {}", job.getId(), branchId);

        // Step 5: Build engine request
        EngineOptimizationRequest engineRequest = buildEngineRequest(
                orders,
                vehicles,
                depots,
                vehicleTypes,
                request.getPreferences()
        );

        final Long jobId = job.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                engineApiClient.submitOptimizationAsync(jobId, engineRequest);
            }
        });

        return jobMapper.toDTO(job);
    }

    /**
     * Cancel running job
     * @param jobId Job ID
     * @param branchId Branch ID
     */
    @Transactional
    public void cancelJob(Long jobId, Long branchId) {
        log.info("Cancelling job #{} for branch {}", jobId, branchId);

        OptimizationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found", "Job"));

        // Verify branch ownership
        if (!job.getBranch().getId().equals(branchId)) {
            throw new ResourceNotFoundException("Job does not belong to this branch", "Job");
        }

        // Check if can be cancelled
        if (!job.canBeCancelled()) {
            throw new JobCancellationException(
                    "Job cannot be cancelled. Current status: " + job.getStatus());
        }

        // TODO: Cancel in optimization engine
        // if (job.getExternalJobId() != null) {
        //     optimizationEngine.cancelOptimization(job.getExternalJobId());
        // }

        // Update status
        job.setStatus(OptimizationJobStatus.CANCELLED);
        job.setCancelledAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job #{} cancelled successfully", jobId);
    }

    /**
     * Get current running job
     * @param branchId Branch ID
     * @return Optional OptimizationJobDTO
     */
    @Transactional(readOnly = true)
    public Optional<OptimizationJobDTO> getCurrentRunningJob(Long branchId) {
        return jobRepository
                .findFirstByBranchIdAndStatusOrderByCreatedAtDesc(
                        branchId, OptimizationJobStatus.PROCESSING)
                .map(jobMapper::toDTO);
    }

    /**
     * Get job history (paginated)
     * @param branchId Branch ID
     * @param limit Maximum results
     * @return List of OptimizationJobDTO
     */
    @Transactional(readOnly = true)
    public List<OptimizationJobDTO> getJobHistory(Long branchId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<OptimizationJob> jobs = jobRepository.findJobHistory(branchId, pageable);
        return jobMapper.toDTOList(jobs);
    }

    /**
     * Get job by ID
     * @param jobId Job ID
     * @param branchId Branch ID
     * @return OptimizationJobDTO
     */
    @Transactional(readOnly = true)
    public OptimizationJobDTO getJobById(Long jobId, Long branchId) {
        OptimizationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found", "Job"));

        if (!job.getBranch().getId().equals(branchId)) {
            throw new ResourceNotFoundException("Job does not belong to this branch", "Job");
        }

        return jobMapper.toDTO(job);
    }

    // ==================== VALIDATION METHODS ====================

    private List<Order> validateAndLoadOrders(List<Long> orderIds, Long branchId) {
        List<Order> orders = orderRepository.findAllById(orderIds);

        // Check all orders exist
        if (orders.size() != orderIds.size()) {
            Set<Long> foundIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
            List<Long> missingIds = orderIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new ValidationException("Orders not found: " + missingIds);
        }

        // Check all orders belong to branch
        List<Order> wrongBranch = orders.stream()
                .filter(o -> !o.getBranch().getId().equals(branchId))
                .collect(Collectors.toList());

        if (!wrongBranch.isEmpty()) {
            throw new ValidationException(
                    "Orders belong to different branch: " +
                            wrongBranch.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")));
        }

        // Check order status = SCHEDULED
        List<Order> invalidStatus = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.SCHEDULED)
                .collect(Collectors.toList());

        if (!invalidStatus.isEmpty()) {
            throw new ValidationException(
                    "Orders must have SCHEDULED status: " +
                            invalidStatus.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")));
        }

        // Check orders have valid coordinates
        List<Order> invalidCoords = orders.stream()
                .filter(o -> o.getLocation() == null)
                .collect(Collectors.toList());

        if (!invalidCoords.isEmpty()) {
            throw new ValidationException(
                    "Orders have invalid coordinates: " +
                            invalidCoords.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")));
        }

        log.info("Validated {} orders for branch {}", orders.size(), branchId);
        return orders;
    }

    private List<Vehicle> validateAndLoadVehicles(List<Long> vehicleIds, Long branchId) {
        List<Vehicle> vehicles = vehicleRepository.findAllByIdWithDepots(vehicleIds);

        // Check all vehicles exist
        if (vehicles.size() != vehicleIds.size()) {
            Set<Long> foundIds = vehicles.stream().map(Vehicle::getId).collect(Collectors.toSet());
            List<Long> missingIds = vehicleIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new ValidationException("Vehicles not found: " + missingIds);
        }

        // Check all vehicles belong to branch's fleet
        List<Vehicle> wrongFleet = vehicles.stream()
                .filter(v -> !v.getFleet().getBranch().getId().equals(branchId))
                .collect(Collectors.toList());

        if (!wrongFleet.isEmpty()) {
            throw new ValidationException(
                    "Vehicles belong to different branch: " +
                            wrongFleet.stream().map(Vehicle::getVehicleLicensePlate).collect(Collectors.joining(", ")));
        }

        // Check vehicle status = AVAILABLE
        List<Vehicle> notAvailable = vehicles.stream()
                .filter(v -> v.getStatus() != VehicleStatus.AVAILABLE)
                .collect(Collectors.toList());

        if (!notAvailable.isEmpty()) {
            throw new ValidationException(
                    "Vehicles not available: " +
                            notAvailable.stream().map(Vehicle::getVehicleLicensePlate).collect(Collectors.joining(", ")));
        }

        // Check vehicles have valid depots
        List<Vehicle> invalidDepot = vehicles.stream()
                .filter(v -> v.getStartDepot() == null || v.getEndDepot() == null)
                .collect(Collectors.toList());

        if (!invalidDepot.isEmpty()) {
            throw new ValidationException(
                    "Vehicles have invalid depots: " +
                            invalidDepot.stream().map(Vehicle::getVehicleLicensePlate).collect(Collectors.joining(", ")));
        }

        log.info("Validated {} vehicles for branch {}", vehicles.size(), branchId);
        return vehicles;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Build engine request với mapped config
     */
    // Giả định: Các tham số đầu vào được thay đổi thành các List/Set đã được tải và lọc.
// Bạn nên gọi logic lọc này TRƯỚC khi gọi buildEngineRequest
    private EngineOptimizationRequest buildEngineRequest(
            List<Order> orders,
            List<Vehicle> vehicles,
            Set<Depot> depots,
            Set<VehicleType> vehicleTypes,
            RoutePlanningRequest.OptimizationPreferences userPreferences
    ) {

        EngineOptimizationRequest request = new EngineOptimizationRequest();

        List<EngineDepotDTO> depotDTOs = depots.stream()
                .map(depotMapper::toEngineDTO)
                .toList();
        request.setDepots(depotDTOs);

        List<EngineOrderDTO> orderDTOs = orders.stream()
                .map(orderMapper::toEngineDTO)
                .toList();
        request.setOrders(orderDTOs);

        List<EngineVehicleTypeDTO> typeDTOs = vehicleTypes.stream()
                .map(vehicleTypeMapper::toEngineDTO)
                .toList();
        request.setVehicleTypes(typeDTOs);


        List<EngineVehicleDTO> vehicleDTOs = vehicles.stream()
                .map(vehicleMapper::toEngineDTO)
                .toList();
        request.setVehicles(vehicleDTOs);

        EngineOptimizationRequest.OptimizationConfig engineConfig =
                optimizationConfigMapper.toEngineConfig(userPreferences);

        request.setConfig(engineConfig);

        return request;
    }

    private String serializeInputData(RoutePlanningRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize input data", e);
        }
    }

    private RoutePlanningRequest deserializeInputData(String json) {
        try {
            return objectMapper.readValue(json, RoutePlanningRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize input data", e);
        }
    }

    private Integer estimateDuration(int orderCount, int vehicleCount) {
        // Simple estimation: 2 minutes per order + 1 minute per vehicle
        // This can be improved with more sophisticated algorithm
        int baseTime = 5; // 5 minutes base
        int orderTime = orderCount * 2;
        int vehicleTime = vehicleCount;

        return Math.min(baseTime + orderTime + vehicleTime, 30); // Max 30 minutes
    }
}
