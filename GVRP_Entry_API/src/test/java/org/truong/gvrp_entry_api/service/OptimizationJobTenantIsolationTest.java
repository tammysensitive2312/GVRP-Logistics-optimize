package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
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
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;

/**
 * Tenant isolation for {@link OptimizationJobService#submitJob} (SEC-017).
 * Missing and foreign-branch orders or vehicles must produce identical
 * RESOURCE_NOT_FOUND responses with no discriminating {@code field}.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationJobTenantIsolationTest {

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

    private OptimizationJobService service;
    private Branch branchA;
    private Branch branchB;

    @BeforeEach
    void setUp() {
        branchA = Branch.builder().id(BRANCH_A).name("Branch A").build();
        branchB = Branch.builder().id(BRANCH_B).name("Branch B").build();
        service = new OptimizationJobService(jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, new ObjectMapper().findAndRegisterModules(),
                optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));
        when(jobRepository.countByBranchIdAndStatusIn(any(), any())).thenReturn(0L);
    }

    private RoutePlanningRequest request(List<Long> orderIds, List<Long> vehicleIds) {
        RoutePlanningRequest request = new RoutePlanningRequest();
        request.setOrderIds(orderIds);
        request.setVehicleIds(vehicleIds);
        return request;
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

    @Test
    void submitJob_foreignOrder_matchesMissingOrderAndDoesNotLeakOrderCode() {
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
        DataInvalidException foreignEx = assertThrows(DataInvalidException.class,
                () -> service.submitJob(request(List.of(99L), List.of(7L)), BRANCH_A, USER_A));

        when(orderRepository.findAllById(List.of(999L))).thenReturn(List.of());
        DataInvalidException missingEx = assertThrows(DataInvalidException.class,
                () -> service.submitJob(request(List.of(999L), List.of(7L)), BRANCH_A, USER_A));

        assertThat(foreignEx.getErrors()).hasSize(1);
        ErrorDetail detail = foreignEx.getErrors().get(0);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(detail.getMessage()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        assertThat(detail.getResource()).isEqualTo(AppConstant.ORDER);
        assertThat(detail.getField()).isNull();
        assertThat(foreignEx.getErrors()).usingRecursiveComparison().isEqualTo(missingEx.getErrors());

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(engineApiClient);
    }

    @Test
    void submitJob_foreignVehicle_matchesMissingVehicleAndDoesNotExposeMissingIds() {
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(ownOrder(1L)));

        Vehicle foreignVehicle = Vehicle.builder()
                .id(55L)
                .fleet(Fleet.builder().id(5L).branch(branchB).build())
                .build();
        when(vehicleRepository.findAllByIdWithDepots(List.of(55L))).thenReturn(List.of(foreignVehicle));
        DataInvalidException foreignEx = assertThrows(DataInvalidException.class,
                () -> service.submitJob(request(List.of(1L), List.of(55L)), BRANCH_A, USER_A));

        when(vehicleRepository.findAllByIdWithDepots(List.of(888L))).thenReturn(List.of());
        DataInvalidException missingEx = assertThrows(DataInvalidException.class,
                () -> service.submitJob(request(List.of(1L), List.of(888L)), BRANCH_A, USER_A));

        assertThat(foreignEx.getErrors()).hasSize(1);
        ErrorDetail detail = foreignEx.getErrors().get(0);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(detail.getMessage()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        assertThat(detail.getResource()).isEqualTo(AppConstant.VEHICLE);
        assertThat(detail.getField()).isNull();
        assertThat(foreignEx.getErrors()).usingRecursiveComparison().isEqualTo(missingEx.getErrors());

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(engineApiClient);
    }
}
