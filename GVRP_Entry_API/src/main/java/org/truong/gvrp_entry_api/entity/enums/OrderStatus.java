package org.truong.gvrp_entry_api.entity.enums;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    SCHEDULED,
    ON_ROUTE,
    SERVICING,
    COMPLETED,
    UNASSIGNED,
    REJECTED;

    private Set<OrderStatus> allowedTransitions;

    static {
        SCHEDULED.allowedTransitions =
                EnumSet.of(ON_ROUTE, UNASSIGNED, REJECTED);

        ON_ROUTE.allowedTransitions =
                EnumSet.of(SERVICING);

        SERVICING.allowedTransitions =
                EnumSet.of(COMPLETED, REJECTED);

        COMPLETED.allowedTransitions =
                EnumSet.of(SCHEDULED);

        UNASSIGNED.allowedTransitions =
                EnumSet.of(SCHEDULED);

        REJECTED.allowedTransitions =
                EnumSet.of(SCHEDULED);
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedTransitions.contains(next);
    }
}