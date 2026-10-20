package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Frontier view of a Pareto job (PARETO-001, {@code GET /api/v1/solutions/{id}/pareto}).
 * Serialised with the global snake_case naming strategy, so the JSON keys are
 * {@code job_id}, {@code latest_solution_id}, {@code active_candidate_id}, ...
 */
@Getter
@Setter
@Builder
public class ParetoFrontierDTO {

    /** Job at the root of the revision chain; null when the root has no job. */
    private Long jobId;
    /** Requested solution id. */
    private Long solutionId;
    /** Newest revision of the chain. */
    private Long latestSolutionId;
    /** Pareto candidate the latest revision materialises; null when none. */
    private Long activeCandidateId;
    private Boolean clustered;
    private Integer clusterCount;
    private List<ParetoCandidateDTO> candidates;
}
