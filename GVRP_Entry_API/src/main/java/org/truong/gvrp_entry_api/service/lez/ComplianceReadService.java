package org.truong.gvrp_entry_api.service.lez;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.RouteGeometryDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionComplianceDTO;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read side of LEZ compliance (LEZ-001 N8): the planner panel and the map.
 *
 * <p>Branch-scoped and read-only apart from {@link #recheck}: another branch's id
 * answers exactly like a missing one ({@link LezNotFoundException}, 404).
 * {@code STALE} is computed from the stored ruleset hash and never written by a
 * {@code GET} (design §16.2-6).
 */
@Service
@RequiredArgsConstructor
public class ComplianceReadService {

    /** Derived status of a certified route whose ruleset hash no longer matches. */
    public static final String STALE = "STALE";

    private final SolutionRepository solutionRepository;
    private final RouteRepository routeRepository;
    private final RouteComplianceService routeComplianceService;

    /** Compliance of every route of a solution, as stored; nothing is written. */
    @Transactional(readOnly = true)
    public SolutionComplianceDTO compliance(Long solutionId, Long branchId) {
        Solution solution = loadSolution(solutionId, LezSupport.requireBranch(branchId));
        // One compilation feeds both the hash and the zone names.
        return complianceOf(solution, routeComplianceService.currentFor(solution));
    }

    /**
     * Recomputes the verdict with the branch's current restrictions and stores it.
     * This is the only write path of the panel, which is why it is a {@code POST}
     * and not a {@code GET} with a side effect.
     */
    @Transactional
    public SolutionComplianceDTO recheck(Long solutionId, Long branchId) {
        Solution solution = loadSolution(solutionId, LezSupport.requireBranch(branchId));
        CompiledRestrictions current = routeComplianceService.currentFor(solution);
        List<Route> routes = routesOf(solution);
        if (current != null) {
            for (Route route : routes) {
                routeComplianceService.certify(route,
                        routeComplianceService.check(route, current), current);
            }
        }
        // Rechecking with the current data can never leave a route STALE.
        return complianceOf(solution, current);
    }

    /** Legs of one route for the planner map (D1). */
    @Transactional(readOnly = true)
    public RouteGeometryDTO geometry(Long routeId, Long branchId) {
        Long branch = LezSupport.requireBranch(branchId);
        Route route = routeRepository.findById(routeId)
                .filter(found -> found.getSolution() != null
                        && found.getSolution().getBranch() != null
                        && branch.equals(found.getSolution().getBranch().getId()))
                .orElseThrow(() -> new LezNotFoundException(AppConstant.ROUTE));

        List<RouteStop> stops = route.getSegments() == null ? List.of()
                : route.getSegments().stream()
                .sorted(Comparator.comparingInt(RouteComplianceService::planRank))
                .toList();

        List<RouteGeometryDTO.Leg> legs = new ArrayList<>();
        for (int i = 0; i + 1 < stops.size(); i++) {
            RouteStop from = stops.get(i);
            RouteStop to = stops.get(i + 1);
            legs.add(RouteGeometryDTO.Leg.builder()
                    .fromSeq(sequenceOf(from, i))
                    .toSeq(sequenceOf(to, i + 1))
                    .polyline(from.getGeometryToNext())
                    .instructions(LegInstructionCodec.fromJson(from.getInstructionsToNext()))
                    .build());
        }
        return RouteGeometryDTO.builder().routeId(route.getId()).legs(legs).build();
    }

    // --------------------------------------------------

    /**
     * The current ruleset hash for the solution's branch and date. One value for
     * all its routes, because every route is certified with the same compilation.
     */
    public String currentHashOf(Solution solution) {
        CompiledRestrictions current = routeComplianceService.currentFor(solution);
        return current != null ? current.getHash() : null;
    }

    private SolutionComplianceDTO complianceOf(Solution solution, CompiledRestrictions current) {
        String currentHash = current != null ? current.getHash() : null;
        List<SolutionComplianceDTO.RouteCompliance> rows = new ArrayList<>();
        for (Route route : routesOf(solution)) {
            rows.add(SolutionComplianceDTO.RouteCompliance.builder()
                    .routeId(route.getId())
                    .vehicleId(route.getVehicle() != null ? route.getVehicle().getId() : null)
                    .status(statusOf(route, currentHash))
                    .checkedAt(route.getLezCheckedAt())
                    .violations(routeComplianceService.violationsNode(route.getLezViolations()))
                    .build());
        }
        return SolutionComplianceDTO.builder()
                .solutionId(solution.getId())
                .routes(rows)
                .zones(zonesOf(current))
                .lezUnassigned(lezUnassignedOf(solution))
                .build();
    }

    /** Zone identity of the same compilation the hash comes from; empty when there is none. */
    private static List<SolutionComplianceDTO.ZoneRef> zonesOf(CompiledRestrictions current) {
        if (current == null || current.getZones() == null) {
            return List.of();
        }
        List<SolutionComplianceDTO.ZoneRef> zones = new ArrayList<>();
        for (CompiledRestrictions.CompiledZone zone : current.getZones()) {
            if (zone == null) {
                continue;
            }
            zones.add(SolutionComplianceDTO.ZoneRef.builder()
                    .key(zone.getKey())
                    .branchZoneId(zone.getBranchZoneId())
                    .name(zone.getName())
                    .build());
        }
        return zones;
    }

    /**
     * Unassigned rows the engine left out for an LEZ reason only; a capacity or
     * time-window refusal is not a compliance finding and stays out of the panel.
     */
    private static List<SolutionComplianceDTO.LezUnassigned> lezUnassignedOf(Solution solution) {
        if (solution.getUnassignedOrders() == null) {
            return List.of();
        }
        return solution.getUnassignedOrders().stream()
                .filter(row -> row != null && row.getReasonCode() != null
                        && row.getReasonCode().startsWith("LEZ_"))
                .sorted(Comparator.comparing(UnassignedOrder::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> SolutionComplianceDTO.LezUnassigned.builder()
                        .orderId(row.getOrder() != null ? row.getOrder().getId() : null)
                        .orderCode(row.getOrder() != null ? row.getOrder().getOrderCode() : null)
                        .reasonCode(row.getReasonCode())
                        .reason(row.getReason())
                        .build())
                .toList();
    }

    /**
     * Stored status, or {@code STALE} when the ruleset the route was checked with
     * is no longer the current one. A route never checked stays {@code UNCHECKED}:
     * calling it stale would claim it had a verdict.
     */
    private static String statusOf(Route route, String currentHash) {
        LezStatus stored = route.getLezStatus();
        if (stored == null) {
            return LezStatus.UNCHECKED.name();
        }
        if (currentHash == null || !currentHash.equals(route.getLezRulesetHash())) {
            return STALE;
        }
        return stored.name();
    }

    private Solution loadSolution(Long solutionId, Long branchId) {
        return solutionRepository.findWithDetailsById(solutionId)
                .filter(found -> found.getBranch() != null
                        && branchId.equals(found.getBranch().getId()))
                .orElseThrow(() -> new LezNotFoundException(AppConstant.SOLUTION));
    }

    private static List<Route> routesOf(Solution solution) {
        if (solution.getRoutes() == null) {
            return List.of();
        }
        return solution.getRoutes().stream()
                .filter(route -> route != null)
                .sorted(Comparator.comparing(Route::getRouteOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Stored sequence number, or the plan position for engine depots (which have none). */
    private static Integer sequenceOf(RouteStop stop, int position) {
        return Optional.ofNullable(stop.getSequenceNumber()).orElse(position);
    }
}
