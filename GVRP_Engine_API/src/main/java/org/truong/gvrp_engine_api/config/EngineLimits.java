package org.truong.gvrp_engine_api.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Server-side ceilings on the work one optimization request may ask for (SEC-ENGINE-001, D4).
 *
 * <p>Bound from {@code gvrp.limits.*}; single-source defaults cover Entry presets (5000 iterations,
 * 4 threads), 20k-order jobs, and benchmark instances. Values above a ceiling are refused, never clamped.
 */
@Validated
@ConfigurationProperties(prefix = "gvrp.limits")
public record EngineLimits(
        @Min(1) Integer maxIterations,
        @Min(1) Integer maxThreads,
        @Min(0) Integer maxOrders,
        @Min(0) Integer maxVehicles,
        @Min(0) Integer maxDepots,
        @Min(0) Integer maxVehicleTypes,
        @Min(1) Long maxRuntimeSeconds
) {

    public static final int DEFAULT_MAX_ITERATIONS = 20_000;
    public static final int DEFAULT_MAX_THREADS = 16;
    public static final int DEFAULT_MAX_ORDERS = 25_000;
    public static final int DEFAULT_MAX_VEHICLES = 5_000;
    public static final int DEFAULT_MAX_DEPOTS = 500;
    public static final int DEFAULT_MAX_VEHICLE_TYPES = 500;
    public static final long DEFAULT_MAX_RUNTIME_SECONDS = 7_200L;

    public EngineLimits {
        if (maxIterations == null) {
            maxIterations = DEFAULT_MAX_ITERATIONS;
        }
        if (maxThreads == null) {
            maxThreads = DEFAULT_MAX_THREADS;
        }
        if (maxOrders == null) {
            maxOrders = DEFAULT_MAX_ORDERS;
        }
        if (maxVehicles == null) {
            maxVehicles = DEFAULT_MAX_VEHICLES;
        }
        if (maxDepots == null) {
            maxDepots = DEFAULT_MAX_DEPOTS;
        }
        if (maxVehicleTypes == null) {
            maxVehicleTypes = DEFAULT_MAX_VEHICLE_TYPES;
        }
        if (maxRuntimeSeconds == null) {
            maxRuntimeSeconds = DEFAULT_MAX_RUNTIME_SECONDS;
        }
    }

    public static EngineLimits defaults() {
        return new EngineLimits(null, null, null, null, null, null, null);
    }
}
