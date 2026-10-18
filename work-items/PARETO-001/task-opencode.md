# Task for the OpenCode engineer — PARETO-001

You are `gvrp-engineer`. Implementation is authorized by the user. Follow `AGENTS.md` in
full (sections 14 and 15). Run every command yourself from the repository root unless a
step says otherwise.

## Read first
1. `work-items/PARETO-001/request.md`
2. `work-items/PARETO-001/context.md`
3. `work-items/PARETO-001/design.md` — the specification.

If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `git status --short`. If anything is modified or untracked outside
   `work-items/PARETO-001/`, `work-items/PARETO-002/`, `.claude/`, `.mcp.json`, stop and
   report the list.
2. `git switch -c feature/pareto-001`

## Step 1 — workflow ledger (before editing code)
```
node tools/feature-workflow/workflow.mjs init work-items/PARETO-001/run.json . PARETO-001 GVRP_Engine_API/src GVRP_Entry_API/src
node tools/feature-workflow/workflow.mjs step work-items/PARETO-001/run.json ready work-items/PARETO-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/PARETO-001/run.json ready work-items/PARETO-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/PARETO-001/run.json ready work-items/PARETO-001/03-proof.json
```
Each command must print a new state (`discover`, `design`, `implement`). On failure stop
and report the output.

## Step 2 — baseline
User decision 2026-10-18 15:19 (+07:00): the two Engine measurement tests that build a real
1001x1001 GraphHopper matrix are excluded from the per-step runs of this work item only:
`JspritConvergenceBenchmarkTest`, `CircuityFactorMeasurementTest`. They are not edited,
disabled or removed. Their exclusion must be listed under "Deviations From Design".

Define once (PowerShell):
```
$engineTests = '-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest'
```
- Engine: the partial run of 2026-10-18 13:59–14:39 is accepted as baseline for the tests it
  completed (all passed). Run once more `mvnw.cmd -B test "$engineTests"` only if needed to
  get the counts for the remaining fast tests; record the command and counts.
- Entry: `mvnw.cmd -B test` in full (not run yet; required).
Record per module: tests run / failures / errors / skipped and failing test names.
Pre-existing failures are recorded, not fixed.

## Allowed changes
Engine (`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/`):
- `service/OptimizationService.java`, `service/SolutionConverter.java`
- new `service/ParetoFrontierSelector.java`
- `model/OptimizationResult.java`, new `model/ParetoAlternative.java`, new `model/FrontierStatus.java`
- `job/JobRegistry.java` (JobHandle fields only), `controller/OptimizationController.java` (progress only)
- `utils/AppConstant.java` (two constants)

Entry (`GVRP_Entry_API/src/main/`):
- `java/.../dto/request/EngineCallbackRequest.java`
- `java/.../service/OptimizationCallbackService.java`, new `java/.../service/EnginePlanMaterializer.java`
- new `java/.../service/ParetoService.java`, new `java/.../controller/ParetoController.java`
- new `java/.../entity/ParetoCandidate.java`, new `java/.../entity/enums/ParetoFrontierStatus.java`
- `java/.../entity/Solution.java`, `java/.../entity/enums/SolutionType.java`
- new `java/.../repository/ParetoCandidateRepository.java`
- new DTOs `java/.../dto/response/ParetoFrontierDTO.java`, `ParetoCandidateDTO.java`
- `java/.../dto/response/SolutionDetailResponseDTO.java`, `java/.../mapper/SolutionMapper.java`
- `java/.../util/AppConstant.java` (optional `PARETO_CANDIDATE` resource constant)
- new `resources/database/migrations/2026-10-18-pareto-candidates.sql`

New test files only (any name), under `GVRP_Engine_API/src/test/java/...` and
`GVRP_Entry_API/src/test/java/...`, covering E-T1..E-T12 and N-T1..N-T12.

Work-item file you write: `work-items/PARETO-001/implementation.md`.

## Forbidden
- Editing, renaming, disabling, skipping or excluding any existing test, fixture or test
  helper (37 files were snapshotted and will be byte-compared).
- Changing the `OptimizationService` constructor, the private `createAlgorithm(...)`
  signature, or any static member used by existing tests.
- Changing `JobRegistry.ProgressSnapshot`, `JobCleanupService`, `SolutionController`,
  `RouteCustomizationService`, `SecurityConfig`, `GlobalExceptionHandler`.
- Tests needing MySQL, a Spring application context with a datasource, a GraphHopper
  graph, or any running service.
- Any dependency change (`pom.xml`), `application*.properties`, `.github/`, `AGENTS.md`,
  `docs/`, client code.
- Committing, pushing, rewriting history. Logging or reporting tokens, API keys or
  customer coordinates.
- Running the SQL migration against any database (the user does that).

## Step 3 — implement
Implement per `design.md` (Part A, then Part B). Then `mvnw.cmd -B clean test "$engineTests"`
in `GVRP_Engine_API` and `mvnw.cmd -B clean test` in `GVRP_Entry_API` — all green, new tests
included. If surefire reports "No tests were executed" or ignores the `!` pattern, stop and
report instead of changing pom.xml. If E-T11 cannot be made
deterministic and under ~30 s, stop and report.

## Step 4 — implementation record
Write `work-items/PARETO-001/implementation.md` with sections:
Scope, Baseline, Changes Made (file by file), Commands Executed (with exit codes,
including Steps 0–1), Verification Results (test counts per module, new test names),
Modified Tests (must be "none"), Security-Relevant Changes (new endpoints, branch checks,
no permitAll), Deviations From Design, Remaining Uncertainty, Handoff to Reviewer.

Then:
```
node tools/feature-workflow/workflow.mjs step work-items/PARETO-001/run.json ready work-items/PARETO-001/04-proof.json
```
It must print state `verify`. Do not go further than `verify`. Tell the user you are done.
