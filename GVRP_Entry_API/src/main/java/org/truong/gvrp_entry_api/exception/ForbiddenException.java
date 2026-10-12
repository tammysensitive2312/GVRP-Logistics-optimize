package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/** Authenticated but not allowed — HTTP 403. */
@Getter
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
