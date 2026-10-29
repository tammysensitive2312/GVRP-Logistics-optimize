package org.truong.gvrp_entry_api.service.lez;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.RouteGeometryDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionComplianceDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.compilation;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.encode;

/**
 * Tenant isolation and read side of the LEZ panel (LEZ-001 P1b, TI-T1).
 * DB-free: the repositories and the compliance service are Mockito substitutes.
 *
 * <p>Branch A asking for branch B's ids must behave exactly like asking for a
 * missing id: {@link LezNotFoundException} (404) and no read side effect.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceReadServiceTest {

    private static final Long BRANCH_A = 1L;
    private static final Long BRANCH_B = 2L;

    @Mock private SolutionRepository solutionRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteComplianceService routeComplianceService;

    private ComplianceReadService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceReadService(solutionRepository, routeRepository,
                routeComplianceService);
    }

    // ---------- fixtures ----------

    private Solution solution(Long id, Long branchId) {
        return Solution.builder()
                .id(id)
                .branch(Branch.builder().id(branchId).build())
                .routes(new ArrayList<>())
                .build();
    }

    private Route route(Long id, Solution solution) {
        return Route.builder()
                .id(id)
                .solution(solution)
                .vehicle(Vehicle.builder().id(7L).build())
                .routeOrder(1)
                .segments(new ArrayList<>())
                .build();
    }

    // ---------- TI-T1: another branch ----------

    @Test
    void tiT1_complianceOfAnotherBranchSolution_is404AndReadsNothing() {
        when(solutionRepository.findWithDetailsById(99L))
                .thenReturn(Optional.of(solution(99L, BRANCH_B)));

        assertThatThrownBy(() -> service.compliance(99L, BRANCH_A))
                .isInstanceOf(LezNotFoundException.class)
                .hasMessageContaining(AppConstant.SOLUTION);

        verifyNoInteractions(routeComplianceService);
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void tiT1_recheckOfAnotherBranchSolution_is404AndWritesNothing() {
        when(solutionRepository.findWithDetailsById(99L))
                .thenReturn(Optional.of(solution(99L, BRANCH_B)));

        assertThatThrownBy(() -> service.recheck(99L, BRANCH_A))
                .isInstanceOf(LezNotFoundException.class);

        verifyNoInteractions(routeComplianceService);
        verify(solutionRepository, never()).save(any());
        verify(routeRepository, never()).save(any());
    }

    @Test
    void tiT1_geometryOfAnotherBranchRoute_is404() {
        Solution other = solution(99L, BRANCH_B);
        when(routeRepository.findById(88L)).thenReturn(Optional.of(route(88L, other)));

        assertThatThrownBy(() -> service.geometry(88L, BRANCH_A))
                .isInstanceOf(LezNotFoundException.class)
                .hasMessageContaining(AppConstant.ROUTE);

        verifyNoInteractions(routeComplianceService);
    }

    @Test
    void tiT1_missingSolution_answersLikeAnotherBranch() {
        when(solutionRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compliance(99L, BRANCH_A))
                .isInstanceOf(LezNotFoundException.class)
                .hasMessageContaining(AppConstant.SOLUTION);
        verifyNoInteractions(routeComplianceService);
    }

    @Test
    void tiT1_missingBranchContext_is403() {
        assertThatThrownBy(() -> service.compliance(9L, null))
                .isInstanceOf(org.truong.gvrp_entry_api.exception.ForbiddenException.class);
        verifyNoInteractions(routeComplianceService);
    }

    // ---------- read side ----------

    @Test
    void compliance_ownBranch_reportsStaleAndUncheckedWithoutWriting() {
        Solution solution = solution(9L, BRANCH_A);
        Route stale = route(21L, solution);
        stale.setLezStatus(LezStatus.COMPLIANT);
        stale.setLezRulesetHash("old-hash");
        stale.setLezCheckedAt(LocalDateTime.of(2027, 3, 3, 9, 0));
        Route unchecked = route(22L, solution);
        unchecked.setRouteOrder(2);
        solution.getRoutes().addAll(List.of(stale, unchecked));

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution))
                .thenReturn(compilation("new-hash", null));

        SolutionComplianceDTO dto = service.compliance(9L, BRANCH_A);

        assertThat(dto.getSolutionId()).isEqualTo(9L);
        assertThat(dto.getRoutes()).hasSize(2);
        assertThat(dto.getRoutes().get(0).getRouteId()).isEqualTo(21L);
        assertThat(dto.getRoutes().get(0).getStatus()).isEqualTo(ComplianceReadService.STALE);
        assertThat(dto.getRoutes().get(0).getCheckedAt())
                .isEqualTo(LocalDateTime.of(2027, 3, 3, 9, 0));
        assertThat(dto.getRoutes().get(1).getStatus()).isEqualTo(LezStatus.UNCHECKED.name());

        verifyNoInteractions(routeRepository);
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void compliance_ownBranch_matchingHashKeepsTheStoredStatus() {
        Solution solution = solution(9L, BRANCH_A);
        Route compliant = route(21L, solution);
        compliant.setLezStatus(LezStatus.COMPLIANT);
        compliant.setLezRulesetHash("same-hash");
        solution.getRoutes().add(compliant);

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution))
                .thenReturn(compilation("same-hash", null));

        assertThat(service.compliance(9L, BRANCH_A).getRoutes().get(0).getStatus())
                .isEqualTo(LezStatus.COMPLIANT.name());
    }

    @Test
    void geometry_ownBranch_returnsOneLegWithItsInstructions() {
        Solution solution = solution(9L, BRANCH_A);
        Order order = Order.builder().id(11L).branch(solution.getBranch()).build();
        Route route = route(21L, solution);
        String polyline = encode(10.0, 106.0, 10.0, 106.020);
        route.getSegments().addAll(List.of(
                RouteStop.builder().type(LocationType.DEPOT).sequenceNumber(0)
                        .departureTime(LocalTime.of(8, 0)).geometryToNext(polyline)
                        .instructionsToNext("[{\"text\":\"Turn right\"}]").build(),
                RouteStop.builder().type(LocationType.ORDER).order(order).sequenceNumber(1)
                        .arrivalTime(LocalTime.of(8, 30)).build()));
        solution.getRoutes().add(route);

        when(routeRepository.findById(21L)).thenReturn(Optional.of(route));

        RouteGeometryDTO dto = service.geometry(21L, BRANCH_A);

        assertThat(dto.getRouteId()).isEqualTo(21L);
        assertThat(dto.getLegs()).hasSize(1);
        assertThat(dto.getLegs().get(0).getFromSeq()).isZero();
        assertThat(dto.getLegs().get(0).getToSeq()).isEqualTo(1);
        assertThat(dto.getLegs().get(0).getPolyline()).isEqualTo(polyline);
        assertThat(dto.getLegs().get(0).getInstructions()).hasSize(1);
        assertThat(dto.getLegs().get(0).getInstructions().get(0).getText())
                .isEqualTo("Turn right");

        verifyNoInteractions(routeComplianceService);
    }
}
