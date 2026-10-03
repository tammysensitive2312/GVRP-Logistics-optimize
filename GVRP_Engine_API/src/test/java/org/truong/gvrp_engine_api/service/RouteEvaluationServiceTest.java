package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.distance_matrix.Distance;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.DistanceProvider;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.model.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouteEvaluationService} with a stub distance provider
 * (every leg = 1000 m / 120 s), so no GraphHopper access is needed.
 */
class RouteEvaluationServiceTest {

    private static final DistanceProvider STUB_PROVIDER = (from, to) ->
            new DistanceMatrixEntry(Duration.ofSeconds(120), Distance.ofMeters(1000.0));

    private final RouteEvaluationService service =
            new RouteEvaluationService(new DistanceMatrixService(STUB_PROVIDER, 1));

    // ---------- fixtures ----------

    private static Depot depot(long id, String name) {
        Depot depot = new Depot();
        depot.setId(id);
        depot.setName(name);
        depot.setLatitude(0.0);
        depot.setLongitude(0.0);
        return depot;
    }

    private static Order order(long id, double demand) {
        Order order = new Order();
        order.setId(id);
        order.setOrderCode("ORD-" + id);
        order.setLatitude(0.01 * id);
        order.setLongitude(0.0);
        order.setDemand(demand);
        order.setTimeWindowStart("08:00:00");
        order.setTimeWindowEnd("18:00:00");
        order.setServiceTime(10);
        return order;
    }

    private static VehicleType vehicleType(int capacity) {
        VehicleType type = new VehicleType();
        type.setId(1L);
        type.setTypeName("Truck");
        type.setCapacity(capacity);
        type.setFixedCost(100000.0);
        type.setCostPerKm(5000.0);
        type.setCostPerHour(50000.0);
        type.setMaxDistance(100.0);
        type.setMaxDuration(12.0);
        type.setEmissionFactor(200.0);
        type.setSkills(new java.util.LinkedHashSet<>(Set.of("TAILGATE")));
        return type;
    }

    private static EvaluatedRoute route(VehicleType type, Order... orders) {
        EvaluatedRoute route = new EvaluatedRoute();
        route.setVehicleId(7L);
        route.setVehicleLicensePlate("29A-12345");
        route.setVehicleType(type);
        route.setStartDepot(depot(1L, "Depot A"));
        route.setEndDepot(depot(1L, "Depot A"));
        route.setStartTime("08:00:00");
        route.setOrders(new ArrayList<>(List.of(orders)));
        return route;
    }

    private static RouteEvaluateRequest request(EvaluatedRoute... routes) {
        RouteEvaluateRequest request = new RouteEvaluateRequest();
        request.setRoutes(new ArrayList<>(List.of(routes)));
        return request;
    }

    // ---------- happy path ----------

    @Test
    void evaluate_feasibleSequence_computesTimesLoadsAndMetrics() {
        RouteEvaluateResponse response = service.evaluate(
                request(route(vehicleType(100), order(1L, 30.0), order(2L, 50.0))));

        assertTrue(response.isFeasible());
        assertTrue(response.getViolations().isEmpty());

        RouteDetail detail = response.getRoutes().get(0);
        // 4 stops: depot, order 1, order 2, depot.
        assertEquals(4, detail.getStops().size());
        assertEquals(2, detail.getOrderCount());

        Stop first = detail.getStops().get(1);
        assertEquals("08:02:00", first.getArrivalTime());
        assertEquals("08:12:00", first.getDepartureTime());
        assertEquals(0.0, first.getWaitTime());
        assertEquals(50.0, first.getLoadAfter());

        Stop second = detail.getStops().get(2);
        assertEquals("08:14:00", second.getArrivalTime());
        assertEquals(0.0, second.getLoadAfter());

        // 3 legs x 1000 m.
        assertEquals(3.0, detail.getTotalDistance(), 1e-9);
        assertEquals(80.0, detail.getLoadUtilization(), 1e-9);
        // CO2 = 3 km x 200 g/km / 1000.
        assertEquals(0.6, detail.getTotalCO2(), 1e-9);

        assertEquals(1, response.getTotals().getVehiclesUsed());
        assertEquals(2, response.getTotals().getOrdersServed());
    }

    @Test
    void evaluate_earlyArrival_recordsWaitTime() {
        Order order = order(1L, 10.0);
        order.setTimeWindowStart("09:00:00");

        RouteEvaluateResponse response = service.evaluate(
                request(route(vehicleType(100), order)));

        assertTrue(response.isFeasible());
        Stop stop = response.getRoutes().get(0).getStops().get(1);
        assertEquals("08:02:00", stop.getArrivalTime());
        assertEquals(58.0, stop.getWaitTime(), 1e-9);
        assertEquals("09:10:00", stop.getDepartureTime());
    }

    // ---------- violations ----------

    @Test
    void evaluate_lateArrival_reportsTimeWindowViolation() {
        Order order = order(1L, 10.0);
        order.setTimeWindowEnd("08:01:00");

        RouteEvaluateResponse response = service.evaluate(
                request(route(vehicleType(100), order)));

        assertFalse(response.isFeasible());
        assertEquals(1, response.getViolations().size());
        assertEquals("TIME_WINDOW", response.getViolations().get(0).getCode());
        assertEquals(1L, response.getViolations().get(0).getOrderId());
    }

    @Test
    void evaluate_overload_reportsOverCapacity() {
        RouteEvaluateResponse response = service.evaluate(
                request(route(vehicleType(50), order(1L, 30.0), order(2L, 50.0))));

        assertFalse(response.isFeasible());
        assertTrue(response.getViolations().stream()
                .anyMatch(v -> v.getCode().equals("OVER_CAPACITY") && v.getOrderId() == 2L));
    }

    @Test
    void evaluate_missingSkill_reportsSkillMismatch() {
        Order order = order(1L, 10.0);
        order.setRequiredSkills(new java.util.LinkedHashSet<>(Set.of("CRANE")));

        RouteEvaluateResponse response = service.evaluate(
                request(route(vehicleType(100), order)));

        assertFalse(response.isFeasible());
        assertEquals("SKILL_MISMATCH", response.getViolations().get(0).getCode());
    }

    @Test
    void evaluate_excessDistance_reportsMaxDistance() {
        VehicleType type = vehicleType(100);
        type.setMaxDistance(0.5);

        RouteEvaluateResponse response = service.evaluate(request(route(type, order(1L, 10.0))));

        assertFalse(response.isFeasible());
        assertEquals("MAX_DISTANCE", response.getViolations().get(0).getCode());
    }

    @Test
    void evaluate_providerFailure_reportsRoutingUnavailable() {
        DistanceProvider failing = (from, to) -> {
            if (from.equals(new OptCoordinates(BigDecimal.valueOf(0.01), BigDecimal.ZERO))) {
                throw new RuntimeException("road closed");
            }
            return new DistanceMatrixEntry(Duration.ofSeconds(120), Distance.ofMeters(1000.0));
        };
        RouteEvaluationService failingService =
                new RouteEvaluationService(new DistanceMatrixService(failing, 1));

        RouteEvaluateResponse response = failingService.evaluate(
                request(route(vehicleType(100), order(1L, 10.0))));

        assertFalse(response.isFeasible());
        assertEquals("ROUTING_UNAVAILABLE", response.getViolations().get(0).getCode());
    }

    // ---------- malformed requests ----------

    @Test
    void evaluate_duplicateOrder_throwsIllegalArgument() {
        Order first = order(1L, 10.0);
        Order second = order(1L, 20.0);

        assertThrows(IllegalArgumentException.class, () ->
                service.evaluate(request(route(vehicleType(100), first, second))));
    }

    @Test
    void evaluate_emptyRoute_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                service.evaluate(request(route(vehicleType(100)))));
    }

    @Test
    void evaluate_emptyRequest_throwsIllegalArgument() {
        RouteEvaluateRequest empty = new RouteEvaluateRequest();
        empty.setRoutes(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.evaluate(empty));
    }

    @Test
    void evaluate_badStartTime_throwsIllegalArgument() {
        EvaluatedRoute route = route(vehicleType(100), order(1L, 10.0));
        route.setStartTime("not-a-time");

        assertThrows(IllegalArgumentException.class, () -> service.evaluate(request(route)));
    }
}
