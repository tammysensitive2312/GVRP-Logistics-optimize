package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.truong.gvrp_engine_api.distance_matrix.*;
import org.truong.gvrp_engine_api.job.JobRegistry;
import org.truong.gvrp_engine_api.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * DB-free tests for the Pareto pipeline (E-T9..E-T11). The distance matrix service is mocked;
 * no GraphHopper graph, no MySQL, no Spring context.
 */
class OptimizationServiceParetoTest {

    private static EngineOptimizationRequest request(int orderCount, int vehicleCount,
                                                     int typeCount, boolean pareto) {
        Depot depot = new Depot(1L, "D1", "addr", 10.0, 106.0);

        List<VehicleType> types = new ArrayList<>();
        for (int t = 1; t <= typeCount; t++) {
            VehicleType type = new VehicleType();
            type.setId((long) t);
            type.setTypeName("T" + t);
            type.setCapacity(100 * t);
            type.setFixedCost(100_000.0);
            type.setCostPerKm(5_000.0);
            type.setCostPerHour(50_000.0);
            type.setMaxDistance(null);
            type.setMaxDuration(12.0);
            type.setEmissionFactor(t == 1 ? 200.0 : 50.0);
            types.add(type);
        }

        List<Vehicle> vehicles = new ArrayList<>();
        for (int v = 1; v <= vehicleCount; v++) {
            Vehicle vehicle = new Vehicle();
            vehicle.setId((long) v);
            vehicle.setVehicleLicensePlate("V" + v);
            vehicle.setVehicleTypeId((long) (1 + (v % typeCount)));
            vehicle.setStartDepotId(1L);
            vehicle.setEndDepotId(1L);
            vehicles.add(vehicle);
        }

        List<Order> orders = new ArrayList<>();
        for (int o = 1; o <= orderCount; o++) {
            Order order = new Order();
            order.setId((long) o);
            order.setOrderCode("ORD-" + o);
            order.setLatitude(10.0 + (o % 40) * 0.001);
            order.setLongitude(106.0 + (o / 40) * 0.001);
            order.setDemand(1.0);
            order.setServiceTime(0);
            orders.add(order);
        }

        OptimizationConfig config = new OptimizationConfig();
        config.setCostWeight(0.5);
        config.setCo2Weight(0.5);
        config.setMaxIterations(20);
        config.setNumThreads(1);
        config.setSeed(1L);
        config.setEnableParetoAnalysis(pareto);

        return new EngineOptimizationRequest(1L, List.of(depot), orders, types, vehicles, config);
    }

    @Test
    void paretoBuildsClusteredMask() {
        DistanceMatrixService dms = mock(DistanceMatrixService.class);
        when(dms.createDistanceMatrix(anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    throw new IllegalStateException("matrix captured");
                });

        JobRegistry registry = new JobRegistry();
        OptimizationService service = new OptimizationService(dms, null, registry);
        EngineOptimizationRequest request = request(1000, 10, 2, true);
        JobRegistry.JobHandle handle = registry.register(7L);

        assertThrows(IllegalStateException.class, () -> service.optimize(request, handle));

        ArgumentCaptor<MatrixMask> maskCaptor = ArgumentCaptor.forClass(MatrixMask.class);
        verify(dms).createDistanceMatrix(anyList(), maskCaptor.capture(), any());
        assertNotNull(maskCaptor.getValue());
        assertFalse(maskCaptor.getValue().isFull(),
                "1000 orders with 10 vehicles must produce a clustered (non-full) mask in the Pareto branch");
    }

    @Test
    void smallParetoUsesFullMask() {
        DistanceMatrixService dms = mock(DistanceMatrixService.class);
        when(dms.createDistanceMatrix(anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    throw new IllegalStateException("matrix captured");
                });

        JobRegistry registry = new JobRegistry();
        OptimizationService service = new OptimizationService(dms, null, registry);
        EngineOptimizationRequest request = request(12, 3, 2, true);
        JobRegistry.JobHandle handle = registry.register(8L);

        assertThrows(IllegalStateException.class, () -> service.optimize(request, handle));

        ArgumentCaptor<MatrixMask> maskCaptor = ArgumentCaptor.forClass(MatrixMask.class);
        verify(dms).createDistanceMatrix(anyList(), maskCaptor.capture(), any());
        assertTrue(maskCaptor.getValue().isFull(),
                "12 orders must stay below the cluster threshold and use the full (dense) mask");
    }

    @Test
    void smallParetoEndToEnd() {
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
        EngineOptimizationRequest request = request(12, 3, 2, true);
        JobRegistry.JobHandle handle = registry.register(11L);

        OptimizationResult result = service.optimize(request, handle);

        assertNotNull(result.getParetoAlternatives());
        assertEquals(5, result.getParetoAlternatives().size());

        List<ParetoAlternative> alternatives = result.getParetoAlternatives();
        assertEquals(1,
                alternatives.stream().filter(ParetoAlternative::isSelected).count(),
                "exactly one alternative must be engine-recommended");

        ParetoAlternative selected = alternatives.stream()
                .filter(ParetoAlternative::isSelected).findFirst().orElseThrow();
        assertEquals(FrontierStatus.FRONTIER, selected.getStatus());
        assertEquals(selected.getCandidate(), result.getSelected());

        for (ParetoAlternative alternative : alternatives) {
            if (alternative.getStatus() == FrontierStatus.FRONTIER) {
                assertNotNull(alternative.getRoutes(),
                        "every FRONTIER point must carry extracted routes");
                assertNotNull(alternative.getUnassignedOrders(),
                        "every FRONTIER point must carry an unassigned-orders list");
            }
        }

        assertNull(result.getClusterCount(), "12 orders must not be clustered");
        assertEquals(5, handle.scenarioCount());
    }
}
