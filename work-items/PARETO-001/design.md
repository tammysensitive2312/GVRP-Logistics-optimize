# Design — PARETO-001

Specification for the engineer. Abbreviations as in context.md (`E`, `N`). If any part is
impossible as written (compile constraint, missing API, a DB-free test cannot be written),
**stop and report**; do not redesign.

---

## Part A — Engine (`GVRP_Engine_API`)

### A1. One cluster plan for both branches (PAR-001)

`OptimizationService.optimize(request, handle)` becomes:

```java
OptimizationContext context = prepareContext(request);
OptimizationConfig config = request.getConfig();
validateConfig(config);

Map<String, Integer> clusterAssignment = buildClusterAssignmentIfEligible(context);
MatrixMask mask = MatrixMask.fromClusters(context, clusterAssignment);
DistanceTimeMatrix matrix = calculateDistanceMatrix(context, mask, handle);

if (Boolean.TRUE.equals(config.getEnableParetoAnalysis())) {
    return optimizeMultiObjective(context, matrix, config, request, clusterAssignment, handle);
}
return optimizeSingleObjective(context, matrix, config, request, clusterAssignment, handle);
```

`optimizeMultiObjective` gains the `Map<String, Integer> clusterAssignment` parameter and:
- passes it to `createAlgorithm(vrp, context, presetConfig, clusterAssignment, handle)` for
  every weight point (remove the "not applied to Pareto" comment);
- passes it to `UnassignedOrderDiagnosticService.diagnose(...)` for every candidate whose
  routes are extracted (A4).

Cluster count for the payload: number of distinct values among the `"order-*"` keys of
`clusterAssignment` (null when `clusterAssignment == null`). Compute it in a small private
static helper.

Compile constraints (existing tests): keep the constructor
`OptimizationService(DistanceMatrixService, CallbackService, JobRegistry)`; keep the private
`createAlgorithm(VehicleRoutingProblem, OptimizationContext, OptimizationConfig, Map, JobRegistry.JobHandle)`
signature; keep all static members used by tests.

Fix the scenario-count log line to use `weightPoints.size()`.

### A2. Frontier classification and selection (PAR-002, PAR-003)

New enum `E/model/FrontierStatus { FRONTIER, DOMINATED, SERVICE_DOMINATED }`.

New final class `E/service/ParetoFrontierSelector` (pure, static, no Spring):

```java
public static List<FrontierStatus> classify(List<SolutionCandidate> candidates)
public static int select(List<SolutionCandidate> candidates, List<FrontierStatus> statuses,
                         double costWeight, double co2Weight)   // returns index into candidates
```

`classify` (same order as input):
1. `maxServed` = max of `metrics.ordersServed` (treat null as 0).
2. `served < maxServed` -> `SERVICE_DOMINATED`.
3. Among the others ("eligible"), candidate j is `DOMINATED` if some eligible i != j has
   `cost_i <= cost_j && co2_i <= co2_j && (cost_i < cost_j || co2_i < co2_j)`.
4. Identical vectors (`cost_i == cost_j && co2_i == co2_j`, exact double equality): the
   lowest index stays `FRONTIER`, later ones become `DOMINATED`.
5. Otherwise `FRONTIER`. Empty input -> empty list. Null metrics -> `IllegalArgumentException`.

`select`:
1. Consider only indices with status `FRONTIER`; none -> `IllegalStateException`.
2. Normalise the weights: `w = GreenVRPCostCalculator.normalizeWeights(costWeight, co2Weight)`
   (already used by `OptimizationConfig.getEffectiveWeights`).
3. For cost and CO2 separately over the frontier: `norm(x) = (x - min) / (max - min)` when
   `max > min`, else `0.0`. No `+1`.
4. score = w[0] * normCost + w[1] * normCo2; smallest wins. Tie (|delta| <= 1e-12) -> lower
   cost, then lower index.

`OptimizationService` replaces `buildParetoFrontier` / `selectFromPareto` with these calls
(delete the two private methods). `SolutionCandidate.dominates` stays untouched.
Selection weights come from `config.getEffectiveWeights()` of the user request (unchanged
behaviour: the goal chosen in the dialog picks the recommended point).

### A3. Result model

New class `E/model/ParetoAlternative` (Lombok `@Getter @Builder @AllArgsConstructor` or a
record):

| field | type | note |
|---|---|---|
| index | int | position in the weight-point list (k) |
| candidate | SolutionCandidate | label, weights, metrics, solution |
| status | FrontierStatus | |
| selected | boolean | engine-recommended point; exactly one, always `FRONTIER` |
| routes | List<RouteDetail> | non-null only for `FRONTIER` |
| unassignedOrders | List<UnassignedOrder> | non-null only for `FRONTIER` (may be empty) |

`OptimizationResult`: add `List<ParetoAlternative> paretoAlternatives` and
`Integer clusterCount` (both `@JsonInclude(NON_NULL)`). Change the `pareto(...)` factory to
`pareto(jobId, selected, paretoFrontier, alternatives, clusterCount, matrix, routes, unassigned)`;
`paretoFrontier` keeps the `FRONTIER` candidates (backward compatible field). `single(...)`
stays as is (both new fields null).

### A4. Route extraction for every frontier point

For each candidate with status `FRONTIER` (including the selected one):
`extractRouteDetails(solution, context, matrix).routes()` and
`extractUnassignedOrders(solution, context, UnassignedOrderDiagnosticService.diagnose(vrp_k, solution_k, clusterAssignment, handle::isCancelRequested, 1000))`
where `vrp_k` is that candidate's problem (keep the existing `candidateProblems` map).
Top-level `routes` / `unassignedOrders` of the result are those of the selected candidate
(identical objects, no second extraction).

### A5. Callback payload (PAR-004)

`SolutionConverter.convertToSolutionData(result)`: unchanged keys for both modes. When
`result.getOptimizationMode() == MULTI_PARETO` and `paretoAlternatives != null`, add key
`"pareto"`:

```json
"pareto": {
  "sampler_n": 4,
  "sampler_p": 2.0,
  "clustered": true,
  "cluster_count": 7,
  "selection_rule": "SERVICE_FIRST_MIN_MAX",
  "candidates": [
    {
      "index": 0, "label": "PARETO_K0_OF_4",
      "cost_weight": 0.0001, "co2_weight": 0.9999,
      "total_cost": 1234567.0, "total_distance": 85.2, "total_co2": 14.1, "total_time": 6.3,
      "total_vehicles_used": 4, "served_orders": 120, "unserved_orders": 0,
      "frontier_status": "FRONTIER", "selected": false,
      "routes": [ ...same shape as top-level "routes"... ],
      "unassigned_orders": [ ...same shape as top-level "unassigned_orders"... ]
    }
  ]
}
```

- `routes` / `unassigned_orders` keys present only for `FRONTIER` candidates
  (`unassigned_orders` may be `[]`); absent for `DOMINATED` / `SERVICE_DOMINATED`.
- Reuse the existing private `convertRoutes(routes, matrix)`.
- `clustered` = `cluster_count != null`. Put `PARETO_SAMPLER_N = 4` and
  `PARETO_SAMPLER_P = 2.0` in `E/utils/AppConstant` and use them both in
  `optimizeMultiObjective` and the converter.
- Single mode: no `"pareto"` key (byte-identical map keys to today).

### A6. Scenario progress (PAR-005)

- `JobRegistry.JobHandle`: add `private volatile int scenarioIndex;` (0-based) and
  `private volatile int scenarioCount;` (0 = not Pareto) with getters `scenarioIndex()` /
  `scenarioCount()` (setters come from the class-level `@Setter`). **Do not change
  `ProgressSnapshot`.**
- `optimizeMultiObjective`: `handle.setScenarioCount(weightPoints.size())` before the loop,
  `handle.setScenarioIndex(k)` at the start of each iteration.
- `OptimizationController.progress`: when `h.scenarioCount() > 0` put
  `"scenario" = scenarioIndex + 1`, `"scenarioCount" = scenarioCount`; and when a snapshot
  exists compute `percent = 100 * (scenarioIndex * maxIter + iteration) / (scenarioCount * maxIter)`
  using long arithmetic (guard `maxIter > 0`). Non-Pareto: unchanged keys and formula.

---

## Part B — Entry (`GVRP_Entry_API`)

### B1. Schema

New file `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-18-pareto-candidates.sql`:

```sql
-- Pareto candidates of a job (PARETO-001). Run manually: ddl-auto=update does not
-- widen the solutions.type ENUM.
CREATE TABLE IF NOT EXISTS pareto_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    candidate_index INT NOT NULL,
    label VARCHAR(40) NOT NULL,
    cost_weight DECIMAL(10,6) NOT NULL,
    co2_weight DECIMAL(10,6) NOT NULL,
    total_cost DECIMAL(18,2) NULL,
    total_distance DECIMAL(10,2) NULL,
    total_co2 DECIMAL(10,2) NULL,
    total_time DECIMAL(10,2) NULL,
    total_vehicles_used INT NULL,
    served_orders INT NULL,
    unserved_orders INT NULL,
    frontier_status VARCHAR(20) NOT NULL,
    engine_selected BOOLEAN NOT NULL DEFAULT FALSE,
    clustered BOOLEAN NOT NULL DEFAULT FALSE,
    cluster_count INT NULL,
    plan_json JSON NULL COMMENT 'routes + unassigned_orders as received; FRONTIER only',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_pareto_job FOREIGN KEY (job_id) REFERENCES optimization_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_pareto_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    CONSTRAINT uk_pareto_job_index UNIQUE (job_id, candidate_index),
    INDEX idx_pareto_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE solutions
    ADD COLUMN pareto_candidate_id BIGINT NULL
        COMMENT 'Pareto candidate this solution materialises; NULL otherwise' AFTER parent_solution_id,
    ADD CONSTRAINT fk_solution_pareto_candidate
        FOREIGN KEY (pareto_candidate_id) REFERENCES pareto_candidates (id) ON DELETE SET NULL;

ALTER TABLE solutions
    MODIFY COLUMN type ENUM('ENGINE_GENERATED', 'FILE_IMPORTED', 'MANUAL_EDITED', 'PARETO_ALTERNATIVE')
        NOT NULL DEFAULT 'ENGINE_GENERATED';
```

(Verified: `Branch` maps to table `branches`; `OptimizationJob` to `optimization_jobs`.)

Entity `N/entity/ParetoCandidate` mirroring the table: `job` `@ManyToOne(LAZY)` with
`@OnDelete(action = OnDeleteAction.CASCADE)`, `branch` `@ManyToOne(LAZY)`,
`frontierStatus` `@Enumerated(STRING)` of new enum `N/entity/enums/ParetoFrontierStatus`
(`FRONTIER, DOMINATED, SERVICE_DOMINATED`), `planJson` `@Column(columnDefinition = "JSON")`
String, `@Builder`, Lombok getters/setters like `Solution`.

`Solution`: add `@ManyToOne(fetch = LAZY) @JoinColumn(name = "pareto_candidate_id")
@OnDelete(action = OnDeleteAction.SET_NULL) private ParetoCandidate paretoCandidate;`
`SolutionType`: add `PARETO_ALTERNATIVE`.
`JobCleanupService` is **not** changed: the DB cascade removes candidates with the job, and
solutions are deleted before the job.

Repository `N/repository/ParetoCandidateRepository`:
`List<ParetoCandidate> findByJobIdAndBranchIdOrderByCandidateIndexAsc(Long jobId, Long branchId)`,
`Optional<ParetoCandidate> findByIdAndBranchId(Long id, Long branchId)`.

### B2. Callback (PAR-006)

`EngineCallbackRequest.SolutionData`: add `private ParetoData pareto;` and nested static
`@Data` classes, field names following the existing camelCase style (the global snake_case
mapping turns `totalCO2` into `total_co2`, as for `SolutionData.totalCO2`):
- `ParetoData { Integer samplerN; Double samplerP; Boolean clustered; Integer clusterCount;
  String selectionRule; List<ParetoCandidateData> candidates; }`
- `ParetoCandidateData { Integer index; String label; BigDecimal costWeight, co2Weight,
  totalCost, totalDistance, totalCO2, totalTime; Integer totalVehiclesUsed, servedOrders,
  unservedOrders; String frontierStatus; Boolean selected; List<RouteData> routes;
  List<UnassignedOrderData> unassignedOrders; }`

Extract the private builders of `OptimizationCallbackService` (`buildRoute`, `buildStop`,
`buildUnassignedOrder`) into a new `@Component N/service/EnginePlanMaterializer` with the
same behaviour, used by the callback service and by B4. Behaviour of the single-objective
callback must not change.

In `saveSolution`, after the root solution is built and before `solutionRepository.save`:
if `solutionData.getPareto() != null` and candidates non-empty:
1. Validate: indices unique; exactly one `selected == true`; the selected one is
   `FRONTIER`; every `frontierStatus` parses to `ParetoFrontierStatus`. Violation ->
   `DataInvalidException` (VALIDATION_ERROR, message names the rule) -> the existing catch
   marks the job FAILED and the engine moves the payload to `poison/`.
2. For each candidate create a `ParetoCandidate` (job, job.branch, metrics, weights,
   status, `engineSelected = selected`, `clustered`, `clusterCount`, `createdAt = now`);
   `planJson` = JSON of `{"routes": [...], "unassigned_orders": [...]}` serialised with the
   injected `ObjectMapper` for `FRONTIER` candidates, `null` otherwise.
3. `saveAll`, then `solution.setParetoCandidate(selectedRow)`.
Order statuses: unchanged logic (still driven by the top-level routes/unassigned only).

### B3. Read endpoint (PAR-007)

New `N/controller/ParetoController` (`@RequestMapping("/api/v1/solutions")`), new service
`N/service/ParetoService`. Do not add dependencies to `SolutionController`.

`GET /api/v1/solutions/{id}/pareto` -> `ParetoFrontierDTO`:

| field | meaning |
|---|---|
| job_id | job of the chain root (null when the root has no job) |
| solution_id | requested id |
| latest_solution_id | `routeCustomizationService.latestRevisionId(rootId, branchId)` |
| active_candidate_id | `paretoCandidate.id` of the latest revision (null if none) |
| clustered, cluster_count | from the candidate rows (first row) |
| candidates[] | `ParetoCandidateDTO` ordered by index |

`ParetoCandidateDTO`: `id, index, label, cost_weight, co2_weight, total_cost,
total_distance, total_co2, total_time, total_vehicles_used, served_orders,
unserved_orders, frontier_status, engine_selected, applicable, not_applicable_reason`.
No `plan_json` in the DTO.

`ParetoService.getFrontier(solutionId, branchId)` (`@Transactional(readOnly = true)`):
1. `solutionRepository.findById(id).filter(s -> SolutionService.belongsTo(s, branchId))`
   else `SolutionService.solutionNotFound()` — before any other lookup.
2. Walk `parentSolution` to the root. Root job null -> DTO with empty candidates.
3. Candidates via `findByJobIdAndBranchIdOrderByCandidateIndexAsc(job.id, branchId)`.
4. Latest revision = `solutionRepository.findWithDetailsById(latestId)`; its assigned order
   set = order ids of `ORDER` stops across its routes.
5. Per candidate: `applicable = true` unless (first match wins)
   `NOT_ON_FRONTIER` (status != FRONTIER), `NO_PLAN` (planJson null),
   `ALREADY_ACTIVE` (latest.paretoCandidate id equals candidate id),
   `ORDER_SET_DIFFERS` (assigned order ids in planJson != latest assigned set).

### B4. Apply endpoint (PAR-008)

`POST /api/v1/solutions/{id}/pareto/{candidateId}/apply` -> `SolutionDetailResponseDTO`
(via existing `SolutionMapper`). `ParetoService.apply(solutionId, candidateId, branchId)`
(`@Transactional`), checks in this order:

1. Base: `findWithDetailsById(solutionId)` + `belongsTo` else 404.
2. Root + job (null job -> 404 for the candidate).
3. Candidate: `findByIdAndBranchId(candidateId, branchId)` and
   `candidate.job.id == root.job.id`, else 404 (same `RESOURCE_NOT_FOUND` error, resource
   `AppConstant.SOLUTION` or a new `PARETO_CANDIDATE` constant).
4. `latestId = routeCustomizationService.latestRevisionId(root.id, branchId)`;
   `base.id != latestId` -> `RevisionConflictException(message, latestId)` (409).
5. `base.paretoCandidate` is this candidate -> return `solutionMapper.toDTO(base)`; no save.
6. Status != FRONTIER or planJson null -> `UnprocessablePlanException` (422).
7. Parse planJson; assigned order ids != base assigned order ids -> 422
   ("order set differs").
8. Load orders (`orderRepository.findAllById`) and vehicles (`vehicleRepository.findAllById`);
   any missing, or any `order.branch.id` / `vehicle.fleet.branch.id` != `branchId` -> 422.
   (Verified: `Vehicle.fleet` is `@ManyToOne` non-null, `Fleet.branch` non-null.)
9. Build the revision: `branch = base.branch`, `parentSolution = base`, `job = null`,
   `type = PARETO_ALTERNATIVE`, `paretoCandidate = candidate`, totals and counts from the
   candidate row, `status = unserved > 0 ? PARTIAL_SUCCESS : SUCCESS`, `createdAt = now`;
   routes / stops / unassigned rows via `EnginePlanMaterializer`. `solutionRepository.save`.
10. No order status changes. No engine call.

Controller error mapping uses the existing `GlobalExceptionHandler` (404 via
`DataInvalidException` RESOURCE_NOT_FOUND, 409, 422) — verify the 404 mapping by reading
the handler and the existing controller tests; do not change the handler.

### B5. Solution DTO

`SolutionDetailResponseDTO`: add `Long paretoCandidateId`; `SolutionMapper`:
`@Mapping(source = "paretoCandidate.id", target = "paretoCandidateId")`.

---

## Failure and edge cases

| Case | Expected |
|---|---|
| Pareto job, N < threshold | dense matrix, `clusterAssignment == null`, payload `clustered=false`, `cluster_count=null` |
| Pareto job, N >= threshold and C > 1 | one block matrix, all 5 solves constrained, `clustered=true` |
| All candidates serve the same count | normal (cost, CO2) frontier |
| Only one candidate has max served | it is the only `FRONTIER`, it is selected |
| Two candidates with identical cost and CO2 | lower index FRONTIER, other DOMINATED |
| Frontier cost range 0 | normCost = 0 for all, CO2 decides |
| Cancel during scenario k | `JobCancelledException` as today; no partial frontier sent |
| Callback with two selected candidates | 400, job FAILED, no candidates saved |
| GET for a non-Pareto solution | 200, `candidates: []` |
| GET/POST with another branch's solution id | 404, no candidate lookup |
| POST with another job's or branch's candidate id | 404, no save |
| POST on a stale base | 409 with latest id, no save |
| POST for DOMINATED / SERVICE_DOMINATED candidate | 422, no save |
| POST when order sets differ | 422, no save |
| POST for the already active candidate | 200, returns base, no save |

---

## Tests (all new; DB-free; no GraphHopper)

### Engine

| # | Class / case | Setup | Expected |
|---|---|---|---|
| E-T1 | `ParetoFrontierSelectorTest.serviceFirst` | 3 candidates, one with served 9 of 10, cheapest and lowest CO2 | it is `SERVICE_DOMINATED`; others classified among themselves |
| E-T2 | `.dominance` | A(100, 10), B(120, 8), C(130, 12) same served | A, B FRONTIER; C DOMINATED |
| E-T3 | `.duplicates` | two identical vectors | lower index FRONTIER, other DOMINATED |
| E-T4 | `.selectUsesMinMaxWithoutBias` | frontier costs 1_000_000 / 1_100_000, CO2 10.0 / 8.0, weights (0.5, 0.5) | picks the candidate the unbiased formula picks; add a case where the old `+1` formula would pick the other one |
| E-T5 | `.selectZeroRangeAndTieBreak` | one frontier point; and two points with equal score | single point returned; tie -> lower cost |
| E-T6 | `.selectNoFrontierThrows` / `.classifyNullMetricsThrows` | | exceptions as specified |
| E-T7 | `SolutionConverterParetoTest.paretoBlock` | `OptimizationResult.pareto(...)` with 3 alternatives (FRONTIER selected, FRONTIER, DOMINATED), tiny `RouteDetail` lists, matrix null | `pareto.candidates` size 3; `routes` present only on the two FRONTIER entries; `selected` true only on one; top-level keys unchanged |
| E-T8 | `SolutionConverterParetoTest.singleHasNoParetoKey` | `OptimizationResult.single(...)` | no `"pareto"` key |
| E-T9 | `OptimizationServiceParetoTest.paretoBuildsClusteredMask` | Mockito `DistanceMatrixService`; request with 1000 orders, 10 vehicles, Pareto on; the mock captures the `MatrixMask` then throws a test exception | captured mask `isFull() == false` |
| E-T10 | `.smallParetoUsesFullMask` | same with 12 orders | captured mask `isFull() == true` |
| E-T11 | `.smallParetoEndToEnd` | 12 orders, 3 vehicles (two vehicle types with different emission factors), mock returns a dense Euclidean `DistanceMatrix`; `maxIterations = 20`, `numThreads = 1`, `seed = 1` | 5 alternatives; exactly one `selected`, and it is FRONTIER; every FRONTIER has non-null routes; `clusterCount == null`; handle `scenarioCount == 5` |
| E-T12 | `OptimizationControllerProgressTest` (standalone MockMvc or direct call) | handle with scenarioCount 5, scenarioIndex 2, snapshot iteration 50 / max 100 | `scenario = 3`, `scenarioCount = 5`, `percent = 50`; non-Pareto handle -> no scenario keys, percent = 50 |

For E-T9..E-T11 construct `new OptimizationService(mockDms, null, new JobRegistry())` and
call `optimize(request, registry.register(jobId))`. Build `DistanceMatrix` with
`new DistanceMatrix(coords, new DenseCostMatrix(dist, time))` (coordinates: the list the
service passes in). If E-T11 cannot run under ~30 s or cannot be made deterministic, stop
and report instead of weakening it.

### Entry

| # | Class / case | Setup | Expected |
|---|---|---|---|
| N-T1 | `OptimizationCallbackServiceParetoTest.savesCandidatesAndLinksSelected` | Mockito repos; callback with 3 candidates | 3 rows saved, planJson non-null exactly for FRONTIER, root solution's `paretoCandidate` is the selected row, order transitions called as today |
| N-T2 | `.rejectsTwoSelected` | 2 selected | `DataInvalidException`; candidate repo `saveAll` never called |
| N-T3 | `.singleCallbackUnchanged` | no `pareto` | candidate repo never touched |
| N-T4 | `ParetoServiceTest.getFrontier_otherBranch_notFound` | solution of branch 1, caller 2 | 404; candidate repo never called |
| N-T5 | `.getFrontier_nonPareto_empty` | root job without candidates | empty list |
| N-T6 | `.getFrontier_applicability` | 4 candidates: active, FRONTIER same set, FRONTIER different set, DOMINATED | reasons `ALREADY_ACTIVE`, applicable, `ORDER_SET_DIFFERS`, `NOT_ON_FRONTIER` |
| N-T7 | `.apply_createsParetoAlternativeRevision` | latest = base, FRONTIER same set | saved Solution: type PARETO_ALTERNATIVE, parent = base, job null, paretoCandidate = candidate, routes size as plan; order transition service never called |
| N-T8 | `.apply_staleBase_409` | latest != base | `RevisionConflictException` with latest id; no save |
| N-T9 | `.apply_notFrontier_422` / `.apply_orderSetDiffers_422` | | `UnprocessablePlanException`; no save |
| N-T10 | `.apply_otherBranchSolution_404` / `.apply_candidateOfOtherJob_404` / `.apply_candidateOfOtherBranch_404` | | 404; no save |
| N-T11 | `.apply_alreadyActive_returnsBaseWithoutSave` | base.paretoCandidate = candidate | DTO of base; no save |
| N-T12 | `ParetoControllerTest` (standalone MockMvc + `GlobalExceptionHandler`, pattern of `SolutionCustomizationControllerTest`) | service mocked | GET 200; POST 200; 404 / 409 / 422 envelopes |

If a test needs a Spring context or MySQL, stop and report.

---

## Scientific statement to keep in code comments and the handoff

Cluster-first in the Pareto branch is a problem approximation (AGENTS.md §9). All five
weight points share one vehicle-to-cluster assignment that ignores emission factors, so the
reported frontier is the frontier of the clustered problem, not of the original problem.
No claim of equal quality to the dense Pareto branch is made; not verified.
