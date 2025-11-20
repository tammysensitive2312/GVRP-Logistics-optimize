package org.truong.gvrp_entry_api.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SolutionRepository extends JpaRepository<Solution, Long> {

    /**
     * Find all solutions by branch ID
     * @param branchId Branch ID
     * @return List of solutions
     */
    List<Solution> findByBranchId(Long branchId);

    /**
     * Find solutions by branch ID and type
     * @param branchId Branch ID
     * @param type Solution type
     * @return List of solutions
     */
    List<Solution> findByBranchIdAndType(Long branchId, SolutionType type);

    /**
     * Find solutions by branch ID and status
     * @param branchId Branch ID
     * @param status Solution status
     * @return List of solutions
     */
    List<Solution> findByBranchIdAndStatus(Long branchId, SolutionStatus status);

    /**
     * Find latest solution by branch ID
     * @param branchId Branch ID
     * @return Optional Solution
     */
    Optional<Solution> findFirstByBranchIdOrderByCreatedAtDesc(Long branchId);

    /**
     * Find completed solutions for statistics
     * @param branchId Branch ID
     * @param limit Maximum number of results
     * @return List of solutions
     */
//    @Query("""
//        SELECT s FROM Solution s
//        WHERE s.branch.id = :branchId
//        AND s.status = 'COMPLETED'
//        ORDER BY s.completedAt DESC
//        LIMIT :limit
//        """)
//    List<Solution> findCompletedSolutionsForStatistics(
//            @Param("branchId") Long branchId,
//            @Param("limit") int limit
//    );

    /**
     * Count solutions by branch
     * @param branchId Branch ID
     * @return Count
     */
    Long countByBranchId(Long branchId);

    /**
     * Count solutions by branch and status
     * @param branchId Branch ID
     * @param status Solution status
     * @return Count
     */
    Long countByBranchIdAndStatus(Long branchId, SolutionStatus status);

    /**
     * Check if branch has any completed solutions
     * @param branchId Branch ID
     * @return true if exists
     */
    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM Solution s
        WHERE s.branch.id = :branchId
        AND s.status = 'COMPLETED'
        """)
    boolean hasCompletedSolutions(@Param("branchId") Long branchId);

    /**
     * Find solutions by date range
     * @param branchId Branch ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of solutions
     */
//    @Query("""
//        SELECT s FROM Solution s
//        WHERE s.branch.id = :branchId
//        AND s.cre BETWEEN :startDate AND :endDate
//        ORDER BY s.created_at DESC
//        """)
//    List<Solution> findSolutionsByDateRange(
//            @Param("branchId") Long branchId,
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate
//    );
}
