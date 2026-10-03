package org.truong.gvrp_engine_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.model.OptimizationContext;
import org.truong.gvrp_engine_api.model.Order;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UnassignedReasonExtractionTest {
    @Test void preservesReasonAndExistingPayloadFields() throws Exception {
        var order = new Order();
        order.setId(1L);
        order.setOrderCode("ORD-1");
        var context = new OptimizationContext(List.of(), Map.of(), Map.of(1L, order),
                Map.of(), Map.of());
        var job = Service.Builder.newInstance("order-1")
                .setLocation(Location.newInstance("customer")).build();
        var solution = new VehicleRoutingProblemSolution(List.of(), List.of(job), 0);
        var result = OptimizationResultExtractor.extractUnassignedOrders(solution, context,
                Map.of("order-1", "Specific diagnostic evidence."));
        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().getOrderId());
        assertEquals("ORD-1", result.getFirst().getOrderCode());
        assertEquals("Specific diagnostic evidence.", result.getFirst().getReason());
        var json = new ObjectMapper().valueToTree(result.getFirst());
        assertEquals(3, json.size());
        assertTrue(json.has("orderId"));
        assertTrue(json.has("orderCode"));
        assertTrue(json.has("reason"));
        assertEquals(UnassignedOrderDiagnosticService.UNKNOWN,
                OptimizationResultExtractor.extractUnassignedOrders(solution, context)
                        .getFirst().getReason());
    }
}
