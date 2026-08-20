package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.Solution;

import java.util.Optional;

public interface SolutionRepository extends JpaRepository<Solution, Long> {

    @EntityGraph(attributePaths = {
            "branch",
            "job",
            "routes",
            "routes.vehicle"
    })
    Optional<Solution> findWithDetailsById(Long id);

    Optional<Solution> findByJobId(Long jobId);

}
