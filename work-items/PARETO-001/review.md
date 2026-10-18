# Review — PARETO-001

Reviewer: Claude (independent of the implementer), 2026-10-18 ~17:10 (+07:00).
Method: static reading of every changed/new source and test file on the working tree,
`run.json`, `.git/HEAD`, and byte comparison of the 37 pre-existing test files against the
snapshot taken on 2026-10-18 before implementation. Tests were **not** re-executed by the
reviewer (no shell on the user's machine); test counts are taken from `implementation.md`.

## State

- `.git/HEAD` = `refs/heads/feature/pareto-001` ✔
- `run.json` state = `verify`, revision 4, scope `GVRP_Engine_API/src`, `GVRP_Entry_API/src` ✔
- Working tree uncommitted (expected at this node).

## Inspected files

Engine: `OptimizationService` (optimize, optimizeMultiObjective, clusterCount),
`ParetoFrontierSelector`, `ParetoAlternative`, `OptimizationResult`, `SolutionConverter`,
`JobRegistry`, `OptimizationController`; tests `ParetoFrontierSelectorTest`,
`OptimizationServiceParetoTest`.
Entry: `ParetoService`, `ParetoController`, `ParetoCandidate`, `ParetoCandidateRepository`,
`EnginePlanMaterializer`, `OptimizationCallbackService`, `SolutionMapper`,
`ParetoFrontierDTO`, `ParetoCandidateDTO`, migration `2026-10-18-pareto-candidates.sql`;
tests `ParetoServiceTest`, `OptimizationCallbackServiceParetoTest`.

## Test integrity

- 22 Engine + 15 Entry pre-existing test files: content **identical** to the snapshot after
  line-ending normalisation. 8 Entry files differ only in line endings (LF → CRLF:
  SolutionCustomizationControllerTest, JwtAuthenticationFilterTest, RoleAccessTest,
  FleetServiceTest, RouteCustomizationServiceTest, RouteSendServiceTest, SolutionServiceTest,
  VehicleServiceTest). Consistent with a git checkout under `core.autocrlf` after the
  master fast-forward; `git diff --name-only -- "*/src/test/*"` reported empty. Not a test
  modification.
- `JspritPerformanceBenchmarkTest`: its `@Disabled` was already commented out in the
  snapshot (line 161) — pre-existing, not introduced by this run.
- No `@Disabled`, skip, focus, or pom/CI exclusion added.

## Findings

| # | Severity | Finding | Verdict |
|---|---|---|---|
| F1 | Medium (process) | A **third** Engine test, `JspritPerformanceBenchmarkTest`, was excluded from the Step 3 run. The user approved only `JspritConvergenceBenchmarkTest` and `CircuityFactorMeasurementTest`; implementation.md calls the third an "extension" without a recorded approval. Justification is valid (it builds the real 1001×1001 matrix), but AGENTS.md §14 needs explicit approval per test. | Needs user confirmation |
| F2 | Medium (deploy) | Migration order: with `ddl-auto=update`, starting Entry on this branch **before** running the SQL makes Hibernate create `pareto_candidates` and `solutions.pareto_candidate_id`; the script's `ALTER TABLE solutions ADD COLUMN ...` then fails and, if run as one script, the `MODIFY COLUMN type ENUM(...)` after it never runs → inserting a `PARETO_ALTERNATIVE` revision fails. Mitigation: run the SQL before starting Entry, or run the `MODIFY COLUMN type` statement on its own. | Accept with manual-test step |
| F3 | Low (spec) | Not-found answers HTTP **400** with code `0040402`, not 404 — `GlobalExceptionHandler` maps `DataInvalidException` to 400 for every endpoint. Consistent with the repo; foreign and missing ids are indistinguishable (tenant rule satisfied). PARETO-002 must treat 400 + `0040402` as not-found. | Accept |
| F4 | Low | Callback validates the Pareto block **after** order-status bulk transitions inside the same transaction. A validation failure rolls everything back, so no data effect; validating first would be cleaner. | Accept |
| F5 | Low | Entity `ParetoCandidate` does not declare `uk_pareto_job_index`; only the SQL does. If Hibernate creates the table (see F2) the unique constraint is missing. | Accept (follow-up) |
| F6 | Low (test strength) | E-T11 observed all 5 scenarios collapsing to one frontier point on the synthetic matrix, so extraction of ≥2 frontier plans is not exercised end-to-end; E-T4 case (b) relies on the tie-break rather than a strict win. Selector logic itself is correct by inspection. | Accept; covered by manual test |
| F7 | Info | `plan_json` round-trip is tested with a plain `ObjectMapper` (camelCase); production uses the injected snake_case mapper in both directions (symmetric by construction). Verify in the manual test that apply reads a real stored plan. | Manual test |

## Checks that passed (by inspection)

- A1: cluster assignment and matrix built once in `optimize`, shared by both branches; every
  Pareto weight point calls `createAlgorithm(..., clusterAssignment, ...)` and the diagnosis
  receives the assignment. Constructor and `createAlgorithm` signature unchanged.
- A2: classification is service-first; dominance and duplicate rules correct (marking during
  the loop is safe because dominance is transitive). Selection has no `+1`, zero-range guard,
  tie-break lower cost then lower index; throws when no frontier.
- A4/A5: routes extracted for every FRONTIER point; top-level routes are the selected
  candidate's; `"pareto"` block only for `MULTI_PARETO`; routes only on FRONTIER rows.
- A6: `ProgressSnapshot` unchanged; overall percent uses long arithmetic; non-Pareto keys
  unchanged.
- B2: validation (unique indices, exactly one selected, selected FRONTIER, known status);
  `plan_json` only for FRONTIER; root solution linked to the selected row; single callbacks
  untouched.
- B3/B4: tenant check first on both endpoints; candidate looked up with branch **and** job;
  409 before any mutation; idempotent when already active; 422 for non-frontier / different
  order set / foreign order or vehicle; no order-status write; no engine call. Branch taken
  from `CurrentUserUtil`; endpoints fall under the existing PLANNER rule; no `permitAll`.
- No secrets, tokens or coordinates logged; no dependency change.

## Verdict

**PASS with conditions**: F1 needs the user's explicit approval of the third excluded test;
F2 and F7 are covered by the manual test steps (migration first). No blocking code defect
found.
