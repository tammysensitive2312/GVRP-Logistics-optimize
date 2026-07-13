package org.truong.gvrp_entry_api.exception;

import lombok.*;

import java.util.List;

/**
 * Standard error response format
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private List<ErrorDetail> errors;
}
