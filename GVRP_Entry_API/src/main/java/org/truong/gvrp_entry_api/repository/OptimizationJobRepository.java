package org.truong.gvrp_entry_api.repository;

import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OptimizationJobRepository extends JpaRepository<OptimizationJob, Long> {

    /**
     * Find current running job for branch
     * @param branchId Branch ID
     * @param status Job status (PROCESSING)
     * @return Optional OptimizationJob
     */
    Optional<OptimizationJob> findFirstByBranchIdAndStatusOrderByCreatedAtDesc(
            Long branchId,
            OptimizationJobStatus status
    );

    /**
     * Check if branch has running job
     * @param branchId Branch ID
     * @param status Job status (PROCESSING)
     * @return true if exists
     */
    boolean existsByBranchIdAndStatus(Long branchId, OptimizationJobStatus status);

    /**
     * Find job history (paginated)
     * @param branchId Branch ID
     * @param pageable Pageable
     * @return List of jobs
     */
    @Query("""
        SELECT j FROM OptimizationJob j
        WHERE j.branch.id = :branchId
        ORDER BY j.createdAt DESC
        """)
    List<OptimizationJob> findJobHistory(
            @Param("branchId") Long branchId,
            Pageable pageable
    );

    /**
     * Find jobs by status
     * @param branchId Branch ID
     * @param status Job status
     * @return List of jobs
     */
    List<OptimizationJob> findByBranchIdAndStatus(Long branchId, OptimizationJobStatus status);

    /**
     * Count jobs by branch and status
     * @param branchId Branch ID
     * @param status Job status
     * @return Count
     */
    Long countByBranchIdAndStatus(Long branchId, OptimizationJobStatus status);
}
