package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Retention sweep for old terminal jobs (hard delete, no archive).
 *
 * <p>Deletion order per job is forced by DB constraints — the
 * {@code trg_prevent_job_deletion_with_solution} trigger blocks deleting a
 * job that still has a solution, and sends/revisions RESTRICT their solution:
 * <pre>
 *   route_sends (of solution + revisions) → revisions → solution
 *   (routes/segments cascade via JPA) → job
 * </pre>
 * PENDING/PROCESSING jobs are never swept, no matter their age.
 *
 * <p>Ship with {@code gvrp.cleanup.dry-run=true} (default): the sweep only
 * logs what it WOULD delete. Flip to {@code false} after reviewing the logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCleanupService {

    private final OptimizationJobRepository jobRepository;
    private final SolutionRepository solutionRepository;
    private final RouteSendRepository routeSendRepository;

    @Value("${gvrp.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${gvrp.cleanup.dry-run:true}")
    private boolean dryRun;

    @Value("${gvrp.cleanup.cancelled-retention-days:7}")
    private int cancelledRetentionDays;

    @Value("${gvrp.cleanup.failed-retention-days:30}")
    private int failedRetentionDays;

    @Value("${gvrp.cleanup.completed-retention-days:90}")
    private int completedRetentionDays;

    @Value("${gvrp.cleanup.batch-size:200}")
    private int batchSize;

    public record CleanupCounts(long cancelled, long failed, long completed) {
        public long total() {
            return cancelled + failed + completed;
        }
    }

    @Scheduled(cron = "${gvrp.cleanup.cron:0 30 3 * * *}")
    public void scheduledCleanup() {
        if (!enabled) {
            return;
        }
        if (dryRun) {
            CleanupCounts wouldDelete = preview();
            log.info("🧹 Cleanup dry-run: WOULD delete {} jobs "
                            + "(cancelled={}, failed={}, completed={}); "
                            + "set gvrp.cleanup.dry-run=false to delete for real",
                    wouldDelete.total(), wouldDelete.cancelled(),
                    wouldDelete.failed(), wouldDelete.completed());
            return;
        }
        CleanupCounts deleted = cleanOldJobs();
        log.info("🧹 Cleanup: deleted {} jobs "
                        + "(cancelled={}, failed={}, completed={})",
                deleted.total(), deleted.cancelled(),
                deleted.failed(), deleted.completed());
    }

    /** Read-only counts of jobs that the next sweep would delete. */
    public CleanupCounts preview() {
        return new CleanupCounts(
                countExpired(OptimizationJobStatus.CANCELLED, cancelledRetentionDays),
                countExpired(OptimizationJobStatus.FAILED, failedRetentionDays),
                countExpired(OptimizationJobStatus.COMPLETED, completedRetentionDays));
    }

    @Transactional
    public CleanupCounts cleanOldJobs() {
        return new CleanupCounts(
                cleanStatus(OptimizationJobStatus.CANCELLED, cancelledRetentionDays),
                cleanStatus(OptimizationJobStatus.FAILED, failedRetentionDays),
                cleanStatus(OptimizationJobStatus.COMPLETED, completedRetentionDays));
    }

    private long countExpired(OptimizationJobStatus status, int retentionDays) {
        return jobRepository.countByStatusAndCreatedAtBefore(
                status, LocalDateTime.now().minusDays(retentionDays));
    }

    private long cleanStatus(OptimizationJobStatus status, int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        // Guard against a zero/negative batch-size misconfiguration.
        int pageSize = Math.max(1, batchSize);
        long deleted = 0;
        List<Long> batch;
        do {
            batch = jobRepository.findExpiredIds(
                    status, cutoff, PageRequest.of(0, pageSize));
            for (Long jobId : batch) {
                deleteJobChain(jobId);
                deleted++;
            }
        } while (batch.size() == pageSize);
        return deleted;
    }

    /**
     * Deletes one job with everything pinning it, constraint order:
     * sends → revisions → solution → job. The trigger then sees a
     * solution-free job and lets the delete through.
     */
    private void deleteJobChain(Long jobId) {
        OptimizationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        solutionRepository.findByJobId(jobId).ifPresent(solution -> {
            List<Long> solutionIds = new ArrayList<>();
            solutionIds.add(solution.getId());
            List<Solution> revisions =
                    solutionRepository.findByParentSolutionId(solution.getId());
            for (Solution revision : revisions) {
                solutionIds.add(revision.getId());
            }
            for (Long solutionId : solutionIds) {
                routeSendRepository.deleteAll(
                        routeSendRepository.findBySolutionId(solutionId));
            }
            solutionRepository.deleteAll(revisions);
            solutionRepository.delete(solution);
        });
        jobRepository.delete(job);
    }
}
