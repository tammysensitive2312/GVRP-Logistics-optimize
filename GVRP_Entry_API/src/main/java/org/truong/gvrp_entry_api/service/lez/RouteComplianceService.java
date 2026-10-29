package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.entity.enums.LocationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the {@link ComplianceChecker} verdict to stored routes (LEZ-001,
 * design §5.3, §16.2-5): compile, check, persist.
 *
 * <p>One place for the four triggers — completion callback, Pareto apply,
 * revision save and send — so they cannot disagree about what a certified route
 * looks like. Every method tolerates an empty compilation: a branch without
 * enabled zones certifies every route {@code COMPLIANT} (design §16.2-7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteComplianceService {

    /** Machine-readable reason stored on an LEZ-unassigned order (design §16.2-2). */
    public static final String LEZ_NO_COMPLIANT_VEHICLE = "LEZ_NO_COMPLIANT_VEHICLE";

    private final RestrictionCompiler restrictionCompiler;
    private final ObjectMapper objectMapper;

    // ---------- compilation ----------

    /** Current restrictions of the branch for one date; never null. */
    public CompiledRestrictions compileFor(Long branchId, LocalDate date,
                                           Collection<Order> orders, Collection<Vehicle> vehicles) {
        return restrictionCompiler.compile(branchId, date,
                vehicles != null ? new ArrayList<>(vehicles) : List.of(),
                orders != null ? new ArrayList<>(orders) : List.of());
    }

    /**
     * Current restrictions of a solution's branch and date, scoped to the orders
     * and vehicles of that solution. Every read-side recomputation of the ruleset
     * hash uses exactly this scope, so {@code STALE} compares like with like.
     *
     * @return null when the solution has no single service date (no orders, or
     *         pre-D4 routes spanning several dates)
     */
    public CompiledRestrictions currentFor(Solution solution) {
        if (solution == null || solution.getBranch() == null) {
            return null;
        }
        List<Order> orders = ordersOf(solution);
        LocalDate serviceDate = serviceDateOf(orders);
        if (serviceDate == null) {
            return null;
        }
        return compileFor(solution.getBranch().getId(), serviceDate, orders, vehiclesOf(solution));
    }

    /** Orders of a solution: served by its routes plus its unassigned rows. */
    public static List<Order> ordersOf(Solution solution) {
        List<Order> orders = new ArrayList<>();
        if (solution == null) {
            return orders;
        }
        Set<Long> seen = new HashSet<>();
        if (solution.getRoutes() != null) {
            for (Route route : solution.getRoutes()) {
                if (route == null || route.getSegments() == null) {
                    continue;
                }
                for (RouteStop stop : route.getSegments()) {
                    Order order = stop != null ? stop.getOrder() : null;
                    if (order != null && order.getId() != null && seen.add(order.getId())) {
                        orders.add(order);
                    }
                }
            }
        }
        if (solution.getUnassignedOrders() != null) {
            for (var row : solution.getUnassignedOrders()) {
                Order order = row != null ? row.getOrder() : null;
                if (order != null && order.getId() != null && seen.add(order.getId())) {
                    orders.add(order);
                }
            }
        }
        return orders;
    }

    /** Vehicles referenced by a solution's routes, distinct by id. */
    public static List<Vehicle> vehiclesOf(Solution solution) {
        List<Vehicle> vehicles = new ArrayList<>();
        if (solution == null || solution.getRoutes() == null) {
            return vehicles;
        }
        Set<Long> seen = new HashSet<>();
        for (Route route : solution.getRoutes()) {
            Vehicle vehicle = route != null ? route.getVehicle() : null;
            if (vehicle != null && vehicle.getId() != null && seen.add(vehicle.getId())) {
                vehicles.add(vehicle);
            }
        }
        return vehicles;
    }

    /**
     * The single delivery date of a route's orders (D4); null when the orders
     * span several dates, which only pre-D4 routes can do.
     */
    public static LocalDate serviceDateOf(Collection<Order> orders) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        if (orders != null) {
            for (Order order : orders) {
                if (order != null && order.getDeliveryDate() != null) {
                    dates.add(order.getDeliveryDate());
                }
            }
        }
        return dates.size() == 1 ? dates.iterator().next() : null;
    }

    /** Whether the route's orders span several delivery dates (send refuses those). */
    public static boolean hasMixedDates(Collection<Order> orders) {
        return serviceDateOf(orders) == null && orders != null
                && orders.stream().filter(Objects::nonNull)
                .map(Order::getDeliveryDate).filter(Objects::nonNull).distinct().count() > 1;
    }

    // ---------- snapshot ----------

    /** Snapshot text of a job; null when the job has none (route made before P1b). */
    public CompiledRestrictions readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CompiledRestrictions.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read the compiled LEZ snapshot", e);
        }
    }

    public String writeSnapshot(CompiledRestrictions restrictions) {
        try {
            return objectMapper.writeValueAsString(restrictions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the compiled LEZ restrictions", e);
        }
    }

    // ---------- checking ----------

    /** Violations of one stored route under the given restrictions; empty when compliant. */
    public List<ComplianceChecker.Violation> check(Route route, CompiledRestrictions restrictions) {
        if (route == null) {
            return List.of();
        }
        Long vehicleId = route.getVehicle() != null ? route.getVehicle().getId() : null;
        return check(vehicleId, stopViews(route.getSegments()), restrictions);
    }

    /** Null-safe variant used by the preview, whose data comes from the engine response. */
    public List<ComplianceChecker.Violation> check(Long vehicleId, List<ComplianceChecker.StopView> stops,
                                                   CompiledRestrictions restrictions) {
        if (restrictions == null) {
            return List.of();
        }
        return ComplianceChecker.check(vehicleId, stops, restrictions);
    }

    /** Stops of a route in plan order; the start depot has a departure, the end depot only an arrival. */
    public static List<ComplianceChecker.StopView> stopViews(List<RouteStop> stops) {
        List<ComplianceChecker.StopView> views = new ArrayList<>();
        if (stops == null) {
            return views;
        }
        List<RouteStop> ordered = new ArrayList<>(stops);
        ordered.sort(Comparator.comparingInt(RouteComplianceService::planRank));
        for (RouteStop stop : ordered) {
            views.add(new ComplianceChecker.StopView(
                    stop.getType(),
                    stop.getOrder() != null ? stop.getOrder().getId() : null,
                    stop.getLocation(),
                    stop.getArrivalTime(),
                    stop.getDepartureTime(),
                    stop.getGeometryToNext()));
        }
        return views;
    }

    /** Engine-generated depots carry no sequence number: start depot first, end depot last. */
    static int planRank(RouteStop stop) {
        if (stop.getSequenceNumber() != null) {
            return stop.getSequenceNumber();
        }
        if (stop.getType() == LocationType.DEPOT && stop.getDepartureTime() != null) {
            return Integer.MIN_VALUE;
        }
        return Integer.MAX_VALUE;
    }

    // ---------- certification ----------

    /** Writes the verdict on the route; the caller saves the route or its solution. */
    public void certify(Route route, List<ComplianceChecker.Violation> violations,
                        CompiledRestrictions restrictions) {
        if (route == null) {
            return;
        }
        List<ComplianceChecker.Violation> found = violations != null ? violations : List.of();
        route.setLezStatus(found.isEmpty() ? LezStatus.COMPLIANT : LezStatus.VIOLATION);
        route.setLezCheckedAt(LocalDateTime.now());
        route.setLezRulesetHash(restrictions != null ? restrictions.getHash() : null);
        route.setLezViolations(found.isEmpty() ? null : violationsJson(found));
    }

    /** Certifies a route from the current data of its branch and date. */
    public List<ComplianceChecker.Violation> certifyCurrent(Route route, Long branchId, LocalDate date,
                                                            Collection<Order> orders,
                                                            Collection<Vehicle> vehicles) {
        CompiledRestrictions restrictions = compileFor(branchId, date, orders, vehicles);
        List<ComplianceChecker.Violation> violations = check(route, restrictions);
        certify(route, violations, restrictions);
        return violations;
    }

    /** JSON text stored in {@code routes.lez_violations}. */
    public String violationsJson(List<ComplianceChecker.Violation> violations) {
        try {
            return objectMapper.writeValueAsString(violations);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize LEZ violations", e);
        }
    }

    /** Violations as JSON for the read APIs; null stays null. */
    public JsonNode violationsNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read LEZ violations", e);
        }
    }

    // ---------- unassigned reason (design §16.2-2) ----------

    /**
     * True when no vehicle of the request carries every LEZ skill of the order,
     * i.e. some zone of the order bans every vehicle all day. Unusable vehicles
     * are not part of the request.
     */
    public static boolean noCompliantVehicle(CompiledRestrictions restrictions, Long orderId) {
        if (restrictions == null || !restrictions.hasZones()) {
            return false;
        }
        List<String> keys = restrictions.orderZoneKeys(orderId);
        if (keys.isEmpty()) {
            return false;
        }
        Set<Long> unusable = restrictions.unusableVehicleIds();
        Set<Long> vehicleIds = new HashSet<>();
        for (CompiledRestrictions.VehicleRestriction restriction : restrictions.getVehicleRestrictions()) {
            if (!unusable.contains(restriction.getVehicleId())) {
                vehicleIds.add(restriction.getVehicleId());
            }
        }
        for (Long vehicleId : vehicleIds) {
            boolean compliant = true;
            for (String key : keys) {
                CompiledRestrictions.VehicleRestriction restriction = restrictions.restriction(vehicleId, key);
                if (restriction == null || restriction.isAllDay()) {
                    compliant = false;
                    break;
                }
            }
            if (compliant) {
                return false;
            }
        }
        return true;
    }

    /** Human reason naming the order's zones. */
    public static String noCompliantVehicleReason(CompiledRestrictions restrictions, Long orderId) {
        return "No compliant vehicle for zone " + String.join(", ", restrictions.orderZoneKeys(orderId));
    }

    // ---------- helpers ----------
}
