package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.dto.response.DriverRouteDTO;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SendStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for dispatcher sends and driver-scoped reads.
 */
@ExtendWith(MockitoExtension.class)
class RouteSendServiceTest {

    @Mock
    private RouteSendRepository sendRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private RouteSendService service;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- fixtures ----------

    private void asUser(Long userId, Long branchId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        request.setAttribute("branchId", branchId);
        request.setAttribute("role", role);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Fixture fixture() {
        Branch branch = Branch.builder().id(1L).build();

        User sender = User.builder().id(1L).branch(branch)
                .username("boss").role(UserRole.PLANNER).enabled(true).build();
        User driver = User.builder().id(2L).branch(branch)
                .username("taylai").fullName("Tai Lai").phone("0901")
                .role(UserRole.DRIVER).enabled(true).build();

        OptimizationJob job = OptimizationJob.builder().id(5L).branch(branch).build();
        Solution solution = Solution.builder().id(9L).job(job).branch(branch)
                .status(SolutionStatus.SUCCESS).type(SolutionType.ENGINE_GENERATED)
                .unservedOrders(0).routes(new ArrayList<>()).build();

        Order order = Order.builder().id(11L).branch(branch)
                .customerName("Khach").customerPhone("0902").deliveryNotes("Goi truoc")
                .address("Hanoi").demand(BigDecimal.TEN).build();

        Vehicle vehicle = Vehicle.builder().id(7L).vehicleLicensePlate("29A-1").build();

        RouteStop depot = RouteStop.builder().type(LocationType.DEPOT).sequenceNumber(0)
                .departureTime(LocalTime.of(8, 0)).build();
        RouteStop stop = RouteStop.builder().type(LocationType.ORDER).order(order)
                .sequenceNumber(1).arrivalTime(LocalTime.of(8, 30)).build();
        Route route = Route.builder().id(21L).solution(solution).vehicle(vehicle)
                .routeOrder(1).segments(new ArrayList<>(List.of(depot, stop))).build();
        solution.getRoutes().add(route);

        return new Fixture(branch, sender, driver, job, solution, order, vehicle, route);
    }

    private SendRouteRequest sendRequest() {
        SendRouteRequest request = new SendRouteRequest();
        request.setDriverUserId(2L);
        request.setSolutionId(9L);
        return request;
    }

    // ---------- send ----------

    @Test
    void sendRoute_recordsCurrentAndSupersedesSiblings() {
        Fixture f = fixture();
        asUser(1L, 1L, "PLANNER");
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(f.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.solution()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(f.sender()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(f.driver()));
        RouteSend old = RouteSend.builder().id(100L).status(SendStatus.CURRENT).build();
        when(sendRepository.findByRouteIdAndStatusOrderBySentAtDescIdDesc(21L, SendStatus.CURRENT))
                .thenReturn(new ArrayList<>(List.of(old)))
                .thenReturn(List.of(RouteSend.builder().status(SendStatus.CURRENT).build()));
        when(sendRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RouteSendDTO dto = service.sendRoute(21L, sendRequest());

        assertEquals(SendStatus.SUPERSEDED, old.getStatus());
        ArgumentCaptor<RouteSend> captor = ArgumentCaptor.forClass(RouteSend.class);
        verify(sendRepository).saveAndFlush(captor.capture());
        assertEquals(SendStatus.CURRENT, captor.getValue().getStatus());
        assertEquals(2L, captor.getValue().getDriver().getId());
        assertEquals(9L, dto.getSolutionId());
    }

    @Test
    void sendRoute_nonPlanner_forbidden() {
        fixture();
        asUser(2L, 1L, "DRIVER");

        assertThrows(ForbiddenException.class, () -> service.sendRoute(21L, sendRequest()));
    }

    @Test
    void sendRoute_emptyRoute_rejects() {
        Fixture f = fixture();
        asUser(1L, 1L, "PLANNER");
        f.route().getSegments().clear();
        when(userRepository.findById(1L)).thenReturn(Optional.of(f.sender()));
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(f.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.solution()));

        assertThrows(DataInvalidException.class, () -> service.sendRoute(21L, sendRequest()));
    }

    @Test
    void sendRoute_infeasibleSolution_rejects() {
        Fixture f = fixture();
        asUser(1L, 1L, "PLANNER");
        f.solution().setStatus(SolutionStatus.INFEASIBLE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(f.sender()));
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(f.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.solution()));

        assertThrows(DataInvalidException.class, () -> service.sendRoute(21L, sendRequest()));
    }

    @Test
    void sendRoute_wrongBranchDriver_rejects() {
        Fixture f = fixture();
        asUser(1L, 1L, "PLANNER");
        Branch other = Branch.builder().id(2L).build();
        f.driver().setBranch(other);
        when(userRepository.findById(1L)).thenReturn(Optional.of(f.sender()));
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(f.route()));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(f.solution()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(f.driver()));

        assertThrows(DataInvalidException.class, () -> service.sendRoute(21L, sendRequest()));
    }

    // ---------- driver reads ----------

    @Test
    void mySends_currentFirst() {
        Fixture f = fixture();
        asUser(2L, 1L, "DRIVER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(f.driver()));
        RouteSend current = RouteSend.builder().id(1L).solution(f.solution())
                .route(f.route()).driver(f.driver()).sender(f.sender())
                .status(SendStatus.CURRENT).sentAt(java.time.LocalDateTime.now()).build();
        RouteSend old = RouteSend.builder().id(0L).solution(f.solution())
                .route(f.route()).driver(f.driver()).sender(f.sender())
                .status(SendStatus.SUPERSEDED).sentAt(java.time.LocalDateTime.now()).build();
        when(sendRepository.findByDriverIdAndStatusOrderBySentAtDescIdDesc(2L, SendStatus.CURRENT))
                .thenReturn(List.of(current));
        when(sendRepository.findByDriverIdAndStatusOrderBySentAtDescIdDesc(2L, SendStatus.SUPERSEDED))
                .thenReturn(List.of(old));

        List<RouteSendDTO> sends = service.mySends();

        assertEquals(List.of(1L, 0L), sends.stream().map(RouteSendDTO::getId).toList());
    }

    @Test
    void sendDetail_foreignSend_forbidden() {
        Fixture f = fixture();
        asUser(2L, 1L, "DRIVER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(f.driver()));
        User other = User.builder().id(3L).role(UserRole.DRIVER).enabled(true).build();
        RouteSend send = RouteSend.builder().id(9L).driver(other).build();
        when(sendRepository.findById(9L)).thenReturn(Optional.of(send));

        assertThrows(ForbiddenException.class, () -> service.sendDetail(9L));
    }

    @Test
    void sendDetail_includesCustomerInfo() {
        Fixture f = fixture();
        asUser(2L, 1L, "DRIVER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(f.driver()));
        RouteSend send = RouteSend.builder().id(9L).solution(f.solution())
                .route(f.route()).driver(f.driver()).status(SendStatus.CURRENT).build();
        when(sendRepository.findById(9L)).thenReturn(Optional.of(send));
        when(orderRepository.findAllById(any())).thenReturn(List.of(f.order()));

        DriverRouteDTO detail = service.sendDetail(9L);

        assertEquals(2, detail.getStops().size());
        assertEquals("Khach", detail.getStops().get(1).getCustomerName());
        assertEquals("0902", detail.getStops().get(1).getCustomerPhone());
        assertEquals("Goi truoc", detail.getStops().get(1).getDeliveryNotes());
    }

    private record Fixture(
            Branch branch, User sender, User driver, OptimizationJob job,
            Solution solution, Order order, Vehicle vehicle, Route route) {
    }
}
