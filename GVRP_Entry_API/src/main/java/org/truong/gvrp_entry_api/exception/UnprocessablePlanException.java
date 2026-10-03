package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/** Candidate sequences violate hard constraints — HTTP 422. */
@Getter
public class UnprocessablePlanException extends RuntimeException {
    public UnprocessablePlanException(String message) {
        super(message);
    }
}
