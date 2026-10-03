package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/** The optimization engine could not be reached — HTTP 502. */
@Getter
public class EngineUnavailableException extends RuntimeException {
    public EngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
