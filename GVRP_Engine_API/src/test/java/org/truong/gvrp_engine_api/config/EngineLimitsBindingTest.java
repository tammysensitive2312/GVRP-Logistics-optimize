package org.truong.gvrp_engine_api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link EngineLimits} {@code @ConfigurationProperties} binding, single-source defaults,
 * and bean-validation failure on invalid values.
 */
class EngineLimitsBindingTest {

    @Configuration
    @EnableConfigurationProperties(EngineLimits.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultsWhenPropertiesAreUnset() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EngineLimits limits = context.getBean(EngineLimits.class);
            assertThat(limits.maxIterations()).isEqualTo(EngineLimits.DEFAULT_MAX_ITERATIONS);
            assertThat(limits.maxThreads()).isEqualTo(EngineLimits.DEFAULT_MAX_THREADS);
            assertThat(limits.maxOrders()).isEqualTo(25_000);
            assertThat(limits.maxVehicles()).isEqualTo(EngineLimits.DEFAULT_MAX_VEHICLES);
            assertThat(limits.maxDepots()).isEqualTo(EngineLimits.DEFAULT_MAX_DEPOTS);
            assertThat(limits.maxVehicleTypes()).isEqualTo(EngineLimits.DEFAULT_MAX_VEHICLE_TYPES);
            assertThat(limits.maxRuntimeSeconds()).isEqualTo(EngineLimits.DEFAULT_MAX_RUNTIME_SECONDS);
        });
    }

    @Test
    void overridesFromConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "gvrp.limits.max-orders=30000",
                        "gvrp.limits.max-runtime-seconds=3600"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EngineLimits limits = context.getBean(EngineLimits.class);
                    assertThat(limits.maxOrders()).isEqualTo(30_000);
                    assertThat(limits.maxRuntimeSeconds()).isEqualTo(3_600L);
                    assertThat(limits.maxIterations()).isEqualTo(EngineLimits.DEFAULT_MAX_ITERATIONS);
                });
    }

    @Test
    void rejectsInvalidValuesAtStartup() {
        contextRunner
                .withPropertyValues("gvrp.limits.max-iterations=0")
                .run(context -> assertThat(context).hasFailed());

        contextRunner
                .withPropertyValues("gvrp.limits.max-orders=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
