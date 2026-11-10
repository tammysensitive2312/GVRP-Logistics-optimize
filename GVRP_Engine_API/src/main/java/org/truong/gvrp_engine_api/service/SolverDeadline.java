package org.truong.gvrp_engine_api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Premature-termination rule for one solve: stop when the job is cancelled or when the job-wide
 * runtime cap has passed (SEC-ENGINE-001, design D3 option B).
 *
 * <p>The request's {@code timeoutSeconds} is deliberately not used here: it was never enforced, so
 * enforcing it would change every recorded result. Jsprit evaluates the rule once per iteration and
 * returns the best solution found so far.
 */
public final class SolverDeadline {

    private final BooleanSupplier cancelRequested;
    private final Instant deadline;
    private final Clock clock;
    private volatile boolean deadlineHit;

    public SolverDeadline(BooleanSupplier cancelRequested, Instant jobStart, Duration maxRuntime, Clock clock) {
        this.cancelRequested = cancelRequested;
        this.deadline = jobStart.plus(maxRuntime);
        this.clock = clock;
    }

    public boolean shouldStop() {
        if (cancelRequested.getAsBoolean()) {
            return true;
        }
        if (clock.instant().isAfter(deadline)) {
            deadlineHit = true;
            return true;
        }
        return false;
    }

    /** True once the runtime cap (not the cancel flag) ended the solve. */
    public boolean deadlineHit() {
        return deadlineHit;
    }

    public Instant deadline() {
        return deadline;
    }
}
