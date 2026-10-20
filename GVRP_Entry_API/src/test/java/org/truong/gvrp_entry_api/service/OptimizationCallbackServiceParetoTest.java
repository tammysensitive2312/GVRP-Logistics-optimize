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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.ParetoCandidate;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.entity.enums.ParetoFrontierStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteStopRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pareto persistence through the completion callback (PARETO-001, N-T1..N-T3).
 * DB-free: every repository/collaborator is a Mockito substitute.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationCallbackServiceParetoTest {

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private SolutionRepository solutionRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ParetoCandidateRepository paretoCandidateRepository;
    @Mock private EmailService emailService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private EnginePlanMaterializer enginePlanMaterializer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JobStatusUpdater jobStatusUpdater;
    @Mock private OrderStatusTransitionService orderStatusTransitionService;

    @InjectMocks
    private OptimizationCallbackService service;

    // ---------- fixtures ----------

    private Branch branch() {
        return Branch.builder().id(1L).name("Branch 1").build();
    }

    private OptimizationJob processingJob(Branch branch) {
        User user = User.builder().id(2L).build();
        return OptimizationJob.builder()
                .id(5L).branch(branch).createdBy(user)
                .status(OptimizationJobStatus.PROCESSING)
                .build();
    }

    private EngineCallbackRequest.RouteData routeWithOrder(Long orderId) {
        EngineCallbackRequest.StopData stop = new EngineCallbackRequest.StopData();
        stop.setType("ORDER");
        stop.setOrderId(orderId);
        stop.setSequenceNumber(1);

        EngineCallbackRequest.RouteData route = new EngineCallbackRequest.RouteData();
        route.setVehicleId(7L);
        route.setRouteOrder(1);
        route.setStops(List.of(stop));
        return route;
    }

    private EngineCallbackRequest.ParetoCandidateData candidate(int index, String status,
                                                                boolean selected, boolean withPlan) {
        EngineCallbackRequest.ParetoCandidateData candidate = new EngineCallbackRequest.ParetoCandidateData();
        candidate.setIndex(index);
        candidate.setLabel("PARETO_K" + index + "_OF_4");
        candidate.setCostWeight(BigDecimal.valueOf(0.25));
        candidate.setCo2Weight(BigDecimal.valueOf(0.75));
        candidate.setTotalCost(BigDecimal.valueOf(1_000_000));
        candidate.setTotalDistance(BigDecimal.valueOf(85.2));
        candidate.setTotalCO2(BigDecimal.valueOf(14.1));
        candidate.setTotalTime(BigDecimal.valueOf(6.3));
        candidate.setTotalVehiclesUsed(4);
        candidate.setServedOrders(120);
        candidate.setUnservedOrders(0);
        candidate.setFrontierStatus(status);
        candidate.setSelected(selected);
        if (withPlan) {
            candidate.setRoutes(List.of(routeWithOrder(11L)));
            candidate.setUnassignedOrders(List.of());
        }
        return candidate;
    }

    private EngineCallbackRequest.ParetoData pareto(List<EngineCallbackRequest.ParetoCandidateData> candidates) {
        EngineCallbackRequest.ParetoData pareto = new EngineCallbackRequest.ParetoData();
        pareto.setSamplerN(4);
        pareto.setSamplerP(2.0);
        pareto.setClustered(true);
        pareto.setClusterCount(7);
        pareto.setSelectionRule("SERVICE_FIRST_MIN_MAX");
        pareto.setCandidates(candidates);
        return pareto;
    }

    private EngineCallbackRequest.CompletionCallback callback(EngineCallbackRequest.SolutionData solutionData) {
        EngineCallbackRequest.CompletionCallback callback = new EngineCallbackRequest.CompletionCallback();
        callback.setJobId(5L);
        callback.setSolution(solutionData);
        return callback;
    }

    // ---------- N-T1 ----------

    @Test
    void savesCandidatesAndLinksSelected() {
        Branch branch = branch();
        OptimizationJob job = processingJob(branch);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(5L)).thenReturn(Optional.empty());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order11 = Order.builder().id(11L).branch(branch).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(order11));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(Vehicle.builder().id(7L).build()));

        when(enginePlanMaterializer.buildRoute(any(), any(), any(), any())).thenAnswer(inv -> {
            Solution solution = inv.getArgument(0);
            return Route.builder().solution(solution).routeOrder(1).segments(new ArrayList<>()).build();
        });
        when(enginePlanMaterializer.buildStop(any(), any(), any())).thenReturn(List.of(RouteStop.builder().build()));

        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        solutionData.setRoutes(List.of(routeWithOrder(11L)));
        solutionData.setServedOrders(1);
        solutionData.setUnservedOrders(0);
        solutionData.setPareto(pareto(List.of(
                candidate(0, "FRONTIER", true, true),
                candidate(1, "FRONTIER", false, true),
                candidate(2, "DOMINATED", false, false))));

        service.handleCompletion(callback(solutionData));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParetoCandidate>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paretoCandidateRepository).saveAll(rowsCaptor.capture());
        List<ParetoCandidate> rows = rowsCaptor.getValue();
        assertEquals(3, rows.size());

        // planJson present exactly for FRONTIER candidates
        assertNotNull(rows.get(0).getPlanJson());
        assertNotNull(rows.get(1).getPlanJson());
        assertNull(rows.get(2).getPlanJson());

        // top-level pareto flags propagated to every row
        assertTrue(rows.get(0).getClustered());
        assertEquals(7, rows.get(0).getClusterCount());
        assertEquals(ParetoFrontierStatus.FRONTIER, rows.get(0).getFrontierStatus());

        // engineSelected only on the selected row
        assertTrue(rows.get(0).getEngineSelected());
        assertFalse(rows.get(1).getEngineSelected());
        assertFalse(rows.get(2).getEngineSelected());

        // root solution links the selected candidate row
        ArgumentCaptor<Solution> solutionCaptor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(solutionCaptor.capture());
        assertSame(rows.get(0), solutionCaptor.getValue().getParetoCandidate());

        // order transitions unchanged (assigned order -> ON_ROUTE)
        verify(orderStatusTransitionService).bulkTransition(List.of(order11), OrderStatus.ON_ROUTE);
    }

    // ---------- N-T2 ----------

    @Test
    void rejectsTwoSelected() {
        Branch branch = branch();
        OptimizationJob job = processingJob(branch);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(5L)).thenReturn(Optional.empty());

        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        solutionData.setPareto(pareto(List.of(
                candidate(0, "FRONTIER", true, true),
                candidate(1, "FRONTIER", true, true))));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(DataInvalidException.class,
                    () -> service.handleCompletion(callback(solutionData)));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(paretoCandidateRepository, never()).saveAll(any());
        verify(solutionRepository, never()).save(any());
    }

    // ---------- N-T3 ----------

    @Test
    void singleCallbackUnchanged() {
        Branch branch = branch();
        OptimizationJob job = processingJob(branch);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(5L)).thenReturn(Optional.empty());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        solutionData.setServedOrders(0);
        solutionData.setUnservedOrders(0);

        service.handleCompletion(callback(solutionData));

        verifyNoInteractions(paretoCandidateRepository);
    }
}
