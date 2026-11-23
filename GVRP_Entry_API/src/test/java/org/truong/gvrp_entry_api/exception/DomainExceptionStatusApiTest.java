package org.truong.gvrp_entry_api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.controller.OptimizationJobController;
import org.truong.gvrp_entry_api.controller.VehicleController;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationConfigMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationJobMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.OptimizationJobService;
import org.truong.gvrp_entry_api.service.VehicleService;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;

/**
 * HTTP status and envelope verification for unified domain exceptions (E-2, E-3, E-4, E-5):
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 with {@code resource} and optional {@code field}</li>
 *   <li>{@link ResourceConflictException} → 409 with {@code resource} and optional {@code field}</li>
 *   <li>Normalized {@code DataInvalidException} {@code invalidCoords} → 400 with {@code orders}/{@code location}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DomainExceptionStatusApiTest {

    private static final Long BRANCH_A = 1L;
    private static final Long USER_A = 10L;

    @Mock private VehicleMapper vehicleMapper;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private FleetRepository fleetRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private VehicleTypeRepository vehicleTypeRepository;

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepotMapper depotMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private VehicleTypeMapper vehicleTypeMapper;
    @Mock private OptimizationJobMapper jobMapper;
    @Mock private OptimizationConfigMapper optimizationConfigMapper;
    @Mock private EngineApiClient engineApiClient;
    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    private MockMvc mockMvc;
    private Branch branchA;
    private Fleet fleetA;

    @BeforeEach
    void setUp() {
        branchA = Branch.builder().id(BRANCH_A).name("Branch A").build();
        fleetA = Fleet.builder().id(5L).branch(branchA).build();

        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();

        VehicleService vehicleService = new VehicleService(
                vehicleMapper, vehicleRepository, fleetRepository, depotRepository, vehicleTypeRepository);

        OptimizationJobService jobService = new OptimizationJobService(
                jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, snake, optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));
        lenient().when(jobRepository.countByBranchIdAndStatusIn(any(), any())).thenReturn(0L);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VehicleController(vehicleService),
                        new OptimizationJobController(jobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    private MockHttpServletRequestBuilder plannerPost(String url, String body) {
        return post(url)
                .requestAttr("userId", USER_A)
                .requestAttr("branchId", BRANCH_A)
                .requestAttr("role", "PLANNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder plannerPut(String url, String body) {
        return put(url)
                .requestAttr("userId", USER_A)
                .requestAttr("branchId", BRANCH_A)
                .requestAttr("role", "PLANNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Order scheduledOrder(long id, String code) {
        return Order.builder()
                .id(id)
                .branch(branchA)
                .orderCode(code)
                .location(point(10.0, 106.0))
                .demand(BigDecimal.ONE)
                .deliveryDate(WEDNESDAY)
                .status(OrderStatus.SCHEDULED)
                .build();
    }

    // ---------- E-3 + E-5: VehicleController depot 404 with startDepotId / endDepotId ----------

    @Test
    void createVehicle_missingStartDepot_returns404WithStartDepotField() throws Exception {
        when(fleetRepository.findByIdAndBranchId(5L, BRANCH_A)).thenReturn(Optional.of(fleetA));
        when(depotRepository.findByIdAndBranchId(3L, BRANCH_A)).thenReturn(Optional.empty());

        mockMvc.perform(plannerPost("/api/v1/vehicles?fleetId=5",
                        """
                        {"vehicle_license_plate":"51A-12345","vehicle_type_id":4,"start_depot_id":3,"end_depot_id":4}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.RESOURCE_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.DEPOT))
                .andExpect(jsonPath("$.errors[0].field").value("startDepotId"));
    }

    @Test
    void createVehicle_missingEndDepot_returns404WithEndDepotField() throws Exception {
        when(fleetRepository.findByIdAndBranchId(5L, BRANCH_A)).thenReturn(Optional.of(fleetA));
        when(depotRepository.findByIdAndBranchId(3L, BRANCH_A)).thenReturn(Optional.of(Depot.builder().id(3L).build()));
        when(depotRepository.findByIdAndBranchId(4L, BRANCH_A)).thenReturn(Optional.empty());

        mockMvc.perform(plannerPost("/api/v1/vehicles?fleetId=5",
                        """
                        {"vehicle_license_plate":"51A-12345","vehicle_type_id":4,"start_depot_id":3,"end_depot_id":4}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.RESOURCE_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.DEPOT))
                .andExpect(jsonPath("$.errors[0].field").value("endDepotId"));
    }

    // ---------- E-4 + E-5: OptimizationJobController 409 conflicts & invalidCoords 400 ----------

    @Test
    void cancelJobs_completedJob_returns409ConflictEnvelope() throws Exception {
        OptimizationJob completed = OptimizationJob.builder()
                .id(101L)
                .branch(branchA)
                .status(OptimizationJobStatus.COMPLETED)
                .build();
        when(jobRepository.findAllById(List.of(101L))).thenReturn(List.of(completed));

        mockMvc.perform(plannerPut("/api/v1/jobs",
                        """
                        {"ids":[101]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.JOB));
    }

    @Test
    void planJob_nonScheduledOrders_returns409ConflictWithOrderCodesField() throws Exception {
        Order bad1 = scheduledOrder(1L, "ORD-1");
        bad1.setStatus(OrderStatus.ON_ROUTE);
        Order bad2 = scheduledOrder(2L, "ORD-2");
        bad2.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(bad1, bad2));

        mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1,2],"vehicle_ids":[7]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.ORDER))
                .andExpect(jsonPath("$.errors[0].field").value("ORD-1, ORD-2"));
    }

    @Test
    void planJob_unavailableVehicle_returns409ConflictEnvelope() throws Exception {
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(scheduledOrder(1L, "ORD-1")));
        Vehicle maintenance = Vehicle.builder()
                .id(7L)
                .fleet(fleetA)
                .status(VehicleStatus.MAINTENANCE)
                .build();
        when(vehicleRepository.findAllByIdWithDepots(List.of(7L))).thenReturn(List.of(maintenance));

        mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1],"vehicle_ids":[7]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.VEHICLE));
    }

    @Test
    void planJob_vehicleMissingDepot_returns409ConflictWithDepotField() throws Exception {
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(scheduledOrder(1L, "ORD-1")));
        Vehicle noDepot = Vehicle.builder()
                .id(7L)
                .fleet(fleetA)
                .status(VehicleStatus.AVAILABLE)
                .startDepot(null)
                .endDepot(null)
                .build();
        when(vehicleRepository.findAllByIdWithDepots(List.of(7L))).thenReturn(List.of(noDepot));

        mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1],"vehicle_ids":[7]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.VEHICLE))
                .andExpect(jsonPath("$.errors[0].field").value("depot"));
    }

    @Test
    void planJob_ordersMissingCoordinates_returns400NormalizedLocationField() throws Exception {
        Order missingLoc = Order.builder()
                .id(1L)
                .branch(branchA)
                .orderCode("ORD-MISSING-LOC")
                .location(null)
                .demand(BigDecimal.ONE)
                .deliveryDate(WEDNESDAY)
                .status(OrderStatus.SCHEDULED)
                .build();
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(missingLoc));

        mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1],"vehicle_ids":[7]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.ORDER))
                .andExpect(jsonPath("$.errors[0].field").value(AppConstant.LOCATION))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("ORD-MISSING-LOC")));
    }
}
