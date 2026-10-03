package org.truong.gvrp_engine_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.MatrixMask;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.model.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates candidate route sequences without invoking the solver.
 *
 * <p>Uses the same {@link DistanceMatrixService} provider (GraphHopper road network)
 * and the same cost math as the solve path, so recalculated metrics stay comparable
 * with engine output. Time semantics mirror {@link OptimizationResultExtractor}:
 * arrival = previous departure + matrix time, waiting absorbs early arrival,
 * departure = arrival + wait + service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteEvaluationService {

    private final DistanceMatrixService distanceMatrixService;

    /** Upper bound on evaluated stops per request (matrix cost is quadratic). */
    @Value("${gvrp.evaluate.max-stops:200}")
    private int maxStops = 200;

    /** Same sentinel rule as the solve path: a real leg never approaches this. */
    private static final double SENTINEL_DISTANCE_THRESHOLD = MatrixMask.PRUNED_METERS / 2.0;

    public RouteEvaluateResponse evaluate(RouteEvaluateRequest request) {
        validateShape(request);

        int totalStops = request.getRoutes().stream()
                .mapToInt(route -> route.getOrders().size()).sum();
        if (totalStops > maxStops) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Too many stops to evaluate: " + totalStops + " (max " + maxStops + ")");
        }

        DistanceMatrix matrix = buildMatrix(request);

        List<RouteViolation> violations = new ArrayList<>();
        List<RouteDetail> evaluatedRoutes = new ArrayList<>();
        double totalDistance = 0.0;
        double totalTime = 0.0;
        double fuelCost = 0.0;
        double timeCost = 0.0;
        double fixedCost = 0.0;
        double totalCO2 = 0.0;
        int ordersServed = 0;

        int base = 0;
        for (int routeIndex = 0; routeIndex < request.getRoutes().size(); routeIndex++) {
            EvaluatedRoute route = request.getRoutes().get(routeIndex);
            EvaluatedRouteResult result = evaluateRoute(route, routeIndex, matrix, base, violations);
            base += route.getOrders().size() + 2;

            evaluatedRoutes.add(result.detail());
            totalDistance += result.distanceKm();
            totalTime += result.timeHours();
            fuelCost += result.fuelCost();
            timeCost += result.timeCost();
            fixedCost += result.fixedCost();
            totalCO2 += result.co2();
            ordersServed += result.orderCount();
        }

        EvaluateTotals totals = new EvaluateTotals(
                totalDistance, totalTime, fuelCost + timeCost + fixedCost,
                fuelCost, timeCost, fixedCost, totalCO2,
                evaluatedRoutes.size(), ordersServed);

        return new RouteEvaluateResponse(violations.isEmpty(), violations, evaluatedRoutes, totals);
    }

    // --------------------------------------------------

    private EvaluatedRouteResult evaluateRoute(EvaluatedRoute route, int routeIndex,
                                               DistanceMatrix matrix, int base,
                                               List<RouteViolation> violations) {
        VehicleType type = route.getVehicleType();
        long startSeconds = parseTime(route.getStartTime(), "startTime");

        List<Stop> stops = new ArrayList<>();
        double totalDemand = route.getOrders().stream()
                .mapToDouble(order -> demandOf(order)).sum();

        // Start depot: arrival = departure = start time, fully loaded.
        stops.add(depotStop(route.getStartDepot(), formatTime(startSeconds),
                formatTime(startSeconds), totalDemand));

        double routeDistanceMeters = 0.0;
        double currentSeconds = startSeconds;
        double currentLoad = totalDemand;
        double servedLoad = 0.0;
        int orderCount = 0;
        int prevIndex = base;
        boolean routingFailed = false;

        for (Order order : route.getOrders()) {
            int stopIndex = prevIndex + 1;

            double legMeters = matrix.distanceMeters(prevIndex, stopIndex);
            double legSeconds = matrix.timeSeconds(prevIndex, stopIndex);
            if (legMeters >= SENTINEL_DISTANCE_THRESHOLD) {
                violations.add(new RouteViolation("ROUTING_UNAVAILABLE", routeIndex,
                        order.getId(), "No road-network data for the leg into order "
                                + order.getOrderCode()));
                routingFailed = true;
                break;
            }
            // Backfill the previous stop's leg values now that the leg is known good.
            stops.get(stops.size() - 1).setDistanceToNext(legMeters);
            stops.get(stops.size() - 1).setTimeToNext(legSeconds);

            // Skill compatibility (same normalization spirit as the solve path).
            if (!skillsSatisfied(order, type)) {
                violations.add(new RouteViolation("SKILL_MISMATCH", routeIndex, order.getId(),
                        "Order requires " + order.getRequiredSkills()
                                + " but vehicle offers " + type.getSkills()));
            }

            double arrival = currentSeconds + legSeconds;
            double wait = 0.0;
            if (hasTimeWindow(order)) {
                long windowStart = parseTime(order.getTimeWindowStart(), "timeWindowStart");
                long windowEnd = parseTime(order.getTimeWindowEnd(), "timeWindowEnd");
                if (arrival < windowStart) {
                    wait = (windowStart - arrival) / 60.0;
                }
                if (arrival > windowEnd) {
                    violations.add(new RouteViolation("TIME_WINDOW", routeIndex, order.getId(),
                            "Arrival " + formatTime(arrival) + " is after window end "
                                    + order.getTimeWindowEnd()));
                }
            }
            double serviceMinutes = order.getServiceTime() != null ? order.getServiceTime() : 0;
            double departure = arrival + wait * 60.0 + serviceMinutes * 60.0;
            currentLoad -= demandOf(order);
            servedLoad += demandOf(order);
            orderCount++;

            if (servedLoad > capacityOf(type)) {
                violations.add(new RouteViolation("OVER_CAPACITY", routeIndex, order.getId(),
                        "Load " + servedLoad + " kg exceeds capacity "
                                + type.getCapacity() + " kg"));
            }

            Stop stop = customerStop(order, orderCount, formatTime(arrival),
                    formatTime(departure), serviceMinutes, wait, currentLoad);
            stops.add(stop);

            routeDistanceMeters += legMeters;
            currentSeconds = departure;
            prevIndex = stopIndex;
        }

        // Return leg to the end depot.
        double returnMeters = matrix.distanceMeters(prevIndex, base + route.getOrders().size() + 1);
        double returnSeconds = matrix.timeSeconds(prevIndex, base + route.getOrders().size() + 1);
        if (returnMeters >= SENTINEL_DISTANCE_THRESHOLD) {
            violations.add(new RouteViolation("ROUTING_UNAVAILABLE", routeIndex, null,
                    "No road-network data for the return leg to the depot"));
            routingFailed = true;
        } else {
            routeDistanceMeters += returnMeters;
            currentSeconds += returnSeconds;
            stops.get(stops.size() - 1).setDistanceToNext(returnMeters);
            stops.get(stops.size() - 1).setTimeToNext(returnSeconds);
        }

        double routeDistanceKm = routeDistanceMeters / 1000.0;
        if (type.getMaxDistance() != null && routeDistanceKm > type.getMaxDistance()) {
            violations.add(new RouteViolation("MAX_DISTANCE", routeIndex, null,
                    "Route distance " + String.format("%.1f", routeDistanceKm)
                            + " km exceeds max " + type.getMaxDistance() + " km"));
        }

        double timeHours = Math.max(0.0, (currentSeconds - startSeconds) / 3600.0);
        double routeFuel = routeDistanceKm * costOrZero(type.getCostPerKm());
        double routeTimeCost = timeHours * costOrZero(type.getCostPerHour());
        double routeFixed = costOrZero(type.getFixedCost());
        double routeCO2 = GreenVRPCostCalculator.calculateRouteCO2(routeDistanceKm, type);

        stops.add(depotStop(route.getEndDepot(), formatTime(currentSeconds), null, 0.0));

        RouteDetail detail = new RouteDetail(
                route.getVehicleId(), route.getVehicleLicensePlate(),
                type.getTypeName(), type.getEmissionFactor(), orderCount,
                routeDistanceKm, timeHours, totalDemand,
                type.getCapacity() != null && type.getCapacity() > 0
                        ? totalDemand / type.getCapacity() * 100.0 : 0.0,
                routeCO2, stops);

        if (routingFailed) {
            log.warn("Route {} partially evaluated: routing data missing", routeIndex);
        }
        return new EvaluatedRouteResult(detail, routeDistanceKm, timeHours,
                routeFuel, routeTimeCost, routeFixed, routeCO2, orderCount);
    }

    // --------------------------------------------------

    private DistanceMatrix buildMatrix(RouteEvaluateRequest request) {
        List<OptCoordinates> coordinates = new ArrayList<>();
        for (EvaluatedRoute route : request.getRoutes()) {
            coordinates.add(depotCoordinates(route.getStartDepot()));
            for (Order order : route.getOrders()) {
                coordinates.add(new OptCoordinates(
                        BigDecimal.valueOf(order.getLatitude()),
                        BigDecimal.valueOf(order.getLongitude())));
            }
            coordinates.add(depotCoordinates(route.getEndDepot()));
        }
        // Null mask = dense layout, right for dispatcher-scale candidate sets.
        return distanceMatrixService.createDistanceMatrix(coordinates, null);
    }

    private void validateShape(RouteEvaluateRequest request) {
        if (request == null || request.getRoutes() == null || request.getRoutes().isEmpty()) {
            throw new IllegalArgumentException("Routes list cannot be empty");
        }
        Set<Long> seenOrders = new HashSet<>();
        for (int i = 0; i < request.getRoutes().size(); i++) {
            EvaluatedRoute route = request.getRoutes().get(i);
            if (route.getVehicleType() == null) {
                throw new IllegalArgumentException("Route " + i + " is missing vehicleType");
            }
            if (route.getStartDepot() == null || route.getEndDepot() == null) {
                throw new IllegalArgumentException("Route " + i + " is missing depots");
            }
            if (route.getOrders() == null || route.getOrders().isEmpty()) {
                throw new IllegalArgumentException("Route " + i + " has no stops (EMPTY_ROUTE)");
            }
            parseTime(route.getStartTime(), "route " + i + " startTime");
            for (Order order : route.getOrders()) {
                if (order.getId() == null) {
                    throw new IllegalArgumentException("Route " + i + " has a stop without order id");
                }
                if (order.getLatitude() == null || order.getLongitude() == null) {
                    throw new IllegalArgumentException(
                            "Order " + order.getId() + " is missing coordinates");
                }
                if (!seenOrders.add(order.getId())) {
                    throw new IllegalArgumentException(
                            "Order " + order.getId() + " appears twice (DUPLICATE_ORDER)");
                }
            }
        }
    }

    // --------------------------------------------------

    private static OptCoordinates depotCoordinates(Depot depot) {
        if (depot.getLatitude() == null || depot.getLongitude() == null) {
            throw new IllegalArgumentException("Depot " + depot.getName() + " is missing coordinates");
        }
        return new OptCoordinates(
                BigDecimal.valueOf(depot.getLatitude()),
                BigDecimal.valueOf(depot.getLongitude()));
    }

    private static double demandOf(Order order) {
        return order.getDemand() != null ? order.getDemand() : 0.0;
    }

    private static double capacityOf(VehicleType type) {
        if (type.getCapacity() == null) {
            throw new IllegalArgumentException("Vehicle type is missing capacity");
        }
        return type.getCapacity();
    }

    private static double costOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static boolean hasTimeWindow(Order order) {
        return order.getTimeWindowStart() != null && order.getTimeWindowEnd() != null;
    }

    private static boolean skillsSatisfied(Order order, VehicleType type) {
        if (order.getRequiredSkills() == null || order.getRequiredSkills().isEmpty()) {
            return true;
        }
        return type.getSkills() != null && type.getSkills().containsAll(order.getRequiredSkills());
    }

    private static long parseTime(String time, String field) {
        try {
            return LocalTime.parse(time).toSecondOfDay();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time for " + field + ": " + time);
        }
    }

    /** Same HH:mm:ss rendering as the solve path. */
    private static String formatTime(double seconds) {
        long totalSeconds = (long) seconds;
        long hours = (totalSeconds / 3600) % 24;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static Stop depotStop(Depot depot, String arrival, String departure, double loadAfter) {
        Stop stop = new Stop();
        stop.setType("DEPOT");
        stop.setLocationId("depot-" + depot.getId());
        stop.setLocationName(depot.getName());
        stop.setArrivalTime(arrival);
        stop.setDepartureTime(departure);
        stop.setLoadAfter(loadAfter);
        stop.setDistanceToNext(0.0);
        stop.setTimeToNext(0.0);
        stop.setLatitude(depot.getLatitude());
        stop.setLongitude(depot.getLongitude());
        return stop;
    }

    private static Stop customerStop(Order order, int sequenceNumber, String arrival,
                                     String departure, double serviceMinutes, double waitMinutes,
                                     double loadAfter) {
        Stop stop = new Stop();
        stop.setType("CUSTOMER");
        stop.setSequenceNumber(sequenceNumber);
        stop.setOrderId(order.getId());
        stop.setOrderCode(order.getOrderCode());
        stop.setLocationId("order-" + order.getId());
        stop.setLocationName(order.getOrderCode());
        stop.setDemand(demandOf(order));
        stop.setArrivalTime(arrival);
        stop.setDepartureTime(departure);
        stop.setServiceTime(serviceMinutes);
        stop.setWaitTime(waitMinutes);
        stop.setLoadAfter(loadAfter);
        stop.setDistanceToNext(0.0);
        stop.setTimeToNext(0.0);
        stop.setLatitude(order.getLatitude());
        stop.setLongitude(order.getLongitude());
        return stop;
    }

    private record EvaluatedRouteResult(
            RouteDetail detail,
            double distanceKm,
            double timeHours,
            double fuelCost,
            double timeCost,
            double fixedCost,
            double co2,
            int orderCount) {
    }
}
