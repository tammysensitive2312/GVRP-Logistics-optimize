package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * One weight-point outcome of a Pareto run (PAR-004).
 * <p>
 * Carries the candidate (label, weights, metrics, Jsprit solution), its service-first
 * {@link FrontierStatus} and whether it is the engine-recommended point. Full routes and
 * unassigned orders are populated only for {@code FRONTIER} candidates (may be empty lists),
 * and stay {@code null} for {@code DOMINATED} / {@code SERVICE_DOMINATED} candidates.
 */
@Getter
@Builder
@AllArgsConstructor
public class ParetoAlternative {

    /** Position in the weight-point list (k). */
    private final int index;

    private final SolutionCandidate candidate;

    private final FrontierStatus status;

    /** Engine-recommended point; exactly one alternative, always {@code FRONTIER}. */
    private final boolean selected;

    /** Non-null only for {@code FRONTIER}. */
    private final List<RouteDetail> routes;

    /** Non-null only for {@code FRONTIER} (may be empty). */
    private final List<UnassignedOrder> unassignedOrders;
}
