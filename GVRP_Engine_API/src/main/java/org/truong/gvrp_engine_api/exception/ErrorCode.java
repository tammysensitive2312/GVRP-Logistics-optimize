package org.truong.gvrp_engine_api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR("0040001", "Invalid data validation."),
    UNSUPPORTED_VALUE("0040002", "Unsupported value."),
    EMPTY_FIELD_ERROR("0040004", "Empty required field."),
    INVALID_VALUE_TYPE("0040005", "Invalid value type."),

    // 401
    UNAUTHORIZED("0040102", "Unauthorized."),

    // 404
    RESOURCE_NOT_FOUND("0040402", "Resource not found."),

    // 405
    METHOD_NOT_ALLOWED("0040501", "Method not allowed."),

    // 409
    RESOURCE_CONFLICT("0040901", "Resource conflict."),

    // 413
    PAYLOAD_TOO_LARGE("0041301", "Payload too large."),

    // 415
    UNSUPPORTED_MEDIA_TYPE("0041501", "Unsupported media type."),

    // 500
    INTERNAL_ERROR("0050001", "Internal server error."),
    BACKEND_SERVER_ERROR("0050002", "Backend server error.");

    private final String code;
    private final String message;
}
