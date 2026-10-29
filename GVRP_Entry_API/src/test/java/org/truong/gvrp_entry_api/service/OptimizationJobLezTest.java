package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.EngineDepotDTO;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.request.EngineOrderDTO;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleDTO;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleTypeDTO;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationConfigMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationJobMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.CompiledRestrictions;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;
import org.truong.gvrp_entry_api.service.lez.RuleSelector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;

/**
 * Job submission with LEZ (LEZ-001 P1b, JS-T1…JS-T3). DB-free: every repository
 * is a Mockito substitute and the compiler runs on mocked LEZ repositories, so
 * the skills and the snapshot are produced by the real production code.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationJobLezTest {

    private static final Long BRANCH = 1L;
    private static final Long USER = 2L;

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepotMapper depotMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private VehicleTypeMapper vehicleTypeMapper;
    @Mock private OptimizationJobMapper jobMapper;
    @Mock private OptimizationConfigMapper optimizationConfigMapper;
    @Mock private EngineApiClient engineApiClient;
    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OptimizationJobService service;

    private Branch branch;
    private Fleet fleet;

    // ---------- fixtures ----------

    @BeforeEach
    void setUp() {
        branch = Branch.builder().id(BRANCH).name("Branch 1").build();
        fleet = Fleet.builder().id(1L).branch(branch).fleetName("F").build();
        service = new OptimizationJobService(jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, objectMapper, optimizationConfigMapper,
                engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));

        lenient().when(jobRepository.countByBranchIdAndStatus(any(), any())).thenReturn(0L);
        lenient().when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        lenient().when(userRepository.findById(USER))
                .thenReturn(Optional.of(User.builder().id(USER).branch(branch).build()));
        lenient().when(jobRepository.save(any())).thenAnswer(inv -> {
            OptimizationJob job = inv.getArgument(0);
            job.setId(5L);
            return job;
        });
        lenient().when(jobMapper.toDTO(any())).thenReturn(
                OptimizationJobDTO.builder().id(5L).status(OptimizationJobStatus.PENDING).build());
        lenient().when(orderMapper.toEngineDTO(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            EngineOrderDTO dto = new EngineOrderDTO();
            dto.setId(order.getId());
            return dto;
        });
        lenient().when(vehicleMapper.toEngineDTO(any())).thenAnswer(inv -> {
            Vehicle vehicle = inv.getArgument(0);
            EngineVehicleDTO dto = new EngineVehicleDTO();
            dto.setId(vehicle.getId());
            return dto;
        });
        lenient().when(vehicleTypeMapper.toEngineDTO(any())).thenAnswer(inv -> {
            VehicleType type = inv.getArgument(0);
            EngineVehicleTypeDTO dto = new EngineVehicleTypeDTO();
            dto.setId(type.getId());
            return dto;
        });
        lenient().when(depotMapper.toEngineDTO(any())).thenAnswer(inv -> {
            Depot depot = inv.getArgument(0);
            EngineDepotDTO dto = new EngineDepotDTO();
            dto.setId(depot.getId());
            return dto;
        });
        lenient().when(optimizationConfigMapper.toEngineConfig(any()))
                .thenReturn(new EngineOptimizationRequest.OptimizationConfig());
    }

    private ZoneTemplate template() {
        return ZoneTemplate.builder()
                .id(10L).code("TEST").name("Test zone").jurisdiction("XX")
                .geometry(square(106.005, 9.995, 106.015, 10.005))
                .version(2)
                .build();
    }

    private void stubZone() {
        ZoneTemplate template = template();
        lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(
                BranchZone.builder().id(1L).branch(branch).mode(BranchZoneMode.LINKED)
                        .sourceTemplate(template).enabled(true).build()));
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

    private Order order(long id, String code, double lat, double lon, LocalDate date) {
        return Order.builder()
                .id(id).branch(branch).orderCode(code)
                .location(point(lat, lon)).demand(BigDecimal.ONE)
                .deliveryDate(date)
                .status(org.truong.gvrp_entry_api.entity.enums.OrderStatus.SCHEDULED)
                .build();
    }

    private Vehicle vehicle(long id, EnergyType energy, Depot depot) {
        VehicleType type = VehicleType.builder()
                .id(1L).typeName("T").capacity(1500).legalClass(LegalClass.TRUCK)
                .energyType(energy).grossWeightKg(4000).emissionStandard(5)
                .build();
        return Vehicle.builder()
                .id(id).fleet(fleet).vehicleType(type)
                .startDepot(depot).endDepot(depot)
                .vehicleLicensePlate("V" + id)
                .status(org.truong.gvrp_entry_api.entity.enums.VehicleStatus.AVAILABLE)
                .build();
    }

    private RoutePlanningRequest planningRequest(List<Long> orderIds, Long... vehicleIds) {
        RoutePlanningRequest request = new RoutePlanningRequest();
        request.setOrderIds(orderIds);
        request.setVehicleIds(List.of(vehicleIds));
        return request;
    }

    private static List<String> messages(DataInvalidException ex) {
        return ex.getErrors().stream().map(error -> error.getMessage()).toList();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private EngineOptimizationRequest submitAndCapture(RoutePlanningRequest request) {
        TransactionSynchronizationManager.initSynchronization();
        service.submitJob(request, BRANCH, USER);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<EngineOptimizationRequest> captor =
                ArgumentCaptor.forClass(EngineOptimizationRequest.class);
        verify(engineApiClient).submitOptimizationAsync(eq(5L), captor.capture());
        return captor.getValue();
    }

    // ---------- JS-T1 ----------

    private void assertMixedDatesRejected(boolean branchHasZones) {
        if (branchHasZones) {
            stubZone();
        } else {
            lenient().when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());
        }

        Order first = order(11L, "ORD-11", 10.0, 106.010, WEDNESDAY);
        Order second = order(12L, "ORD-12", 10.0, 106.200, WEDNESDAY.plusDays(1));
        when(orderRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(vehicleRepository.findAllByIdWithDepots(any())).thenReturn(List.of(
                vehicle(7L, EnergyType.ELECTRIC, Depot.builder().id(1L)
                        .location(point(10.0, 106.2)).build())));

        DataInvalidException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DataInvalidException.class,
                () -> service.submitJob(planningRequest(List.of(11L, 12L), 7L), BRANCH, USER));

        assertThat(messages(ex).get(0))
                .contains(WEDNESDAY.toString())
                .contains(WEDNESDAY.plusDays(1).toString());
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(engineApiClient);
        verifyNoInteractions(zoneRepository);
    }

    @Test
    void jsT1_twoDatesInABranchWithZones_is400AndNothingIsCompiled() {
        assertMixedDatesRejected(true);
    }

    @Test
    void jsT1_twoDatesInABranchWithoutZones_is400Too() {
        assertMixedDatesRejected(false);
    }

    // ---------- JS-T2 ----------

    @Test
    void jsT2_skillsOnInZoneOrdersAndAllowedVehicles_snapshotStored() throws Exception {
        stubZone();

        Depot inside = Depot.builder().id(1L).location(point(10.0, 106.010)).build();
        Depot outside = Depot.builder().id(2L).location(point(10.0, 106.200)).build();
        Vehicle banned = vehicle(7L, EnergyType.DIESEL, inside);
        Vehicle allowed = vehicle(8L, EnergyType.ELECTRIC, outside);

        Order inZone = order(11L, "ORD-11", 10.0, 106.010, WEDNESDAY);
        Order outsideZone = order(12L, "ORD-12", 10.0, 106.200, WEDNESDAY);
        when(orderRepository.findAllById(any())).thenReturn(List.of(inZone, outsideZone));
        when(vehicleRepository.findAllByIdWithDepots(any())).thenReturn(List.of(banned, allowed));

        EngineOptimizationRequest engine = submitAndCapture(planningRequest(List.of(11L, 12L), 7L, 8L));

        EngineOrderDTO inZoneDto = engine.getOrders().stream()
                .filter(dto -> dto.getId().equals(11L)).findFirst().orElseThrow();
        EngineOrderDTO outsideDto = engine.getOrders().stream()
                .filter(dto -> dto.getId().equals(12L)).findFirst().orElseThrow();
        assertThat(inZoneDto.getLezSkills()).containsExactly("LEZ_Z1");
        assertThat(outsideDto.getLezSkills()).isEmpty();

        assertThat(engine.getVehicles()).extracting(EngineVehicleDTO::getId).containsExactly(8L);
        assertThat(engine.getVehicles().get(0).getLezSkills()).containsExactly("LEZ_Z1");

        ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        String snapshot = jobCaptor.getValue().getLezSnapshot();
        assertThat(snapshot).isNotBlank();
        CompiledRestrictions parsed = objectMapper.readValue(snapshot, CompiledRestrictions.class);
        assertThat(parsed.getZones()).hasSize(1);
        assertThat(parsed.getZones().get(0).getKey()).isEqualTo("Z1");
        assertThat(parsed.unusableVehicleIds()).containsExactly(7L);
    }

    @Test
    void jsT2_noZones_leavesEverySkillEmptyAndStillStoresTheSnapshot() throws Exception {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of());

        Depot depot = Depot.builder().id(1L).location(point(10.0, 106.010)).build();
        when(orderRepository.findAllById(any()))
                .thenReturn(List.of(order(11L, "ORD-11", 10.0, 106.010, WEDNESDAY)));
        when(vehicleRepository.findAllByIdWithDepots(any()))
                .thenReturn(List.of(vehicle(7L, EnergyType.DIESEL, depot)));

        EngineOptimizationRequest engine = submitAndCapture(planningRequest(List.of(11L), 7L));

        assertThat(engine.getOrders().get(0).getLezSkills()).isEmpty();
        assertThat(engine.getVehicles().get(0).getLezSkills()).isEmpty();

        ArgumentCaptor<OptimizationJob> jobCaptor = ArgumentCaptor.forClass(OptimizationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        CompiledRestrictions parsed = objectMapper.readValue(
                jobCaptor.getValue().getLezSnapshot(), CompiledRestrictions.class);
        assertThat(parsed.hasZones()).isFalse();
    }

    // ---------- JS-T3 ----------

    @Test
    void jsT3_allVehiclesUnusable_is400NamingZoneAndVehicle_noEngineCall_noJob() {
        stubZone();

        Depot inside1 = Depot.builder().id(1L).location(point(10.0, 106.010)).build();
        Depot inside2 = Depot.builder().id(2L).location(point(10.0, 106.012)).build();
        when(orderRepository.findAllById(any()))
                .thenReturn(List.of(order(11L, "ORD-11", 10.0, 106.010, WEDNESDAY)));
        when(vehicleRepository.findAllByIdWithDepots(any())).thenReturn(List.of(
                vehicle(7L, EnergyType.DIESEL, inside1),
                vehicle(8L, EnergyType.DIESEL, inside2)));

        DataInvalidException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DataInvalidException.class,
                () -> service.submitJob(planningRequest(List.of(11L), 7L, 8L), BRANCH, USER));

        assertThat(messages(ex).get(0)).contains("Z1").contains("7").contains("8");
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(engineApiClient);
    }
}
