package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.dto.request.CustomizeRoutesRequest;
import org.truong.gvrp_entry_api.dto.request.EngineOrderDTO;
import org.truong.gvrp_entry_api.dto.request.RouteSequenceRequest;
import org.truong.gvrp_entry_api.dto.response.EngineEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;
import org.truong.gvrp_entry_api.service.lez.RuleSelector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.encode;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;

/**
 * LEZ findings in the dispatcher customization flow (LEZ-001 P1b, CU-T1/CU-T2).
 * DB-free: the compliance service runs the real compiler and checker over
 * mocked LEZ repositories, and the engine is a Mockito substitute.
 */
@ExtendWith(MockitoExtension.class)
class RouteCustomizationServiceLezTest {

    private static final Long BRANCH = 1L;

    @Mock private SolutionRepository solutionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private DepotMapper depotMapper;
    @Mock private VehicleTypeMapper vehicleTypeMapper;
    @Mock private VehicleFeaturesService vehicleFeaturesService;
    @Mock private GeometryMapper geometryMapper;
    @Mock private SolutionMapper solutionMapper;
    @Mock private EngineApiClient engineApiClient;

    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    @InjectMocks
    private RouteCustomizationService service;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        service = new RouteCustomizationService(solutionRepository, orderRepository,
                vehicleRepository, orderMapper, depotMapper, vehicleTypeMapper,
                vehicleFeaturesService, geometryMapper, solutionMapper, engineApiClient);
        ReflectionTestUtils.setField(service, "routeComplianceService",
                new RouteComplianceService(new RestrictionCompiler(zoneRepository,
                        zoneRuleRepository, ruleTemplateRepository, permitRepository),
                        new ObjectMapper().findAndRegisterModules()));

        fixture = fixture();
    }

    // ---------- fixtures ----------

    private record Fixture(Branch branch, Depot depot, VehicleType type, Vehicle vehicle,
                           Order order1, Order order2, Route route, Solution base) {
    }

    private Fixture fixture() {
        Branch branch = Branch.builder().id(BRANCH).build();
        Depot depot = Depot.builder().id(1L).location(point(10.0, 106.0)).build();

        VehicleType type = VehicleType.builder()
                .id(1L).typeName("Truck").capacity(1500)
                .legalClass(LegalClass.TRUCK).energyType(EnergyType.DIESEL)
                .grossWeightKg(4000).emissionStandard(5)
                .build();

        Vehicle vehicle = Vehicle.builder()
                .id(7L).vehicleType(type)
                .startDepot(depot).endDepot(depot)
                .vehicleLicensePlate("29A-12345").build();

        Order order1 = Order.builder()
                .id(11L).branch(branch).demand(BigDecimal.valueOf(30))
                .deliveryDate(WEDNESDAY).status(org.truong.gvrp_entry_api.entity.enums.OrderStatus.ON_ROUTE)
                .build();
        Order order2 = Order.builder()
                .id(12L).branch(branch).demand(BigDecimal.valueOf(50))
                .deliveryDate(WEDNESDAY).status(org.truong.gvrp_entry_api.entity.enums.OrderStatus.ON_ROUTE)
                .build();

        RouteStop depotStart = RouteStop.builder()
                .type(LocationType.DEPOT).sequenceNumber(0)
                .departureTime(LocalTime.of(8, 0)).location(point(10.0, 106.0)).build();
        RouteStop stop1 = RouteStop.builder()
                .type(LocationType.ORDER).order(order1).sequenceNumber(1)
                .arrivalTime(LocalTime.of(8, 30)).location(point(10.0, 106.020)).build();
        RouteStop stop2 = RouteStop.builder()
                .type(LocationType.ORDER).order(order2).sequenceNumber(2)
                .arrivalTime(LocalTime.of(9, 0)).location(point(10.0, 106.040)).build();

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

        return new Fixture(branch, depot, type, vehicle, order1, order2, route, base);
    }

    /** One enabled zone; a diesel truck is banned there all day. */
    private void stubZone() {
        ZoneTemplate template = ZoneTemplate.builder()
                .id(10L).code("TEST").name("Test zone").jurisdiction("XX")
                .geometry(square(106.005, 9.995, 106.015, 10.005))
                .version(2)
                .build();
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(
                BranchZone.builder().id(1L).branch(fixture.branch())
                        .mode(BranchZoneMode.LINKED).sourceTemplate(template).enabled(true).build()));
        lenient().when(ruleTemplateRepository
                        .findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(10L, 2))
                .thenReturn(List.of(ZoneRuleTemplate.builder()
                        .id(1L).zoneTemplate(template).templateVersion(2)
                        .selector(new RuleSelector(List.of(LegalClass.TRUCK),
                                List.of(EnergyType.DIESEL), null, null, null, null))
                        .schedule(new RuleSchedule(RuleSchedule.Type.ALWAYS_FORBIDDEN, null, null))
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .build()));
    }

    private void stubLookups(EngineEvaluateResponseDTO engine) {
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(fixture.base()));
        when(orderRepository.findAllById(any()))
                .thenReturn(List.of(fixture.order1(), fixture.order2()));
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(fixture.vehicle()));
        when(vehicleFeaturesService.getSkills(any())).thenReturn(Set.of());
        when(orderMapper.toEngineDTO(any())).thenReturn(new EngineOrderDTO());
        when(engineApiClient.evaluateRoutes(any())).thenReturn(engine);
    }

    private EngineEvaluateResponseDTO.EngineEvaluatedStopDTO stop(String type, Long orderId,
                                                                  double lat, double lon,
                                                                  String arrival, String departure,
                                                                  String geometryToNext) {
        EngineEvaluateResponseDTO.EngineEvaluatedStopDTO stop =
                new EngineEvaluateResponseDTO.EngineEvaluatedStopDTO();
        stop.setType(type);
        stop.setOrderId(orderId);
        stop.setLatitude(lat);
        stop.setLongitude(lon);
        stop.setArrivalTime(arrival);
        stop.setDepartureTime(departure);
        stop.setGeometryToNext(geometryToNext);
        return stop;
    }

    private EngineEvaluateResponseDTO engineWith(List<EngineEvaluateResponseDTO.EngineEvaluatedStopDTO> stops) {
        EngineEvaluateResponseDTO response = new EngineEvaluateResponseDTO();
        response.setFeasible(true);
        response.setViolations(List.of());

        EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO route =
                new EngineEvaluateResponseDTO.EngineEvaluatedRouteDTO();
        route.setVehicleId(7L);
        route.setVehicleLicensePlate("29A-12345");
        route.setOrderCount(2);
        route.setTotalDistance(3.0);
        route.setTotalTime(1.5);
        route.setTotalCO2(0.6);
        route.setLoadUtilization(80.0);
        route.setStops(stops);
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

    /** The return leg of the preview crosses the zone; both stops are outside it. */
    private EngineEvaluateResponseDTO transitViolationEngine() {
        return engineWith(List.of(
                stop("DEPOT", null, 10.0, 106.0, null, "08:00:00", encode(10.0, 106.0, 10.0, 106.020)),
                stop("CUSTOMER", 11L, 10.0, 106.020, "08:30:00", "08:40:00", null)));
    }

    /** Same route, but the leg stays west of the zone. */
    private EngineEvaluateResponseDTO compliantEngine() {
        return engineWith(List.of(
                stop("DEPOT", null, 10.0, 106.0, null, "08:00:00", encode(10.0, 106.0, 10.0, 106.001)),
                stop("CUSTOMER", 11L, 10.0, 106.001, "08:10:00", "08:20:00", null)));
    }

    private CustomizeRoutesRequest sequences(Long vehicleId, Long... orderIds) {
        RouteSequenceRequest seq = new RouteSequenceRequest();
        seq.setVehicleId(vehicleId);
        seq.setStopOrderIds(List.of(orderIds));
        CustomizeRoutesRequest request = new CustomizeRoutesRequest();
        request.setRoutes(List.of(seq));
        return request;
    }

    // ---------- CU-T1 ----------

    @Test
    void cuT1_previewListsTheTransitViolationAndPersistsNothing() {
        stubZone();
        stubLookups(transitViolationEngine());

        RouteEvaluateResponseDTO preview = service.evaluate(9L, BRANCH, sequences(7L, 11L, 12L));

        assertThat(preview.getViolations())
                .anySatisfy(violation -> {
                    assertThat(violation.getCode()).isEqualTo("ZONE_TRANSIT");
                    assertThat(violation.getDetail()).contains("Z1");
                });
        assertThat(preview.isFeasible()).isFalse();
        verify(solutionRepository, never()).save(any());
    }

    // ---------- CU-T2 ----------

    @Test
    void cuT2_saveWithTheViolation_is422AndPersistsNothing() {
        stubZone();
        stubLookups(transitViolationEngine());
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of());

        UnprocessablePlanException ex = assertThrows(UnprocessablePlanException.class,
                () -> service.saveRevision(9L, BRANCH, sequences(7L, 11L, 12L)));

        assertThat(ex.getMessage()).contains("ZONE_TRANSIT");
        verify(solutionRepository, never()).save(any());
    }

    // ---------- compliant save certifies the new routes (N6c) ----------

    @Test
    void saveCompliantPlan_certifiesTheSavedRoutesWithTheCurrentHash() {
        stubZone();
        stubLookups(compliantEngine());
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of());
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(solutionMapper.toDTO(any())).thenReturn(SolutionDetailResponseDTO.builder().build());

        service.saveRevision(9L, BRANCH, sequences(7L, 11L, 12L));

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(captor.capture());
        Route saved = captor.getValue().getRoutes().get(0);
        assertThat(saved.getLezStatus()).isEqualTo(LezStatus.COMPLIANT);
        assertThat(saved.getLezCheckedAt()).isNotNull();
        assertThat(saved.getLezRulesetHash()).isNotBlank();
        assertThat(saved.getLezViolations()).isNull();
    }
}
