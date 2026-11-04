package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.exception.CallbackRejectedException;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteStopRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CB-T6..CB-T9 (SEC-CALLBACK-001, SEC-013): a completion callback that names an
 * order or vehicle outside the job's branch, or an unknown order, is refused
 * before anything is written, and the job status stays as it was.
 * DB-free, same Mockito setup as the other callback service tests.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationCallbackBranchScopeTest {

    private static final Long JOB = 5L;

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private SolutionRepository solutionRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ParetoCandidateRepository paretoCandidateRepository;
    @Mock private EmailService emailService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock private EnginePlanMaterializer enginePlanMaterializer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JobStatusUpdater jobStatusUpdater;
    @Mock private OrderStatusTransitionService orderStatusTransitionService;

    @InjectMocks
    private OptimizationCallbackService service;

    private final Branch branchA = Branch.builder().id(1L).name("Branch A").build();
    private final Branch branchB = Branch.builder().id(1000L).name("Branch B").build();

    private OptimizationJob processingJob() {
        OptimizationJob job = OptimizationJob.builder()
                .id(JOB).branch(branchA).createdBy(User.builder().id(2L).build())
                .status(OptimizationJobStatus.PROCESSING)
                .build();
        when(jobRepository.findById(JOB)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(JOB)).thenReturn(Optional.empty());
        return job;
    }

    private static Vehicle vehicle(Long id, Branch branch) {
        return Vehicle.builder().id(id).fleet(Fleet.builder().branch(branch).build()).build();
    }

    private static EngineCallbackRequest.CompletionCallback callback(Long servedOrderId, Long vehicleId,
                                                                     Long unassignedOrderId) {
        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        if (servedOrderId != null) {
            EngineCallbackRequest.StopData stop = new EngineCallbackRequest.StopData();
            stop.setType("ORDER");
            stop.setOrderId(servedOrderId);
            stop.setSequenceNumber(1);
            EngineCallbackRequest.RouteData route = new EngineCallbackRequest.RouteData();
            route.setVehicleId(vehicleId);
            route.setRouteOrder(1);
            route.setStops(List.of(stop));
            solutionData.setRoutes(List.of(route));
        }
        if (unassignedOrderId != null) {
            EngineCallbackRequest.UnassignedOrderData left = new EngineCallbackRequest.UnassignedOrderData();
            left.setOrderId(unassignedOrderId);
            left.setReason("engine said no");
            solutionData.setUnassignedOrders(List.of(left));
        }
        EngineCallbackRequest.CompletionCallback callback = new EngineCallbackRequest.CompletionCallback();
        callback.setJobId(JOB);
        callback.setSolution(solutionData);
        return callback;
    }

    private void assertNothingWritten(OptimizationJob job) {
        verifyNoInteractions(orderStatusTransitionService, enginePlanMaterializer,
                paretoCandidateRepository, eventPublisher, jobStatusUpdater);
        verify(solutionRepository, never()).save(any());
        verify(jobRepository, never()).save(any());
        assertThat(job.getStatus()).isEqualTo(OptimizationJobStatus.PROCESSING);
        assertThat(job.getSolution()).isNull();
    }

    @Test
    void cbT6_unassignedOrderOfOtherBranch_rejectedBeforeAnyWrite() {
        OptimizationJob job = processingJob();
        Order foreign = Order.builder().id(12L).branch(branchB).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.handleCompletion(callback(null, null, 12L)))
                .isInstanceOf(CallbackRejectedException.class);

        assertNothingWritten(job);
    }

    @Test
    void cbT6_servedOrderOfOtherBranch_rejectedBeforeAnyWrite() {
        OptimizationJob job = processingJob();
        Order own = Order.builder().id(11L).branch(branchA).build();
        Order foreign = Order.builder().id(12L).branch(branchB).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(foreign, own));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle(7L, branchA)));

        assertThatThrownBy(() -> service.handleCompletion(callback(12L, 7L, 11L)))
                .isInstanceOf(CallbackRejectedException.class);

        assertNothingWritten(job);
    }

    @Test
    void cbT7_vehicleOfOtherBranch_rejectedBeforeAnyWrite() {
        OptimizationJob job = processingJob();
        Order own = Order.builder().id(11L).branch(branchA).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(own));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle(7L, branchB)));

        assertThatThrownBy(() -> service.handleCompletion(callback(11L, 7L, null)))
                .isInstanceOf(CallbackRejectedException.class);

        assertNothingWritten(job);
    }

    @Test
    void cbT7_unknownVehicle_rejectedBeforeAnyWrite() {
        OptimizationJob job = processingJob();
        Order own = Order.builder().id(11L).branch(branchA).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(own));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.handleCompletion(callback(11L, 7L, null)))
                .isInstanceOf(CallbackRejectedException.class);

        assertNothingWritten(job);
    }

    @Test
    void cbT8_unknownOrder_rejectedBeforeAnyWrite() {
        OptimizationJob job = processingJob();
        Order own = Order.builder().id(11L).branch(branchA).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(own));

        assertThatThrownBy(() -> service.handleCompletion(callback(null, null, 999L)))
                .isInstanceOf(CallbackRejectedException.class);

        assertNothingWritten(job);
    }

    @Test
    void cbT9_sameBranch_savedAsBefore() {
        OptimizationJob job = processingJob();
        Order served = Order.builder().id(11L).branch(branchA).build();
        Vehicle vehicle = vehicle(7L, branchA);
        when(orderRepository.findAllById(any())).thenReturn(List.of(served));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enginePlanMaterializer.buildRoute(any(), any(), any(), any())).thenAnswer(inv ->
                Route.builder().solution(inv.getArgument(0)).vehicle(vehicle).routeOrder(1)
                        .segments(new ArrayList<>()).build());
        when(enginePlanMaterializer.buildStop(any(), any(), any())).thenReturn(List.of());

        service.handleCompletion(callback(11L, 7L, null));

        verify(solutionRepository).save(any());
        verify(orderStatusTransitionService).bulkTransition(eq(List.of(served)), eq(OrderStatus.ON_ROUTE));
        assertThat(job.getStatus()).isEqualTo(OptimizationJobStatus.COMPLETED);
    }
}
