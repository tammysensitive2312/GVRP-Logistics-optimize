package org.truong.gvrp_engine_api.exception;

/**
 * Factory helpers for {@link ErrorDetail} instances.
 */
public final class ErrorDetails {

    private ErrorDetails() {
    }

    public static ErrorDetail of(ErrorCode code) {
        return of(code, null, null, code.getMessage());
    }

    public static ErrorDetail of(ErrorCode code, String resource, String field, String message) {
        return new ErrorDetail(code.getCode(), message, resource, field);
    }

    public static ErrorDetail validation(String message) {
        return of(ErrorCode.VALIDATION_ERROR, null, null, message);
    }

    public static ErrorDetail validation(String field, String message) {
        return of(ErrorCode.VALIDATION_ERROR, null, field, message);
    }

    public static ErrorDetail unsupportedValue(String field, String message) {
        return of(ErrorCode.UNSUPPORTED_VALUE, null, field, message);
    }
}
