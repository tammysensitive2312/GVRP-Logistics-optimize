package org.truong.gvrp_entry_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.service.OptimizationJobService;
import org.truong.gvrp_entry_api.service.VehicleService;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;
import org.truong.gvrp_entry_api.service.orders.OrderStatusTransitionService;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests for pagination and history limit bounds (R-2):
 * out-of-range parameters answer 400 VALIDATION_ERROR instead of 500,
 * while existing Angular client ceilings stay valid.
 */
@ExtendWith(MockitoExtension.class)
class PaginationValidationApiTest {

    private static final Long BRANCH_A = 1L;

    @Mock private VehicleMapper vehicleMapper;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private FleetRepository fleetRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private VehicleTypeRepository vehicleTypeRepository;

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderStatusTransitionService transitionService;
    @Mock private OrderImportService orderImportService;

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepotMapper depotMapper;
    @Mock private VehicleTypeMapper vehicleTypeMapper;
    @Mock private OptimizationJobMapper jobMapper;
    @Mock private OptimizationConfigMapper optimizationConfigMapper;
    @Mock private EngineApiClient engineApiClient;
    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();

        VehicleService vehicleService = new VehicleService(
                vehicleMapper, vehicleRepository, fleetRepository, depotRepository, vehicleTypeRepository);
        OrderService orderService = new OrderService(
                geocodingService, branchRepository, orderRepository, orderMapper, transitionService);
        OptimizationJobService jobService = new OptimizationJobService(
                jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, snake, optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));

        lenient().when(vehicleMapper.toDTOList(any())).thenReturn(List.of());
        lenient().when(orderMapper.toDTOList(any())).thenReturn(List.of());

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VehicleController(vehicleService),
                        new OrderController(orderImportService, orderService),
                        new OptimizationJobController(jobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    @Test
    void vehiclesPagination_invalidBoundsReturn400_andClientCeiling1000Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles").param("page", "-1").param("size", "10")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        mockMvc.perform(get("/api/v1/vehicles").param("page", "0").param("size", "0")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        mockMvc.perform(get("/api/v1/vehicles").param("page", "0")
                        .param("size", String.valueOf(AppConstant.MAX_VEHICLE_PAGE_SIZE + 1))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        verifyNoInteractions(vehicleRepository);

        when(vehicleRepository.findByFleetBranchId(eq(BRANCH_A), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/vehicles").param("page", "0")
                        .param("size", String.valueOf(AppConstant.MAX_VEHICLE_PAGE_SIZE))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isOk());
    }

    @Test
    void ordersPagination_invalidBoundsReturn400_andSelectAllCeiling10000Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("page", "-1").param("size", "10")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        mockMvc.perform(get("/api/v1/orders").param("page", "0").param("size", "0")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        mockMvc.perform(get("/api/v1/orders").param("page", "0")
                        .param("size", String.valueOf(AppConstant.MAX_ORDER_PAGE_SIZE + 1))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        verifyNoInteractions(orderRepository);

        when(orderRepository.searchOrders(eq(BRANCH_A), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/orders").param("page", "0")
                        .param("size", String.valueOf(AppConstant.MAX_ORDER_PAGE_SIZE))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isOk());
    }

    @Test
    void jobHistoryLimit_invalidBoundsReturn400_andValidBoundsReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("limit", "0")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));

        mockMvc.perform(get("/api/v1/jobs")
                        .param("limit", String.valueOf(AppConstant.MAX_JOB_HISTORY_LIMIT + 1))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));

        verifyNoInteractions(jobRepository);

        when(jobRepository.findJobHistory(eq(BRANCH_A), any(Pageable.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs").param("limit", "50")
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs")
                        .param("limit", String.valueOf(AppConstant.MAX_JOB_HISTORY_LIMIT))
                        .requestAttr("branchId", BRANCH_A))
                .andExpect(status().isOk());
    }
}
