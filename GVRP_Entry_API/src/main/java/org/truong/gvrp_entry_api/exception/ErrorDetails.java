package org.truong.gvrp_entry_api.exception;

import org.truong.gvrp_entry_api.util.ErrorCode;

/**
 * Factory for common {@link ErrorDetail} shapes.
 * Centralizes detail construction instead of duplicating inline builders.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    /** ErrorCode with its default message. */
    public static ErrorDetail of(ErrorCode code) {
        return of(code, null, null, code.getMessage());
    }

    /** ErrorCode with its default message and a resource name. */
    public static ErrorDetail of(ErrorCode code, String resource) {
        return of(code, resource, null, code.getMessage());
    }

    /** Full ErrorDetail builder wrapper; optional resource/field may be null. */
    public static ErrorDetail of(ErrorCode code, String resource, String field, String message) {
        return ErrorDetail.builder()
                .code(code.getCode())
                .message(message)
                .resource(resource)
                .field(field)
                .build();
    }

    /**
     * VALIDATION_ERROR with the given message.
     */
    public static ErrorDetail validation(String message) {
        return of(ErrorCode.VALIDATION_ERROR, null, null, message);
    }

    /**
     * VALIDATION_ERROR with a field and custom message.
     */
    public static ErrorDetail validation(String field, String message) {
        return of(ErrorCode.VALIDATION_ERROR, null, field, message);
    }

    /**
     * VALIDATION_ERROR with a resource name and custom message.
     */
    public static ErrorDetail validationWithResource(String resource, String message) {
        return of(ErrorCode.VALIDATION_ERROR, resource, null, message);
    }

    /**
     * VALIDATION_ERROR with the default message and the given field.
     * Preserves the previous GeometryMapper shape (code/message + field).
     */
    public static ErrorDetail validationWithField(String field) {
        return of(ErrorCode.VALIDATION_ERROR, null, field, ErrorCode.VALIDATION_ERROR.getMessage());
    }

    /**
     * UNSUPPORTED_VALUE for a parameter/field whose value is out of range or unsupported.
     */
    public static ErrorDetail unsupportedValue(String field, String message) {
        return of(ErrorCode.UNSUPPORTED_VALUE, null, field, message);
    }
}
