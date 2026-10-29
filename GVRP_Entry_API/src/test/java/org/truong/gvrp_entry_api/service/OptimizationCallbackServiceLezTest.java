package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
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
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteStopRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.lez.CompiledRestrictions;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.allDay;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.compilation;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.noZones;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.zone;

/**
 * LEZ certification on the completion callback (LEZ-001 P1b, CB-T1). DB-free:
 * every collaborator is a Mockito substitute; the compliance service runs the
 * real checker and the real hash function over mocked LEZ repositories.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationCallbackServiceLezTest {

    private static final Long BRANCH = 1L;
    private static final Long JOB = 5L;
    private static final long VEHICLE = 7L;

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

    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    @InjectMocks
    private OptimizationCallbackService service;

    private RouteComplianceService complianceService;
    private Branch branch;
    private Vehicle vehicle;
    private Order orderInsideZone;
    private Order orderOutsideZone;

    @BeforeEach
    void setUp() {
        complianceService = new RouteComplianceService(
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository),
                objectMapper);
        ReflectionTestUtils.setField(service, "routeComplianceService", complianceService);

        branch = Branch.builder().id(BRANCH).name("Branch 1").build();
        vehicle = Vehicle.builder().id(VEHICLE).build();
        orderInsideZone = Order.builder().id(11L).branch(branch).deliveryDate(WEDNESDAY).build();
        orderOutsideZone = Order.builder().id(12L).branch(branch).deliveryDate(WEDNESDAY).build();
    }

    // ---------- fixtures ----------

    private OptimizationJob job(String snapshot) {
        return OptimizationJob.builder()
                .id(JOB)
                .branch(branch)
                .createdBy(User.builder().id(2L).build())
                .status(OptimizationJobStatus.PROCESSING)
                .lezSnapshot(snapshot)
                .build();
    }

    private EngineCallbackRequest.RouteData routeDataFor(Long orderId) {
        EngineCallbackRequest.StopData stop = new EngineCallbackRequest.StopData();
        stop.setType("ORDER");
        stop.setOrderId(orderId);
        stop.setSequenceNumber(1);

        EngineCallbackRequest.RouteData route = new EngineCallbackRequest.RouteData();
        route.setVehicleId(VEHICLE);
        route.setRouteOrder(1);
        route.setStops(List.of(stop));
        return route;
    }

    private EngineCallbackRequest.UnassignedOrderData unassignedData(Long orderId, String reason) {
        EngineCallbackRequest.UnassignedOrderData data = new EngineCallbackRequest.UnassignedOrderData();
        data.setOrderId(orderId);
        data.setReason(reason);
        return data;
    }

    private EngineCallbackRequest.CompletionCallback callback(
            EngineCallbackRequest.SolutionData solutionData) {
        EngineCallbackRequest.CompletionCallback callback = new EngineCallbackRequest.CompletionCallback();
        callback.setJobId(JOB);
        callback.setSolution(solutionData);
        return callback;
    }

    /** One zone covering order 11 and the single vehicle banned all day. */
    private String snapshotWithAllDayBan() {
        CompiledRestrictions restrictions = compilation("snapshot-hash",
                zone("Z1", square(106.005, 9.995, 106.015, 10.005)),
                allDay(VEHICLE, "Z1"));
        restrictions.getOrderZones().put(11L, List.of("Z1"));
        return complianceService.writeSnapshot(restrictions);
    }

    private void stubCommon(OptimizationJob job, String engineReason) {
        when(jobRepository.findById(JOB)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(JOB)).thenReturn(Optional.empty());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(orderRepository.findAllById(any()))
                .thenReturn(List.of(orderInsideZone, orderOutsideZone));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));

        when(enginePlanMaterializer.buildRoute(any(), any(), any(), any())).thenAnswer(inv -> {
            Solution solution = inv.getArgument(0);
            return Route.builder()
                    .solution(solution)
                    .vehicle(vehicle)
                    .routeOrder(1)
                    .segments(new ArrayList<>())
                    .build();
        });
        when(enginePlanMaterializer.buildStop(any(), any(), any())).thenReturn(List.of(
                RouteStop.builder()
                        .sequenceNumber(1)
                        .type(LocationType.ORDER)
                        .order(orderInsideZone)
                        .location(point(10.0, 106.010))
                        .arrivalTime(LocalTime.of(10, 0))
                        .departureTime(LocalTime.of(10, 10))
                        .build()));
        when(enginePlanMaterializer.buildUnassignedOrder(any(), any(), any()))
                .thenAnswer(inv -> List.of(
                        UnassignedOrder.builder()
                                .order(orderOutsideZone)
                                .reason(engineReason)
                                .reasonCode("ENGINE_UNASSIGNED")
                                .build()));
    }

    private EngineCallbackRequest.SolutionData solutionData() {
        EngineCallbackRequest.SolutionData solutionData = new EngineCallbackRequest.SolutionData();
        solutionData.setRoutes(List.of(routeDataFor(11L)));
        solutionData.setUnassignedOrders(List.of(unassignedData(12L, "engine said no")));
        solutionData.setServedOrders(1);
        solutionData.setUnservedOrders(1);
        return solutionData;
    }

    private Solution savedSolution() {
        org.mockito.ArgumentCaptor<Solution> captor =
                org.mockito.ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- CB-T1 ----------

    @Test
    void cbT1_routeIsCertifiedByTheJobSnapshot_notByTheCurrentRules() {
        // The repositories hold no zone: a current compile would certify COMPLIANT.
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        stubCommon(job(snapshotWithAllDayBan()), "engine said no");

        service.handleCompletion(callback(solutionData()));

        Route route = savedSolution().getRoutes().get(0);
        assertThat(route.getLezStatus()).isEqualTo(LezStatus.VIOLATION);
        assertThat(route.getLezCheckedAt()).isNotNull();
        assertThat(route.getLezViolations())
                .contains("ZONE_ACCESS")
                .contains("Z1");
        assertThat(route.getLezRulesetHash())
                .isNotBlank()
                .isNotEqualTo("snapshot-hash")
                .isEqualTo(complianceService.compileFor(BRANCH, WEDNESDAY, List.of(), List.of())
                        .getHash());
    }

    @Test
    void cbT1_noZonesSnapshot_certifiesCompliant() {
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        String snapshot = complianceService.writeSnapshot(noZones("empty-hash"));
        stubCommon(job(snapshot), "engine said no");

        service.handleCompletion(callback(solutionData()));

        Route route = savedSolution().getRoutes().get(0);
        assertThat(route.getLezStatus()).isEqualTo(LezStatus.COMPLIANT);
        assertThat(route.getLezViolations()).isNull();
    }

    @Test
    void cbT1_jobWithoutSnapshot_keepsPreP1bBehaviour() {
        stubCommon(job(null), "engine said no");

        service.handleCompletion(callback(solutionData()));

        Route route = savedSolution().getRoutes().get(0);
        assertThat(route.getLezStatus()).isNull();
        assertThat(route.getLezCheckedAt()).isNull();
        assertThat(route.getLezViolations()).isNull();
    }

    @Test
    void cbT1_unassignedOrderWithNoCompliantVehicle_getsTheLezReason() {
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        CompiledRestrictions restrictions = compilation("snapshot-hash",
                zone("Z1", square(106.005, 9.995, 106.015, 10.005)),
                allDay(VEHICLE, "Z1"));
        restrictions.getOrderZones().put(11L, List.of("Z1"));
        restrictions.getOrderZones().put(12L, List.of("Z1"));
        stubCommon(job(complianceService.writeSnapshot(restrictions)), "engine said no");

        service.handleCompletion(callback(solutionData()));

        List<UnassignedOrder> rows = savedSolution().getUnassignedOrders();
        assertThat(rows).isNotEmpty();
        assertThat(rows)
                .allSatisfy(row -> {
                    assertThat(row.getOrder().getId()).isEqualTo(12L);
                    assertThat(row.getReasonCode())
                            .isEqualTo(RouteComplianceService.LEZ_NO_COMPLIANT_VEHICLE);
                    assertThat(row.getReason()).contains("Z1");
                });
    }

    @Test
    void cbT1_unassignedOrderOutsideEveryZone_keepsTheEngineReason() {
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        CompiledRestrictions restrictions = compilation("snapshot-hash",
                zone("Z1", square(106.005, 9.995, 106.015, 10.005)),
                allDay(VEHICLE, "Z1"));
        restrictions.getOrderZones().put(11L, List.of("Z1"));
        stubCommon(job(complianceService.writeSnapshot(restrictions)), "engine said no");

        service.handleCompletion(callback(solutionData()));

        List<UnassignedOrder> rows = savedSolution().getUnassignedOrders();
        assertThat(rows).isNotEmpty();
        assertThat(rows)
                .allSatisfy(row -> {
                    assertThat(row.getReasonCode()).isEqualTo("ENGINE_UNASSIGNED");
                    assertThat(row.getReason()).isEqualTo("engine said no");
                });
    }
}
