package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteStopRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;

/**
 * BF-T5 (BROWSER-FIX-001, E5): the completion callback stores each unassigned
 * order once. The duplicated {@code addAll} used to store every row twice.
 * DB-free, same Mockito setup as the LEZ callback test; no LEZ snapshot.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationCallbackUnassignedOnceTest {

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

    @Test
    void bfT5_oneUnassignedOrder_isStoredOnce() {
        Branch branch = Branch.builder().id(1L).name("Branch 1").build();
        Vehicle vehicle = Vehicle.builder().id(7L).build();
        Order served = Order.builder().id(11L).branch(branch).deliveryDate(WEDNESDAY).build();
        Order unserved = Order.builder().id(12L).branch(branch).deliveryDate(WEDNESDAY).build();
        OptimizationJob job = OptimizationJob.builder()
                .id(JOB).branch(branch).createdBy(User.builder().id(2L).build())
                .status(OptimizationJobStatus.PROCESSING)
                .build();

        when(jobRepository.findById(JOB)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(JOB)).thenReturn(Optional.empty());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findAllById(any())).thenReturn(List.of(served, unserved));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));
        when(enginePlanMaterializer.buildRoute(any(), any(), any(), any())).thenAnswer(inv ->
                Route.builder().solution(inv.getArgument(0)).vehicle(vehicle).routeOrder(1)
                        .segments(new ArrayList<>()).build());
        when(enginePlanMaterializer.buildStop(any(), any(), any())).thenReturn(List.of(
                RouteStop.builder().sequenceNumber(1).type(LocationType.ORDER).order(served)
                        .location(point(10.0, 106.010))
                        .arrivalTime(LocalTime.of(10, 0)).departureTime(LocalTime.of(10, 10))
                        .build()));
        when(enginePlanMaterializer.buildUnassignedOrder(any(), any(), any())).thenAnswer(inv -> List.of(
                UnassignedOrder.builder().order(unserved).reason("engine said no")
                        .reasonCode("ENGINE_UNASSIGNED").build()));

        EngineCallbackRequest.StopData stop = new EngineCallbackRequest.StopData();
        stop.setType("ORDER");
        stop.setOrderId(11L);
        stop.setSequenceNumber(1);
        EngineCallbackRequest.RouteData route = new EngineCallbackRequest.RouteData();
        route.setVehicleId(7L);
        route.setRouteOrder(1);
        route.setStops(List.of(stop));
        EngineCallbackRequest.UnassignedOrderData left = new EngineCallbackRequest.UnassignedOrderData();
        left.setOrderId(12L);
        left.setReason("engine said no");
        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        solutionData.setRoutes(List.of(route));
        solutionData.setUnassignedOrders(List.of(left));
        solutionData.setServedOrders(1);
        solutionData.setUnservedOrders(1);
        EngineCallbackRequest.CompletionCallback callback = new EngineCallbackRequest.CompletionCallback();
        callback.setJobId(JOB);
        callback.setSolution(solutionData);

        service.handleCompletion(callback);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(captor.capture());
        List<UnassignedOrder> rows = captor.getValue().getUnassignedOrders();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrder().getId()).isEqualTo(12L);
    }
}
