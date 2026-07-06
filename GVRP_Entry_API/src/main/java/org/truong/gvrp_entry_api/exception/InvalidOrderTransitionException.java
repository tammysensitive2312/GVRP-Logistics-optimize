package org.truong.gvrp_entry_api.exception;

import org.springframework.http.HttpStatus;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

public class InvalidOrderTransitionException extends BusinessException {
    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(
                String.format(
                "Cannot transition order from [%s] to [%s]", from, to),
                ErrorCode.INVALID_ORDER_TRANSITION.getCode(),
                HttpStatus.CONFLICT
                );
    }
}
