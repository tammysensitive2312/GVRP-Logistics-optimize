package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Solution;

import java.util.Optional;

public interface SolutionRepository extends JpaRepository<Solution, Long> {


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

    @EntityGraph(attributePaths = {
            "branch",
            "job",
            "routes",
            "routes.vehicle"
    })
    Optional<Solution> findWithDetailsById(Long id);

    Boolean deleteByJobId(Long jobId);
    Optional<Solution> findByJobId(Long jobId);

}
