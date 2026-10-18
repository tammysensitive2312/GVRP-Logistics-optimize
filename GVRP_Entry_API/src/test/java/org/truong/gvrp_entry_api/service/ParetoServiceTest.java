package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.ParetoCandidateDTO;
import org.truong.gvrp_entry_api.dto.response.ParetoFrontierDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.ParetoCandidate;
import org.truong.gvrp_entry_api.entity.Route;
import org.truong.gvrp_entry_api.entity.RouteStop;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.truong.gvrp_entry_api.entity.enums.ParetoFrontierStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.ParetoCandidateRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pareto frontier read (PAR-007) and apply (PAR-008) — N-T4..N-T11.
 * DB-free Mockito; the injected ObjectMapper is a real instance so plan_json
 * round-trips exactly as in production.
 */
@ExtendWith(MockitoExtension.class)
class ParetoServiceTest {

    @Mock private SolutionRepository solutionRepository;
    @Mock private ParetoCandidateRepository paretoCandidateRepository;
    @Mock private RouteCustomizationService routeCustomizationService;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private EnginePlanMaterializer enginePlanMaterializer;
    @Mock private SolutionMapper solutionMapper;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ParetoService service;

    // ---------- fixtures ----------

    private Branch branch(long id) {
        return Branch.builder().id(id).build();
    }

    private OptimizationJob job(long id, Branch branch) {
        return OptimizationJob.builder().id(id).branch(branch).build();
    }

    private Route routeWithOrders(Long... orderIds) {
        Route route = Route.builder().routeOrder(1).segments(new ArrayList<>()).build();
        int sequence = 0;
        for (Long orderId : orderIds) {
            route.getSegments().add(RouteStop.builder()
                    .type(LocationType.ORDER)
                    .sequenceNumber(sequence++)
                    .order(Order.builder().id(orderId).build())
                    .build());
        }
        return route;
    }

    /** plan_json as read/written by the plain ObjectMapper injected in this test. */
    private String planJson(Long... orderIds) {
        StringBuilder stops = new StringBuilder();
        for (int i = 0; i < orderIds.length; i++) {
            if (i > 0) {
                stops.append(",");
            }
            stops.append("{\"type\":\"ORDER\",\"sequenceNumber\":").append(i + 1)
                    .append(",\"orderId\":").append(orderIds[i]).append("}");
        }
        return "{\"routes\":[{\"vehicleId\":7,\"stops\":[" + stops + "]}],\"unassignedOrders\":[]}";
    }

    private ParetoCandidate candidate(long id, int index, ParetoFrontierStatus status, String planJson) {
        return ParetoCandidate.builder()
                .id(id)
                .candidateIndex(index)
                .label("PARETO_K" + index + "_OF_4")
                .frontierStatus(status)
                .planJson(planJson)
                .build();
    }

    private static void assertNotFound(DataInvalidException ex) {
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getErrors().get(0).getCode());
    }

    // ---------- N-T4: tenant isolation on read ----------

    @Test
    void getFrontier_otherBranch_notFound() {
        Solution foreign = Solution.builder().id(9L).branch(branch(1L)).build();
        when(solutionRepository.findById(9L)).thenReturn(Optional.of(foreign));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.getFrontier(9L, 2L));
        assertNotFound(ex);
        verifyNoInteractions(paretoCandidateRepository);
    }

    // ---------- N-T5: non-Pareto job ----------

    @Test
    void getFrontier_nonPareto_empty() {
        Branch branch = branch(1L);
        Solution root = Solution.builder().id(9L).branch(branch).job(job(5L, branch)).build();
        when(solutionRepository.findById(9L)).thenReturn(Optional.of(root));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(9L);
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(root));
        when(paretoCandidateRepository.findByJobIdAndBranchIdOrderByCandidateIndexAsc(5L, 1L))
                .thenReturn(List.of());

        ParetoFrontierDTO dto = service.getFrontier(9L, 1L);

        assertEquals(5L, dto.getJobId());
        assertEquals(9L, dto.getSolutionId());
        assertEquals(9L, dto.getLatestSolutionId());
        assertTrue(dto.getCandidates().isEmpty());
    }

    // ---------- N-T6: applicability reasons ----------

    @Test
    void getFrontier_applicability() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        Solution root = Solution.builder().id(9L).branch(branch).job(job).build();

        ParetoCandidate active = candidate(100L, 0, ParetoFrontierStatus.FRONTIER, planJson(11L));
        Solution latest = Solution.builder()
                .id(10L).branch(branch).parentSolution(root).paretoCandidate(active)
                .routes(new ArrayList<>(List.of(routeWithOrders(11L))))
                .build();

        when(solutionRepository.findById(9L)).thenReturn(Optional.of(root));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(10L);
        when(solutionRepository.findWithDetailsById(10L)).thenReturn(Optional.of(latest));
        when(paretoCandidateRepository.findByJobIdAndBranchIdOrderByCandidateIndexAsc(5L, 1L))
                .thenReturn(List.of(
                        candidate(100L, 0, ParetoFrontierStatus.FRONTIER, planJson(11L)),
                        candidate(101L, 1, ParetoFrontierStatus.FRONTIER, planJson(11L)),
                        candidate(102L, 2, ParetoFrontierStatus.FRONTIER, planJson(12L)),
                        candidate(103L, 3, ParetoFrontierStatus.DOMINATED, null)));

        ParetoFrontierDTO dto = service.getFrontier(9L, 1L);

        assertEquals(100L, dto.getActiveCandidateId());
        assertEquals(
                Arrays.asList(ParetoService.ALREADY_ACTIVE, null, ParetoService.ORDER_SET_DIFFERS,
                        ParetoService.NOT_ON_FRONTIER),
                dto.getCandidates().stream().map(ParetoCandidateDTO::getNotApplicableReason).toList());
        assertEquals(
                List.of(false, true, false, false),
                dto.getCandidates().stream().map(ParetoCandidateDTO::getApplicable).toList());
    }

    // ---------- N-T7: apply creates a PARETO_ALTERNATIVE revision ----------

    @Test
    void apply_createsParetoAlternativeRevision() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        Solution base = Solution.builder()
                .id(9L).branch(branch).job(job)
                .type(SolutionType.ENGINE_GENERATED).status(SolutionStatus.SUCCESS)
                .unservedOrders(0)
                .routes(new ArrayList<>(List.of(routeWithOrders(11L))))
                .build();

        ParetoCandidate candidate = ParetoCandidate.builder()
                .id(100L).job(job).branch(branch).candidateIndex(0).label("PARETO_K0_OF_4")
                .costWeight(BigDecimal.valueOf(0.0001)).co2Weight(BigDecimal.valueOf(0.9999))
                .totalCost(BigDecimal.valueOf(1_000_000)).totalDistance(BigDecimal.valueOf(85.2))
                .totalCO2(BigDecimal.valueOf(14.1)).totalTime(BigDecimal.valueOf(6.3))
                .totalVehiclesUsed(1).servedOrders(1).unservedOrders(0)
                .frontierStatus(ParetoFrontierStatus.FRONTIER).engineSelected(true)
                .planJson(planJson(11L))
                .build();

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(candidate));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(9L);

        Order order11 = Order.builder().id(11L).branch(branch).build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(order11));
        Vehicle vehicle = Vehicle.builder().id(7L)
                .fleet(Fleet.builder().id(3L).branch(branch).build()).build();
        when(vehicleRepository.findAllById(any())).thenReturn(List.of(vehicle));

        when(enginePlanMaterializer.buildRoute(any(), any(), any(), any())).thenAnswer(inv -> {
            Solution solution = inv.getArgument(0);
            return Route.builder().solution(solution).routeOrder(1).segments(new ArrayList<>()).build();
        });
        when(enginePlanMaterializer.buildStop(any(), any(), any())).thenReturn(List.of(RouteStop.builder().build()));
        when(solutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(solutionMapper.toDTO(any())).thenReturn(SolutionDetailResponseDTO.builder().id(50L).build());

        SolutionDetailResponseDTO dto = service.apply(9L, 100L, 1L);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionRepository).save(captor.capture());
        Solution revision = captor.getValue();
        assertEquals(SolutionType.PARETO_ALTERNATIVE, revision.getType());
        assertEquals(9L, revision.getParentSolution().getId());
        assertNull(revision.getJob());
        assertEquals(100L, revision.getParetoCandidate().getId());
        assertEquals(1, revision.getRoutes().size());
        assertEquals(50L, dto.getId());

        // No order status changes: the transition mechanism is never invoked.
        verify(orderRepository, never()).updateStatusByIds(any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ---------- N-T8: stale base ----------

    @Test
    void apply_staleBase_409() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        Solution base = Solution.builder().id(9L).branch(branch).job(job).build();
        ParetoCandidate candidate = ParetoCandidate.builder()
                .id(100L).job(job).branch(branch)
                .frontierStatus(ParetoFrontierStatus.FRONTIER).planJson(planJson(11L)).build();

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(candidate));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(10L);

        RevisionConflictException ex = assertThrows(RevisionConflictException.class,
                () -> service.apply(9L, 100L, 1L));
        assertEquals(10L, ex.getLatestRevisionId());
        verify(solutionRepository, never()).save(any());
    }

    // ---------- N-T9: 422 cases ----------

    @Test
    void apply_notFrontier_422() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        Solution base = Solution.builder().id(9L).branch(branch).job(job).build();
        ParetoCandidate candidate = ParetoCandidate.builder()
                .id(100L).job(job).branch(branch)
                .frontierStatus(ParetoFrontierStatus.DOMINATED).planJson(null).build();

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(candidate));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(9L);

        assertThrows(UnprocessablePlanException.class, () -> service.apply(9L, 100L, 1L));
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void apply_orderSetDiffers_422() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        Solution base = Solution.builder()
                .id(9L).branch(branch).job(job)
                .routes(new ArrayList<>(List.of(routeWithOrders(11L))))
                .build();
        ParetoCandidate candidate = ParetoCandidate.builder()
                .id(100L).job(job).branch(branch)
                .frontierStatus(ParetoFrontierStatus.FRONTIER).planJson(planJson(12L)).build();

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(candidate));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(9L);

        assertThrows(UnprocessablePlanException.class, () -> service.apply(9L, 100L, 1L));
        verify(solutionRepository, never()).save(any());
    }

    // ---------- N-T10: tenant isolation on apply ----------

    @Test
    void apply_otherBranchSolution_404() {
        Branch foreign = branch(1L);
        Solution base = Solution.builder().id(9L).branch(foreign).build();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.apply(9L, 100L, 2L));
        assertNotFound(ex);
        verifyNoInteractions(paretoCandidateRepository);
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void apply_candidateOfOtherJob_404() {
        Branch branch = branch(1L);
        Solution base = Solution.builder().id(9L).branch(branch).job(job(5L, branch)).build();
        OptimizationJob otherJob = job(6L, branch);
        ParetoCandidate other = ParetoCandidate.builder()
                .id(100L).job(otherJob).branch(branch)
                .frontierStatus(ParetoFrontierStatus.FRONTIER).planJson(planJson(11L)).build();

        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(other));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.apply(9L, 100L, 1L));
        assertNotFound(ex);
        verify(solutionRepository, never()).save(any());
    }

    @Test
    void apply_candidateOfOtherBranch_404() {
        Branch branch = branch(1L);
        Solution base = Solution.builder().id(9L).branch(branch).job(job(5L, branch)).build();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.empty());

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.apply(9L, 100L, 1L));
        assertNotFound(ex);
        verify(solutionRepository, never()).save(any());
    }

    // ---------- N-T11: already active ----------

    @Test
    void apply_alreadyActive_returnsBaseWithoutSave() {
        Branch branch = branch(1L);
        OptimizationJob job = job(5L, branch);
        ParetoCandidate candidate = ParetoCandidate.builder()
                .id(100L).job(job).branch(branch)
                .frontierStatus(ParetoFrontierStatus.FRONTIER).planJson(planJson(11L)).build();
        Solution base = Solution.builder()
                .id(9L).branch(branch).job(job)
                .paretoCandidate(candidate)
                .build();

        SolutionDetailResponseDTO dto = SolutionDetailResponseDTO.builder()
                .id(9L).paretoCandidateId(100L).build();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(base));
        when(paretoCandidateRepository.findByIdAndBranchId(100L, 1L)).thenReturn(Optional.of(candidate));
        when(routeCustomizationService.latestRevisionId(9L, 1L)).thenReturn(9L);
        when(solutionMapper.toDTO(base)).thenReturn(dto);

        assertSame(dto, service.apply(9L, 100L, 1L));
        verify(solutionRepository, never()).save(any());
    }
}
