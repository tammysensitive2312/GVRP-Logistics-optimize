package org.truong.gvrp_entry_api.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR("0040001", "Invalid data validation."),
    UNSUPPORTED_VALUE("0040002", "Unsupported value."),
    FILE_SIZE_EXCEEDED("0040003", "File size exceed limit."),
    EMPTY_FIELD_ERROR("0040004", "Empty required field."),

    // 404
    RESOURCE_NOT_FOUND("0040402", "Resource not found."),

    // 409
    RESOURCE_CONFLICT("0040901", "Resource conflict."),
    INVALID_ORDER_TRANSITION("0040902", "Invalid order status transition."),
    JOB_LIMIT_EXCEEDED("0040903", "Branch reached job limit."),

    // 500
    INTERNAL_ERROR("0050001", "Internal server error."),
    BACKEND_SERVER_ERROR("0050002", "Backend server error."),
    DATABASE_ACCESS_ERROR("0050003", "Database access error.");

    private final String code;
    private final String message;
}
