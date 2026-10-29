package org.truong.gvrp_entry_api.service.lez;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.SolutionComplianceDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.repository.RouteRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1c additions to the compliance read side (LEZ-001, CR-T1): the zone names of
 * the same compilation that produced the hash, and the LEZ-only unassigned rows.
 * A new class, so {@code ComplianceReadServiceTest} keeps its accepted assertions.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceReadAdditionsTest {

    private static final Long BRANCH_A = 1L;

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

    private static Solution solution(Long id) {
        return Solution.builder()
                .id(id)
                .branch(Branch.builder().id(BRANCH_A).build())
                .routes(new ArrayList<>())
                .unassignedOrders(new ArrayList<>())
                .build();
    }

    private static CompiledRestrictions.CompiledZone zone(String key, Long branchZoneId, String name) {
        CompiledRestrictions.CompiledZone zone = new CompiledRestrictions.CompiledZone();
        zone.setKey(key);
        zone.setBranchZoneId(branchZoneId);
        zone.setName(name);
        zone.setGeometry(LezFixtures.geoJson(LezFixtures.square(106.0, 10.0, 106.1, 10.1)));
        return zone;
    }

    private static UnassignedOrder unassigned(Long id, Long orderId, String orderCode,
                                              String reasonCode, String reason) {
        return UnassignedOrder.builder()
                .id(id)
                .order(Order.builder().id(orderId).orderCode(orderCode).build())
                .reasonCode(reasonCode)
                .reason(reason)
                .build();
    }

    // ---------- CR-T1 ----------

    @Test
    void crT1_complianceCarriesTheCompiledZones() {
        Solution solution = solution(9L);
        CompiledRestrictions compiled = LezFixtures.compilation("hash-1", zone("Z17", 17L, "Hanoi LEZ"));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution)).thenReturn(compiled);

        SolutionComplianceDTO dto = service.compliance(9L, BRANCH_A);

        assertThat(dto.getZones()).hasSize(1);
        assertThat(dto.getZones().get(0).getKey()).isEqualTo("Z17");
        assertThat(dto.getZones().get(0).getBranchZoneId()).isEqualTo(17L);
        assertThat(dto.getZones().get(0).getName()).isEqualTo("Hanoi LEZ");
    }

    @Test
    void crT1_complianceWithoutCompilationHasNoZones() {
        Solution solution = solution(9L);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution)).thenReturn(null);

        SolutionComplianceDTO dto = service.compliance(9L, BRANCH_A);

        assertThat(dto.getZones()).isEmpty();
    }

    @Test
    void crT1_onlyLezUnassignedRowsAreListed() {
        Solution solution = solution(9L);
        solution.getUnassignedOrders().addAll(List.of(
                unassigned(2L, 12L, "ORD-12", "CAPACITY_EXCEEDED", "Too heavy"),
                unassigned(1L, 11L, "ORD-11",
                        RouteComplianceService.LEZ_NO_COMPLIANT_VEHICLE, "No compliant vehicle")));
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution)).thenReturn(LezFixtures.noZones("hash-2"));

        SolutionComplianceDTO dto = service.compliance(9L, BRANCH_A);

        assertThat(dto.getLezUnassigned()).hasSize(1);
        SolutionComplianceDTO.LezUnassigned row = dto.getLezUnassigned().get(0);
        assertThat(row.getOrderId()).isEqualTo(11L);
        assertThat(row.getOrderCode()).isEqualTo("ORD-11");
        assertThat(row.getReasonCode()).isEqualTo(RouteComplianceService.LEZ_NO_COMPLIANT_VEHICLE);
        assertThat(row.getReason()).isEqualTo("No compliant vehicle");
    }

    @Test
    void crT1_recheckReturnsZonesAndLezUnassigned() {
        Solution solution = solution(9L);
        Route route = Route.builder()
                .id(21L)
                .solution(solution)
                .vehicle(Vehicle.builder().id(7L).build())
                .routeOrder(1)
                .segments(new ArrayList<>())
                .build();
        solution.getRoutes().add(route);
        solution.getUnassignedOrders().add(unassigned(1L, 11L, "ORD-11",
                RouteComplianceService.LEZ_NO_COMPLIANT_VEHICLE, "No compliant vehicle"));
        CompiledRestrictions compiled = LezFixtures.compilation("hash-3", zone("Z17", 17L, "Hanoi LEZ"));

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(routeComplianceService.currentFor(solution)).thenReturn(compiled);
        when(routeComplianceService.check(route, compiled)).thenReturn(List.of());

        SolutionComplianceDTO dto = service.recheck(9L, BRANCH_A);

        assertThat(dto.getZones()).extracting(SolutionComplianceDTO.ZoneRef::getName)
                .containsExactly("Hanoi LEZ");
        assertThat(dto.getLezUnassigned()).extracting(SolutionComplianceDTO.LezUnassigned::getOrderCode)
                .containsExactly("ORD-11");
        verify(routeComplianceService).certify(route, List.of(), compiled);
    }
}
