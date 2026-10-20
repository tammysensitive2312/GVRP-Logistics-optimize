# Verification — PARETO-002

## Environment

| Item | Value |
|---|---|
| Repository | `E:\OR-tools java example\GVRP-Logistics_Optimize` |
| Revision | `4e2f928` (branch `feature/pareto-002`) **+ uncommitted working-tree changes** — nothing was committed for this work item |
| Client | `GVRP_Entry_client_v2`, Angular 18.2.14, Node v22.23.2, npm 10.9.8 |
| Stack used for the live run | client `ng serve` on `:4200` (proxy `proxy.conf.json` → `:8080`), Entry API `:8080`, Engine API `:8081`, MySQL dev DB `:3306` |
| Browser | browser-skill 0.3.3 (`bsk`), Edge 155, sessions `tbjv` and `zqsp` (the daemon restarted mid-run, so two sessions) |
| Tenant used | dev branch "Branch Huge 1", planner user (credentials intentionally not recorded) |

## Checks

| Check | Result | Evidence |
|---|---|---|
| `npm test -- --watch=false --browsers=ChromeHeadless` (cwd `GVRP_Entry_client_v2`) | 220 specs, 215 SUCCESS, **5 failures — exactly the 5 pre-existing names**; baseline was 186 / 181 SUCCESS / same 5 | `implementation.md` §Verification Results; orchestrator logs `pareto002-test-baseline.log`, `pareto002-test-GATE.log` |
| `npm run build` | exit **1**, unchanged from baseline; the only ERROR is the pre-existing `login.component.scss` budget error (`angular.json` forbidden, deviation 1). Error/warning sets identical to baseline (11 `exceeded maximum budget` lines both runs); bundle 665.23 → 665.41 kB (+0.18 kB, warning only) | logs `pareto002-build-baseline.log`, `pareto002-build-GATE.log` |
| Test integrity (AGENTS.md §14) | **no existing spec or `src/testing/` file modified**: `git diff --name-only -- "*/src/**/*.spec.ts" "*/src/testing/*"` is empty; all 6 new spec files are untracked | `implementation.md` §Modified Tests; re-checked in this node |
| Dependency / forbidden-file discipline | no `package.json`/lockfile change, no new npm dependency, no `angular.json` / `proxy.conf.json` / i18n / storage change; `git status` shows 11 modified + 5 new source files under `GVRP_Entry_client_v2/src` and nothing else in scope | `implementation.md` §Changes Made; `git status --porcelain -- GVRP_Entry_client_v2/src` |
| Live browser run (UI-facing change) | executed; see below | `implementation.md` §Live End-to-End Browser Verification |

## Requirement coverage

Per `traceability.md` (statuses updated in this node):

- **VERIFIED (unit + live):** PUI-001, PUI-003, PUI-004, PUI-005.
- **VERIFIED_WITH_LIMITATION:** PUI-002 (`SERVICE_DOMINATED` marker never produced by a live run),
  PUI-007 (scenario progress live; the planning-dialog "about n times longer" estimate line not read
  live), PUI-009 (specs/unchanged tests verified; build remains red on the pre-existing error).
- **PARTIALLY_VERIFIED:** PUI-006 (clustered note: unit evidence only, no live clustered run),
  PUI-008 (send-route `PARETO_ALTERNATIVE` label: unit evidence only, dialog not opened live).

## Live browser run — session steps and observations

1. Log in as the dev planner; open the **Orders** screen, filter the delivery date to `2026-08-21`
   (the data date; the default "today" filter yields 0 rows), select **SCHEDULED** orders only.
2. Route Planning dialog: tick **Enable Pareto Analysis**; the request captured in-page carried
   `preferences.enable_pareto_analysis: true`, `speed: "FAST"`. Submit.
3. Job monitoring: observed **"Scenario k of 5 · Solving…"** with `.scenario-dots` states
   `DCPPP` (31 %) → `DDDCP` (71 %) → `DDDDC` (100 %) on a real job; afterwards the result card shows
   the Pareto note.
4. Reopen the Orders section: the **Pareto** tab appears (it is fetched through
   `GET /solutions/{id}/pareto`, also via the deep link `/?solution=<id>`).
5. Inspect the chart DOM against the DB: 4 frontier + 1 dominated point, frontier path in cost order
   excluding the dominated point, `.active-ring` on the active candidate, `.engine-star` on the
   `engine_selected` candidate, axis ticks/titles/legend.
6. Inspect the candidate list and selection panel; recompute every delta from the DB `total_*`
   columns (all matched, including the hour/minute formatting).
7. Apply flow, all outcomes: **Cancel** (no new row — `MAX(solutions)` unchanged); **409**
   (`POST /solutions/61/pareto/4/apply`, and later `/64/pareto/3/apply`) with the banner
   "A newer revision exists. | Load latest revision"; the banner button →
   `GET /solutions/64/latest-revision` → `GET /solutions/67` + `/solutions/67/pareto`; and **200**
   (`POST /solutions/64/pareto/4/apply`) → toast "Plan PARETO_K3_OF_4 applied (revision #67)".
8. Verify revision #67 in the DB: `PARETO_ALTERNATIVE`, `job_id` NULL, `parent_solution_id` 64,
   `pareto_candidate_id` 4, metrics equal to candidate 4, 4 routes / 42 stop rows / 34 distinct
   orders, **all 34 orders still `ON_ROUTE`** (order statuses untouched, as designed).

Full detail, including the degenerate five-identical-candidate case and the observations handed to
review, is in `implementation.md` §Live End-to-End Browser Verification.

## Not executed

- No live run produced a `SERVICE_DOMINATED` candidate (marker, chip, reason text unverified live).
- 422 paths (`NO_PLAN`, non-frontier, `ORDER_SET_DIFFERS`) and the 400 `0040402` "Plan not found"
  toast: not triggered live.
- No live clustered Pareto job (PUI-006 chip with real data).
- The send-route revision picker was not opened live (PUI-008).
- `npm run build` cannot exit 0 in this repository until the pre-existing
  `login.component.scss` budget error is resolved (out of scope; deviation 1).

## Limitations of this verification

- The live run is evidence about the **dirty working tree**, not about `4e2f928` or any commit; the
  repository was left uncommitted deliberately.
- Reviewer independence has **not** been exercised for this work item: the live run and this record
  were produced by the same orchestrator that dispatched the implementation. A separate
  `gvrp-reviewer` pass is recommended before acceptance (see `implementation.md` §Handoff to
  Reviewer).
- The verification covers the dev database only, with test data; the dev DB was mutated by the run
  (jobs #86/#87, solutions 65/66/67, served orders set to `ON_ROUTE`) and was not rolled back.
