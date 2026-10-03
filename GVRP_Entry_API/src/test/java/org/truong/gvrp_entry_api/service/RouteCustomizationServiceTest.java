package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.CustomizeRoutesRequest;
import org.truong.gvrp_entry_api.dto.request.EngineOrderDTO;
import org.truong.gvrp_entry_api.dto.request.RouteSequenceRequest;
import org.truong.gvrp_entry_api.dto.response.EngineEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for dispatcher route customization (preview + save-as-revision).
 */
@ExtendWith(MockitoExtension.class)
class RouteCustomizationServiceTest {

    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private DepotMapper depotMapper;
    @Mock
    private VehicleTypeMapper vehicleTypeMapper;
    @Mock
    private VehicleFeaturesService vehicleFeaturesService;
    @Mock
    private GeometryMapper geometryMapper;
    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private EngineApiClient engineApiClient;

    @InjectMocks
    private RouteCustomizationService service;

    // ---------- fixtures ----------

    private Fixture fixture() {
        Branch branch = Branch.builder().id(1L).build();

        Depot depot = Depot.builder().id(1L).build();

        VehicleType type = VehicleType.builder()
                .id(1L).capacity(100).build();

        Vehicle vehicle = Vehicle.builder()
                .id(7L).fleet(null).vehicleType(type)
                .startDepot(depot).endDepot(depot)
                .vehicleLicensePlate("29A-12345").build();

        Order order1 = Order.builder()
                .id(11L).branch(branch).demand(BigDecimal.valueOf(30)).build();
        Order order2 = Order.builder()
                .id(12L).branch(branch).demand(BigDecimal.valueOf(50)).build();

        RouteStop depotStart = RouteStop.builder()
                .type(LocationType.DEPOT).sequenceNumber(0)
                .departureTime(LocalTime.of(8, 0)).build();
        RouteStop stop1 = RouteStop.builder()
                .type(LocationType.ORDER).order(order1).sequenceNumber(1)
                .arrivalTime(LocalTime.of(8, 30)).build();
        RouteStop stop2 = RouteStop.builder()
                .type(LocationType.ORDER).order(order2).sequenceNumber(2)
                .arrivalTime(LocalTime.of(9, 0)).build();

        Route route = Route.builder()
                .id(21L).vehicle(vehicle).routeOrder(1)
                .segments(new ArrayList<>(List.of(depotStart, stop1, stop2)))
                .build();

        OptimizationJob job = OptimizationJob.builder().id(5L).branch(branch).build();

        Solution base = Solution.builder()
                .id(9L).job(job).branch(branch)
                .status(SolutionStatus.SUCCESS).type(SolutionType.ENGINE_GENERATED)
                .unservedOrders(0)
                .routes(new ArrayList<>(List.of(route)))
                .build();
        route.setSolution(base);

        return new Fixture(branch, depot, type, vehicle, order1, order2, route, job, base);
    }

    private void stubLookups(Fixture f) {
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.base()));
        when(orderRepository.findAllById(any())).thenReturn(List.of(f.order1(), f.order2()));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(f.vehicle()));
        when(vehicleFeaturesService.getSkills(any())).thenReturn(Set.of());
        when(orderMapper.toEngineDTO(any())).thenReturn(new EngineOrderDTO());
        when(engineApiClient.evaluateRoutes(any())).thenReturn(feasibleEngine());
    }

    private EngineEvaluateResponseDTO feasibleEngine() {
        EngineEvaluateResponseDTO response = new EngineEvaluateResponseDTO();
        response.setFeasible(true);
        response.setViolations(List.of());

        EngineEvaluateResponseDTO.EngineEvaluatedStopDTO stop =
                new EngineEvaluateResponseDTO.EngineEvaluatedStopDTO();
        stop.setType("CUSTOMER");
        stop.setSequenceNumber(1);
        stop.setOrderId(11L);
        stop.setArrivalTime("08:02:00");
        stop.setDepartureTime("08:12:00");

        EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO route =
                new EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO();
        route.setVehicleId(7L);
        route.setVehicleLicensePlate("29A-12345");
        route.setOrderCount(2);
        route.setTotalDistance(3.0);
        route.setTotalTime(1.5);
        route.setTotalCO2(0.6);
        route.setLoadUtilization(80.0);
        route.setStops(List.of(stop));

        response.setRoutes(List.of(route));

        EngineEvaluateResponseDTO.EngineEvaluateTotalsDTO totals =
                new EngineEvaluateResponseDTO.EngineEvaluateTotalsDTO();
        totals.setTotalDistance(3.0);
        totals.setTotalTime(1.5);
        totals.setTotalCost(100000.0);
        totals.setTotalCO2(0.6);
        totals.setVehiclesUsed(1);
        totals.setOrdersServed(2);
        response.setTotals(totals);
        return response;
    }

    private CustomizeRoutesRequest sequences(Long vehicleId, Long... orderIds) {
        RouteSequenceRequest seq = new RouteSequenceRequest();
        seq.setVehicleId(vehicleId);
        seq.setStopOrderIds(List.of(orderIds));
        CustomizeRoutesRequest request = new CustomizeRoutesRequest();
        request.setRoutes(List.of(seq));
        return request;
    }

    // ---------- preview ----------

    @Test
    void evaluate_feasiblePreview_mapsEngineResult() {
        Fixture f = fixture();
        stubLookups(f);

        RouteEvaluateResponseDTO preview = service.evaluate(9L, sequences(7L, 12L, 11L));

        assertTrue(preview.isFeasible());
        assertTrue(preview.getViolations().isEmpty());
        assertEquals(1, preview.getRoutes().size());
        assertEquals(1, preview.getRoutes().get(0).getStops().size());
        assertEquals("ORDER", preview.getRoutes().get(0).getStops().get(0).getType());
        assertEquals(LocalTime.of(8, 2), preview.getRoutes().get(0).getStops().get(0).getArrivalTime());
        assertEquals(3.0, preview.getTotals().getTotalDistance().doubleValue(), 1e-9);
    }

    @Test
    void evaluate_entryCapacityCheck_addsViolation() {
        Fixture f = fixture();
        f.type().setCapacity(50);
        stubLookups(f);

        RouteEvaluateResponseDTO preview = service.evaluate(9L, sequences(7L, 11L, 12L));

        assertFalse(preview.isFeasible());
        assertTrue(preview.getViolations().stream()
                .anyMatch(v -> v.getCode().equals("OVER_CAPACITY")));
    }

    @Test
    void evaluate_duplicateOrder_rejects400() {
        Fixture f = fixture();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.base()));

        assertThrows(DataInvalidException.class,
                () -> service.evaluate(9L, sequences(7L, 11L, 11L)));
    }

    @Test
    void evaluate_missingPlanOrder_rejects400() {
        Fixture f = fixture();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.base()));

        assertThrows(DataInvalidException.class,
                () -> service.evaluate(9L, sequences(7L, 11L)));
    }

    @Test
    void evaluate_nullDepotDeparture_fallsBackToFirstArrival() {
        Fixture f = fixture();
        // Real engine rows may lack depot departure times (solution 47 case).
        f.route().getSegments().get(0).setDepartureTime(null);
        stubLookups(f);

        RouteEvaluateResponseDTO preview = service.evaluate(9L, sequences(7L, 11L, 12L));

        assertTrue(preview.isFeasible());
    }

    // ---------- save ----------

    @Test
    void saveRevision_persistsManualEditedChildOfBase() {
        Fixture f = fixture();
        stubLookups(f);
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(solutionMapper.toDTO(any())).thenReturn(SolutionDetailResponseDTO.builder().build());

        service.saveRevision(9L, sequences(7L, 12L, 11L));

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(captor.capture());
        Solution revision = captor.getValue();
        assertEquals(SolutionType.MANUAL_EDITED, revision.getType());
        assertEquals(9L, revision.getParentSolution().getId());
        assertNull(revision.getJob());
        assertEquals(1, revision.getRoutes().size());
        assertEquals(SolutionStatus.SUCCESS, revision.getStatus());
    }

    @Test
    void saveRevision_infeasibleEngine_rejects422WithoutSaving() {
        Fixture f = fixture();
        stubLookups(f);
        EngineEvaluateResponseDTO bad = feasibleEngine();
        bad.setFeasible(false);
        EngineEvaluateResponseDTO.EngineViolationDTO violation =
                new EngineEvaluateResponseDTO.EngineViolationDTO();
        violation.setCode("TIME_WINDOW");
        violation.setRouteIndex(0);
        violation.setOrderId(11L);
        bad.setViolations(List.of(violation));
        when(engineApiClient.evaluateRoutes(any())).thenReturn(bad);
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of());

        assertThrows(UnprocessablePlanException.class,
                () -> service.saveRevision(9L, sequences(7L, 11L, 12L)));
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void saveRevision_staleBase_rejects409WithoutSaving() {
        Fixture f = fixture();
        // 409 is detected before any engine call, so only persistence lookups are stubbed.
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.base()));
        when(orderRepository.findAllById(any())).thenReturn(List.of(f.order1(), f.order2()));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(f.vehicle()));
        when(vehicleFeaturesService.getSkills(any())).thenReturn(Set.of());
        when(orderMapper.toEngineDTO(any())).thenReturn(new EngineOrderDTO());
        Solution newer = Solution.builder().id(10L)
                .createdAt(LocalDateTime.now()).build();
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of(newer));

        RevisionConflictException ex = assertThrows(RevisionConflictException.class,
                () -> service.saveRevision(9L, sequences(7L, 11L, 12L)));
        assertEquals(10L, ex.getLatestRevisionId());
        verify(solutionRepository, never()).save(any());
        verify(engineApiClient, never()).evaluateRoutes(any());
    }

    private record Fixture(
            Branch branch, Depot depot, VehicleType type, Vehicle vehicle,
            Order order1, Order order2, Route route, OptimizationJob job, Solution base) {
    }
}
