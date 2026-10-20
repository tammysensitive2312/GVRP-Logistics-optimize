# Implementation — PARETO-001

Work item: `PARETO-001` (Pareto branch backend). Branch `feature/pareto-001`, base revision
`e98ec95`. Working tree **DIRTY** (uncommitted); no commit/push performed.

Scientific note (AGENTS.md §9): cluster-first in the Pareto branch is a **problem
approximation**, not a representation change. All five weight points share one
vehicle-to-cluster assignment that ignores emission factors, so the reported frontier is the
frontier of the clustered problem, not of the original problem. No claim of equal quality to
the dense Pareto branch is made. **Not verified.**

---

## Scope

- Engine (`GVRP_Engine_API`): Part A of `design.md` (A1–A6) — one cluster plan reused by both
  branches; `FrontierStatus` + `ParetoFrontierSelector`; `ParetoAlternative` and
  `OptimizationResult` fields; route extraction for every FRONTIER point; `"pareto"` callback
  block; scenario progress.
- Entry (`GVRP_Entry_API`): Part B of `design.md` (B1–B5) — schema + entities + repository;
  callback ingestion and persistence of candidates; read and apply endpoints.
- New tests only: E-T1..E-T12 (Engine), N-T1..N-T12 (Entry).
- Out of scope: client v2 (PARETO-002), any dependency change, running the SQL migration.

---

## Baseline

| Module | Command | Result |
|---|---|---|
| Entry | `mvnw.cmd -B test` | 86 run, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS (exit 0) |
| Engine (fast tests) | `mvnw.cmd -B test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest"` | every fast class passed; the run was stopped while `JspritPerformanceBenchmarkTest` built the real 1001×1001 matrix |

- The partial Engine run of 2026-10-18 13:59–14:39 is accepted as baseline for the tests it
  completed (all passed), including `CircuityFactorMeasurementTest` and
  `JspritConvergenceBenchmarkTest`.
- No pre-existing failing test was observed in either module.
- Baseline logs kept (gitignored, repo root): `entry-baseline.log`, `engine-baseline.log`,
  `engine-fast-baseline.log`.

---

## Changes Made

### Engine — new (main)

| File (under `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/`) | Purpose |
|---|---|
| `model/FrontierStatus.java` | enum `FRONTIER, DOMINATED, SERVICE_DOMINATED` (A2) |
| `model/ParetoAlternative.java` | result item: `index, candidate, status, selected, routes, unassignedOrders` (A3) |
| `service/ParetoFrontierSelector.java` | pure static `classify` / `select` (A2) |

### Engine — modified (main)

| File | Change |
|---|---|
| `service/OptimizationService.java` | A1: `optimize` builds the cluster assignment and matrix **once** and shares them with both branches; `optimizeMultiObjective` gains `clusterAssignment`, classifies/selects the frontier, extracts routes for every FRONTIER point; deleted `buildParetoFrontier` / `selectFromPareto`; `clusterCount` helper; scenario-count log line fixed to `weightPoints.size()` |
| `service/SolutionConverter.java` | A5: `"pareto"` block emitted only for `MULTI_PARETO` with non-null alternatives; `routes`/`unassigned_orders` only on FRONTIER candidates; single mode byte-identical key set |
| `model/OptimizationResult.java` | A3: `paretoAlternatives`, `clusterCount` (`NON_NULL`); `pareto(...)` factory signature updated; `single(...)` unchanged |
| `job/JobRegistry.java` | A6: `JobHandle.scenarioIndex` / `scenarioCount` + accessors (pure additions; `ProgressSnapshot` unchanged) |
| `controller/OptimizationController.java` | A6: `progress` adds `scenario`, `scenarioCount` and overall `percent` for Pareto runs |
| `utils/AppConstant.java` | `PARETO_SAMPLER_N = 4`, `PARETO_SAMPLER_P = 2.0` |

### Entry — new (main)

| File (under `GVRP_Entry_API/src/main/`) | Purpose |
|---|---|
| `java/.../entity/ParetoCandidate.java` | candidate row (job, branch, metrics, weights, status, `planJson`, `engineSelected`, `clustered`, `clusterCount`) |
| `java/.../entity/enums/ParetoFrontierStatus.java` | `FRONTIER, DOMINATED, SERVICE_DOMINATED` |
| `java/.../repository/ParetoCandidateRepository.java` | `findByJobIdAndBranchIdOrderByCandidateIndexAsc`, `findByIdAndBranchId` |
| `java/.../service/EnginePlanMaterializer.java` | route/stop/unassigned builders extracted from the callback service (B2) |
| `java/.../service/ParetoService.java` | `getFrontier`, `apply` (B3, B4) |
| `java/.../controller/ParetoController.java` | `GET /api/v1/solutions/{id}/pareto`, `POST /api/v1/solutions/{id}/pareto/{candidateId}/apply` |
| `java/.../dto/response/ParetoFrontierDTO.java` | frontier envelope (no `plan_json`) |
| `java/.../dto/response/ParetoCandidateDTO.java` | candidate + `applicable` / `not_applicable_reason` |
| `resources/database/migrations/2026-10-18-pareto-candidates.sql` | table + `solutions.pareto_candidate_id` + `solutions.type` ENUM widening — **written, NOT executed** |

### Entry — modified (main)

| File | Change |
|---|---|
| `java/.../dto/request/EngineCallbackRequest.java` | `SolutionData.pareto` + nested `ParetoData` / `ParetoCandidateData` |
| `java/.../service/OptimizationCallbackService.java` | candidate validation (unique indices, exactly one `selected`, selected is `FRONTIER`, statuses parse), persists candidates, links the selected row to the root solution; private builders now delegated to `EnginePlanMaterializer` |
| `java/.../entity/Solution.java` | `paretoCandidate` `@ManyToOne(LAZY)` + `@OnDelete(SET_NULL)` |
| `java/.../entity/enums/SolutionType.java` | `PARETO_ALTERNATIVE` |
| `java/.../dto/response/SolutionDetailResponseDTO.java` | `paretoCandidateId` |
| `java/.../mapper/SolutionMapper.java` | `paretoCandidate.id` → `paretoCandidateId` |
| `java/.../util/AppConstant.java` | `PARETO_CANDIDATE` resource constant |

---

## Commands Executed

### Step 0 — preconditions and branch

| Command | Exit | Result |
|---|---|---|
| `git status --short` | 0 | only `work-items/PARETO-00{1,2}/` and `.mcp.json` untracked — nothing else |
| `git switch -c feature/pareto-001` | 0 | branch created from `e98ec95` |

### Step 1 — workflow ledger

| Command | Exit | Printed state |
|---|---|---|
| `node tools/feature-workflow/workflow.mjs init work-items/PARETO-001/run.json . PARETO-001 GVRP_Engine_API/src GVRP_Entry_API/src` | 0 | `intake` |
| `... step work-items/PARETO-001/run.json ready work-items/PARETO-001/01-proof.json` | 0 | `discover` |
| `... step work-items/PARETO-001/run.json ready work-items/PARETO-001/02-proof.json` | 0 | `design` |
| `... step work-items/PARETO-001/run.json ready work-items/PARETO-001/03-proof.json` | 0 | `implement` |

### Step 2 — baseline

| Command | Exit |
|---|---|
| (Entry) `mvnw.cmd -B test` | 0 |
| (Engine) `mvnw.cmd -B test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest"` | stopped after all fast tests passed |

### Step 3 — verification

| Command | Exit | Result |
|---|---|---|
| (Engine) `mvnw.cmd -B test-compile` | 0 | BUILD SUCCESS |
| (Engine) `mvnw.cmd -B clean test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest,!JspritPerformanceBenchmarkTest"` | 0 | **Tests run: 136, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |
| (Entry) `mvnw.cmd -B clean test` | 0 | **Tests run: 105, Failures: 0, Errors: 0, Skipped: 0** — BUILD SUCCESS |

Java 21 (`JAVA_HOME='C:\Program Files\Java\jdk-21'`) for every Maven invocation. Logs kept:
`engine-step3.log`, `entry-step3.log` (repo root, gitignored).

---

## Verification Results

### New tests

| # | Class | Tests | Result |
|---|---|---|---|
| E-T1..E-T6 | `ParetoFrontierSelectorTest` | 7 | pass |
| E-T7, E-T8 | `SolutionConverterParetoTest` | 2 | pass |
| E-T9..E-T11 | `OptimizationServiceParetoTest` | 3 | pass |
| E-T12 | `OptimizationControllerProgressTest` | 2 | pass |
| N-T1..N-T3 | `OptimizationCallbackServiceParetoTest` | 3 | pass |
| N-T4..N-T11 | `ParetoServiceTest` | 11 | pass |
| N-T12 | `ParetoControllerTest` | 5 | pass |

Absolute paths:
- `GVRP_Engine_API/src/test/java/org/truong/gvrp_engine_api/service/ParetoFrontierSelectorTest.java`
- `GVRP_Engine_API/src/test/java/org/truong/gvrp_engine_api/service/SolutionConverterParetoTest.java`
- `GVRP_Engine_API/src/test/java/org/truong/gvrp_engine_api/service/OptimizationServiceParetoTest.java`
- `GVRP_Engine_API/src/test/java/org/truong/gvrp_engine_api/controller/OptimizationControllerProgressTest.java`
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/OptimizationCallbackServiceParetoTest.java`
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/ParetoServiceTest.java`
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/controller/ParetoControllerTest.java`

### Counts

| Module | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| Engine (3 heavy tests excluded) | 136 | 0 | 0 | 0 |
| Entry | 105 | 0 | 0 | 0 |

Entry delta from baseline: 86 → 105 (+19, all new). Engine: all pre-existing fast tests plus
+14 new.

### Orchestrator spot-checks on the produced tree

- `git diff --name-only -- "*/src/test/*"` → **empty** (no existing test/fixture/helper changed).
- `SecurityConfig`, `GlobalExceptionHandler`, `SolutionController`, `RouteCustomizationService`,
  `JobCleanupService` → **not modified**.
- No `+permitAll` line added anywhere in the diff.
- `job/JobRegistry.java` diff is 14 insertions / 0 deletions → `ProgressSnapshot` untouched.
- `ParetoFrontierSelector` contains `+1` only inside a Javadoc sentence about the legacy
  formula; no `+1` in executable code.
- Both Entry endpoints take the branch from `CurrentUserUtil.getCurrentBranchId()`;
  `ParetoService` additionally checks `SolutionService.belongsTo`, candidate
  `findByIdAndBranchId`, and order/vehicle branch ownership.

---

## Modified Tests

**none.** Four new Engine test files and three new Entry test files were added; no existing
test, fixture, test helper, build file or CI command was edited, renamed, disabled, skipped or
excluded.

---

## Security-Relevant Changes

- Two new authenticated endpoints, both under the existing `/api/v1/solutions/**` rule
  (PLANNER-only):
  - `GET /api/v1/solutions/{id}/pareto`
  - `POST /api/v1/solutions/{id}/pareto/{candidateId}/apply`
- **No** `SecurityConfig` change and **no** new `permitAll`.
- Branch is always taken from the authenticated principal; never from a request body, path or
  query parameter.
- Tenant isolation: solution access via `SolutionService.belongsTo`; candidate access via
  `findByIdAndBranchId` plus a job-ownership check; applied orders/vehicles are re-checked for
  branch ownership (`order.branch.id`, `vehicle.fleet.branch.id`).
- No new dependency; no secret, token, or customer coordinate written to code, tests or logs.

---

## Deviations From Design

1. **Three Engine tests excluded from per-step runs (user-authorized).** By user decision
   (2026-10-18 15:19, extended 2026-10-18 for this work item), the tests that build a real
   1001×1001 GraphHopper matrix are excluded from the per-step runs:
   `JspritConvergenceBenchmarkTest`, `CircuityFactorMeasurementTest`, and — discovered during
   implementation — `JspritPerformanceBenchmarkTest` (its `@Disabled` is commented out at
   line 161, so it also builds the real matrix). These tests are **not** edited, disabled or
   removed; they are only filtered out of the per-step commands via
   `-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest,!JspritPerformanceBenchmarkTest`.
   Their pass/fail status is therefore **not covered** by this work item.
2. **Not-found maps to HTTP 400, not 404.** `design.md` expects a 404 envelope for a foreign
   or missing solution/candidate, but `GlobalExceptionHandler` maps `DataInvalidException` to
   HTTP **400** (body code `ErrorCode.RESOURCE_NOT_FOUND` = `"0040402"`) and maps no
   exception to 404. Per instruction, the handler was **not** changed: N-T4/N-T10 assert at
   the service level (`DataInvalidException`, code `0040402`), and N-T12 asserts the actual
   HTTP **400**. (This matches the existing `SolutionServiceTest` pattern.)
3. **E-T9/E-T10** assert the clustering decision through a captured `MatrixMask` (the mock
   matrix service throws after capture) instead of executing a 1000-order optimization; this
   avoids the real-matrix path while still exercising A1's mask-selection code.
4. **E-T11** asserts exactly one `selected` (a FRONTIER point), that every FRONTIER point has
   non-null routes, `clusterCount == null`, and `scenarioCount == 5`. It does **not** assert
   `frontier size > 1`: under the test's synthetic dense Euclidean matrix all five candidates
   collapse to a single non-dominated point (observed). Frontier size ≥ 2 is covered only by
   the `ParetoFrontierSelector` unit tests.
5. **Progress `percent` type.** For Pareto runs `percent` is computed with long arithmetic
   over `scenarioCount × maxIterations`; for non-Pareto runs the formula is unchanged. The
   value is emitted as a numeric `Object` (Integer / Long); tests compare via `Number`.
6. **N-T7 / N-T2 test mechanics.** N-T7 proves "no order-status write" by verifying the order
   repository is never called (ParetoService deliberately has no `OrderStatusTransitionService`
   dependency). N-T2 wraps the call in `TransactionSynchronizationManager` so the callback's
   `registerSynchronization` works without a running transaction.
7. **E-T11 fixture detail.** Test orders set `serviceTime(0)` to avoid a test-only NPE in
   `buildJspritService`; production code was not changed.
8. **Process.** The Engineer ran a targeted fast filter during development; the full Engine
   Step 3 suite (`clean test` with the three exclusions) was executed by the orchestrator.

---

## Remaining Uncertainty

- The SQL migration is written but **not applied** and **not verified** against MySQL
  (`ddl-auto=update` cannot widen the `solutions.type` ENUM). Applying it is a user action.
- No end-to-end/integration test: no real MySQL, no real GraphHopper graph, no running engine.
  All new tests are unit / standalone-MockMvc and DB-free, as required.
- Entry-side ingestion was unit-tested, but the Engine→Entry `"pareto"` payload has not been
  round-tripped through an actual callback.
- `OptimizationResult.clusterCount` non-null path is only exercised indirectly (converter
  test); not asserted end-to-end.
- Frontier with ≥ 2 non-dominated points is verified only at the selector unit level.

---

## Handoff to Reviewer

- **Claim under review:** PARETO-001 Part A and Part B are implemented per `design.md`, no
  existing behaviour/test was altered, and all new and existing tests pass.
- **Verification type:** experimental / runtime (JUnit execution) plus static trace.
- **Revision:** `e98ec95`, branch `feature/pareto-001`, **working tree DIRTY** (all PARETO-001
  changes uncommitted). The result is therefore not reproducible from the recorded commit
  alone; the diff must be captured alongside it.
- **Reproduce (Windows, `JAVA_HOME='C:\Program Files\Java\jdk-21'`):**
  - Engine, workdir `GVRP_Engine_API`:
    `mvnw.cmd -B clean test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest,!JspritPerformanceBenchmarkTest"`
    → expect 136 run, 0 failures.
  - Entry, workdir `GVRP_Entry_API`: `mvnw.cmd -B clean test` → expect 105 run, 0 failures.
- **Artifacts:** the files listed under *Changes Made*; logs `engine-step3.log`,
  `entry-step3.log`.
- **Do not treat this summary as proof.** Independently inspect at least: A1 clustering/matrix
  sharing in `OptimizationService.optimize`; `ParetoFrontierSelector.select` (no residual
  `+1` bias, zero-range guard, tie-break); `SolutionConverter` conditional `"pareto"` block;
  the callback validation rules and `planJson` handling; the `apply` ordering of checks
  (base → root/job → candidate → latest revision → already-active → FRONTIER/plan → order set
  → branch ownership) and that it performs no order-status writes and no engine call.
- Explicit non-goals: PARETO-002 (client), migration execution, commit/push.
