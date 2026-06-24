package org.truong.gvrp_entry_api.exception;

import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(String.format(
                "Cannot transition order from [%s] to [%s]", from, to));
    }
}
