package org.truong.gvrp_entry_api.exception;

/**
 * A completion callback refers to orders or vehicles outside the job's branch,
 * or to ids that do not exist (SEC-013).
 *
 * <p>Thrown before anything is written. {@code OptimizationCallbackService}
 * rethrows it without marking the job FAILED, and {@code GlobalExceptionHandler}
 * answers 422: the payload is wrong, not the job.
 */
public class CallbackRejectedException extends RuntimeException {

    public CallbackRejectedException(String message) {
        super(message);
    }
}
