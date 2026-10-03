package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UnassignedOrderDiagnosticServiceTest {
    private final Service order = Service.Builder.newInstance("order-1")
            .setLocation(Location.newInstance("customer")).addRequiredSkill("cold")
            .addSizeDimension(0, 10).build();

    private VehicleImpl vehicle(String id, int capacity, boolean cold) {
        var builder = VehicleImpl.Builder.newInstance(id)
                .setStartLocation(Location.newInstance("depot"))
                .setType(VehicleTypeImpl.Builder.newInstance("type-" + id)
                        .addCapacityDimension(0, capacity).build());
        if (cold) builder.addSkill("cold");
        return builder.build();
    }

    private String reason(Map<String, Integer> clusters, long budget, boolean cancelled,
                          VehicleImpl... vehicles) {
        var builder = VehicleRoutingProblem.Builder.newInstance().addJob(order);
        for (var vehicle : vehicles) builder.addVehicle(vehicle);
        var solution = new VehicleRoutingProblemSolution(List.of(), List.of(order), 123);
        String reason = UnassignedOrderDiagnosticService.diagnose(builder.build(), solution,
                clusters, () -> cancelled, budget).get(order.getId());
        assertEquals(123, solution.getCost());
        assertTrue(solution.getRoutes().isEmpty());
        assertEquals(List.of(order), List.copyOf(solution.getUnassignedJobs()));
        return reason;
    }

    @Test void missingSkills() {
        assertTrue(reason(null, 1000, false, vehicle("v1", 20, false))
                .contains("all required skills"));
    }
    @Test void capacityOnlyConsidersCompatibleVehicles() {
        assertTrue(reason(null, 1000, false, vehicle("v1", 9, true),
                vehicle("v2", 100, false)).contains("capacity"));
    }
    @Test void exactCapacityIsNotRejected() {
        assertEquals(UnassignedOrderDiagnosticService.UNKNOWN,
                reason(null, 1000, false, vehicle("v1", 10, true)));
    }
    @Test void clusterExcludesOtherwiseCompatibleVehicle() {
        assertTrue(reason(Map.of("order-1", 1, "v1", 2), 1000, false,
                vehicle("v1", 20, true)).contains("cluster assignment"));
    }
    @Test void missingClusterMappingIsNotRejected() {
        assertEquals(UnassignedOrderDiagnosticService.UNKNOWN,
                reason(Map.of("order-1", 1), 1000, false, vehicle("v1", 20, true)));
    }
    @Test void budgetAndCancellationDoNotInventReasons() {
        assertEquals(UnassignedOrderDiagnosticService.INCOMPLETE,
                reason(null, 0, false, vehicle("v1", 20, true)));
        assertEquals(UnassignedOrderDiagnosticService.INCOMPLETE,
                reason(null, 1000, true, vehicle("v1", 20, true)));
    }
}
