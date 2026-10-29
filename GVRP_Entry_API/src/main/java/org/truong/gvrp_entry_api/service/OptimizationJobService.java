package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.CompiledRestrictions;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.mapper.*;
import org.truong.gvrp_entry_api.repository.*;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
    private final RestrictionCompiler restrictionCompiler;

    /**
     * VRP-002 Step 8-11: Submit optimization job
     * @param request RoutePlanningRequest
     * @param branchId Branch ID
     * @param userId User ID
     * @return OptimizationJobDTO
     */
    @Transactional
    public OptimizationJobDTO submitJob(RoutePlanningRequest request, Long branchId, Long userId) {

        // Step 1: Check concurrent job limit
        long runningJobsCount = jobRepository.countByBranchIdAndStatus(branchId, OptimizationJobStatus.PROCESSING);

        if (runningJobsCount >= maxConcurrentJobs) {
            log.warn("Branch {} has reached the limit of running jobs {}", branchId, maxConcurrentJobs);
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.JOB_LIMIT_EXCEEDED.getCode())
                            .message(ErrorCode.JOB_LIMIT_EXCEEDED.getMessage())
                            .resource(AppConstant.JOB)
                            .build()
            ));
        }

        // Step 2: Validate orders BEFORE creating job
        List<Order> orders = validateAndLoadOrders(request.getOrderIds(), branchId);

        // Step 3: Validate vehicles BEFORE creating job
        List<Vehicle> vehicles = validateAndLoadVehicles(request.getVehicleIds(), branchId);

        // Step 3b (D4): one delivery date per job, for every branch.
        LocalDate serviceDate = requireSingleServiceDate(orders);

        // Step 3c: compile the branch's LEZ restrictions for that date and drop
        // vehicles whose depot lies in a zone where they are banned all day.
        CompiledRestrictions restrictions =
                restrictionCompiler.compile(branchId, serviceDate, vehicles, orders);
        List<Vehicle> usableVehicles = vehicles.stream()
                .filter(vehicle -> !restrictions.unusableVehicleIds().contains(vehicle.getId()))
                .toList();
        if (usableVehicles.isEmpty() && !vehicles.isEmpty()) {
            throw unusableVehiclesError(restrictions);
        }

        Set<Depot> depots = usableVehicles.stream()
                .flatMap(v -> Stream.of(v.getStartDepot(), v.getEndDepot()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<VehicleType> vehicleTypes = usableVehicles.stream()
                .map(Vehicle::getVehicleType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Create job entity
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.BRANCH)
                                .build()
                )));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.USER)
                                .build()
                )));

        OptimizationJob job = OptimizationJob.builder()
                .branch(branch)
                .createdBy(user)
                .status(OptimizationJobStatus.PENDING)
                .inputData(serializeInputData(request))
                .lezSnapshot(serializeSnapshot(restrictions))
                .estimatedDurationMinutes(estimateDuration(orders.size(), usableVehicles.size()))
                .build();

        job = jobRepository.save(job);
        log.info("Created optimization job #{} for branch {}", job.getId(), branchId);

        // Step 5: Build engine request
        EngineOptimizationRequest engineRequest = buildEngineRequest(
                orders, usableVehicles, depots, vehicleTypes, restrictions, request.getPreferences()
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
     * @param jobIds list request Job ID
     * @param branchId Branch ID
     */
    @Transactional
    public void cancelJobs(List<Long> jobIds, Long branchId) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }

        log.info("Cancelling jobs {} for branch {}", jobIds, branchId);

        List<OptimizationJob> jobs = jobRepository.findAllById(jobIds);


        if (jobs.size() != jobIds.size()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.JOB)
                            .build()
            ));
        }

        LocalDateTime cancelledAt = LocalDateTime.now();

        for (OptimizationJob job : jobs) {
            if (!job.getBranch().getId().equals(branchId)) {
                throw new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.JOB)
                                .build()
                ));
            }

            if (!job.canBeCancelled()) {
                throw new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_CONFLICT.getCode())
                                .message("Job can't be cancelled")
                                .resource(AppConstant.JOB)
                                .build()
                ));
            }

            job.setStatus(OptimizationJobStatus.CANCELLED);
            job.setCancelledAt(cancelledAt);
        }

        jobRepository.saveAll(jobs);
        jobs.forEach(job -> engineApiClient.requestCancel(job.getId()));
        log.info("Cancelled {} jobs successfully", jobs.size());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getJobProgress(Long jobId, Long branchId) {
        OptimizationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.JOB)
                                .build()
                )));

        if (!job.getBranch().getId().equals(branchId)) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.JOB)
                            .build()
            ));
        }

        return engineApiClient.getProgress(jobId);
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
        return jobRepository.findJobHistory(branchId, pageable).stream()
                .map(jobMapper::fromHistory)
                .collect(Collectors.toList());
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
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.JOB)
                                .build()
                )));

        if (!job.getBranch().getId().equals(branchId)) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.JOB)
                            .build()
            ));
        }

        return jobMapper.toDTO(job);
    }

    private List<Order> validateAndLoadOrders(List<Long> orderIds, Long branchId) {
        List<Order> orders = orderRepository.findAllById(orderIds);

        if (orders.size() != orderIds.size()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.ORDER)
                            .build()
            ));
        }

        List<Order> wrongBranch = orders.stream()
                .filter(o -> !o.getBranch().getId().equals(branchId))
                .toList();
        if (!wrongBranch.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.ORDER)
                            .field(wrongBranch.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")))
                            .build()
            ));
        }

        List<Order> invalidStatus = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.SCHEDULED)
                .toList();
        if (!invalidStatus.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_CONFLICT.getCode())
                            .message(ErrorCode.RESOURCE_CONFLICT.getMessage())
                            .resource(AppConstant.ORDER)
                            .field(invalidStatus.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")))
                            .build()
            ));
        }

        List<Order> invalidCoords = orders.stream()
                .filter(o -> o.getLocation() == null)
                .toList();
        if (!invalidCoords.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message(ErrorCode.VALIDATION_ERROR.getMessage())
                            .resource(invalidCoords.stream().map(Order::getOrderCode).collect(Collectors.joining(", ")))
                            .field(AppConstant.LOCATION)
                            .build()
            ));
        }

        log.info("Validated {} orders for branch {}", orders.size(), branchId);
        return orders;
    }

    private List<Vehicle> validateAndLoadVehicles(List<Long> vehicleIds, Long branchId) {
        List<Vehicle> vehicles = vehicleRepository.findAllByIdWithDepots(vehicleIds);

        if (vehicles.size() != vehicleIds.size()) {
            Set<Long> foundIds = vehicles.stream().map(Vehicle::getId).collect(Collectors.toSet());
            List<Long> missingIds = vehicleIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();

            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.VEHICLE)
                            .field(missingIds.toString())
                            .build()
            ));
        }

        List<Vehicle> wrongFleet = vehicles.stream()
                .filter(v -> !v.getFleet().getBranch().getId().equals(branchId))
                .toList();
        if (!wrongFleet.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                            .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                            .resource(AppConstant.VEHICLE)
                            .build()
            ));
        }

        List<Vehicle> notAvailable = vehicles.stream()
                .filter(v -> v.getStatus() != VehicleStatus.AVAILABLE)
                .toList();
        if (!notAvailable.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_CONFLICT.getCode())
                            .message(ErrorCode.RESOURCE_CONFLICT.getMessage())
                            .resource(AppConstant.VEHICLE)
                            .build()
            ));
        }

        List<Vehicle> invalidDepot = vehicles.stream()
                .filter(v -> v.getStartDepot() == null || v.getEndDepot() == null)
                .toList();
        if (!invalidDepot.isEmpty()) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.RESOURCE_CONFLICT.getCode())
                            .message(ErrorCode.RESOURCE_CONFLICT.getMessage())
                            .resource(AppConstant.DEPOT)
                            .field(AppConstant.VEHICLE)
                            .build()
            ));
        }

        log.info("Validated {} vehicles for branch {}", vehicles.size(), branchId);
        return vehicles;
    }

    private EngineOptimizationRequest buildEngineRequest(
            List<Order> orders,
            List<Vehicle> vehicles,
            Set<Depot> depots,
            Set<VehicleType> vehicleTypes,
            CompiledRestrictions restrictions,
            RoutePlanningRequest.OptimizationPreferences userPreferences) {

        EngineOptimizationRequest request = new EngineOptimizationRequest();

        List<EngineDepotDTO> depotDTOs = depots.stream().map(depotMapper::toEngineDTO).toList();
        request.setDepots(depotDTOs);

        List<EngineOrderDTO> orderDTOs = orders.stream().map(orderMapper::toEngineDTO).toList();
        applyOrderLezSkills(orderDTOs, restrictions);
        request.setOrders(orderDTOs);

        List<EngineVehicleTypeDTO> typeDTOs = vehicleTypes.stream().map(vehicleTypeMapper::toEngineDTO).toList();
        request.setVehicleTypes(typeDTOs);

        List<EngineVehicleDTO> vehicleDTOs = vehicles.stream().map(vehicleMapper::toEngineDTO).toList();
        applyVehicleLezSkills(vehicleDTOs, restrictions);
        request.setVehicles(vehicleDTOs);

        EngineOptimizationRequest.OptimizationConfig engineConfig =
                optimizationConfigMapper.toEngineConfig(userPreferences);
        request.setConfig(engineConfig);

        return request;
    }

    /**
     * One service date per job (LEZ-001 D4). {@code delivery_date} is mandatory on
     * orders, so a null date is ignored; with no date at all the compiled
     * restrictions are empty.
     */
    private static LocalDate requireSingleServiceDate(List<Order> orders) {
        Set<LocalDate> dates = orders.stream()
                .map(Order::getDeliveryDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        if (dates.size() > 1) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message("All orders of one optimisation job must have the same delivery date: "
                                    + dates.stream().map(LocalDate::toString).collect(Collectors.joining(", ")))
                            .resource(AppConstant.ORDER)
                            .field("deliveryDate")
                            .build()
            ));
        }
        return dates.isEmpty() ? null : dates.iterator().next();
    }

    /** 400 naming the zones and depots that leave every vehicle unusable (design §4, R09). */
    private DataInvalidException unusableVehiclesError(CompiledRestrictions restrictions) {
        String zones = restrictions.getUnusableVehicles().stream()
                .map(CompiledRestrictions.UnusableVehicle::getZoneKey)
                .distinct()
                .collect(Collectors.joining(", "));
        String depots = restrictions.getUnusableVehicles().stream()
                .map(CompiledRestrictions.UnusableVehicle::getVehicleId)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message("No usable vehicle: every depot lies in a zone that bans the vehicle all day"
                                + " (zones: " + zones + "; vehicles: " + depots + ")")
                        .resource(AppConstant.VEHICLE)
                        .build()
        ));
    }

    /** {@code LEZ_<key>} on every order inside zone {@code key} (design §5.1). */
    private void applyOrderLezSkills(List<EngineOrderDTO> orders, CompiledRestrictions restrictions) {
        if (!restrictions.hasZones()) {
            return;
        }
        for (EngineOrderDTO order : orders) {
            for (String key : restrictions.orderZoneKeys(order.getId())) {
                order.getLezSkills().add(CompiledRestrictions.skillOf(key));
            }
        }
    }

    /** {@code LEZ_<key>} on every vehicle not banned all day in zone {@code key} (design §5.1). */
    private void applyVehicleLezSkills(List<EngineVehicleDTO> vehicles, CompiledRestrictions restrictions) {
        if (!restrictions.hasZones()) {
            return;
        }
        for (EngineVehicleDTO vehicle : vehicles) {
            for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
                CompiledRestrictions.VehicleRestriction restriction =
                        restrictions.restriction(vehicle.getId(), zone.getKey());
                if (restriction != null && !restriction.isAllDay()) {
                    vehicle.getLezSkills().add(CompiledRestrictions.skillOf(zone.getKey()));
                }
            }
        }
    }

    private String serializeInputData(RoutePlanningRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize input data", e);
        }
    }

    /** Snapshot written in the same transaction as the job row (LEZ-001 R13). */
    private String serializeSnapshot(CompiledRestrictions restrictions) {
        try {
            return objectMapper.writeValueAsString(restrictions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize compiled LEZ restrictions", e);
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
        int baseTime = 5;
        int orderTime = orderCount * 2;
        return Math.min(baseTime + orderTime + vehicleCount, 30);
    }
}
