package org.truong.gvrp_engine_api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_engine_api.config.EngineLimits;
import org.truong.gvrp_engine_api.controller.OptimizationController;
import org.truong.gvrp_engine_api.job.JobRegistry;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-ENGINE-001 EN-T5: the filter in front of the real controller. A percent-encoded path that
 * Spring MVC still routes to a handler must not get past the filter without the key.
 */
class ServiceKeyFilterMockMvcTest {

    private static final String KEY = "s".repeat(48);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JobRegistry registry = new JobRegistry();
        registry.register(42L);
        OptimizationController controller = new OptimizationController(
                null, registry, null, null, null, null, EngineLimits.defaults());
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new ServiceKeyFilter(KEY)).build();
    }

    @Test
    void encodedProgressPathNeedsTheKey() throws Exception {
        URI encoded = URI.create("/api/v1/optimization/42/%70rogress");
        mvc.perform(get(encoded)).andExpect(status().isUnauthorized());
        mvc.perform(get(encoded).header(ServiceKeyFilter.HEADER, KEY)).andExpect(status().isOk());
    }

    @Test
    void cancelWithoutKeyNeverReachesTheRegistry() throws Exception {
        JobRegistry registry = new JobRegistry();
        JobRegistry.JobHandle handle = registry.register(7L);
        MockMvc local = MockMvcBuilders
                .standaloneSetup(new OptimizationController(
                        null, registry, null, null, null, null, EngineLimits.defaults()))
                .addFilters(new ServiceKeyFilter(KEY)).build();

        local.perform(post("/api/v1/optimization/7/cancel")).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertFalse(handle.isCancelRequested());

        local.perform(post("/api/v1/optimization/7/cancel").header(ServiceKeyFilter.HEADER, KEY))
                .andExpect(status().is2xxSuccessful());
        org.junit.jupiter.api.Assertions.assertTrue(handle.isCancelRequested());
    }

    @Test
    void healthStaysOpen() throws Exception {
        mvc.perform(get(ServiceKeyFilter.HEALTH_PATH)).andExpect(status().isOk());
    }
}
