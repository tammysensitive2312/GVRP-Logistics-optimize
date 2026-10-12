package org.truong.gvrp_entry_api.repository;

import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, Long> {

    /**
     * Find job history (paginated)
     * @param branchId Branch ID
     * @param pageable Pageable
     * @return List of jobs
     */
    @Query("""
        SELECT j.id AS id, j.branch.id AS branchId,
               u.id AS userId, u.fullName AS userName,
               j.status AS status, j.externalJobId AS externalJobId,
               j.estimatedDurationMinutes AS estimatedDurationMinutes,
               j.errorMessage AS errorMessage, j.createdAt AS createdAt,
               j.completedAt AS completedAt, j.cancelledAt AS cancelledAt,
               s.id AS solutionId
        FROM OptimizationJob j
        LEFT JOIN j.createdBy u
        LEFT JOIN j.solution s
        WHERE j.branch.id = :branchId
        ORDER BY j.createdAt DESC
        """)
    List<JobHistoryView> findJobHistory(
            @Param("branchId") Long branchId,
            Pageable pageable
    );

    /**
     * Count jobs by branch and status
     * @param branchId Branch ID
     * @param status Job status
     * @return Count
     */
    Long countByBranchIdAndStatus(Long branchId, OptimizationJobStatus status);
}
