package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation and lock ordering for {@link RouteSendService#sendRoute} (SEC-018).
 */
@ExtendWith(MockitoExtension.class)
class RouteSendTenantIsolationTest {

    private static final Long BRANCH_A = 1L;
    private static final Long BRANCH_B = 2L;
    private static final Long PLANNER_ID = 1L;

    @Mock private RouteSendRepository sendRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private SolutionRepository solutionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private RouteSendService service;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void asPlannerA() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", PLANNER_ID);
        request.setAttribute("branchId", BRANCH_A);
        request.setAttribute("role", "PLANNER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User planner = User.builder()
                .id(PLANNER_ID)
                .branch(Branch.builder().id(BRANCH_A).build())
                .username("planner-a")
                .role(UserRole.PLANNER)
                .enabled(true)
                .build();
        when(userRepository.findById(PLANNER_ID)).thenReturn(Optional.of(planner));
    }

    private SendRouteRequest request(long solutionId, long driverId) {
        SendRouteRequest request = new SendRouteRequest();
        request.setSolutionId(solutionId);
        request.setDriverUserId(driverId);
        return request;
    }

    private Solution solution(long id, Long branchId, SolutionStatus status, long routeId) {
        Branch branch = Branch.builder().id(branchId).build();
        Solution solution = Solution.builder()
                .id(id)
                .branch(branch)
                .status(status)
                .type(SolutionType.ENGINE_GENERATED)
                .routes(new ArrayList<>())
                .build();
        Route route = Route.builder()
                .id(routeId)
                .solution(solution)
                .vehicle(Vehicle.builder().id(7L).build())
                .routeOrder(1)
                .segments(new ArrayList<>(List.of(
                        RouteStop.builder().type(LocationType.ORDER)
                                .order(Order.builder().id(11L).branch(branch).build())
                                .sequenceNumber(1)
                                .build())))
                .build();
        solution.getRoutes().add(route);
        return solution;
    }

    @Test
    void sendRoute_foreignSolution_matchesMissingSolutionAndNeverLocksRoute() {
        asPlannerA();

        Solution foreignSuccess = solution(90L, BRANCH_B, SolutionStatus.SUCCESS, 200L);
        when(solutionRepository.findWithDetailsById(90L)).thenReturn(Optional.of(foreignSuccess));
        ResourceNotFoundException foreignSuccessEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(200L, request(90L, 2L)));

        Solution foreignInfeasible = solution(91L, BRANCH_B, SolutionStatus.INFEASIBLE, 201L);
        when(solutionRepository.findWithDetailsById(91L)).thenReturn(Optional.of(foreignInfeasible));
        ResourceNotFoundException foreignInfeasibleEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(201L, request(91L, 2L)));

        when(solutionRepository.findWithDetailsById(999L)).thenReturn(Optional.empty());
        ResourceNotFoundException missingEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(200L, request(999L, 2L)));

        assertThat(foreignSuccessEx.getResource()).isEqualTo(AppConstant.SOLUTION);
        assertThat(foreignSuccessEx.getField()).isNull();

        assertThat(foreignSuccessEx).usingRecursiveComparison().isEqualTo(missingEx);
        assertThat(foreignInfeasibleEx).usingRecursiveComparison().isEqualTo(missingEx);

        verify(routeRepository, never()).findWithLockById(any());
        verify(sendRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendRoute_foreignRouteWithOwnSolution_answersNotFoundWithoutLockingRoute() {
        asPlannerA();

        Solution ownSolution = solution(9L, BRANCH_A, SolutionStatus.SUCCESS, 21L);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(ownSolution));

        ResourceNotFoundException foreignRouteEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(888L, request(9L, 2L)));

        assertThat(foreignRouteEx.getResource()).isEqualTo(AppConstant.ROUTE);
        assertThat(foreignRouteEx.getField()).isNull();

        verify(routeRepository, never()).findWithLockById(any());
        verify(sendRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendRoute_foreignDisabledNonDriverAndMissingDriver_returnIdenticalNotFound() {
        asPlannerA();

        Solution ownSolution = solution(9L, BRANCH_A, SolutionStatus.SUCCESS, 21L);
        Route ownRoute = ownSolution.getRoutes().get(0);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(ownSolution));
        when(routeRepository.findWithLockById(21L)).thenReturn(Optional.of(ownRoute));

        Branch branchA = ownSolution.getBranch();
        Branch branchB = Branch.builder().id(BRANCH_B).build();

        User foreignDriver = User.builder().id(30L).branch(branchB).role(UserRole.DRIVER).enabled(true).build();
        User disabledDriver = User.builder().id(31L).branch(branchA).role(UserRole.DRIVER).enabled(false).build();
        User plannerUser = User.builder().id(32L).branch(branchA).role(UserRole.PLANNER).enabled(true).build();

        when(userRepository.findById(30L)).thenReturn(Optional.of(foreignDriver));
        ResourceNotFoundException foreignDriverEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(21L, request(9L, 30L)));

        when(userRepository.findById(31L)).thenReturn(Optional.of(disabledDriver));
        ResourceNotFoundException disabledDriverEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(21L, request(9L, 31L)));

        when(userRepository.findById(32L)).thenReturn(Optional.of(plannerUser));
        ResourceNotFoundException nonDriverEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(21L, request(9L, 32L)));

        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        ResourceNotFoundException missingDriverEx = assertThrows(ResourceNotFoundException.class,
                () -> service.sendRoute(21L, request(9L, 999L)));

        assertThat(foreignDriverEx.getResource()).isEqualTo("Driver");
        assertThat(foreignDriverEx.getField()).isNull();

        assertThat(foreignDriverEx).usingRecursiveComparison().isEqualTo(missingDriverEx);
        assertThat(disabledDriverEx).usingRecursiveComparison().isEqualTo(missingDriverEx);
        assertThat(nonDriverEx).usingRecursiveComparison().isEqualTo(missingDriverEx);

        verify(sendRepository, never()).saveAndFlush(any());
    }
}
