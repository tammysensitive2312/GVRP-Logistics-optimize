package org.truong.gvrp_entry_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.truong.gvrp_entry_api.repository.OptimizationJobRepository;
import org.truong.gvrp_entry_api.repository.RouteSendRepository;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Retention sweep for old terminal jobs (hard delete, no archive).
 *
 * <p>Deletion order per job is forced by DB constraints — the
 * {@code trg_prevent_job_deletion_with_solution} trigger blocks deleting a
 * job that still has a solution, and sends/revisions RESTRICT their solution:
 * <pre>
 *   route_sends (of solution + every revision) → revisions (deepest first)
 *   → solution (routes/segments cascade via JPA) → job
 * </pre>
 * Revisions form a tree of any depth (each new revision hangs off the latest
 * one), so the whole tree is collected, not only direct children
 * (SEC-LOW-001, audit lead #14). Each job is deleted in its own transaction;
 * a job that fails is rolled back, logged by id and skipped, so it cannot
 * stall the sweep for other jobs or branches.
 * PENDING/PROCESSING jobs are never swept, no matter their age.
 *
 * <p>Ship with {@code gvrp.cleanup.dry-run=true} (default): the sweep only
 * logs what it WOULD delete. Flip to {@code false} after reviewing the logs.
 */
@Slf4j
@Service
public class JobCleanupService {

    private final OptimizationJobRepository jobRepository;
    private final SolutionRepository solutionRepository;
    private final RouteSendRepository routeSendRepository;
    private final TransactionOperations perJobTransaction;

    public JobCleanupService(
            OptimizationJobRepository jobRepository,
            SolutionRepository solutionRepository,
            RouteSendRepository routeSendRepository,
            PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.solutionRepository = solutionRepository;
        this.routeSendRepository = routeSendRepository;
        if (transactionManager != null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.perJobTransaction = template;
        } else {
            this.perJobTransaction = TransactionOperations.withoutTransaction();
        }
    }

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

    /**
     * Not {@code @Transactional}: every job gets its own transaction in
     * {@link #cleanStatus}, so one failing job rolls back alone.
     */
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
        // Jobs whose deletion failed in this run. They stay in the table, so
        // page 0 is read with room for them and they are filtered out: they
        // never hide later jobs, and each full page holds at least one job not
        // yet seen, so the loop ends.
        Set<Long> skipped = new HashSet<>();
        List<Long> page;
        int requested;
        do {
            requested = pageSize + skipped.size();
            page = jobRepository.findExpiredIds(
                    status, cutoff, PageRequest.of(0, requested));
            for (Long jobId : page) {
                if (skipped.contains(jobId)) {
                    continue;
                }
                try {
                    perJobTransaction.executeWithoutResult(tx -> deleteJobChain(jobId));
                    deleted++;
                } catch (RuntimeException e) {
                    skipped.add(jobId);
                    // Job id and exception class only: the message can carry row data.
                    log.warn("Cleanup: job {} ({}) could not be deleted, skipped: {}",
                            jobId, status, e.getClass().getSimpleName());
                }
            }
        } while (page.size() == requested);
        if (!skipped.isEmpty()) {
            log.warn("Cleanup: {} {} job(s) not deleted in this run: {}",
                    skipped.size(), status, skipped);
        }
        return deleted;
    }

    /**
     * Deletes one job with everything pinning it, constraint order:
     * sends → revisions (deepest first) → solution → job. The trigger
     * then sees a solution-free job and lets the delete through.
     */
    private void deleteJobChain(Long jobId) {
        OptimizationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        solutionRepository.findByJobId(jobId).ifPresent(solution -> {
            List<Solution> revisions = revisionTree(solution);
            List<Long> solutionIds = new ArrayList<>();
            solutionIds.add(solution.getId());
            for (Solution revision : revisions) {
                solutionIds.add(revision.getId());
            }
            for (Long solutionId : solutionIds) {
                routeSendRepository.deleteAll(
                        routeSendRepository.findBySolutionId(solutionId));
            }
            // parent_solution_id is ON DELETE RESTRICT: children before parents.
            List<Solution> deepestFirst = new ArrayList<>(revisions);
            Collections.reverse(deepestFirst);
            solutionRepository.deleteAll(deepestFirst);
            solutionRepository.delete(solution);
        });
        jobRepository.delete(job);
    }

    /**
     * Every revision below {@code root}, breadth-first (a parent always comes
     * before its children). The visited set guards against a malformed cycle.
     */
    private List<Solution> revisionTree(Solution root) {
        List<Solution> tree = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(root.getId());
        Deque<Solution> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Solution parent = queue.poll();
            for (Solution child : solutionRepository.findByParentSolutionId(parent.getId())) {
                if (visited.add(child.getId())) {
                    tree.add(child);
                    queue.add(child);
                }
            }
        }
        return tree;
    }
}
