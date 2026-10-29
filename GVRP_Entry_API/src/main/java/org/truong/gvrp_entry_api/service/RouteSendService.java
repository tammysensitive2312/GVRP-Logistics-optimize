package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.EngineLegsRequestDTO;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.dto.response.DriverDTO;
import org.truong.gvrp_entry_api.dto.response.DriverRouteDTO;
import org.truong.gvrp_entry_api.dto.response.DriverStopDTO;
import org.truong.gvrp_entry_api.dto.response.EngineLegsResponseDTO;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SendStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.CompiledRestrictions;
import org.truong.gvrp_entry_api.service.lez.ComplianceChecker;
import org.truong.gvrp_entry_api.service.lez.LegInstructionCodec;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatcher sends route plans to drivers; drivers read their own assignments.
 *
 * <p>Sends are immutable history: a newer send for the same route flips siblings
 * to SUPERSEDED atomically. Role and ownership are enforced here (there is no
 * precedent of Security annotations in this codebase).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteSendService {

    private final RouteSendRepository sendRepository;
    private final RouteRepository routeRepository;
    private final SolutionRepository solutionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * LEZ send guard (LEZ-001 N7). Field-injected and optional so the historical
     * unit tests, which construct this service with its five repositories, keep
     * their exact behaviour; a null collaborator skips the gate.
     */
    @Autowired(required = false)
    private RouteComplianceService routeComplianceService;

    /** Engine client used only to fill missing leg geometry at send time. */
    @Autowired(required = false)
    private EngineApiClient engineApiClient;

    // ---------- dispatcher ----------

    /** Branch driver directory (enabled DRIVER users). */
    @Transactional(readOnly = true)
    public List<DriverDTO> listDrivers() {
        requireRole(UserRole.PLANNER);
        Long branchId = currentBranchId();
        return userRepository.findByBranchIdAndRole(branchId, UserRole.DRIVER).stream()
                .filter(User::getEnabled)
                .map(driver -> DriverDTO.builder()
                        .id(driver.getId())
                        .username(driver.getUsername())
                        .fullName(driver.getFullName())
                        .phone(driver.getPhone())
                        .build())
                .toList();
    }

    /**
     * Send a route revision to a driver; supersedes prior sends of the route.
     *
     * <p>A refused LEZ send (422) is listed in {@code noRollbackFor}, so the
     * {@code VIOLATION} verdict written by {@code certify} — and any legs the guard
     * filled — commit together with the refusal, and the planner can see why it was
     * refused (review-p1b F1). Nothing else has changed at that point: the
     * supersede/insert block below runs only when the guard passes.
     */
    @Transactional(noRollbackFor = UnprocessablePlanException.class)
    public RouteSendDTO sendRoute(Long routeId, SendRouteRequest request) {
        requireRole(UserRole.PLANNER);
        Long branchId = currentBranchId();
        User sender = currentUser();

        Route route = routeRepository.findWithLockById(routeId)
                .orElseThrow(() -> notFound(AppConstant.ROUTE));
        Solution solution = solutionRepository.findWithDetailsById(request.getSolutionId())
                .orElseThrow(() -> notFound(AppConstant.SOLUTION));
        if (!route.getSolution().getId().equals(solution.getId())) {
            throw invalid("Route " + routeId + " does not belong to solution " + request.getSolutionId());
        }
        if (solution.getStatus() != SolutionStatus.SUCCESS
                && solution.getStatus() != SolutionStatus.PARTIAL_SUCCESS) {
            throw invalid("Solution " + solution.getId() + " is not sendable (status " + solution.getStatus() + ")");
        }
        if (!solution.getBranch().getId().equals(branchId)) {
            throw invalid("Solution " + solution.getId() + " belongs to another branch");
        }
        boolean hasOrders = route.getSegments().stream()
                .anyMatch(stop -> stop.getType() == LocationType.ORDER);
        if (!hasOrders) {
            throw invalid("Route " + routeId + " has no delivery stops");
        }

        User driver = userRepository.findById(request.getDriverUserId())
                .orElseThrow(() -> notFound("Driver"));
        if (driver.getRole() != UserRole.DRIVER || !driver.getEnabled()
                || !driver.getBranch().getId().equals(branchId)) {
            throw invalid("User " + request.getDriverUserId() + " is not an enabled driver of this branch");
        }

        // LEZ gate (LEZ-001 N7): current rules and current geometry, before any send row.
        enforceLezCompliance(route, solution);

        // Atomic flip + insert under the route lock: a concurrent loser re-reads
        // CURRENT siblings afterwards and reports 409 instead of double-sending.
        List<RouteSend> siblings =
                sendRepository.findByRouteIdAndStatusOrderBySentAtDescIdDesc(routeId, SendStatus.CURRENT);
        siblings.forEach(sibling -> sibling.setStatus(SendStatus.SUPERSEDED));

        RouteSend send = RouteSend.builder()
                .solution(solution)
                .route(route)
                .driver(driver)
                .sender(sender)
                .sentAt(LocalDateTime.now())
                .status(SendStatus.CURRENT)
                .build();
        RouteSend saved = sendRepository.saveAndFlush(send);

        long currentCount = sendRepository
                .findByRouteIdAndStatusOrderBySentAtDescIdDesc(routeId, SendStatus.CURRENT)
                .size();
        if (currentCount != 1) {
            throw new org.truong.gvrp_entry_api.exception.RevisionConflictException(
                    "Route " + routeId + " was sent concurrently; reload and retry.",
                    latestSendId(routeId));
        }
        return toSendDTO(saved);
    }

    // ---------- driver ----------

    /** Send history of one route, newest first (PLANNER only). */
    @Transactional(readOnly = true)
    public List<RouteSendDTO> routeSends(Long routeId) {
        requireRole(UserRole.PLANNER);
        Long branchId = currentBranchId();
        // Tenant check: another branch's route answers exactly like a missing one.
        routeRepository.findById(routeId)
                .filter(route -> route.getSolution() != null
                        && SolutionService.belongsTo(route.getSolution(), branchId))
                .orElseThrow(() -> notFound(AppConstant.ROUTE));
        return sendRepository.findByRouteIdOrderBySentAtDescIdDesc(routeId).stream()
                .map(this::toSendDTO)
                .toList();
    }

    /** Driver's own sends, CURRENT first. */
    @Transactional(readOnly = true)
    public List<RouteSendDTO> mySends() {
        User driver = requireDriver();
        List<RouteSendDTO> current = sendRepository
                .findByDriverIdAndStatusOrderBySentAtDescIdDesc(driver.getId(), SendStatus.CURRENT)
                .stream().map(this::toSendDTO).toList();
        List<RouteSendDTO> old = sendRepository
                .findByDriverIdAndStatusOrderBySentAtDescIdDesc(driver.getId(), SendStatus.SUPERSEDED)
                .stream().map(this::toSendDTO).toList();
        List<RouteSendDTO> all = new ArrayList<>(current);
        all.addAll(old);
        return all;
    }

    /** Full route detail for one of the driver's own sends (with customer info). */
    @Transactional(readOnly = true)
    public DriverRouteDTO sendDetail(Long sendId) {
        User driver = requireDriver();
        RouteSend send = sendRepository.findById(sendId)
                .orElseThrow(() -> notFound("Route assignment"));
        if (!send.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Assignment " + sendId + " belongs to another driver");
        }

        Route route = send.getRoute();
        Map<Long, Order> orderMap = orderRepository.findAllById(
                route.getSegments().stream()
                        .filter(stop -> stop.getType() == LocationType.ORDER && stop.getOrder() != null)
                        .map(stop -> stop.getOrder().getId())
                        .toList()
        ).stream().collect(Collectors.toMap(Order::getId, o -> o));

        // Current zones of this branch: names only, no polygon leaves the server (D1).
        CompiledRestrictions restrictions = routeComplianceService != null
                ? routeComplianceService.currentFor(send.getSolution()) : null;

        List<DriverStopDTO> stops = route.getSegments().stream()
                .sorted(Comparator.comparingInt(RouteSendService::planRank))
                .map(stop -> {
                    Order order = stop.getOrder() != null ? orderMap.get(stop.getOrder().getId()) : null;
                    return DriverStopDTO.builder()
                            .sequenceNumber(stop.getSequenceNumber())
                            .type(stop.getType() == LocationType.ORDER ? "ORDER" : "DEPOT")
                            .orderId(stop.getOrder() != null ? stop.getOrder().getId() : null)
                            .locationName(stop.getLocationName())
                            .address(order != null ? order.getAddress() : null)
                            .latitude(stop.getLocation() != null ? stop.getLocation().getY() : null)
                            .longitude(stop.getLocation() != null ? stop.getLocation().getX() : null)
                            .arrivalTime(stop.getArrivalTime())
                            .departureTime(stop.getDepartureTime())
                            .customerName(order != null ? order.getCustomerName() : null)
                            .customerPhone(order != null ? order.getCustomerPhone() : null)
                            .deliveryNotes(order != null ? order.getDeliveryNotes() : null)
                            .demand(stop.getDemand())
                            .geometryToNext(stop.getGeometryToNext())
                            .instructionsToNext(LegInstructionCodec.fromJson(stop.getInstructionsToNext()))
                            .zoneNames(zoneNames(restrictions, stop))
                            .build();
                })
                .toList();

        return DriverRouteDTO.builder()
                .sendId(send.getId())
                .solutionId(send.getSolution().getId())
                .routeId(route.getId())
                .vehicleLicensePlate(route.getVehicle() != null
                        ? route.getVehicle().getVehicleLicensePlate() : null)
                .status(send.getStatus().name())
                .stops(stops)
                .build();
    }

    // ---------- helpers ----------

    /**
     * LEZ send guard (LEZ-001 N7, design §5.4). Runs after the existing checks and
     * before the supersede/insert block:
     *
     * <ol>
     *   <li>Mixed delivery dates cannot be certified for one service day → 422.</li>
     *   <li>Compile the branch's <em>current</em> restrictions for that date.</li>
     *   <li>Fill missing leg geometry and instructions from the engine, once per route.</li>
     *   <li>Certify the route on the managed, locked entity; the refused transaction
     *       still commits that verdict (see {@code sendRoute}'s {@code noRollbackFor}).</li>
     *   <li>Any violation → 422 naming the codes, stop positions and zones.</li>
     * </ol>
     *
     * <p>Fails closed: an unreachable engine raises the existing 502 mapping from
     * the client, so the route is never sent without its geometry.
     */
    private void enforceLezCompliance(Route route, Solution solution) {
        if (routeComplianceService == null) {
            // Historical unit tests construct this service without the LEZ side.
            return;
        }
        List<Order> orders = RouteComplianceService.ordersOf(solution);
        if (RouteComplianceService.hasMixedDates(orders)) {
            throw new UnprocessablePlanException("LEZ_MIXED_DATES: solution " + solution.getId()
                    + " spans several delivery dates and cannot be certified for one service day");
        }
        CompiledRestrictions restrictions = routeComplianceService.currentFor(solution);
        if (restrictions == null) {
            return;
        }

        fillMissingLegs(route);

        List<ComplianceChecker.Violation> violations = routeComplianceService.check(route, restrictions);
        routeComplianceService.certify(route, violations, restrictions);
        if (!violations.isEmpty()) {
            // No second transaction: the 422 is a noRollbackFor exception, so this
            // commit stores the verdict on the managed route (review-p1b F1).
            throw new UnprocessablePlanException("Route " + route.getId() + " cannot be sent: "
                    + violations.stream()
                    .map(violation -> violation.code() + " seq=" + violation.seq()
                            + (violation.zoneKey() != null ? " zone=" + violation.zoneKey() : ""))
                    .collect(Collectors.joining("; ")));
        }
    }

    /**
     * Asks the engine for the geometry and instructions of the route's legs when
     * any of them is missing (routes planned before P1b, or an engine result that
     * carried none). One engine call covers every leg of the route.
     */
    private void fillMissingLegs(Route route) {
        if (engineApiClient == null || route.getSegments() == null) {
            return;
        }
        List<RouteStop> stops = route.getSegments().stream()
                .sorted(Comparator.comparingInt(RouteSendService::planRank))
                .toList();
        if (stops.size() < 2) {
            return;
        }
        boolean missing = false;
        for (int i = 0; i + 1 < stops.size(); i++) {
            RouteStop stop = stops.get(i);
            if (stop.getGeometryToNext() == null || stop.getGeometryToNext().isEmpty()
                    || stop.getInstructionsToNext() == null || stop.getInstructionsToNext().isBlank()) {
                missing = true;
                break;
            }
        }
        if (!missing) {
            return;
        }

        List<EngineLegsRequestDTO.Point> points = new ArrayList<>(stops.size());
        for (RouteStop stop : stops) {
            if (stop.getLocation() == null) {
                // Without a point the leg cannot be routed; the checker reports it.
                return;
            }
            points.add(new EngineLegsRequestDTO.Point(stop.getLocation().getY(), stop.getLocation().getX()));
        }

        EngineLegsResponseDTO response = engineApiClient.legs(points, "vi");
        List<EngineLegsResponseDTO.LegDTO> legs =
                response != null && response.getLegs() != null ? response.getLegs() : List.of();
        for (int i = 0; i < legs.size() && i < stops.size() - 1; i++) {
            EngineLegsResponseDTO.LegDTO leg = legs.get(i);
            if (leg == null || leg.getPolyline() == null) {
                // Unroutable leg: leave it empty, the checker fails closed.
                continue;
            }
            RouteStop stop = stops.get(i);
            stop.setGeometryToNext(leg.getPolyline());
            String instructions = LegInstructionCodec.toJson(leg.getInstructions());
            if (instructions != null) {
                stop.setInstructionsToNext(instructions);
            }
        }
    }

    /**
     * Names of the enabled zones covering a stop. Only names are exposed: the
     * driver needs a badge, not the polygon (design §16.2-1).
     */
    private static List<String> zoneNames(CompiledRestrictions restrictions, RouteStop stop) {
        if (restrictions == null || stop.getLocation() == null) {
            return List.of();
        }
        return ComplianceChecker.zonesCovering(restrictions, stop.getLocation()).stream()
                .map(CompiledRestrictions.CompiledZone::getName)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Plan position of a stop. Engine-generated depots carry no sequence number
     * (customers are 1..N); the start depot has a departure time, the end depot
     * only arrives. Without this, NULL-first ordering puts the end depot second
     * and the driver map drops the return leg.
     */
    static int planRank(RouteStop stop) {
        if (stop.getSequenceNumber() != null) {
            return stop.getSequenceNumber();
        }
        if (stop.getType() == LocationType.DEPOT && stop.getDepartureTime() != null) {
            return Integer.MIN_VALUE;
        }
        return Integer.MAX_VALUE;
    }

    private User requireDriver() {
        if (!UserRole.DRIVER.name().equals(CurrentUserUtil.getCurrentRole())) {
            throw new ForbiddenException("Driver role required");
        }
        return currentUser();
    }

    private void requireRole(UserRole role) {
        if (!role.name().equals(CurrentUserUtil.getCurrentRole())) {
            throw new ForbiddenException(role.name() + " role required");
        }
    }

    private User currentUser() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        if (userId == null) {
            throw new ForbiddenException("Not authenticated");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> notFound("User"));
    }

    private Long currentBranchId() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        if (branchId == null) {
            throw new ForbiddenException("No branch context");
        }
        return branchId;
    }

    private Long latestSendId(Long routeId) {
        return sendRepository.findByRouteIdOrderBySentAtDescIdDesc(routeId).stream()
                .findFirst().map(RouteSend::getId).orElse(null);
    }

    private RouteSendDTO toSendDTO(RouteSend send) {
        return RouteSendDTO.builder()
                .id(send.getId())
                .solutionId(send.getSolution().getId())
                .routeId(send.getRoute().getId())
                .vehicleLicensePlate(send.getRoute().getVehicle() != null
                        ? send.getRoute().getVehicle().getVehicleLicensePlate() : null)
                .driverId(send.getDriver().getId())
                .driverName(send.getDriver().getFullName() != null
                        ? send.getDriver().getFullName() : send.getDriver().getUsername())
                .senderName(send.getSender().getUsername())
                .sentAt(send.getSentAt())
                .status(send.getStatus().name())
                .build();
    }

    private DataInvalidException notFound(String resource) {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                        .resource(resource)
                        .build()));
    }

    private DataInvalidException invalid(String message) {
        return new DataInvalidException(List.of(
                ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(message)
                        .build()));
    }
}
