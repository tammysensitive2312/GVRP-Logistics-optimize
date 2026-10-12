package org.truong.gvrp_entry_api.exception;

import org.truong.gvrp_entry_api.util.ErrorCode;

/**
 * Factory for common {@link ErrorDetail} shapes.
 * Preserves the same codes/messages as the previous inline builders.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    /**
     * RESOURCE_NOT_FOUND with the given resource name.
     */
    public static ErrorDetail notFound(String resource) {
        return ErrorDetail.builder()
                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                .resource(resource)
                .build();
    }

    /**
     * VALIDATION_ERROR with the given message.
     */
    public static ErrorDetail validation(String message) {
        return ErrorDetail.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(message)
                .build();
    }

    /**
     * VALIDATION_ERROR with the default message and the given field.
     * Preserves the previous GeometryMapper shape (code/message + field).
     */
    public static ErrorDetail validationWithField(String field) {
        return ErrorDetail.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(ErrorCode.VALIDATION_ERROR.getMessage())
                .field(field)
                .build();
    }
}
