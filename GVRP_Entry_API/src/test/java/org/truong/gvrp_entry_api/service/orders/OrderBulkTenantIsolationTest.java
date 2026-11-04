package org.truong.gvrp_entry_api.service.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.BulkUpdateOrderStatusResponse;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SEC-ORDERS-001 (SEC-014): bulk status update must not disclose or touch
 * another branch's orders, and must not distinguish them from unknown ids.
 */
@ExtendWith(MockitoExtension.class)
class OrderBulkTenantIsolationTest {

    @Mock
    private GeocodingService geocodingService;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderStatusTransitionService transitionService;

    @InjectMocks
    private OrderService service;

    private Order order(Long id, Long branchId) {
        return Order.builder()
                .id(id)
                .branch(Branch.builder().id(branchId).build())
                .orderCode("SECRET-" + id)
                .demand(BigDecimal.TEN)
                .status(OrderStatus.SCHEDULED)
                .build();
    }

    @Test
    void foreignAndUnknownIdsAreIndistinguishable() {
        Order foreign = order(13L, 2L);
        when(orderRepository.findAllById(List.of(13L, 99L))).thenReturn(List.of(foreign));
        when(transitionService.bulkTransitionLenient(List.of(), OrderStatus.ON_ROUTE))
                .thenReturn(OrderStatusTransitionService.BulkTransitionResult.empty());

        BulkUpdateOrderStatusResponse response =
                service.bulkUpdateStatus(1L, List.of(13L, 99L), OrderStatus.ON_ROUTE);

        assertTrue(response.getUpdated().isEmpty());
        assertEquals(2, response.getFailed().size());
        BulkUpdateOrderStatusResponse.FailedRow foreignRow = response.getFailed().get(0);
        BulkUpdateOrderStatusResponse.FailedRow unknownRow = response.getFailed().get(1);
        assertEquals(13L, foreignRow.getId());
        assertEquals(99L, unknownRow.getId());
        assertNull(foreignRow.getOrderCode());
        assertNull(unknownRow.getOrderCode());
        assertEquals(unknownRow.getReason(), foreignRow.getReason());
        assertEquals("Order not found in this branch", foreignRow.getReason());
    }

    @Test
    void ownOrderStillUpdatedAndOnlyOwnOrdersReachTransition() {
        Order own = order(11L, 1L);
        Order foreign = order(13L, 2L);
        when(orderRepository.findAllById(List.of(11L, 13L))).thenReturn(List.of(own, foreign));
        when(transitionService.bulkTransitionLenient(List.of(own), OrderStatus.ON_ROUTE))
                .thenReturn(new OrderStatusTransitionService.BulkTransitionResult(
                        List.of(own), List.of()));

        BulkUpdateOrderStatusResponse response =
                service.bulkUpdateStatus(1L, List.of(11L, 13L), OrderStatus.ON_ROUTE);

        assertEquals(List.of(11L), response.getUpdated().stream()
                .map(BulkUpdateOrderStatusResponse.UpdatedRow::getId).toList());
        assertEquals(List.of(13L), response.getFailed().stream()
                .map(BulkUpdateOrderStatusResponse.FailedRow::getId).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void foreignOrderIsNeverTransitionedOrSaved() {
        Order foreign = order(13L, 2L);
        when(orderRepository.findAllById(List.of(13L))).thenReturn(List.of(foreign));
        when(transitionService.bulkTransitionLenient(any(), any()))
                .thenReturn(OrderStatusTransitionService.BulkTransitionResult.empty());

        service.bulkUpdateStatus(1L, List.of(13L), OrderStatus.ON_ROUTE);

        ArgumentCaptor<List<Order>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionService).bulkTransitionLenient(captor.capture(), eq(OrderStatus.ON_ROUTE));
        assertTrue(captor.getValue().isEmpty());
        assertEquals(OrderStatus.SCHEDULED, foreign.getStatus());
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).saveAll(any());
    }
}
