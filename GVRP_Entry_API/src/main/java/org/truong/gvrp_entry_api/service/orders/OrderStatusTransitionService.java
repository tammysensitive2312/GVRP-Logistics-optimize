package org.truong.gvrp_entry_api.service.orders;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusTransitionService {

    private final OrderRepository orderRepository;

    public void transition(Order order, OrderStatus newStatus) {
        validateTransition(order.getStatus(), newStatus, order.getOrderCode());

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        orderRepository.save(order);

        log.info("Order [{}] transitioned: {} → {}",
                order.getOrderCode(), oldStatus, newStatus);
    }

    public void bulkTransition(List<Order> orders, OrderStatus newStatus) {
        if (orders.isEmpty()) return;

        orders.forEach(order ->
                validateTransition(order.getStatus(), newStatus, order.getOrderCode())
        );

        List<Long> ids = orders.stream()
                .map(Order::getId)
                .toList();
        orderRepository.updateStatusByIds(ids, newStatus);
        log.info("Bulk transitioned {} orders to [{}]", orders.size(), newStatus);
    }

    public BulkTransitionResult bulkTransitionLenient(
            List<Order> orders, OrderStatus newStatus) {

        if (orders.isEmpty()) {
            return BulkTransitionResult.empty();
        }

        Map<Boolean, List<Order>> partitioned = orders.stream()
                .collect(Collectors.partitioningBy(
                        order -> order.getStatus().canTransitionTo(newStatus)
                ));

        List<Order> validOrders = partitioned.get(true);
        List<Order> invalidOrders = partitioned.get(false);

        invalidOrders.forEach(order ->
                log.warn("Skipping order [{}]: cannot transition {} → {}",
                        order.getOrderCode(), order.getStatus(), newStatus)
        );

        if (!validOrders.isEmpty()) {
            List<Long> validIds = validOrders.stream()
                    .map(Order::getId)
                    .toList();
            orderRepository.updateStatusByIds(validIds, newStatus);
        }

        log.info("Lenient bulk transition: {}/{} orders transitioned to [{}]",
                validOrders.size(), orders.size(), newStatus);

        return new BulkTransitionResult(validOrders, invalidOrders);
    }

    private void validateTransition(OrderStatus current, OrderStatus next, String orderCode) {
        if (!current.canTransitionTo(next)) {
            ErrorDetail errorDetail = ErrorDetail.builder()
                    .code(ErrorCode.INVALID_ORDER_TRANSITION.getCode())
                    .message(String.format("Cannot transition order '%s' from [%s] to [%s]", orderCode, current, next))
                    .resource(AppConstant.ORDER)
                    .field("status")
                    .build();

            throw new DataInvalidException(List.of(errorDetail));
        }
    }

    public record BulkTransitionResult(
            List<Order> succeeded,
            List<Order> failed) {

        public static BulkTransitionResult empty() {
            return new BulkTransitionResult(List.of(), List.of());
        }

        public boolean hasFailures() {
            return !failed.isEmpty();
        }

        public int successCount() { return succeeded.size(); }
        public int failureCount() { return failed.size(); }
    }
}