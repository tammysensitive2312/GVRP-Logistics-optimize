package org.truong.gvrp_entry_api.exception;

public class JobCancellationException extends RuntimeException {
    public JobCancellationException(String message) {
        super(message);
    }
}
