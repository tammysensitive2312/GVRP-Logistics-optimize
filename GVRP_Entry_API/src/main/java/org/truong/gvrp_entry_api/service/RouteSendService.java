package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.dto.response.DriverDTO;
import org.truong.gvrp_entry_api.dto.response.DriverRouteDTO;
import org.truong.gvrp_entry_api.dto.response.DriverStopDTO;
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
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

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

    /** Send a route revision to a driver; supersedes prior sends of the route. */
    @Transactional
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
