package org.truong.gvrp_engine_api.controller;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.job.JobRegistry;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pareto scenario progress reporting (E-T12). Direct call to the controller — no Spring
 * context, no MockMvc needed.
 */
class OptimizationControllerProgressTest {

    private static OptimizationController controller(JobRegistry registry) {
        return new OptimizationController(null, registry, null, null, null);
    }

    @Test
    void paretoProgressReportsScenarioAndOverallPercent() {
        JobRegistry registry = new JobRegistry();
        JobRegistry.JobHandle handle = registry.register(42L);
        handle.setScenarioCount(5);
        handle.setScenarioIndex(2);
        handle.updateSnapshot(new JobRegistry.ProgressSnapshot(
                50, 100, 0.0, 0, 0, 0, JobRegistry.Phase.SOLVING, Instant.now()));

        Map<String, Object> body = controller(registry).progress(42L).getBody();

        assertNotNull(body);
        assertEquals(3, ((Number) body.get("scenario")).intValue());
        assertEquals(5, ((Number) body.get("scenarioCount")).intValue());
        assertEquals(50L, ((Number) body.get("percent")).longValue());
    }

    @Test
    void nonParetoProgressHasNoScenarioKeys() {
        JobRegistry registry = new JobRegistry();
        JobRegistry.JobHandle handle = registry.register(43L);
        handle.updateSnapshot(new JobRegistry.ProgressSnapshot(
                50, 100, 0.0, 0, 0, 0, JobRegistry.Phase.SOLVING, Instant.now()));

        Map<String, Object> body = controller(registry).progress(43L).getBody();

        assertNotNull(body);
        assertFalse(body.containsKey("scenario"));
        assertFalse(body.containsKey("scenarioCount"));
        assertEquals(50, ((Number) body.get("percent")).intValue());
    }
}
