package org.truong.gvrp_entry_api.service.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.truong.gvrp_entry_api.dto.response.BulkUpdateOrderStatusResponse;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for filtered listing and lenient bulk status updates.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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

    private Order order(Long id, Long branchId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .branch(Branch.builder().id(branchId).build())
                .orderCode("ORD-" + id)
                .demand(BigDecimal.TEN)
                .status(status)
                .build();
    }

    @Test
    void list_passesFiltersToRepository() {
        when(orderRepository.searchOrders(eq(1L), eq(LocalDate.of(2026, 10, 11)),
                eq(OrderStatus.SCHEDULED), eq("hanoi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<?> response = service.getAllOrdersPaginated(
                1L, 0, 10, LocalDate.of(2026, 10, 11), OrderStatus.SCHEDULED, "hanoi");

        verify(orderRepository).searchOrders(eq(1L), eq(LocalDate.of(2026, 10, 11)),
                eq(OrderStatus.SCHEDULED), eq("hanoi"), any(Pageable.class));
        assertEquals(0, response.getTotalElements());
    }

    @Test
    void bulk_flipsLegalAndReportsRest() {
        Order ok = order(11L, 1L, OrderStatus.SCHEDULED);
        Order bad = order(12L, 1L, OrderStatus.COMPLETED);
        Order foreign = order(13L, 2L, OrderStatus.SCHEDULED);
        when(orderRepository.findAllById(List.of(11L, 12L, 13L, 14L)))
                .thenReturn(List.of(ok, bad, foreign));
        when(transitionService.bulkTransitionLenient(List.of(ok, bad), OrderStatus.ON_ROUTE))
                .thenReturn(new OrderStatusTransitionService.BulkTransitionResult(
                        List.of(ok), List.of(bad)));

        BulkUpdateOrderStatusResponse response =
                service.bulkUpdateStatus(1L, List.of(11L, 12L, 13L, 14L), OrderStatus.ON_ROUTE);

        assertEquals(List.of(11L),
                response.getUpdated().stream()
                        .map(BulkUpdateOrderStatusResponse.UpdatedRow::getId).toList());
        assertEquals(java.util.Set.of(12L, 13L, 14L),
                new java.util.HashSet<>(response.getFailed().stream()
                        .map(BulkUpdateOrderStatusResponse.FailedRow::getId).toList()));
        assertTrue(response.getFailed().stream()
                .filter(f -> f.getId() == 12L)
                .findFirst().orElseThrow().getReason().contains("COMPLETED"));
    }

    @Test
    void lenientPartition_writesOnlyLegal() {
        OrderStatusTransitionService real =
                new OrderStatusTransitionService(orderRepository);
        Order ok = order(11L, 1L, OrderStatus.ON_ROUTE);
        Order bad = order(12L, 1L, OrderStatus.SCHEDULED);

        OrderStatusTransitionService.BulkTransitionResult result =
                real.bulkTransitionLenient(List.of(ok, bad), OrderStatus.SERVICING);

        assertEquals(List.of(12L),
                result.failed().stream().map(Order::getId).toList());
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).updateStatusByIds(captor.capture(), eq(OrderStatus.SERVICING));
        assertEquals(List.of(11L), captor.getValue());
    }

    @Test
    void bulk_emptyIds_returnsEmptyReport() {
        when(orderRepository.findAllById(List.of())).thenReturn(List.of());
        when(transitionService.bulkTransitionLenient(List.of(), OrderStatus.ON_ROUTE))
                .thenReturn(OrderStatusTransitionService.BulkTransitionResult.empty());

        BulkUpdateOrderStatusResponse response =
                service.bulkUpdateStatus(1L, List.of(), OrderStatus.ON_ROUTE);

        assertTrue(response.getUpdated().isEmpty());
        assertTrue(response.getFailed().isEmpty());
    }
}
