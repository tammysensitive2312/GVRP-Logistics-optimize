package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.ParetoCandidate;

import java.util.List;
import java.util.Optional;

/**
 * Branch-scoped access to a job's Pareto candidates (PARETO-001).
 * Every lookup carries the branch id so another branch's rows are never returned.
 */
public interface ParetoCandidateRepository extends JpaRepository<ParetoCandidate, Long> {

    List<ParetoCandidate> findByJobIdAndBranchIdOrderByCandidateIndexAsc(Long jobId, Long branchId);

    Optional<ParetoCandidate> findByIdAndBranchId(Long id, Long branchId);
}
