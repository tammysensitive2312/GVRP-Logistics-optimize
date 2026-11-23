package org.truong.gvrp_entry_api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetails;

import java.util.List;

/**
 * Shared bounds checking for pagination and history limit parameters.
 * Rejects out-of-range inputs with HTTP 400 {@link ErrorCode#UNSUPPORTED_VALUE}.
 */
public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static Pageable pageRequest(int page, int size, int maxSize, Sort sort) {
        if (page < 0) {
            throw new DataInvalidException(List.of(
                    ErrorDetails.unsupportedValue("page", "Page index must not be negative")));
        }
        if (size < 1 || size > maxSize) {
            throw new DataInvalidException(List.of(
                    ErrorDetails.unsupportedValue("size", "Page size must be between 1 and " + maxSize)));
        }
        return PageRequest.of(page, size, sort);
    }

    public static Pageable limitRequest(int limit, int maxLimit) {
        if (limit < 1 || limit > maxLimit) {
            throw new DataInvalidException(List.of(
                    ErrorDetails.unsupportedValue("limit", "Limit must be between 1 and " + maxLimit)));
        }
        return PageRequest.of(0, limit);
    }
}
