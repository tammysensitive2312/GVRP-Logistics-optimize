package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.distance_matrix.DenseCostMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.job.JobRegistry;
import org.truong.gvrp_engine_api.model.Depot;
import org.truong.gvrp_engine_api.model.EngineOptimizationRequest;
import org.truong.gvrp_engine_api.model.OptimizationConfig;
import org.truong.gvrp_engine_api.model.OptimizationResult;
import org.truong.gvrp_engine_api.model.Order;
import org.truong.gvrp_engine_api.model.RouteDetail;
import org.truong.gvrp_engine_api.model.Vehicle;
import org.truong.gvrp_engine_api.model.VehicleType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EB-T2: the zone skill compiled by Entry actually constrains jsprit. One order
 * needs {@code LEZ_Z1}; with a skilled and an unskilled vehicle the order must go
 * to the skilled one, and with only unskilled vehicles it must stay unassigned.
 *
 * <p>No GraphHopper graph and no MySQL: the distance matrix is a mocked haversine.
 */
class LezSkillAssignmentTest {

    private static final double DEPOT_LAT = 10.0;
    private static final double DEPOT_LON = 106.0;

    private static Set<String> skills(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static Vehicle vehicle(long id, Set<String> lezSkills) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setVehicleLicensePlate("V" + id);
        vehicle.setVehicleTypeId(1L);
        vehicle.setStartDepotId(1L);
        vehicle.setEndDepotId(1L);
        vehicle.setLezSkills(lezSkills);
        return vehicle;
    }

    /** One depot, one 1 kg order next to it, two vehicles of one type. */
    private static EngineOptimizationRequest request(Set<String> firstVehicleSkills,
                                                     Set<String> secondVehicleSkills) {
        Depot depot = new Depot(1L, "D1", "addr", DEPOT_LAT, DEPOT_LON);

        VehicleType type = new VehicleType();
        type.setId(1L);
        type.setTypeName("T1");
        type.setCapacity(100);
        type.setFixedCost(100_000.0);
        type.setCostPerKm(5_000.0);
        type.setCostPerHour(50_000.0);
        type.setMaxDuration(12.0);
        type.setEmissionFactor(200.0);

        Order order = new Order();
        order.setId(1L);
        order.setOrderCode("ORD-1");
        order.setLatitude(DEPOT_LAT + 0.001);
        order.setLongitude(DEPOT_LON + 0.001);
        order.setDemand(1.0);
        order.setServiceTime(0);
        order.setLezSkills(skills("LEZ_Z1"));

        OptimizationConfig config = new OptimizationConfig();
        config.setCostWeight(0.5);
        config.setCo2Weight(0.5);
        config.setMaxIterations(20);
        config.setNumThreads(1);
        config.setSeed(1L);
        config.setEnableParetoAnalysis(false);

        return new EngineOptimizationRequest(1L, List.of(depot), List.of(order),
                List.of(type), List.of(vehicle(1L, firstVehicleSkills), vehicle(2L, secondVehicleSkills)),
                config);
    }

    private static OptimizationResult solve(EngineOptimizationRequest request) {
        DistanceMatrixService dms = mock(DistanceMatrixService.class);
        when(dms.createDistanceMatrix(anyList(), any(), any())).thenAnswer(invocation -> {
            List<OptCoordinates> coords = invocation.getArgument(0);
            int n = coords.size();
            double[][] distance = new double[n][n];
            double[][] time = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double dLat = (coords.get(i).latDouble() - coords.get(j).latDouble()) * 111_320.0;
                    double dLon = (coords.get(i).lonDouble() - coords.get(j).lonDouble()) * 111_320.0;
                    double meters = Math.sqrt(dLat * dLat + dLon * dLon);
                    distance[i][j] = meters;
                    time[i][j] = meters / 10.0;
                }
            }
            return new DistanceMatrix(coords, new DenseCostMatrix(distance, time));
        });

        JobRegistry registry = new JobRegistry();
        OptimizationService service = new OptimizationService(dms, null, registry);
        return service.optimize(request, registry.register(21L));
    }

    @Test
    void zoneOrderGoesToTheVehicleCarryingTheZoneSkill() {
        OptimizationResult result = solve(request(skills("LEZ_Z1"), skills()));

        assertEquals(1, result.getRoutes().size(), "the single order must be served");
        RouteDetail route = result.getRoutes().get(0);
        assertEquals(1L, route.getVehicleId(),
                "only the vehicle with LEZ_Z1 can carry an order in zone Z1");
        assertTrue(route.getStops().stream().anyMatch(stop -> Long.valueOf(1L).equals(stop.getOrderId())),
                "the zone order must be on that route");
        assertTrue(result.getUnassignedOrders() == null || result.getUnassignedOrders().isEmpty(),
                "nothing must be left unassigned when a skilled vehicle exists");
    }

    @Test
    void zoneOrderStaysUnassignedWhenNoVehicleHasTheSkill() {
        OptimizationResult result = solve(request(skills(), skills()));

        assertNotNull(result.getUnassignedOrders());
        assertEquals(1, result.getUnassignedOrders().size());
        assertEquals(1L, result.getUnassignedOrders().get(0).getOrderId());
        assertFalse(result.getRoutes().stream()
                        .flatMap(route -> route.getStops().stream())
                        .anyMatch(stop -> Long.valueOf(1L).equals(stop.getOrderId())),
                "no unskilled vehicle may serve an order requiring the zone skill");
    }
}
