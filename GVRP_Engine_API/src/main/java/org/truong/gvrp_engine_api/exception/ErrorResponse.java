package org.truong.gvrp_engine_api.exception;

import java.util.List;

/**
 * Standard error response format matching GVRP Entry API.
 */
public record ErrorResponse(List<ErrorDetail> errors) {

    public static ErrorResponse of(ErrorDetail detail) {
        return new ErrorResponse(List.of(detail));
    }

    public static ErrorResponse of(List<ErrorDetail> details) {
        return new ErrorResponse(List.copyOf(details));
    }
}
