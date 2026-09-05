package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.distance_matrix.MatrixBasedTransportCosts;
import org.truong.gvrp_engine_api.job.JobRegistry;
import org.truong.gvrp_engine_api.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MaxDistanceRegressionTest {
    // Synthetic meters/seconds only. A-C-B is the only time-feasible full-service order.
    private final double[] x = {0, 1, 2, .5};
    private final List<Location> locations = new ArrayList<>();
    private final double[][] distances = new double[4][4];
    private final Service a, b, c;
    private final VehicleImpl vehicle;
    private final MatrixBasedTransportCosts costs;

    MaxDistanceRegressionTest() {
        for (int i = 0; i < x.length; i++) {
            locations.add(Location.Builder.newInstance().setId("l" + i).setIndex(i).build());
            for (int j = 0; j < x.length; j++) distances[i][j] = Math.abs(x[i] - x[j]);
        }
        costs = new MatrixBasedTransportCosts(distances, distances);
        vehicle = VehicleImpl.Builder.newInstance("vehicle-1").setStartLocation(locations.get(0))
                .setType(VehicleTypeImpl.Builder.newInstance("t").setCostPerDistance(1).build()).build();
        a = job("a", 1, 1);
        b = job("b", 2, 3);
        c = job("c", 3, 1.5);
    }

    private Service job(String id, int index, double time) {
        return Service.Builder.newInstance(id).setLocation(locations.get(index))
                .setTimeWindow(TimeWindow.newInstance(time, time)).build();
    }

    private OptimizationContext context(double limitMeters) {
        var type = new org.truong.gvrp_engine_api.model.VehicleType();
        type.setId(1L);
        type.setMaxDistance(limitMeters / 1000);
        var dto = new org.truong.gvrp_engine_api.model.Vehicle();
        dto.setId(1L);
        dto.setVehicleTypeId(1L);
        return new OptimizationContext(locations, Map.of(), Map.of(), Map.of(1L, type), Map.of(1L, dto));
    }

    @Test
    void checksActualPositionRatherThanBestDistancePosition() {
        var route = VehicleRoute.Builder.newInstance(vehicle).addService(a).addService(b).build();
        var newActivity = VehicleRoute.Builder.newInstance(vehicle).addService(c).build().getActivities().get(0);
        var insertion = new JobInsertionContext(route, c, vehicle, null, 0);
        var constraint = new OptimizationService.MaxDistanceConstraint(costs, Map.of("vehicle-1", 4.5));
        assertEquals(ConstraintsStatus.FULFILLED,
                constraint.fulfilled(insertion, route.getStart(), newActivity, route.getActivities().get(0), 0));
        assertEquals(ConstraintsStatus.NOT_FULFILLED,
                constraint.fulfilled(insertion, route.getActivities().get(0), newActivity, route.getActivities().get(1), 1));
    }

    @Test
    void usesCandidateVehicleLimitAndDepots() {
        var route = VehicleRoute.Builder.newInstance(vehicle).addService(a).addService(b).build();
        var replacement = VehicleImpl.Builder.newInstance("replacement").setStartLocation(locations.get(2))
                .setEndLocation(locations.get(2)).setType(vehicle.getType()).build();
        var newActivity = VehicleRoute.Builder.newInstance(vehicle).addService(c).build().getActivities().get(0);
        var insertion = new JobInsertionContext(route, c, replacement, null, 0);
        // Replacement route 2 -> .5 -> 1 -> 2 -> 2 is exactly 3 meters.
        for (double limit : new double[]{3.0, 2.9}) {
            var constraint = new OptimizationService.MaxDistanceConstraint(costs,
                    Map.of("vehicle-1", .1, "replacement", limit));
            assertEquals(limit == 3 ? ConstraintsStatus.FULFILLED : ConstraintsStatus.NOT_FULFILLED,
                    constraint.fulfilled(insertion, route.getStart(), newActivity, route.getActivities().get(0), 0));
        }
    }

    @Test
    void finalValidationRejectsViolationAndAcceptsExactBoundary() {
        var route = VehicleRoute.Builder.newInstance(vehicle).addService(a).addService(c).addService(b).build();
        var solution = new VehicleRoutingProblemSolution(List.of(route), 0);
        var matrix = new DistanceTimeMatrix(distances, distances, locations);
        assertThrows(IllegalStateException.class,
                () -> OptimizationService.assertMaxDistances(solution, matrix, context(4.5)));
        assertDoesNotThrow(() -> OptimizationService.assertMaxDistances(solution, matrix, context(5)));
    }

    @Test
    void realAlgorithmHonorsDistanceWithBindingTimeWindows() throws Exception {
        for (double limit : new double[]{4.5, 5.0}) {
            var problem = VehicleRoutingProblem.Builder.newInstance().addVehicle(vehicle)
                    .addJob(a).addJob(b).addJob(c).setRoutingCost(costs)
                    .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE).build();
            var registry = new JobRegistry();
            var service = new OptimizationService(null, null, registry);
            var config = new OptimizationConfig();
            config.setSeed(1L);
            config.setNumThreads(1);
            config.setMaxIterations(100);
            var factory = OptimizationService.class.getDeclaredMethod("createAlgorithm",
                    VehicleRoutingProblem.class, OptimizationContext.class, OptimizationConfig.class,
                    Map.class, JobRegistry.JobHandle.class);
            factory.setAccessible(true);
            var algorithm = (VehicleRoutingAlgorithm) factory.invoke(service, problem, context(limit),
                    config, null, registry.register(1));
            var solution = Solutions.bestOf(algorithm.searchSolutions());
            int served = 0;
            for (var route : solution.getRoutes()) {
                // Independent coordinate arithmetic, not the production constraint or matrix sum.
                double length = 0;
                double previous = 0;
                for (var activity : route.getActivities()) {
                    double current = x[activity.getLocation().getIndex()];
                    length += Math.abs(current - previous);
                    previous = current;
                    served++;
                    assertTrue(activity.getArrTime() <= activity.getTheoreticalLatestOperationStartTime());
                }
                length += Math.abs(previous);
                assertTrue(length <= limit, "Returned route must meet its physical distance limit");
            }
            assertEquals(3, served + solution.getUnassignedJobs().size());
            assertEquals(limit == 5 ? 0 : 1, solution.getUnassignedJobs().size(),
                    "Full service is feasible at 5 m; one job must remain unserved at 4.5 m");
        }
    }
}
