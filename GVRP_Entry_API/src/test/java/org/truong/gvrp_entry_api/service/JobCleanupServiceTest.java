package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Retention sweep: terminal jobs past retention are hard-deleted
 * (sends → revisions → solution → job); live statuses never swept.
 */
@ExtendWith(MockitoExtension.class)
class JobCleanupServiceTest {

    @Mock
    private OptimizationJobRepository jobRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private RouteSendRepository routeSendRepository;

    @InjectMocks
    private JobCleanupService cleanupService;

    @Test
    void cleanOldJobs_deletesSendsRevisionsSolutionThenJob_inOrder() {
        OptimizationJob job = mock(OptimizationJob.class);
        Solution solution = mock(Solution.class);
        Solution revision = mock(Solution.class);
        RouteSend send = mock(RouteSend.class);
        when(solution.getId()).thenReturn(10L);
        when(revision.getId()).thenReturn(11L);
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.CANCELLED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L), List.of());
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.FAILED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.COMPLETED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(1L)).thenReturn(Optional.of(solution));
        when(solutionRepository.findByParentSolutionId(10L))
                .thenReturn(List.of(revision));
        when(routeSendRepository.findBySolutionId(10L)).thenReturn(List.of(send));
        when(routeSendRepository.findBySolutionId(11L)).thenReturn(List.of());

        JobCleanupService.CleanupCounts counts = cleanupService.cleanOldJobs();

        assertEquals(1, counts.cancelled());
        assertEquals(1, counts.total());
        InOrder order = inOrder(routeSendRepository, solutionRepository,
                jobRepository);
        order.verify(routeSendRepository).deleteAll(List.of(send));
        order.verify(solutionRepository).deleteAll(List.of(revision));
        order.verify(solutionRepository).delete(solution);
        order.verify(jobRepository).delete(job);
    }

    @Test
    void cleanOldJobs_jobWithoutSolution_deletesJobDirectly() {
        OptimizationJob job = mock(OptimizationJob.class);
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.FAILED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(2L), List.of());
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.CANCELLED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobRepository.findExpiredIds(eq(OptimizationJobStatus.COMPLETED),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job));
        when(solutionRepository.findByJobId(2L)).thenReturn(Optional.empty());

        JobCleanupService.CleanupCounts counts = cleanupService.cleanOldJobs();

        assertEquals(1, counts.failed());
        verify(solutionRepository, never()).delete(any());
        verify(jobRepository).delete(job);
    }

    @Test
    void cleanOldJobs_neverSweepsLiveStatuses() {
        when(jobRepository.findExpiredIds(any(), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(List.of());

        cleanupService.cleanOldJobs();

        verify(jobRepository, never()).findExpiredIds(
                eq(OptimizationJobStatus.PENDING), any(), any());
        verify(jobRepository, never()).findExpiredIds(
                eq(OptimizationJobStatus.PROCESSING), any(), any());
    }

    @Test
    void preview_countsWithoutDeleting() {
        when(jobRepository.countByStatusAndCreatedAtBefore(
                eq(OptimizationJobStatus.CANCELLED), any(LocalDateTime.class)))
                .thenReturn(3L);
        when(jobRepository.countByStatusAndCreatedAtBefore(
                eq(OptimizationJobStatus.FAILED), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(jobRepository.countByStatusAndCreatedAtBefore(
                eq(OptimizationJobStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(0L);

        JobCleanupService.CleanupCounts preview = cleanupService.preview();

        assertEquals(3, preview.cancelled());
        assertEquals(1, preview.failed());
        assertEquals(0, preview.completed());
        assertEquals(4, preview.total());
        verify(jobRepository, never()).delete(any());
        verify(solutionRepository, never()).delete(any());
    }
}
