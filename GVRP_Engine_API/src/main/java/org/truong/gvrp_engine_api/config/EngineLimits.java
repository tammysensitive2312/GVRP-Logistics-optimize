package org.truong.gvrp_engine_api.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side ceilings on the work one optimization request may ask for (SEC-ENGINE-001, D4).
 *
 * <p>Defaults leave room above every Entry preset (5000 iterations, 4 threads) and every benchmark
 * instance (5000 orders, 3000 vehicles). Values above a ceiling are refused, never clamped.
 */
@Component
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EngineLimits {

    @Value("${gvrp.limits.max-iterations:20000}")
    private int maxIterations = 20000;

    @Value("${gvrp.limits.max-threads:16}")
    private int maxThreads = 16;

    @Value("${gvrp.limits.max-orders:10000}")
    private int maxOrders = 10000;

    @Value("${gvrp.limits.max-vehicles:5000}")
    private int maxVehicles = 5000;

    @Value("${gvrp.limits.max-depots:500}")
    private int maxDepots = 500;

    @Value("${gvrp.limits.max-vehicle-types:500}")
    private int maxVehicleTypes = 500;

    /** Wall-clock bound on solving for one job, measured from job start (design D3, option B). */
    @Value("${gvrp.solver.max-runtime-seconds:7200}")
    private long maxRuntimeSeconds = 7200;
}
