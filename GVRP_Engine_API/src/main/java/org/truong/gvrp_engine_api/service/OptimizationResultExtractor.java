package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.model.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
/**
 * Result Extraction Methods for OptimizationService
 * <p>
 * Refactored from monolithic extractResult() method
 * Maintains all logging and logic, just better organized
 */
@Slf4j
public class OptimizationResultExtractor {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== EXTRACT UNASSIGNED ORDERS ====================

    /**
     * Extract unassigned orders from solution
     *
     * Simple extraction with warning logs
     */
    public static List<UnassignedOrder> extractUnassignedOrders(
            VehicleRoutingProblemSolution solution,
            OptimizationContext context) {

        List<UnassignedOrder> unassignedOrders = new ArrayList<>();

        for (var job : solution.getUnassignedJobs()) {
            String orderId = job.getId().replace("order-", "");
            var orderDTO = context.orderDTOs().get(Long.parseLong(orderId));

            if (orderDTO != null) {
                UnassignedOrder unassigned = new UnassignedOrder();
                unassigned.setOrderId(orderDTO.getId());
                unassigned.setOrderCode(orderDTO.getOrderCode());
                unassigned.setReason("No suitable vehicle found or capacity exceeded");
                unassignedOrders.add(unassigned);

                log.warn("⚠️  Unassigned Order: {}", orderDTO.getOrderCode());
            }
        }

        return unassignedOrders;
    }

    // ==================== EXTRACT ROUTE DETAILS ====================

    /**
     * Extract all route details with comprehensive logging
     *
     * This is the BIG method - processes all routes and logs everything
     * Maintains ALL your existing logging format
     */
    public static RouteExtractionResult extractRouteDetails(
            VehicleRoutingProblemSolution solution,
            OptimizationContext context,
            DistanceTimeMatrix matrix) {

        List<RouteDetail> routes = new ArrayList<>();
        double totalDistance = 0;
        double totalTime = 0;
        double totalCO2 = 0;
        int totalOrdersServed = 0;

        int routeNumber = 0;

        // Process each route
        for (VehicleRoute route : solution.getRoutes()) {
            routeNumber++;

            // Extract vehicle info
            String vehicleId = route.getVehicle().getId().replace("vehicle-", "");
            var vehicleDTO = context.vehicleDTOs().get(Long.parseLong(vehicleId));

            if (vehicleDTO == null) {
                log.warn("Vehicle not found: {}", vehicleId);
                continue;
            }

            var vehicleTypeDTO = context.vehicleTypeDTOs().get(vehicleDTO.getVehicleTypeId());

            // Build route detail
            RouteDetail routeDetail = new RouteDetail();
            routeDetail.setVehicleId(vehicleDTO.getId());
            routeDetail.setVehicleLicensePlate(vehicleDTO.getVehicleLicensePlate());
            routeDetail.setVehicleType(vehicleTypeDTO.getTypeName());
            routeDetail.setEmissionFactor(vehicleTypeDTO.getEmissionFactor());

            // ========== START DETAILED LOGGING ==========
            logRouteHeader(routeNumber, vehicleDTO);

            // Extract stops
            StopExtractionResult stopResult = extractStops(
                    route,
                    vehicleDTO,
                    vehicleTypeDTO,
                    context,
                    matrix
            );

            routeDetail.setStops(stopResult.stops);
            routeDetail.setTotalDistance(stopResult.routeDistance / 1000.0); // km
            routeDetail.setTotalTime(stopResult.routeTime / 3600.0); // hours
            routeDetail.setOrderCount(stopResult.orderCount);
            routeDetail.setTotalLoad(stopResult.routeLoad);
            routeDetail.setLoadUtilization((stopResult.routeLoad / vehicleTypeDTO.getCapacity()) * 100.0);

            // Calculate CO2 emissions
            double co2Kg = (routeDetail.getTotalDistance() * vehicleTypeDTO.getEmissionFactor()) / 1000.0;
            routeDetail.setTotalCO2(co2Kg);

            routes.add(routeDetail);

            // Accumulate totals
            totalDistance += routeDetail.getTotalDistance();
            totalTime += routeDetail.getTotalTime();
            totalCO2 += co2Kg;
            totalOrdersServed += stopResult.orderCount;

            // ========== LOG ROUTE SUMMARY ==========
            logRouteSummary(routeDetail, stopResult);
        }

        return new RouteExtractionResult(
                routes,
                totalDistance,
                totalTime,
                totalCO2,
                totalOrdersServed
        );
    }

    // ==================== EXTRACT STOPS (PER ROUTE) ====================

    /**
     * Extract all stops for a single route
     * <p>
     * Processes: Start depot → Customer stops → End depot
     * Maintains all your detailed logging
     */
    private static StopExtractionResult extractStops(
            VehicleRoute route,
            Vehicle vehicleDTO,
            VehicleType vehicleTypeDTO,
            OptimizationContext context,
            DistanceTimeMatrix matrix) {

        List<Stop> stops = new ArrayList<>();
        double routeDistance = 0;
        double routeTime = 0;
        double routeLoad = 0;
        int orderCount = 0;

        // ========== START DEPOT ==========
        var startDepot = context.depotDTOs().get(vehicleDTO.getStartDepotId());
        Stop startStop = createDepotStop(
                startDepot,
                formatTime(route.getStart().getEndTime()),
                formatTime(route.getStart().getEndTime()),
                0.0
        );
        stops.add(startStop);

        logStartDepot(startDepot, route.getStart().getEndTime());

        TourActivity prevActivity = route.getStart();

        // ========== CUSTOMER STOPS ==========
        for (TourActivity activity : route.getActivities()) {
            if (activity instanceof TourActivity.JobActivity jobActivity) {
                com.graphhopper.jsprit.core.problem.job.Service service =
                        (com.graphhopper.jsprit.core.problem.job.Service) jobActivity.getJob();

                String orderId = service.getId().replace("order-", "");
                var orderDTO = context.orderDTOs().get(Long.parseLong(orderId));

                if (orderDTO != null) {
                    orderCount++;

                    // Calculate segment metrics
                    double segmentDistance = getTransportDistance(
                            prevActivity.getLocation(),
                            activity.getLocation(),
                            matrix,
                            context
                    );

                    double segmentTime = getTransportTime(
                            prevActivity.getLocation(),
                            activity.getLocation(),
                            matrix,
                            context
                    );

                    double waitTime = Math.max(0,
                            (activity.getArrTime() - prevActivity.getEndTime() - segmentTime) / 60.0);

                    routeLoad += orderDTO.getDemand();

                    // Create stop
                    Stop stop = createCustomerStop(
                            orderDTO,
                            orderCount,
                            activity,
                            routeLoad,
                            waitTime
                    );
                    stops.add(stop);

                    // Log customer stop details
                    logCustomerStop(
                            orderCount,
                            orderDTO,
                            activity,
                            stop,
                            segmentDistance,
                            segmentTime,
                            waitTime,
                            routeLoad,
                            vehicleTypeDTO
                    );

                    // Accumulate
                    routeDistance += segmentDistance;
                    routeTime += segmentTime;
                    routeTime += activity.getOperationTime(); // service time

                    prevActivity = activity;
                }
            }
        }

        // ========== END DEPOT ==========
        var endDepot = context.depotDTOs().get(vehicleDTO.getEndDepotId());

        double returnDistance = getTransportDistance(
                prevActivity.getLocation(),
                route.getEnd().getLocation(),
                matrix,
                context
        );
        routeDistance += returnDistance;

        double returnTime = getTransportTime(
                prevActivity.getLocation(),
                route.getEnd().getLocation(),
                matrix,
                context
        );
        routeTime += returnTime;

        Stop endStop = createDepotStop(
                endDepot,
                formatTime(route.getEnd().getArrTime()),
                null,
                0.0
        );
        stops.add(endStop);

        logEndDepot(endDepot, route.getEnd().getArrTime(), returnDistance, returnTime);

        return new StopExtractionResult(
                stops,
                routeDistance,
                routeTime,
                routeLoad,
                orderCount
        );
    }

    /**
     * Create depot stop (start or end)
     */
    private static Stop createDepotStop(
            Depot depot,
            String arrivalTime,
            String departureTime,
            double loadAfter) {

        Stop stop = new Stop();
        stop.setType("DEPOT");
        stop.setLocationId("depot-" + depot.getId());
        stop.setLocationName(depot.getName());
        stop.setArrivalTime(arrivalTime);
        stop.setDepartureTime(departureTime);
        stop.setLoadAfter(loadAfter);
        stop.setLatitude(depot.getLatitude());
        stop.setLongitude(depot.getLongitude());
        return stop;
    }

    /**
     * Create customer stop
     */
    private static Stop createCustomerStop(
            Order orderDTO,
            int sequenceNumber,
            TourActivity activity,
            double loadAfter,
            double waitTime) {

        Stop stop = new Stop();
        stop.setType("CUSTOMER");
        stop.setSequenceNumber(sequenceNumber);
        stop.setOrderId(orderDTO.getId());
        stop.setOrderCode(orderDTO.getOrderCode());
        stop.setLocationId("order-" + orderDTO.getId());
        stop.setLocationName(orderDTO.getOrderCode());
        stop.setDemand(orderDTO.getDemand());
        stop.setArrivalTime(formatTime(activity.getArrTime()));
        stop.setDepartureTime(formatTime(activity.getEndTime()));
        stop.setServiceTime(activity.getOperationTime() / 60.0); // minutes
        stop.setWaitTime(waitTime);
        stop.setLoadAfter(loadAfter);
        stop.setLatitude(orderDTO.getLatitude());
        stop.setLongitude(orderDTO.getLongitude());

        return stop;
    }

    private static void logRouteHeader(int routeNumber, Vehicle vehicleDTO) {
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║  📍 ROUTE #{}: {}                                                    ",
                routeNumber, vehicleDTO.getVehicleLicensePlate());
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
    }

    private static void logStartDepot(Depot startDepot, double startTime) {
        log.info("║  🏢 START: {} at {}",
                startDepot.getName(),
                formatTime(startTime));
        log.info("║     📍 Location: ({}, {})",
                startDepot.getLatitude(),
                startDepot.getLongitude());
        log.info("╠────────────────────────────────────────────────────────────────────────────╣");
    }

    private static void logCustomerStop(
            int orderCount,
            Order orderDTO,
            TourActivity activity,
            Stop stop,
            double segmentDistance,
            double segmentTime,
            double waitTime,
            double routeLoad,
            VehicleType vehicleTypeDTO) {

        log.info("║  #{} 📦 {}", orderCount, orderDTO.getOrderCode());
        log.info("║     📍 Location: ({}, {})",
                orderDTO.getLatitude(),
                orderDTO.getLongitude());
        log.info("║     🚚 Demand: {}kg → Load after: {}kg / {}kg capacity",
                orderDTO.getDemand(),
                routeLoad,
                vehicleTypeDTO.getCapacity());
        log.info("║     ⏱️  Arrival: {} | Departure: {} (Service: {}min)",
                formatTime(activity.getArrTime()),
                formatTime(activity.getEndTime()),
                stop.getServiceTime());
        log.info("║     🛣️  From previous: {}km in {}min{}",
                segmentDistance / 1000.0,
                segmentTime / 60.0,
                waitTime > 0 ? String.format(" (Wait: %.0fmin)", waitTime) : "");

        // Time window validation
        if (orderDTO.getTimeWindowStart() != null && orderDTO.getTimeWindowEnd() != null) {
            boolean isLate = activity.getArrTime() > parseTimeToSeconds(orderDTO.getTimeWindowEnd());
            log.info("║     🕐 Time Window: {} - {}{}",
                    orderDTO.getTimeWindowStart(),
                    orderDTO.getTimeWindowEnd(),
                    isLate ? " ⚠️ LATE" : " ✓");
        }

        log.info("╠────────────────────────────────────────────────────────────────────────────╣");
    }

    private static void logEndDepot(Depot endDepot, double endTime, double returnDistance, double returnTime) {
        log.info("║  🏢 END: {} at {}",
                endDepot.getName(),
                formatTime(endTime));
        log.info("║     📍 Location: ({}, {})",
                endDepot.getLatitude(),
                endDepot.getLongitude());
        log.info("║     🛣️  From last stop: {}km in {}min",
                returnDistance / 1000.0,
                returnTime / 60.0);
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
    }

    private static void logRouteSummary(RouteDetail routeDetail, StopExtractionResult stopResult) {
        log.info("║  📊 ROUTE SUMMARY:");
        log.info("║     • Orders delivered: {}", stopResult.orderCount);
        log.info("║     • Total distance: {} km", routeDetail.getTotalDistance());
        log.info("║     • Total time: {} hours ({} minutes)",
                routeDetail.getTotalTime(),
                routeDetail.getTotalTime() * 60);
        log.info("║     • Total load: {}kg ({}% capacity)",
                stopResult.routeLoad,
                routeDetail.getLoadUtilization());
        log.info("║     • CO2 emissions: {} kg", routeDetail.getTotalCO2());
        log.info("║     • Average speed: {} km/h",
                routeDetail.getTotalDistance() / routeDetail.getTotalTime());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    private static void logOverallSummary(
            OptimizationResult result,
            OptimizationContext context) {
        log.info("╔════════════════════════════════════════════════════════════════════════════╗");
        log.info("║  🎯 OPTIMIZATION RESULT SUMMARY                                            ║");
        log.info("╠════════════════════════════════════════════════════════════════════════════╣");
        log.info("║  💰 Total Cost: {} VND", result.getTotalCost());
        log.info("║  🛣️  Total Distance: {} km", result.getTotalDistance());
        log.info("║  ⏱️  Total Time: {} hours", result.getTotalTime());
        log.info("║  🌱 Total CO2: {} kg", result.getTotalCO2());
        log.info("║  🚚 Vehicles Used: {}/{}", result.getTotalVehiclesUsed(), context.vehicleDTOs().size());
        log.info("║  📦 Orders Served: {}/{}", result.getTotalOrdersServed(), context.orderDTOs().size());
        log.info("║  ⚠️  Unassigned Orders: {}", result.getTotalOrdersUnassigned());
        log.info("╚════════════════════════════════════════════════════════════════════════════╝");
    }

    private static double getTransportDistance(
            Location from,
            Location to,
            DistanceTimeMatrix matrix,
            OptimizationContext context) {

        int fromIndex = context.allLocations().indexOf(from);
        int toIndex = context.allLocations().indexOf(to);

        if (fromIndex == -1 || toIndex == -1) {
            log.warn("Location not found in matrix: {} -> {}", from.getId(), to.getId());
            return 0.0;
        }

        return matrix.distanceMatrix()[fromIndex][toIndex];
    }

    private static double getTransportTime(
            Location from,
            Location to,
            DistanceTimeMatrix matrix,
            OptimizationContext context) {

        int fromIndex = context.allLocations().indexOf(from);
        int toIndex = context.allLocations().indexOf(to);

        if (fromIndex == -1 || toIndex == -1) {
            log.warn("Location not found in matrix: {} -> {}", from.getId(), to.getId());
            return 0.0;
        }

        return matrix.timeMatrix()[fromIndex][toIndex];
    }

    private static String formatTime(double seconds) {
        long totalSeconds = (long) seconds;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static long parseTimeToSeconds(String timeStr) {
        try {
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr, TIME_FORMATTER);
            return time.toSecondOfDay();
        } catch (Exception e) {
            log.warn("Failed to parse time: {}, using default", timeStr);
            return 0;
        }
    }

    // ==================== RESULT CLASSES ====================

    /**
         * Result of route extraction (all routes)
         */
        public record RouteExtractionResult(
                List<RouteDetail> routes,
                double totalDistance,
                double totalTime,
                double totalCO2,
                int totalOrdersServed
    ) {
    }

    /**
     * Result of stop extraction (single route)
     */
    private record StopExtractionResult(
            List<Stop> stops,
            double routeDistance,
            double routeTime,
            double routeLoad,
            int orderCount
    ) {
    }

}
