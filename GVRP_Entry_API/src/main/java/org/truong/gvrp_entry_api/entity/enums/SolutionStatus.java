package org.truong.gvrp_entry_api.entity.enums;

public enum SolutionStatus {
    // A feasible and complete solution (all orders are served)
    SUCCESS,

    // Partially feasible solution (route available, but unfulfilled orders)
    PARTIAL_SUCCESS,

    // No viable solution found (no valid route)
    INFEASIBLE,

    // Initial state
    INITIAL
}