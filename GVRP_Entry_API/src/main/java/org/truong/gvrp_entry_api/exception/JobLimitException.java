package org.truong.gvrp_entry_api.exception;

public class JobLimitException extends RuntimeException {
    public JobLimitException(String message) {
        super(message);
    }
}
