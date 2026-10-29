package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.truong.gvrp_entry_api.dto.request.EngineLegsRequestDTO;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.dto.response.EngineLegsResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehiclePermit;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.entity.enums.SendStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.EngineUnavailableException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;
import org.truong.gvrp_entry_api.service.lez.RuleSelector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.encode;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;

/**
 * LEZ send guard (LEZ-001 P1b, SG-T1…SG-T4). DB-free: the repositories and the
 * engine client are Mockito substitutes; the compliance service runs the real
 * compiler and checker over mocked LEZ repositories.
 */
@ExtendWith(MockitoExtension.class)
class RouteSendServiceLezTest {

    private static final Long BRANCH = 1L;

    @Mock private RouteSendRepository sendRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private SolutionRepository solutionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EngineApiClient engineApiClient;

    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    private RouteSendService service;
    private RouteComplianceService complianceService;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        complianceService = new RouteComplianceService(
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository),
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

        service = new RouteSendService(sendRepository, routeRepository, solutionRepository,
                userRepository, orderRepository);
        ReflectionTestUtils.setField(service, "routeComplianceService", complianceService);
        ReflectionTestUtils.setField(service, "engineApiClient", engineApiClient);

        fixture = fixture();
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- fixtures ----------

    private record Fixture(Branch branch, User sender, User driver, Solution solution,
                           Order order, Vehicle vehicle, Route route) {
    }

    private Fixture fixture() {
        Branch branch = Branch.builder().id(BRANCH).build();

        User sender = User.builder().id(1L).branch(branch)
                .username("boss").role(UserRole.PLANNER).enabled(true).build();
        User driver = User.builder().id(2L).branch(branch)
                .username("taylai").fullName("Tai Lai").phone("0901")
                .role(UserRole.DRIVER).enabled(true).build();

        OptimizationJob job = OptimizationJob.builder().id(5L).branch(branch).build();
        Solution solution = Solution.builder().id(9L).job(job).branch(branch)
                .status(SolutionStatus.SUCCESS).type(SolutionType.ENGINE_GENERATED)
                .unservedOrders(0).routes(new ArrayList<>()).build();

        Order order = Order.builder().id(11L).branch(branch)
                .customerName("Khach").customerPhone("0902").deliveryNotes("Goi truoc")
                .address("Hanoi").demand(BigDecimal.TEN)
                .deliveryDate(WEDNESDAY).status(OrderStatus.ON_ROUTE)
                .build();

        VehicleType type = VehicleType.builder()
                .id(1L).typeName("Truck").capacity(1500)
                .legalClass(LegalClass.TRUCK).energyType(EnergyType.DIESEL)
                .grossWeightKg(4000).emissionStandard(5)
                .build();

        Vehicle vehicle = Vehicle.builder().id(7L).vehicleLicensePlate("29A-1")
                .vehicleType(type).build();

        RouteStop depot = RouteStop.builder().type(LocationType.DEPOT).sequenceNumber(0)
                .departureTime(LocalTime.of(8, 0)).location(point(10.0, 106.0)).build();
        RouteStop stop = RouteStop.builder().type(LocationType.ORDER).order(order)
                .sequenceNumber(1).arrivalTime(LocalTime.of(8, 30))
                .location(point(10.0, 106.020)).build();
        Route route = Route.builder().id(21L).solution(solution).vehicle(vehicle)
                .routeOrder(1).segments(new ArrayList<>(List.of(depot, stop))).build();
        solution.getRoutes().add(route);

        return new Fixture(branch, sender, driver, solution, order, vehicle, route);
    }

    private void asPlanner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.setAttribute("branchId", BRANCH);
        request.setAttribute("role", "PLANNER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private SendRouteRequest sendRequest() {
        SendRouteRequest request = new SendRouteRequest();
        request.setDriverUserId(2L);
        request.setSolutionId(9L);
        return request;
    }

    private void stubSendLookups() {
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(fixture.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(fixture.solution()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(fixture.sender()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(fixture.driver()));
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

    private EngineLegsResponseDTO legsResponse(String polyline, String instruction) {
        EngineLegsResponseDTO.InstructionDTO instructionDTO = new EngineLegsResponseDTO.InstructionDTO();
        instructionDTO.setText(instruction);
        instructionDTO.setDistanceM(120.0);
        instructionDTO.setTimeMs(30_000L);
        instructionDTO.setSign(2);
        instructionDTO.setStreetName("Le Loi");

        EngineLegsResponseDTO.LegDTO leg = new EngineLegsResponseDTO.LegDTO();
        leg.setPolyline(polyline);
        leg.setDistanceM(120.0);
        leg.setTimeMs(30_000L);
        leg.setInstructions(List.of(instructionDTO));

        EngineLegsResponseDTO response = new EngineLegsResponseDTO();
        response.setLocale("vi");
        response.setLegs(List.of(leg));
        return response;
    }

    private void stubSendCreation() {
        when(sendRepository.findByRouteIdAndStatusOrderBySentAtDescIdDesc(21L, SendStatus.CURRENT))
                .thenReturn(List.of())
                .thenReturn(List.of(RouteSend.builder().id(400L).status(SendStatus.CURRENT).build()));
        when(sendRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- SG-T1 ----------

    @Test
    void sgT1_compliantRouteWithoutInstructions_fillsLegsOnceAndSends() {
        asPlanner();
        stubSendLookups();
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        stubSendCreation();

        String polyline = encode(10.0, 106.0, 10.0, 106.020);
        when(engineApiClient.legs(any(), eq("vi"))).thenReturn(legsResponse(polyline, "Rẽ phải"));

        RouteSendDTO dto = service.sendRoute(21L, sendRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EngineLegsRequestDTO.Point>> pointsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(engineApiClient, times(1)).legs(pointsCaptor.capture(), eq("vi"));
        assertThat(pointsCaptor.getValue()).hasSize(2);
        assertThat(pointsCaptor.getValue().get(0).getLatitude()).isEqualTo(10.0);
        assertThat(pointsCaptor.getValue().get(1).getLongitude()).isEqualTo(106.020);

        RouteStop start = fixture.route().getSegments().get(0);
        assertThat(start.getGeometryToNext()).isEqualTo(polyline);
        assertThat(start.getInstructionsToNext()).contains("Rẽ phải");

        assertThat(fixture.route().getLezStatus()).isEqualTo(LezStatus.COMPLIANT);
        verify(sendRepository).saveAndFlush(any());
        assertThat(dto.getSolutionId()).isEqualTo(9L);
        assertThat(dto.getStatus()).isEqualTo("CURRENT");
    }

    // ---------- SG-T2 ----------

    @Test
    void sgT2_permitExpiredSincePlanning_is422_keepsTheVerdictAndCreatesNoSend() {
        asPlanner();
        stubSendLookups();
        stubZone();
        // Planning-time permit, already expired on the service date.
        when(permitRepository.findByVehicleIdAndBranchZoneIdAndBranchId(7L, 1L, BRANCH))
                .thenReturn(List.of(VehiclePermit.builder()
                        .id(41L)
                        .validFrom(LocalDate.of(2026, 1, 1))
                        .validTo(LocalDate.of(2026, 6, 30))
                        .build()));

        // The order stop is inside the zone; geometry and instructions are already stored.
        RouteStop start = fixture.route().getSegments().get(0);
        start.setGeometryToNext(encode(10.0, 106.0, 10.0, 106.010));
        start.setInstructionsToNext("[{\"text\":\"Đi thẳng\"}]");
        fixture.route().getSegments().get(1).setLocation(point(10.0, 106.010));

        assertThatThrownBy(() -> service.sendRoute(21L, sendRequest()))
                .isInstanceOf(UnprocessablePlanException.class)
                .hasMessageContaining("ZONE_ACCESS")
                .hasMessageContaining("zone=Z1");

        verify(engineApiClient, never()).legs(any(), any());
        verify(sendRepository, never()).saveAndFlush(any());
        verify(sendRepository, never())
                .findByRouteIdAndStatusOrderBySentAtDescIdDesc(any(), any());

        // The verdict lives on the managed, locked route: the refused transaction
        // commits it (review-p1b F1), so the planner can read it afterwards.
        assertThat(fixture.route().getLezStatus()).isEqualTo(LezStatus.VIOLATION);
        assertThat(fixture.route().getLezCheckedAt()).isNotNull();
        assertThat(fixture.route().getLezRulesetHash())
                .isNotBlank()
                .isEqualTo(complianceService.currentFor(fixture.solution()).getHash());
        assertThat(fixture.route().getLezViolations()).contains("ZONE_ACCESS");
    }

    // ---------- SG-T3 ----------

    @Test
    void sgT3_engineUnavailableDuringTheLegsFill_failsClosedWith502AndNoSend() {
        asPlanner();
        stubSendLookups();
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        when(engineApiClient.legs(any(), eq("vi")))
                .thenThrow(new EngineUnavailableException("engine down",
                        new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> service.sendRoute(21L, sendRequest()))
                .isInstanceOf(EngineUnavailableException.class);

        verify(sendRepository, never()).saveAndFlush(any());
        verify(sendRepository, never())
                .findByRouteIdAndStatusOrderBySentAtDescIdDesc(any(), any());
    }

    // ---------- SG-T4 ----------

    @Test
    void sgT4_routeOfAnotherBranch_keepsTheExistingBranchRefusal() {
        asPlanner();
        fixture.solution().setBranch(Branch.builder().id(2L).build());
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(fixture.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(fixture.solution()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(fixture.sender()));

        DataInvalidException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DataInvalidException.class, () -> service.sendRoute(21L, sendRequest()));

        assertThat(ex.getErrors().get(0).getMessage()).contains("belongs to another branch");
        verify(sendRepository, never()).saveAndFlush(any());
        verifyNoInteractions(engineApiClient);
        verifyNoInteractions(zoneRepository);
    }
}
