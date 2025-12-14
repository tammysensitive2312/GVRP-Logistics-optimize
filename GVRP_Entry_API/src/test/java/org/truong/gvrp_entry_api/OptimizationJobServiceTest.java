//package org.truong.gvrp_entry_api;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.validation.ValidationException;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Pageable;
//import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
//import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
//import org.truong.gvrp_entry_api.entity.*;
//import org.truong.gvrp_entry_api.entity.enums.*;
//import org.truong.gvrp_entry_api.exception.JobCancellationException;
//import org.truong.gvrp_entry_api.exception.JobLimitException;
//import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
//import org.truong.gvrp_entry_api.mapper.OptimizationJobMapper;
//import org.truong.gvrp_entry_api.repository.*;
//import org.truong.gvrp_entry_api.service.OptimizationJobService;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("OptimizationJobService Tests")
//public class OptimizationJobServiceTest {
//    @Mock
//    private OptimizationJobRepository jobRepository;
//    @Mock
//    private SolutionRepository solutionRepository;
//    @Mock
//    private OrderRepository orderRepository;
//    @Mock
//    private VehicleRepository vehicleRepository;
//    @Mock
//    private DepotRepository depotRepository;
//    @Mock
//    private BranchRepository branchRepository;
//    @Mock
//    private UserRepository userRepository;
//    @Mock
//    private OptimizationJobMapper jobMapper;
//    @Mock
//    private ObjectMapper objectMapper;
//
//    @InjectMocks
//    private OptimizationJobService jobService;
//
//    private Branch testBranch;
//    private User testUser;
//    private RoutePlanningRequest validRequest;
//    private List<Order> validOrders;
//    private List<Vehicle> validVehicles;
//    private List<Depot> validDepots;
//
//    @BeforeEach
//    void setUp() {
//        // Setup test data
//        testBranch = Branch.builder()
//                .id(1L)
//                .name("Test Branch")
//                .build();
//
//        testUser = User.builder()
//                .id(1L)
//                .username("testuser")
//                .fullName("Test User")
//                .email("test@example.com")
//                .build();
//
//        validRequest = new RoutePlanningRequest();
//        validRequest.setOrderIds(Arrays.asList(1L, 2L, 3L));
//        validRequest.setVehicleIds(Arrays.asList(10L, 11L));
//
//        // Setup valid orders
//        validOrders = Arrays.asList(
//                createValidOrder(1L, "ORD-001"),
//                createValidOrder(2L, "ORD-002"),
//                createValidOrder(3L, "ORD-003")
//        );
//
//        // Setup valid vehicles
//        validVehicles = Arrays.asList(
//                createValidVehicle(10L, "ABC-123"),
//                createValidVehicle(11L, "DEF-456")
//        );
//
//        // Setup valid depots
//        validDepots = Arrays.asList(
//                createValidDepot(1L, "Depot 1")
//        );
//    }
//
//    private Order createValidOrder(Long id, String orderCode) {
//        Order order = Order.builder()
//                .id(id)
//                .orderCode(orderCode)
//                .customerName("Customer " + id)
//                .address("Address " + id)
//                .demand(BigDecimal.valueOf(10.0))
//                .status(OrderStatus.SCHEDULED)
//                .branch(testBranch)
//                .build();
//
//        // Mock location (using simple mock, not real Point)
//        order.setLocation(mock(org.locationtech.jts.geom.Point.class));
//
//        return order;
//    }
//
//    private Vehicle createValidVehicle(Long id, String licensePlate) {
//        Depot depot = createValidDepot(1L, "Depot 1");
//        Fleet fleet = Fleet.builder()
//                .id(1L)
//                .fleetName("Fleet 1")
//                .branch(testBranch)
//                .build();
//
//        return Vehicle.builder()
//                .id(id)
//                .vehicleLicensePlate(licensePlate)
//                .capacity(100)
//                .status(VehicleStatus.AVAILABLE)
//                .startDepot(depot)
//                .endDepot(depot)
//                .fleet(fleet)
//                .build();
//    }
//
//    private Depot createValidDepot(Long id, String name) {
//        Depot depot = Depot.builder()
//                .id(id)
//                .name(name)
//                .address("Depot Address " + id)
//                .branch(testBranch)
//                .build();
//
//        // Mock location
//        depot.setLocation(mock(org.locationtech.jts.geom.Point.class));
//
//        return depot;
//    }
//
//    private OptimizationJob createOptimizationJob(Long id, OptimizationJobStatus status) {
//        return OptimizationJob.builder()
//                .id(id)
//                .branch(testBranch)
//                .createdBy(testUser)
//                .status(status)
//                .createdAt(LocalDateTime.now())
//                .estimatedDurationMinutes(10)
//                .inputData("{\"orderIds\":[1,2,3],\"vehicleIds\":[10,11]}")
//                .build();
//    }
//
//    private OptimizationJobDTO createJobDTO(Long id, OptimizationJobStatus status) {
//        return OptimizationJobDTO.builder()
//                .id(id)
//                .branchId(1L)
//                .userId(1L)
//                .userName("Test User")
//                .status(status)
//                .createdAt(LocalDateTime.now())
//                .canBeCancelled(status == OptimizationJobStatus.PROCESSING)
//                .build();
//    }
//
//    private void setupMocksForSuccessfulSubmission() throws Exception {
//        when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                .thenReturn(false);
//        when(orderRepository.findAllById(validRequest.getOrderIds()))
//                .thenReturn(validOrders);
//        when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                .thenReturn(validVehicles);
//        when(depotRepository.findByBranchId(1L))
//                .thenReturn(validDepots);
//        when(branchRepository.findById(1L))
//                .thenReturn(Optional.of(testBranch));
//        when(userRepository.findById(1L))
//                .thenReturn(Optional.of(testUser));
//        when(objectMapper.writeValueAsString(any()))
//                .thenReturn("{\"orderIds\":[1,2,3],\"vehicleIds\":[10,11]}");
////        when(jobRepository.save(any(OptimizationJob.class)))
////                .thenAnswer(invocation -> invocation.getArgument(0));
//        when(jobMapper.toDTO(any(OptimizationJob.class)))
//                .thenReturn(createJobDTO(100L, OptimizationJobStatus.PROCESSING));
//    }
//
//    private void setupMocksForValidation() {
//        when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                .thenReturn(false);
//        when(orderRepository.findAllById(validRequest.getOrderIds()))
//                .thenReturn(validOrders);
//        when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                .thenReturn(validVehicles);
//        when(depotRepository.findByBranchId(1L))
//                .thenReturn(validDepots);
//    }
//
//    @Nested
//    @DisplayName("Submit Job - Normal Cases")
//    class SubmitJobNormalCases {
//
//        @Test
//        @DisplayName("Should successfully submit job with valid data")
//        void shouldSubmitJobSuccessfully() throws Exception {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(validOrders);
//            when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                    .thenReturn(validVehicles);
//            when(depotRepository.findByBranchId(1L))
//                    .thenReturn(validDepots);
//            when(branchRepository.findById(1L))
//                    .thenReturn(Optional.of(testBranch));
//            when(userRepository.findById(1L))
//                    .thenReturn(Optional.of(testUser));
//            when(objectMapper.writeValueAsString(any()))
//                    .thenReturn("{\"orderIds\":[1,2,3],\"vehicleIds\":[10,11]}");
//
//            OptimizationJob savedJob = createOptimizationJob(100L, OptimizationJobStatus.PROCESSING);
//            when(jobRepository.save(any(OptimizationJob.class)))
//                    .thenReturn(savedJob);
//
//            OptimizationJobDTO expectedDTO = createJobDTO(100L, OptimizationJobStatus.PROCESSING);
//            when(jobMapper.toDTO(any(OptimizationJob.class)))
//                    .thenReturn(expectedDTO);
//
//            // When
//            OptimizationJobDTO result = jobService.submitJob(validRequest, 1L, 1L);
//
//            // Then
//            assertThat(result).isNotNull();
//            assertThat(result.getId()).isEqualTo(100L);
//            assertThat(result.getStatus()).isEqualTo(OptimizationJobStatus.PROCESSING);
//
//            verify(jobRepository).existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING);
//            verify(orderRepository).findAllById(validRequest.getOrderIds());
//            verify(vehicleRepository).findAllById(validRequest.getVehicleIds());
//            verify(depotRepository).findByBranchId(1L);
//            verify(jobRepository).save(any(OptimizationJob.class));
//        }
//
//        @Test
//        @DisplayName("Should estimate duration correctly")
//        void shouldEstimateDurationCorrectly() throws Exception {
//            // Given
//            setupMocksForSuccessfulSubmission();
//
//            ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
//            when(jobRepository.save(jobCaptor.capture()))
//                    .thenAnswer(invocation -> invocation.getArgument(0));
//
//            // When
//            jobService.submitJob(validRequest, 1L, 1L);
//
//            // Then
//            OptimizationJob capturedJob = jobCaptor.getValue();
//            assertThat(capturedJob.getEstimatedDurationMinutes()).isNotNull();
//            assertThat(capturedJob.getEstimatedDurationMinutes()).isGreaterThan(0);
//            assertThat(capturedJob.getEstimatedDurationMinutes()).isLessThanOrEqualTo(30);
//        }
//
//        @Test
//        @DisplayName("Should serialize input data correctly")
//        void shouldSerializeInputDataCorrectly() throws Exception {
//            // Given
//            setupMocksForSuccessfulSubmission();
//
//            String expectedJson = "{\"orderIds\":[1,2,3],\"vehicleIds\":[10,11]}";
//            when(objectMapper.writeValueAsString(validRequest))
//                    .thenReturn(expectedJson);
//
//            ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
//            when(jobRepository.save(jobCaptor.capture()))
//                    .thenAnswer(invocation -> invocation.getArgument(0));
//
//            // When
//            jobService.submitJob(validRequest, 1L, 1L);
//
//            // Then
//            OptimizationJob capturedJob = jobCaptor.getValue();
//            assertThat(capturedJob.getInputData()).isEqualTo(expectedJson);
//            verify(objectMapper).writeValueAsString(validRequest);
//        }
//    }
//
//    @Nested
//    @DisplayName("Submit Job - Abnormal Cases")
//    class SubmitJobAbnormalCases {
//
//        @Test
//        @DisplayName("Should throw JobLimitException when job already running")
//        void shouldThrowJobLimitExceptionWhenJobRunning() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(true);
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(JobLimitException.class)
//                    .hasMessageContaining("Already have 1 job running");
//
//            verify(jobRepository).existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING);
//            verify(orderRepository, never()).findAllById(any());
//            verify(jobRepository, never()).save(any());
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when orders not found")
//        void shouldThrowValidationExceptionWhenOrdersNotFound() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//
//            // Only return 2 orders instead of 3
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(Arrays.asList(validOrders.get(0), validOrders.get(1)));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("Orders not found");
//
//            verify(orderRepository).findAllById(validRequest.getOrderIds());
//            verify(jobRepository, never()).save(any());
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when orders belong to different branch")
//        void shouldThrowValidationExceptionWhenOrdersWrongBranch() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//
//            Branch wrongBranch = Branch.builder().id(999L).name("Wrong Branch").build();
//            Order wrongOrder = createValidOrder(1L, "ORD-001");
//            wrongOrder.setBranch(wrongBranch);
//
//            List<Order> mixedOrders = Arrays.asList(
//                    wrongOrder,
//                    validOrders.get(1),
//                    validOrders.get(2)
//            );
//
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(mixedOrders);
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("belong to different branch");
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when orders not SCHEDULED")
//        void shouldThrowValidationExceptionWhenOrdersNotScheduled() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//
//            Order completedOrder = createValidOrder(1L, "ORD-001");
//            completedOrder.setStatus(OrderStatus.COMPLETED);
//
//            List<Order> invalidOrders = Arrays.asList(
//                    completedOrder,
//                    validOrders.get(1),
//                    validOrders.get(2)
//            );
//
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(invalidOrders);
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("SCHEDULED status");
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when orders have invalid coordinates")
//        void shouldThrowValidationExceptionWhenOrdersInvalidCoordinates() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//
//            Order invalidOrder = createValidOrder(1L, "ORD-001");
//            invalidOrder.setLocation(null); // Invalid location
//
//            List<Order> invalidOrders = Arrays.asList(
//                    invalidOrder,
//                    validOrders.get(1),
//                    validOrders.get(2)
//            );
//
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(invalidOrders);
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("invalid coordinates");
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when vehicles not found")
//        void shouldThrowValidationExceptionWhenVehiclesNotFound() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(validOrders);
//
//            // Only return 1 vehicle instead of 2
//            when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                    .thenReturn(Collections.singletonList(validVehicles.get(0)));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("Vehicles not found");
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when vehicles not AVAILABLE")
//        void shouldThrowValidationExceptionWhenVehiclesNotAvailable() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(validOrders);
//
//            Vehicle inUseVehicle = createValidVehicle(10L, "ABC-123");
//            inUseVehicle.setStatus(VehicleStatus.IN_USE);
//
//            List<Vehicle> invalidVehicles = Arrays.asList(
//                    inUseVehicle,
//                    validVehicles.get(1)
//            );
//
//            when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                    .thenReturn(invalidVehicles);
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("not available");
//        }
//
//        @Test
//        @DisplayName("Should throw ValidationException when no depot configured")
//        void shouldThrowValidationExceptionWhenNoDepot() {
//            // Given
//            when(jobRepository.existsByBranchIdAndStatus(1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(false);
//            when(orderRepository.findAllById(validRequest.getOrderIds()))
//                    .thenReturn(validOrders);
//            when(vehicleRepository.findAllById(validRequest.getVehicleIds()))
//                    .thenReturn(validVehicles);
//            when(depotRepository.findByBranchId(1L))
//                    .thenReturn(Collections.emptyList());
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ValidationException.class)
//                    .hasMessageContaining("No depot configured");
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when branch not found")
//        void shouldThrowResourceNotFoundExceptionWhenBranchNotFound() {
//            // Given
//            setupMocksForValidation();
//            when(branchRepository.findById(1L))
//                    .thenReturn(Optional.empty());
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("Resource not found");
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when user not found")
//        void shouldThrowResourceNotFoundExceptionWhenUserNotFound() {
//            // Given
//            setupMocksForValidation();
//            when(branchRepository.findById(1L))
//                    .thenReturn(Optional.of(testBranch));
//            when(userRepository.findById(1L))
//                    .thenReturn(Optional.empty());
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.submitJob(validRequest, 1L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("Resource not found");
//        }
//    }
//
//    @Nested
//    @DisplayName("Cancel Job - Normal Cases")
//    class CancelJobNormalCases {
//
//        @Test
//        @DisplayName("Should successfully cancel PROCESSING job")
//        void shouldCancelProcessingJobSuccessfully() {
//            // Given
//            OptimizationJob processingJob = createOptimizationJob(100L, OptimizationJobStatus.PROCESSING);
//            processingJob.setExternalJobId("ext-123");
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(processingJob));
//            when(jobRepository.save(any(OptimizationJob.class)))
//                    .thenAnswer(invocation -> invocation.getArgument(0));
//
//            // When
//            jobService.cancelJob(100L, 1L);
//
//            // Then
//            ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
//            verify(jobRepository).save(jobCaptor.capture());
//
//            OptimizationJob savedJob = jobCaptor.getValue();
//            assertThat(savedJob.getStatus()).isEqualTo(OptimizationJobStatus.CANCELLED);
//            assertThat(savedJob.getCancelledAt()).isNotNull();
//        }
//    }
//
//    @Nested
//    @DisplayName("Cancel Job - Abnormal Cases")
//    class CancelJobAbnormalCases {
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when job not found")
//        void shouldThrowResourceNotFoundExceptionWhenJobNotFound() {
//            // Given
//            when(jobRepository.findById(999L))
//                    .thenReturn(Optional.empty());
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.cancelJob(999L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("Resource not found");
//        }
//
//        @Test
//        @DisplayName("Should throw UnauthorizedException when job belongs to different branch")
//        void shouldThrowUnauthorizedExceptionWhenWrongBranch() {
//            // Given
//            Branch wrongBranch = Branch.builder().id(999L).name("Wrong Branch").build();
//            OptimizationJob job = createOptimizationJob(100L, OptimizationJobStatus.PROCESSING);
//            job.setBranch(wrongBranch);
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(job));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.cancelJob(100L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("does not belong to this branch");
//        }
//
//        @Test
//        @DisplayName("Should throw JobCancellationException when job already COMPLETED")
//        void shouldThrowJobCancellationExceptionWhenCompleted() {
//            // Given
//            OptimizationJob completedJob = createOptimizationJob(100L, OptimizationJobStatus.COMPLETED);
//            completedJob.setCompletedAt(LocalDateTime.now());
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(completedJob));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.cancelJob(100L, 1L))
//                    .isInstanceOf(JobCancellationException.class)
//                    .hasMessageContaining("cannot be cancelled");
//        }
//
//        @Test
//        @DisplayName("Should throw JobCancellationException when job already FAILED")
//        void shouldThrowJobCancellationExceptionWhenFailed() {
//            // Given
//            OptimizationJob failedJob = createOptimizationJob(100L, OptimizationJobStatus.FAILED);
//            failedJob.setErrorMessage("Something went wrong");
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(failedJob));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.cancelJob(100L, 1L))
//                    .isInstanceOf(JobCancellationException.class)
//                    .hasMessageContaining("cannot be cancelled");
//        }
//
//        @Test
//        @DisplayName("Should throw JobCancellationException when job already CANCELLED")
//        void shouldThrowJobCancellationExceptionWhenAlreadyCancelled() {
//            // Given
//            OptimizationJob cancelledJob = createOptimizationJob(100L, OptimizationJobStatus.CANCELLED);
//            cancelledJob.setCancelledAt(LocalDateTime.now());
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(cancelledJob));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.cancelJob(100L, 1L))
//                    .isInstanceOf(JobCancellationException.class)
//                    .hasMessageContaining("cannot be cancelled");
//        }
//    }
//
//    // ==================== GET CURRENT RUNNING JOB TESTS ====================
//
//    @Nested
//    @DisplayName("Get Current Running Job Tests")
//    class GetCurrentRunningJobTests {
//
//        @Test
//        @DisplayName("Should return current running job")
//        void shouldReturnCurrentRunningJob() {
//            // Given
//            OptimizationJob runningJob = createOptimizationJob(100L, OptimizationJobStatus.PROCESSING);
//            OptimizationJobDTO expectedDTO = createJobDTO(100L, OptimizationJobStatus.PROCESSING);
//
//            when(jobRepository.findFirstByBranchIdAndStatusOrderByCreatedAtDesc(
//                    1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(Optional.of(runningJob));
//            when(jobMapper.toDTO(runningJob))
//                    .thenReturn(expectedDTO);
//
//            // When
//            Optional<OptimizationJobDTO> result = jobService.getCurrentRunningJob(1L);
//
//            // Then
//            assertThat(result).isPresent();
//            assertThat(result.get().getId()).isEqualTo(100L);
//            assertThat(result.get().getStatus()).isEqualTo(OptimizationJobStatus.PROCESSING);
//        }
//
//        @Test
//        @DisplayName("Should return empty when no running job")
//        void shouldReturnEmptyWhenNoRunningJob() {
//            // Given
//            when(jobRepository.findFirstByBranchIdAndStatusOrderByCreatedAtDesc(
//                    1L, OptimizationJobStatus.PROCESSING))
//                    .thenReturn(Optional.empty());
//
//            // When
//            Optional<OptimizationJobDTO> result = jobService.getCurrentRunningJob(1L);
//
//            // Then
//            assertThat(result).isEmpty();
//        }
//    }
//
//    // ==================== GET JOB HISTORY TESTS ====================
//
//    @Nested
//    @DisplayName("Get Job History Tests")
//    class GetJobHistoryTests {
//
//        @Test
//        @DisplayName("Should return job history with specified limit")
//        void shouldReturnJobHistoryWithLimit() {
//            // Given
//            List<OptimizationJob> jobs = Arrays.asList(
//                    createOptimizationJob(100L, OptimizationJobStatus.COMPLETED),
//                    createOptimizationJob(101L, OptimizationJobStatus.FAILED),
//                    createOptimizationJob(102L, OptimizationJobStatus.CANCELLED)
//            );
//
//            List<OptimizationJobDTO> expectedDTOs = Arrays.asList(
//                    createJobDTO(100L, OptimizationJobStatus.COMPLETED),
//                    createJobDTO(101L, OptimizationJobStatus.FAILED),
//                    createJobDTO(102L, OptimizationJobStatus.CANCELLED)
//            );
//
//            when(jobRepository.findJobHistory(eq(1L), any(Pageable.class)))
//                    .thenReturn(jobs);
//            when(jobMapper.toDTOList(jobs))
//                    .thenReturn(expectedDTOs);
//
//            // When
//            List<OptimizationJobDTO> result = jobService.getJobHistory(1L, 20);
//
//            // Then
//            assertThat(result).hasSize(3);
//            assertThat(result.get(0).getId()).isEqualTo(100L);
//            assertThat(result.get(1).getId()).isEqualTo(101L);
//            assertThat(result.get(2).getId()).isEqualTo(102L);
//        }
//
//        @Test
//        @DisplayName("Should return empty list when no history")
//        void shouldReturnEmptyListWhenNoHistory() {
//            // Given
//            when(jobRepository.findJobHistory(eq(1L), any(Pageable.class)))
//                    .thenReturn(Collections.emptyList());
//            when(jobMapper.toDTOList(Collections.emptyList()))
//                    .thenReturn(Collections.emptyList());
//
//            // When
//            List<OptimizationJobDTO> result = jobService.getJobHistory(1L, 20);
//
//            // Then
//            assertThat(result).isEmpty();
//        }
//    }
//
//    // ==================== GET JOB BY ID TESTS ====================
//
//    @Nested
//    @DisplayName("Get Job By ID Tests")
//    class GetJobByIdTests {
//
//        @Test
//        @DisplayName("Should return job by ID")
//        void shouldReturnJobById() {
//            // Given
//            OptimizationJob job = createOptimizationJob(100L, OptimizationJobStatus.COMPLETED);
//            OptimizationJobDTO expectedDTO = createJobDTO(100L, OptimizationJobStatus.COMPLETED);
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(job));
//            when(jobMapper.toDTO(job))
//                    .thenReturn(expectedDTO);
//
//            // When
//            OptimizationJobDTO result = jobService.getJobById(100L, 1L);
//
//            // Then
//            assertThat(result).isNotNull();
//            assertThat(result.getId()).isEqualTo(100L);
//        }
//
//        @Test
//        @DisplayName("Should throw ResourceNotFoundException when job not found")
//        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
//            // Given
//            when(jobRepository.findById(999L))
//                    .thenReturn(Optional.empty());
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.getJobById(999L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("Job not found");
//        }
//
//        @Test
//        @DisplayName("Should throw UnauthorizedException when accessing other branch's job")
//        void shouldThrowUnauthorizedExceptionWhenWrongBranch() {
//            // Given
//            Branch wrongBranch = Branch.builder().id(999L).name("Wrong Branch").build();
//            OptimizationJob job = createOptimizationJob(100L, OptimizationJobStatus.COMPLETED);
//            job.setBranch(wrongBranch);
//
//            when(jobRepository.findById(100L))
//                    .thenReturn(Optional.of(job));
//
//            // When & Then
//            assertThatThrownBy(() -> jobService.getJobById(100L, 1L))
//                    .isInstanceOf(ResourceNotFoundException.class)
//                    .hasMessageContaining("Job does not belong to this branch");
//        }
//    }
//}
