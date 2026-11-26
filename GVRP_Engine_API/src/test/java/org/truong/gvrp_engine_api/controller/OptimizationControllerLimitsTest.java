package org.truong.gvrp_engine_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_engine_api.config.EngineLimits;
import org.truong.gvrp_engine_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_engine_api.job.JobRegistry;
import org.truong.gvrp_engine_api.model.*;
import org.truong.gvrp_engine_api.service.OptimizationService;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-ENGINE-001 EN-T6, EN-T7: work above the server ceilings is refused before anything is
 * queued; Entry's presets (800/2000/5000 iterations, 4 threads) are accepted unchanged.
 */
class OptimizationControllerLimitsTest {

    private final OptimizationService service = mock(OptimizationService.class);

    private OptimizationController controller(EngineLimits limits) {
        return new OptimizationController(
                service,
                new JobRegistry(),
                null,
                null,
                null,
                null,
                limits != null ? limits : EngineLimits.defaults());
    }

    private static EngineOptimizationRequest request(int orders, int vehicles, Integer iterations, Integer threads) {
        EngineOptimizationRequest request = new EngineOptimizationRequest();
        request.setJobId(1L);
        Depot depot = new Depot();
        depot.setId(1L);
        depot.setLatitude(21.0);
        depot.setLongitude(105.8);
        request.setDepots(List.of(depot));
        VehicleType type = new VehicleType();
        type.setId(1L);
        request.setVehicleTypes(List.of(type));
        List<Vehicle> vs = new ArrayList<>();
        for (int i = 0; i < vehicles; i++) {
            Vehicle v = new Vehicle();
            v.setId((long) i);
            vs.add(v);
        }
        request.setVehicles(vs);
        List<Order> os = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            Order o = new Order();
            o.setId((long) i);
            o.setLatitude(21.0);
            o.setLongitude(105.8);
            os.add(o);
        }
        request.setOrders(os);
        OptimizationConfig config = new OptimizationConfig();
        config.setMaxIterations(iterations);
        config.setNumThreads(threads);
        request.setConfig(config);
        return request;
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private void assertRejected(OptimizationController controller, EngineOptimizationRequest request, int statusCode) {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        String expectedErrorCode = statusCode == 413 ? "0041301" : "0040001";
        try {
            mvc.perform(post("/api/v1/optimization")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(request)))
                    .andExpect(status().is(statusCode))
                    .andExpect(jsonPath("$.errors[0].code").value(expectedErrorCode))
                    .andExpect(jsonPath("$.errors[0].message", not(blankOrNullString())));
        } catch (Exception e) {
            fail(e);
        }
        verify(service, never()).optimizeAsync(any());
    }

    @Test
    void entryPresetsAreAccepted() { // EN-T6, EN-R8
        OptimizationController controller = controller(null); // default ceilings
        for (int iterations : new int[]{800, 2000, 5000}) {
            ResponseEntity<EngineOptimizationResponse> response = controller.optimize(request(3, 2, iterations, 4));
            assertEquals(202, response.getStatusCode().value());
        }
        ResponseEntity<EngineOptimizationResponse> defaults = controller.optimize(request(3, 2, null, null));
        assertEquals(202, defaults.getStatusCode().value());
        verify(service, times(4)).optimizeAsync(any());
    }

    @Test
    void iterationsOrThreadsOutOfRangeAreRefusedWith400() { // EN-T6
        OptimizationController controller = controller(null);
        assertRejected(controller, request(3, 2, 20_001, 4), 400);
        assertRejected(controller, request(3, 2, Integer.MAX_VALUE, 4), 400);
        assertRejected(controller, request(3, 2, 0, 4), 400);
        assertRejected(controller, request(3, 2, 2000, 17), 400);
        assertRejected(controller, request(3, 2, 2000, 0), 400);
        assertRejected(controller, request(3, 2, 2000, -1), 400);
    }

    @Test
    void ceilingsAreInclusive() {
        OptimizationController controller = controller(null);
        assertEquals(202, controller.optimize(request(3, 2, 20_000, 16)).getStatusCode().value());
    }

    @Test
    void listsAboveTheirCeilingAreRefusedWith413() { // EN-T7
        EngineLimits small = new EngineLimits(20000, 16, 3, 2, 500, 500, 7200L);
        assertRejected(controller(small), request(4, 2, 2000, 4), 413);
        assertRejected(controller(small), request(3, 3, 2000, 4), 413);
        assertEquals(202, controller(small).optimize(request(3, 2, 2000, 4)).getStatusCode().value());
    }

    @Test
    void depotAndTypeCeilings() { // EN-T7
        EngineLimits none = new EngineLimits(20000, 16, 10, 10, 0, 500, 7200L);
        assertRejected(controller(none), request(1, 1, 2000, 4), 413);
        EngineLimits noTypes = new EngineLimits(20000, 16, 10, 10, 500, 0, 7200L);
        assertRejected(controller(noTypes), request(1, 1, 2000, 4), 413);
    }
}
