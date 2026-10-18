# Context — PARETO-001

Static trace of the working tree on 2026-10-18 (no execution). Paths are relative to the
repository root; `E` = `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api`,
`N` = `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api`,
`C` = `GVRP_Entry_client_v2/src/app`.

## 1. Request path

1. Client: `C/features/main/jobs/route-planning-dialog` checkbox `enablePareto`
   -> `RoutePlanningRequest.preferences.enable_pareto_analysis`.
2. Entry: `N/mapper/OptimizationConfigMapper.toEngineConfig` copies
   `enableParetoAnalysis`; `OptimizationJobService.submitJob` stores the request JSON in
   `optimization_jobs.input_data` and calls the engine after commit.
3. Engine: `E/service/OptimizationService.optimize(request, handle)`:

```java
if (Boolean.TRUE.equals(config.getEnableParetoAnalysis())) {
    DistanceTimeMatrix matrix = calculateDistanceMatrix(context, null, handle);   // always dense
    return optimizeMultiObjective(context, matrix, config, request, handle);
} else {
    Map<String, Integer> clusterAssignment = buildClusterAssignmentIfEligible(context);
    MatrixMask mask = MatrixMask.fromClusters(context, clusterAssignment);
    DistanceTimeMatrix matrix = calculateDistanceMatrix(context, mask, handle);
    return optimizeSingleObjective(context, matrix, config, request, clusterAssignment, handle);
}
```

## 2. Pareto branch today (`OptimizationService.optimizeMultiObjective`)

- Weight points: `ParetoWeightSampler.generate(4, 2.0)` -> 5 points, w_cost = (k/4)^2
  (k = 0..4; w_cost = 0 replaced by `AppConstant.EPSILON`), w_co2 = 1 - w_cost. Labels
  `PARETO_K{k}_OF_4`.
- Per point: clone config, set weights, `buildGreenVRP`, `createAlgorithm(vrp, context,
  presetConfig, null, handle)` — the `null` is explicit (comment: cluster-first not applied
  to Pareto, decision recorded). Same matrix for every point.
- Per point: `assertNoSentinelEdgeTraversed`, `assertMaxDistances`,
  `SolutionMetricsCalculator.calculate` -> `SolutionCandidate(label, solution, metrics, wc, we)`.
- `buildParetoFrontier(candidates)` keeps candidates not dominated by
  `SolutionCandidate.dominates` (cost and CO2 only).
- `selectFromPareto(frontier, config)`: score = wc * (cost - min)/(max - min + 1)
  + we * (co2 - min)/(max - min + 1) on raw units, `min(...)`.
- Only the selected candidate gets `extractRouteDetails` / `extractUnassignedOrders`
  (diagnosis called with `clusterAssignment = null`).
- `OptimizationResult.pareto(jobId, selected, frontier, matrix, routes, unassigned)`.
- Log line says `ObjectivePreset.values().length` (4) scenarios; the loop runs 5.

## 3. Callback and persistence today

- `E/service/CallbackService.sendCompletionCallback` -> `SolutionConverter.convertToSolutionData(result)`
  which writes summary metrics, `routes`, `unassigned_orders` of the **selected** candidate
  only. `OptimizationResult.paretoFrontier` is never serialised -> the frontier is lost
  (spool, callback, Entry DB, UI).
- `N/service/OptimizationCallbackService.handleCompletion`: idempotency via
  `solutionRepository.findByJobId`, status guard, `saveSolution` builds Solution + Route +
  RouteStop + UnassignedOrder (private `buildRoute`, `buildStop`, `buildUnassignedOrder`) and
  transitions orders (`ON_ROUTE` for assigned, `UNASSIGNED` for unassigned).
- `N/dto/request/EngineCallbackRequest.SolutionData` has no Pareto field.
- `N/entity/Solution`: `job` is `@OneToOne` (`solutions.job_id` 1:1 for engine originals);
  revisions have `job = null` and `parent_solution_id` (migration
  `2026-10-02-manual-solution-revision.sql`). `type` is a MySQL ENUM
  `('ENGINE_GENERATED','FILE_IMPORTED','MANUAL_EDITED')`, JPA `length = 20`.
- `N/service/RouteCustomizationService`: `saveRevision` creates `MANUAL_EDITED` revisions,
  409 via `RevisionConflictException(latestId)` when the base is not the latest revision,
  never changes order statuses; `latestRevisionId(solutionId, branchId)` is public.
- `N/service/SolutionService.belongsTo(solution, branchId)` is the tenant check (package
  static); `solutionNotFound()` builds the 404 error.
- Order state machine `N/entity/enums/OrderStatus`: `ON_ROUTE -> SERVICING` only,
  `UNASSIGNED -> SCHEDULED` only. No direct `ON_ROUTE <-> UNASSIGNED` edge.
- Security: `N/config/SecurityConfig` -> `/api/v1/solutions/callbacks/**` permitAll (API key
  filter), every other `/api/v1/**` requires `PLANNER`. New endpoints under
  `/api/v1/solutions/...` are PLANNER-only without config changes.
- No migration runner: SQL files in `GVRP_Entry_API/src/main/resources/database/migrations/`
  are run manually; `ddl-auto=update` adds tables/columns but does not alter a MySQL ENUM.
- `N/service/JobCleanupService.deleteJobChain` deletes sends -> first-level revisions ->
  solution -> job. It does not know about a new child table.

## 4. Progress today

- `E/job/JobRegistry.ProgressSnapshot(iteration, maxIterations, bestCost, routes,
  unassigned, elapsedSeconds, phase, updatedAt)`; `E/controller/OptimizationController.progress`
  computes `percent = 100 * iteration / maxIterations`.
- In Pareto each weight point restarts the listener -> percent goes 0..100 five times; no
  scenario index is exposed.
- Entry `EngineApiClientImpl.getProgress` passes the engine map through unchanged, so new
  keys reach the client without Entry changes.

## 5. Clustering facts relevant to Pareto

- `buildClusterAssignmentIfEligible(context)` depends only on order coordinates, demands,
  min vehicle-type capacity, vehicle start depots — **not on weights**. It can be computed
  once per job.
- `MatrixMask.fromClusters` + `DistanceMatrixService.createDistanceMatrix(coords, mask, cancel)`
  build a `BlockDiagonalCostMatrix` when the mask is not full; the dense branch calls
  `DenseCostMatrix.allocate(n)` which fails loud past the memory guard
  (`MatrixMemory.requireDenseFits`). Today a large Pareto job therefore either fails or pays
  O(n^2) matrix time.
- `ClusterRouteConstraint` (HardRouteConstraint) and `UnassignedOrderDiagnosticService.diagnose(..., clusterAssignment, ...)`
  already accept the combined map ("order-{id}" / "vehicle-{id}" keys).
- Scientific note (AGENTS.md §9 "Matrix optimization"): cluster-first is a **problem
  approximation**, not a representation change. `VehicleClusterAssigner` assigns vehicles
  by depot proximity and demand quota, ignoring vehicle type and emission factor, so the
  CO2 trade-off range of the frontier may narrow. Not verified; must be stated in the UI
  and in any research claim.

## 6. Tests today

- No test covers the Pareto branch, `SolutionConverter`, or the callback service.
- Constraints from existing tests (must keep compiling unchanged):
  - `MaxDistanceRegressionTest` constructs `new OptimizationService(null, null, registry)`
    and reflects on the private method
    `createAlgorithm(VehicleRoutingProblem, OptimizationContext, OptimizationConfig, Map, JobRegistry.JobHandle)`.
  - `OptimizationSkillMappingTest`, `MaxDistanceConstraintTest` use static members of
    `OptimizationService` (`normalizedSkills`, `addVehicleSkills`, `addRequiredSkills`,
    `MaxDistanceConstraint`, `assertMaxDistances`).
  - `SolutionCustomizationControllerTest` uses `@InjectMocks SolutionController` with
    mocks of `SolutionService` and `RouteCustomizationService`.
  - `JobCleanupServiceTest` uses `@InjectMocks JobCleanupService` with three repository
    mocks and in-order verification.
- Snapshot of all 37 existing Engine/Entry test files taken by Claude on 2026-10-18 for
  byte comparison at review.
- Test libraries available: `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ,
  MockMvc) in both modules; `spring-security-test` in Entry. No dependency change needed.

## 7. Out-of-scope observations

- OBS-1: `JobCleanupService.deleteJobChain` deletes only first-level revisions; a chain
  deeper than one level (revision of a revision) will hit `fk_solution_parent ON DELETE
  RESTRICT`. Pre-existing; not fixed here (cleanup is dry-run by default).
- OBS-2: `OptimizationService.createAlgorithm` logs "Constraints applied: MaxDistance"
  regardless of the actual constraints; cosmetic.
- OBS-3: `SolutionMetrics.getWeightedScore` mixes VND and kg; unused by the Pareto
  selection after this change.
- OBS-4: Entry `estimateDuration` ignores Pareto (5x solve time). UI hint handled in
  PARETO-002.
