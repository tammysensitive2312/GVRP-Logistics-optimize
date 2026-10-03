package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Read-only candidate screening, not a proof of global route infeasibility.
 * Uses built solver values so skill normalization and capacity scaling agree.
 */
public final class UnassignedOrderDiagnosticService {
    private UnassignedOrderDiagnosticService() {}

    public static final String UNKNOWN =
            "The solver did not assign this order in the selected plan. "
            + "No conclusive blocker was established by skill, capacity and cluster screening.";
    public static final String INCOMPLETE =
            "Diagnosis was not completed within its budget or was cancelled. "
            + "The reason for non-assignment has not been determined.";

    public static Map<String, String> diagnose(VehicleRoutingProblem problem,
            VehicleRoutingProblemSolution solution, Map<String, Integer> clusters,
            BooleanSupplier cancelled, long budgetMillis) {
        long start = System.nanoTime();
        long budget = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, budgetMillis));
        Map<String, String> reasons = new HashMap<>();
        for (Job job : solution.getUnassignedJobs()) {
            int total = 0, skilled = 0, capable = 0, eligible = 0;
            boolean complete = true;
            for (var vehicle : problem.getVehicles()) {
                if (cancelled.getAsBoolean() || System.nanoTime() - start >= budget) {
                    complete = false;
                    break;
                }
                total++;
                boolean matches = job.getRequiredSkills().values().stream()
                        .allMatch(vehicle.getSkills()::containsSkill);
                if (!matches) continue;
                skilled++;
                boolean fits = true;
                int dimensions = Math.max(job.getSize().getNuOfDimensions(),
                        vehicle.getType().getCapacityDimensions().getNuOfDimensions());
                for (int d = 0; d < dimensions; d++) {
                    if (job.getSize().get(d) > vehicle.getType().getCapacityDimensions().get(d)) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) continue;
                capable++;
                Integer vehicleCluster = clusters == null ? null : clusters.get(vehicle.getId());
                Integer jobCluster = clusters == null ? null : clusters.get(job.getId());
                if (vehicleCluster == null || jobCluster == null || vehicleCluster.equals(jobCluster)) {
                    eligible++;
                }
            }
            String reason;
            if (!complete) reason = INCOMPLETE;
            else if (total == 0) reason = "No vehicles were provided in this planning request.";
            else if (skilled == 0) reason =
                    "None of the " + total + " vehicles in this request has all required skills: "
                    + job.getRequiredSkills().values().stream().sorted().toList() + ".";
            else if (capable == 0) reason =
                    "The order exceeds the configured capacity of every skill-compatible vehicle ("
                    + skilled + " checked).";
            else if (eligible == 0) reason =
                    "There are " + capable + " skill- and capacity-compatible vehicles, but none "
                    + "is eligible under the current cluster assignment. Feasibility without "
                    + "clustering has not been tested.";
            else reason = UNKNOWN;
            reasons.put(job.getId(), reason);
        }
        return reasons;
    }
}
