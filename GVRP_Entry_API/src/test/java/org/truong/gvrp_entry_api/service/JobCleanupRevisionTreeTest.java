package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-LOW-001 LOW-T7, LOW-T8 (audit lead #14): the retention sweep deletes
 * the whole revision tree of an expired job, deepest revision first, and one
 * job that cannot be deleted does not stop the sweep for later jobs.
 *
 * <p>Unit tests over mocks: the ON DELETE RESTRICT foreign keys
 * ({@code fk_solution_parent}, {@code fk_send_solution}) are not exercised
 * here; the tests prove the call sequence that satisfies them.
 */
@ExtendWith(MockitoExtension.class)
class JobCleanupRevisionTreeTest {

    @Mock private OptimizationJobRepository jobRepository;
    @Mock private SolutionRepository solutionRepository;
    @Mock private RouteSendRepository routeSendRepository;

    @InjectMocks
    private JobCleanupService cleanupService;

    private static Solution solution(long id) {
        Solution s = mock(Solution.class);
        lenient().when(s.getId()).thenReturn(id);
        return s;
    }

    private void noExpired(OptimizationJobStatus status) {
        when(jobRepository.findExpiredIds(eq(status), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
    }

    // ---------- LOW-T7 ----------

    @Test
    void nestedRevisionChain_deletesWholeTreeDeepestFirst() {
        // job 1 → root 10 → R1 11 → R2 12; R2 was dispatched (one route send).
        OptimizationJob job = mock(OptimizationJob.class);
        Solution root = solution(10L);
        Solution r1 = solution(11L);
        Solution r2 = solution(12L);
        RouteSend sendOfR2 = mock(RouteSend.class);

        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.COMPLETED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L), List.of());
        noExpired(OptimizationJobStatus.CANCELLED);
        noExpired(OptimizationJobStatus.FAILED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(1L)).thenReturn(Optional.of(root));
        when(solutionRepository.findByParentSolutionId(10L)).thenReturn(List.of(r1));
        when(solutionRepository.findByParentSolutionId(11L)).thenReturn(List.of(r2));
        when(solutionRepository.findByParentSolutionId(12L)).thenReturn(List.of());
        when(routeSendRepository.findBySolutionId(10L)).thenReturn(List.of());
        when(routeSendRepository.findBySolutionId(11L)).thenReturn(List.of());
        when(routeSendRepository.findBySolutionId(12L)).thenReturn(List.of(sendOfR2));

        JobCleanupService.CleanupCounts counts = cleanupService.cleanOldJobs();

        assertThat(counts.completed()).isEqualTo(1);
        InOrder order = inOrder(routeSendRepository, solutionRepository, jobRepository);
        order.verify(routeSendRepository).deleteAll(List.of(sendOfR2));
        order.verify(solutionRepository).deleteAll(List.of(r2, r1));
        order.verify(solutionRepository).delete(root);
        order.verify(jobRepository).delete(job);
    }

    // ---------- LOW-T8 ----------

    @Test
    void oneFailingJob_doesNotStopTheSweep() {
        // CANCELLED jobs 1 and 2, COMPLETED job 3; deleting job 1 fails.
        // Page size is 1 (batch-size 1) to exercise the paging loop.
        ReflectionTestUtils.setField(cleanupService, "batchSize", 1);
        OptimizationJob job1 = mock(OptimizationJob.class);
        OptimizationJob job2 = mock(OptimizationJob.class);
        OptimizationJob job3 = mock(OptimizationJob.class);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job1));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job2));
        when(jobRepository.findById(3L)).thenReturn(Optional.of(job3));
        when(solutionRepository.findByJobId(any())).thenReturn(Optional.empty());

        Set<Long> deleted = new HashSet<>();
        doAnswer(inv -> { throw new DataIntegrityViolationException("fk"); }).when(jobRepository).delete(job1);
        doAnswer(inv -> deleted.add(2L)).when(jobRepository).delete(job2);
        doAnswer(inv -> deleted.add(3L)).when(jobRepository).delete(job3);

        // The repository returns the remaining expired ids of a status, page 0, ordered by id.
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.CANCELLED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenAnswer(inv -> page(List.of(1L, 2L), deleted, inv.getArgument(2)));
        noExpired(OptimizationJobStatus.FAILED);
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.COMPLETED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenAnswer(inv -> page(List.of(3L), deleted, inv.getArgument(2)));

        JobCleanupService.CleanupCounts counts = cleanupService.cleanOldJobs();

        assertThat(counts.cancelled()).isEqualTo(1);
        assertThat(counts.completed()).isEqualTo(1);
        assertThat(deleted).containsExactlyInAnyOrder(2L, 3L);
        verify(jobRepository).delete(job2);
        verify(jobRepository).delete(job3);
        verify(solutionRepository, never()).delete(any());
    }

    private static List<Long> page(List<Long> expired, Set<Long> deleted, Pageable pageable) {
        List<Long> remaining = new ArrayList<>();
        for (Long id : expired) {
            if (!deleted.contains(id)) {
                remaining.add(id);
            }
        }
        return remaining.subList(0, Math.min(pageable.getPageSize(), remaining.size()));
    }
}
