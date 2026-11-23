package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.truong.gvrp_entry_api.dto.request.RoutePlanningRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationConfigMapper;
import org.truong.gvrp_entry_api.mapper.OptimizationJobMapper;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.service.integration.external_api.EngineApiClient;
import org.truong.gvrp_entry_api.service.lez.RestrictionCompiler;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Resilience tests for {@link OptimizationJobService} (R-1, R-2, R-3).
 */
@ExtendWith(MockitoExtension.class)
class OptimizationJobResilienceTest {

    private static final Long BRANCH_A = 1L;
    private static final Long USER_A = 10L;

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepotMapper depotMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private VehicleMapper vehicleMapper;
    @Mock private VehicleTypeMapper vehicleTypeMapper;
    @Mock private OptimizationJobMapper jobMapper;
    @Mock private OptimizationConfigMapper optimizationConfigMapper;
    @Mock private EngineApiClient engineApiClient;
    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;

    private OptimizationJobService service;
    private Branch branchA;

    @BeforeEach
    void setUp() {
        branchA = Branch.builder().id(BRANCH_A).name("Branch A").build();
        service = new OptimizationJobService(jobRepository, orderRepository, vehicleRepository,
                branchRepository, userRepository, depotMapper, orderMapper, vehicleMapper,
                vehicleTypeMapper, jobMapper, new ObjectMapper().findAndRegisterModules(),
                optimizationConfigMapper, engineApiClient,
                new RestrictionCompiler(zoneRepository, zoneRuleRepository, ruleTemplateRepository,
                        permitRepository));
    }

    @AfterEach
    void cleanUpSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submitJob_countsPendingAndProcessingAgainstConcurrencyLimit() {
        List<OptimizationJobStatus> expectedStatuses = List.of(
                OptimizationJobStatus.PENDING, OptimizationJobStatus.PROCESSING);
        when(jobRepository.countByBranchIdAndStatusIn(BRANCH_A, expectedStatuses))
                .thenReturn(Long.valueOf(AppConstant.maxConcurrentJobs));

        RoutePlanningRequest request = new RoutePlanningRequest();
        request.setOrderIds(List.of(1L));
        request.setVehicleIds(List.of(1L));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.submitJob(request, BRANCH_A, USER_A));

        assertThat(ex.getErrors()).hasSize(1);
        assertThat(ex.getErrors().get(0).getCode()).isEqualTo(ErrorCode.JOB_LIMIT_EXCEEDED.getCode());
        verify(jobRepository).countByBranchIdAndStatusIn(BRANCH_A, expectedStatuses);
        verifyNoInteractions(orderRepository, vehicleRepository, engineApiClient);
    }

    @Test
    void getJobHistory_rejectsOutOfBoundsLimitAndAllowsValidBounds() {
        for (int badLimit : List.of(0, -1, AppConstant.MAX_JOB_HISTORY_LIMIT + 1)) {
            DataInvalidException ex = assertThrows(DataInvalidException.class,
                    () -> service.getJobHistory(BRANCH_A, badLimit));
            ErrorDetail detail = ex.getErrors().get(0);
            assertThat(detail.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
            assertThat(detail.getField()).isEqualTo("limit");
        }
        verify(jobRepository, never()).findJobHistory(any(), any());

        when(jobRepository.findJobHistory(eq(BRANCH_A), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.getJobHistory(BRANCH_A, 1)).isEmpty();
        assertThat(service.getJobHistory(BRANCH_A, AppConstant.MAX_JOB_HISTORY_LIMIT)).isEmpty();
    }

    @Test
    void cancelJobs_dispatchesHttpCancelOnlyAfterTransactionCommit() {
        OptimizationJob job1 = OptimizationJob.builder()
                .id(101L).branch(branchA).status(OptimizationJobStatus.PROCESSING).build();
        OptimizationJob job2 = OptimizationJob.builder()
                .id(102L).branch(branchA).status(OptimizationJobStatus.PROCESSING).build();
        when(jobRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(job1, job2));

        TransactionSynchronizationManager.initSynchronization();
        service.cancelJobs(List.of(101L, 102L), BRANCH_A);

        verify(jobRepository).saveAll(List.of(job1, job2));
        verifyNoInteractions(engineApiClient);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(engineApiClient).requestCancel(101L);
        verify(engineApiClient).requestCancel(102L);
    }

    @Test
    void cancelJobs_doesNotDispatchHttpCancelWhenTransactionRollsBack() {
        OptimizationJob job = OptimizationJob.builder()
                .id(101L).branch(branchA).status(OptimizationJobStatus.PROCESSING).build();
        when(jobRepository.findAllById(List.of(101L))).thenReturn(List.of(job));

        TransactionSynchronizationManager.initSynchronization();
        service.cancelJobs(List.of(101L), BRANCH_A);

        verifyNoInteractions(engineApiClient);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verifyNoInteractions(engineApiClient);
    }

    @Test
    void getJobProgress_hasNoTransactionalAnnotationAndChecksBranchBeforeEngineCall() throws Exception {
        Method method = OptimizationJobService.class.getMethod("getJobProgress", Long.class, Long.class);
        assertNull(method.getAnnotation(Transactional.class),
                "getJobProgress must not hold a DB connection across an HTTP call to Engine");

        when(jobRepository.existsByIdAndBranchId(55L, BRANCH_A)).thenReturn(true);
        when(engineApiClient.getProgress(55L)).thenReturn(Map.of("progress", 50));

        assertThat(service.getJobProgress(55L, BRANCH_A)).containsEntry("progress", 50);

        when(jobRepository.existsByIdAndBranchId(99L, BRANCH_A)).thenReturn(false);
        DataInvalidException foreignEx = assertThrows(DataInvalidException.class,
                () -> service.getJobProgress(99L, BRANCH_A));
        assertThat(foreignEx.getErrors().get(0).getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());

        DataInvalidException nullBranchEx = assertThrows(DataInvalidException.class,
                () -> service.getJobProgress(55L, null));
        assertThat(nullBranchEx.getErrors().get(0).getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(engineApiClient, never()).getProgress(99L);
    }
}
