package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
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
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

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
 * BF-T4 (BROWSER-FIX-001, E4): planning orders that are not SCHEDULED is refused
 * with a message that says why and names at most 10 orders; code, resource and
 * field are unchanged.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationJobStatusMessageTest {

    private static final Long BRANCH = 1L;

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
    private Branch branch;

    @BeforeEach
    void setUp() {
        branch = Branch.builder().id(BRANCH).name("Branch 1").build();
        service = new OptimizationJobService(jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, new ObjectMapper().findAndRegisterModules(),
                optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));
        when(jobRepository.countByBranchIdAndStatus(any(), any())).thenReturn(0L);
    }

    private Order order(long id, OrderStatus status) {
        return Order.builder()
                .id(id).branch(branch).orderCode("ORD-" + id)
                .location(point(10.0, 106.0)).demand(BigDecimal.ONE)
                .deliveryDate(WEDNESDAY)
                .status(status)
                .build();
    }

    private DataInvalidException submit(List<Order> orders) {
        when(orderRepository.findAllById(any())).thenReturn(orders);
        RoutePlanningRequest request = new RoutePlanningRequest();
        request.setOrderIds(orders.stream().map(Order::getId).toList());
        request.setVehicleIds(List.of(7L));
        return assertThrows(DataInvalidException.class, () -> service.submitJob(request, BRANCH, 2L));
    }

    @Test
    void bfT4_twelveOnRouteOrders_messageNamesTenAndCountsTheRest() {
        List<Order> orders = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> order(i, OrderStatus.ON_ROUTE)).toList();

        DataInvalidException ex = submit(orders);

        assertThat(ex.getErrors()).hasSize(1);
        ErrorDetail detail = ex.getErrors().get(0);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT.getCode());
        assertThat(detail.getField()).isEqualTo(String.join(", ",
                IntStream.rangeClosed(1, 12).mapToObj(i -> "ORD-" + i).toList()));
        assertThat(detail.getMessage())
                .startsWith("Only SCHEDULED orders can be planned")
                .contains("ORD-1, ORD-2, ORD-3, ORD-4, ORD-5, ORD-6, ORD-7, ORD-8, ORD-9, ORD-10 (+2 more).")
                .doesNotContain("ORD-11")
                .doesNotContain("ORD-12");
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(engineApiClient);
    }

    @Test
    void bfT4_fewOrders_areAllNamedWithoutACount() {
        DataInvalidException ex = submit(List.of(
                order(1, OrderStatus.SCHEDULED), order(2, OrderStatus.UNASSIGNED), order(3, OrderStatus.COMPLETED)));

        ErrorDetail detail = ex.getErrors().get(0);
        assertThat(detail.getField()).isEqualTo("ORD-2, ORD-3");
        assertThat(detail.getMessage()).endsWith(": ORD-2, ORD-3.").doesNotContain("more");
    }
}
