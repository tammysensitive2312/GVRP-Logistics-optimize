package org.truong.gvrp_entry_api.controller;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
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
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.OptimizationJobService;
import org.truong.gvrp_entry_api.service.RouteSendService;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;

/**
 * HTTP-layer API tests for PR 1 security fixes (SEC-017, SEC-018, SEC-019):
 * wires real controllers, real services, and {@link GlobalExceptionHandler}
 * with the production {@code SNAKE_CASE} wire contract over mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class SecurityPr1ApiTest {

    private static final Long BRANCH_A = 1L;
    private static final Long BRANCH_B = 2L;
    private static final Long USER_A = 10L;

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

    @Mock private RouteSendRepository sendRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private SolutionRepository solutionRepository;

    private MockMvc mockMvc;
    private Branch branchA;
    private Branch branchB;

    @BeforeEach
    void setUp() {
        branchA = Branch.builder().id(BRANCH_A).name("Branch A").build();
        branchB = Branch.builder().id(BRANCH_B).name("Branch B").build();

        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();

        OptimizationJobService jobService = new OptimizationJobService(
                jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, snake, optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));
        lenient().when(jobRepository.countByBranchIdAndStatus(any(), any())).thenReturn(0L);

        RouteSendService routeSendService = new RouteSendService(
                sendRepository, routeRepository, solutionRepository, userRepository, orderRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OptimizationJobController(jobService),
                        new RouteSendController(routeSendService))
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

    private void stubPlannerUser() {
        User planner = User.builder()
                .id(USER_A)
                .branch(branchA)
                .username("planner-a")
                .role(UserRole.PLANNER)
                .enabled(true)
                .build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(planner));
    }

    private Order ownOrder(long id) {
        return Order.builder()
                .id(id)
                .branch(branchA)
                .orderCode("ORD-A-" + id)
                .location(point(10.0, 106.0))
                .demand(BigDecimal.ONE)
                .deliveryDate(WEDNESDAY)
                .status(OrderStatus.SCHEDULED)
                .build();
    }

    private Solution solution(long id, Branch branch, SolutionStatus status, long routeId) {
        Solution solution = Solution.builder()
                .id(id)
                .branch(branch)
                .status(status)
                .type(SolutionType.ENGINE_GENERATED)
                .routes(new ArrayList<>())
                .build();
        Route route = Route.builder()
                .id(routeId)
                .solution(solution)
                .vehicle(Vehicle.builder().id(7L).build())
                .routeOrder(1)
                .segments(new ArrayList<>(List.of(
                        RouteStop.builder().type(LocationType.ORDER)
                                .order(Order.builder().id(11L).branch(branch).build())
                                .sequenceNumber(1)
                                .build())))
                .build();
        solution.getRoutes().add(route);
        return solution;
    }

    // ---------- SEC-017: POST /api/v1/jobs/plan ----------

    @Test
    void planJob_foreignOrderVsMissingOrder_returnsByteIdenticalJsonWithoutOrderCode() throws Exception {
        Order foreignOrder = Order.builder()
                .id(99L)
                .branch(branchB)
                .orderCode("SECRET-B-ORDER")
                .location(point(10.0, 106.0))
                .demand(BigDecimal.ONE)
                .deliveryDate(WEDNESDAY)
                .status(OrderStatus.SCHEDULED)
                .build();
        when(orderRepository.findAllById(List.of(99L))).thenReturn(List.of(foreignOrder));
        when(orderRepository.findAllById(List.of(999L))).thenReturn(List.of());

        MvcResult foreignRes = mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[99],"vehicle_ids":[7]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.ORDER))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist())
                .andReturn();

        MvcResult missingRes = mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[999],"vehicle_ids":[7]}"""))
                .andExpect(status().isBadRequest())
                .andReturn();

        String foreignBody = foreignRes.getResponse().getContentAsString();
        String missingBody = missingRes.getResponse().getContentAsString();
        assertThat(foreignBody)
                .isEqualTo(missingBody)
                .doesNotContain("SECRET-B-ORDER");
    }

    @Test
    void planJob_foreignVehicleVsMissingVehicle_returnsByteIdenticalJsonWithoutMissingIds() throws Exception {
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(ownOrder(1L)));

        Vehicle foreignVehicle = Vehicle.builder()
                .id(55L)
                .fleet(Fleet.builder().id(5L).branch(branchB).build())
                .build();
        when(vehicleRepository.findAllByIdWithDepots(List.of(55L))).thenReturn(List.of(foreignVehicle));
        when(vehicleRepository.findAllByIdWithDepots(List.of(888L))).thenReturn(List.of());

        MvcResult foreignRes = mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1],"vehicle_ids":[55]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.VEHICLE))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist())
                .andReturn();

        MvcResult missingRes = mockMvc.perform(plannerPost("/api/v1/jobs/plan",
                        """
                        {"order_ids":[1],"vehicle_ids":[888]}"""))
                .andExpect(status().isBadRequest())
                .andReturn();

        String foreignBody = foreignRes.getResponse().getContentAsString();
        String missingBody = missingRes.getResponse().getContentAsString();
        assertThat(foreignBody)
                .isEqualTo(missingBody)
                .doesNotContain("888");
        assertThat(missingBody).doesNotContain("888");
    }

    // ---------- SEC-018: POST /api/v1/routes/{id}/send ----------

    @Test
    void sendRoute_foreignSolutionVsMissingSolution_returnsByteIdenticalJsonWithoutLocking() throws Exception {
        stubPlannerUser();
        Solution foreignInfeasible = solution(90L, branchB, SolutionStatus.INFEASIBLE, 200L);
        when(solutionRepository.findWithDetailsById(90L)).thenReturn(Optional.of(foreignInfeasible));
        when(solutionRepository.findWithDetailsById(999L)).thenReturn(Optional.empty());

        MvcResult foreignRes = mockMvc.perform(plannerPost("/api/v1/routes/200/send",
                        """
                        {"driver_user_id":2,"solution_id":90}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.SOLUTION))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist())
                .andReturn();

        MvcResult missingRes = mockMvc.perform(plannerPost("/api/v1/routes/200/send",
                        """
                        {"driver_user_id":2,"solution_id":999}"""))
                .andExpect(status().isBadRequest())
                .andReturn();

        String foreignBody = foreignRes.getResponse().getContentAsString();
        assertThat(foreignBody)
                .isEqualTo(missingRes.getResponse().getContentAsString())
                .doesNotContain("another branch")
                .doesNotContain("INFEASIBLE");
        verify(routeRepository, never()).findWithLockById(any());
    }

    @Test
    void sendRoute_ownSolutionWithForeignRoute_returnsNotFoundWithoutLocking() throws Exception {
        stubPlannerUser();
        Solution ownSolution = solution(9L, branchA, SolutionStatus.SUCCESS, 21L);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(ownSolution));

        mockMvc.perform(plannerPost("/api/v1/routes/888/send",
                        """
                        {"driver_user_id":2,"solution_id":9}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value(AppConstant.ROUTE))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist());

        verify(routeRepository, never()).findWithLockById(any());
    }

    @Test
    void sendRoute_foreignDisabledNonDriverAndMissingDriver_returnByteIdenticalJson() throws Exception {
        stubPlannerUser();
        Solution ownSolution = solution(9L, branchA, SolutionStatus.SUCCESS, 21L);
        Route ownRoute = ownSolution.getRoutes().get(0);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(ownSolution));
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(ownRoute));

        User foreignDriver = User.builder().id(30L).branch(branchB).role(UserRole.DRIVER).enabled(true).build();
        User disabledDriver = User.builder().id(31L).branch(branchA).role(UserRole.DRIVER).enabled(false).build();
        User plannerUser = User.builder().id(32L).branch(branchA).role(UserRole.PLANNER).enabled(true).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(foreignDriver));
        when(userRepository.findById(31L)).thenReturn(Optional.of(disabledDriver));
        when(userRepository.findById(32L)).thenReturn(Optional.of(plannerUser));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        String missingBody = mockMvc.perform(plannerPost("/api/v1/routes/21/send",
                        """
                        {"driver_user_id":999,"solution_id":9}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.errors[0].resource").value("Driver"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (long driverId : List.of(30L, 31L, 32L)) {
            String body = mockMvc.perform(plannerPost("/api/v1/routes/21/send",
                            """
                            {"driver_user_id":%d,"solution_id":9}""".formatted(driverId)))
                    .andExpect(status().isBadRequest())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(body)
                    .isEqualTo(missingBody)
                    .doesNotContain("not an enabled driver");
        }
    }

    // ---------- SEC-019: Malformed JSON / Jackson type mismatch ----------

    @Test
    void unreadableBody_malformedJsonAndTypeMismatch_hideJacksonDetailsAndRejectedValue() throws Exception {
        MvcResult typeMismatch = mockMvc.perform(plannerPost("/api/v1/routes/21/send",
                        """
                        {"driver_user_id":"cust-phone-0901234567","solution_id":9}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value("Malformed request body."))
                .andReturn();

        assertThat(typeMismatch.getResponse().getContentAsString())
                .doesNotContain("cust-phone-0901234567")
                .doesNotContain("org.truong")
                .doesNotContain("Long")
                .doesNotContain("InvalidFormatException");

        MvcResult syntaxError = mockMvc.perform(plannerPost("/api/v1/jobs/plan", "{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value("Malformed request body."))
                .andReturn();

        assertThat(syntaxError.getResponse().getContentAsString())
                .doesNotContain("JsonParseException")
                .doesNotContain("org.truong");
    }
}
