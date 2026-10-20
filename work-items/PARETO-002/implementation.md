# Implementation — PARETO-002 (client v2: Pareto display and plan switching)

Work item: PARETO-002. Branch `feature/pareto-002` at HEAD `4e2f928` (base = `master`,
which already contains PARETO-001). Working tree DIRTY (nothing committed).
Specification: `work-items/PARETO-002/design.md`. Dispatch: `work-items/PARETO-002/task-opencode.md`.

Verdict requested by this document: none. This is the implementation record; the run stops at
workflow state `verify` (Reviewer is a separate step).

---

## Scope

Client-only feature for `GVRP_Entry_client_v2` (Angular 18.2, standalone components, signals,
Angular Material 18, Transloco). Adds the "Pareto" tab to the Orders section, the inline-SVG
cost/CO₂ trade-off chart, the candidate list and comparison panel, the apply flow (with 409 /
422 handling) and the job-monitoring scenario progress. No backend change, no new npm
dependency, no storage use. All changes are under `GVRP_Entry_client_v2/src/app/` exactly as
listed in `task-opencode.md` "Allowed changes".

API consumed (already merged in PARETO-001, unchanged here): `GET /solutions/{id}/pareto`,
`POST /solutions/{id}/pareto/{candidateId}/apply`, progress keys `scenario` / `scenarioCount`,
`SolutionDTO.pareto_candidate_id`, `SolutionDTO.type`. JSON is snake_case. Not-found is HTTP
**400** with `errors[0].code = "0040402"`.

---

## Baseline

Measured by the orchestrator on the unmodified revision (`4e2f928`, before any `src/` edit),
in `GVRP_Entry_client_v2`:

- `npm test -- --watch=false --browsers=ChromeHeadless` → **exit 1**: `TOTAL: 5 FAILED, 181 SUCCESS` (186 executed).
  The 5 failures are pre-existing and match the DRIVER-MAP-004 record (FleetSetup ×1, Navbar ×1, CollectionCache ×3), by name:
  1. `FleetSetupComponent keeps the vehicle when the confirmation is dismissed`
  2. `NavbarComponent navigates when a tab is clicked`
  3. `CollectionCache starts a new request once the previous one settled`
  4. `CollectionCache records the error message and rethrows`
  5. `CollectionCache refetches on every load - there is no freshness window`
- `npm run build` → **exit 1**. Cause is a **pre-existing** `anyComponentStyle` budget **ERROR**:
  `src/app/features/auth/login/login.component.scss` — `Budget 8.19 kB was not met by 1.21 kB with a total of 9.40 kB`.
  This is already recorded as pre-existing in `work-items/DRIVER-DELIVERY-001/verification.md` (line 10).
  Other pre-existing output: `bundle initial exceeded maximum budget` WARNING (initial total **665.23 kB** > 512 kB warning, < 1 MB error), `anyComponentStyle` WARNINGs (login 9.40 kB, orders-section 5.08 kB, fleet-setup 4.14 kB, import-orders-dialog 4.11 kB, route-view 4.62 kB, orders-table 7.73 kB, sidebar 5.03 kB, job-monitoring-dialog 4.46 kB, edit-orders-dialog 4.96 kB), and the `leaflet` CommonJS WARNING.

**Consequence for the acceptance criterion.** `task-opencode.md` Step 3 asks for `npm run build`
exit 0. That is **not achievable on this repository without editing a forbidden file**: the only
build error is pre-existing in `login.component.scss`, and `angular.json` (the budget config) is
forbidden. Per the DRIVER-DELIVERY-001 precedent the criterion used here is: **no NEW error and
no NEW/worsened budget warning introduced by PARETO-002**. See Deviations.

---

## Changes Made

### New source files (untracked)

| File | Purpose |
|---|---|
| `core/models/pareto.model.ts` | `FrontierStatus`, `NotApplicableReason`, `ParetoCandidateDTO`, `ParetoFrontierDTO` (snake_case, mirrors the Entry DTOs) |
| `shared/utils/pareto-chart.utils.ts` | Pure helpers: `niceTicks`, `scaleLinear`, `frontierPath`, `compactVnd`, `deltaPct` |
| `features/main/orders/pareto-view/pareto-view.component.ts` | Pareto tab: chart VM, rows VM, comparison VM, apply/409/422/400 handling |
| `features/main/orders/pareto-view/pareto-view.component.html` | Summary strip, active-plan line, inline SVG chart + legend, candidate list, selection panel, conflict banner |
| `features/main/orders/pareto-view/pareto-view.component.scss` | Styles for the above (5.4 kB source; not flagged by the build) |

### Modified tracked files

| File | Change |
|---|---|
| `core/models/index.ts` | +1 line: `export * from './pareto.model';` |
| `core/models/solution.model.ts` | +`pareto_candidate_id?: number \| null`, +`type?: string` |
| `core/models/job.model.ts` | `JobProgressDTO` +`scenario?: number`, +`scenarioCount?: number` |
| `core/services/api.service.ts` | +exactly two methods: `getPareto(solutionId)`, `applyParetoCandidate(solutionId, candidateId)` (empty body `{}`) |
| `features/main/orders/orders-section-view/orders-section.component.ts` | `paretoFrontier` signal, `paretoCandidates`, `showParetoTab`, effect + `queueMicrotask` loader, `onParetoReload()`, `ParetoViewComponent` import |
| `features/main/orders/orders-section-view/orders-section.component.html` | 4th `mat-tab` "Pareto" (`insights` icon) rendered only when `showParetoTab()` |
| `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.ts` | `scenarioProgress()` computed (gated on `scenarioCount > 0`), `paretoLine` signal + best-effort `loadParetoSummary()` after the solution loads |
| `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.html` | "Scenario k of n · Solving…" hint, scenario dots row, Pareto summary line |
| `features/main/jobs/route-planning-dialog/route-planning-dialog.component.html` | Hint and estimated-time line switch when `enablePareto` is checked ("× 5 scenarios (Pareto)") |
| `features/main/orders/route-view/send-route-dialog/send-route-dialog.component.ts` | `typeLabel(type)` — `PARETO_ALTERNATIVE` → "Pareto plan", else raw |
| `features/main/orders/route-view/send-route-dialog/send-route-dialog.component.html` | Option text uses `typeLabel(revision.type)` |

`git diff --stat` for the modified set: 11 files, 205 insertions, 10 deletions (excluding the two
pre-existing `work-items/PARETO-002/*.md` edits, which were already dirty before this run).

### New spec files (untracked)

| File | `it` count | Covers |
|---|---|---|
| `shared/utils/pareto-chart.utils.spec.ts` | 10 (6 describes) | U-T1 |
| `features/main/orders/pareto-view/pareto-view.component.spec.ts` | 11 | U-T2, U-T3, U-T4, U-T5 |
| `features/main/orders/orders-section-view/orders-section-pareto.component.spec.ts` | 2 | U-T6 |
| `features/main/jobs/job-monitoring-dialog/job-monitoring-pareto.component.spec.ts` | 3 | U-T7 |
| `features/main/jobs/route-planning-dialog/route-planning-dialog-pareto.component.spec.ts` | 2 | U-T7 (PUI-007 dialog half) |
| `features/main/orders/route-view/send-route-dialog/send-route-dialog-pareto.component.spec.ts` | 2 | U-T8 |

34 new executed tests (220 − 186).

---

## Commands Executed

All commands run from the repository root unless the working directory is noted.

### Step 0 — preconditions and branch (orchestrator)

| # | Command | Result / exit |
|---|---|---|
| 1 | `git status --short` | only `work-items/PARETO-002/*` (+ untracked `.mcp.json`) — nothing outside the allowed set; exit 0 |
| 2 | `git switch master` | already on `master`; exit 0 |
| 3 | `git rev-parse --short HEAD` | `4e2f928` |
| 4 | `git switch -c feature/pareto-002` | new branch; exit 0 |

### Step 1 — workflow ledger (orchestrator)

| # | Command | Result / exit |
|---|---|---|
| 5 | `node tools/feature-workflow/workflow.mjs init work-items/PARETO-002/run.json . PARETO-002 GVRP_Entry_client_v2/src` | `{"state":"intake","revision":0}`; exit 0 |
| 6 | `… step …/run.json ready …/01-proof.json` | `{"state":"discover","revision":1}`; exit 0 |
| 7 | `… step …/run.json ready …/02-proof.json` | `{"state":"design","revision":2}`; exit 0 |
| 8 | `… step …/run.json ready …/03-proof.json` | `{"state":"implement","revision":3}`; exit 0 |

### Step 2 — baseline (orchestrator, cwd `GVRP_Entry_client_v2`)

| # | Command | Result / exit |
|---|---|---|
| 9 | `npm test -- --watch=false --browsers=ChromeHeadless` | `TOTAL: 5 FAILED, 181 SUCCESS` (186); exit 1 (5 pre-existing) |
| 10 | `npm run build` | exit 1 (pre-existing `login.component.scss` ERROR); initial 665.23 kB WARNING |

### Step 3 — implementation and verification

| # | Command | Who | Result / exit |
|---|---|---|---|
| 11 | `npm test -- --watch=false --browsers=ChromeHeadless` | engineer | first run: 5 pre-existing + 5 NEW failures (fixed) |
| 12 | `npm test -- --watch=false --browsers=ChromeHeadless` | engineer | `TOTAL: 5 FAILED, 215 SUCCESS` (220) |
| 13 | `npm run build` | engineer | exit 1, only the pre-existing ERROR |
| 14 | `npm test -- --watch=false --browsers=ChromeHeadless` | **orchestrator (independent re-run)** | `TOTAL: 5 FAILED, 215 SUCCESS` (220); exit 1 |
| 15 | `npm run build` | **orchestrator (independent re-run)** | exit 1, only the pre-existing ERROR; initial 665.41 kB WARNING |
| 16 | `git diff --name-only -- "*/src/**/*.spec.ts" "*/src/testing/*"` | orchestrator | **empty** (no existing spec/testing file modified) |

Test/build logs (gitignored temp): `pareto002-test-baseline.log`, `pareto002-build-baseline.log`,
`pareto002-test-GATE.log`, `pareto002-build-GATE.log`.

Note: the test command exits 1 in both baseline and after the change because Angular/Karma returns
non-zero whenever any test fails; the 5 failures are the same pre-existing ones in both runs.

---

## Verification Results

Independently re-run by the orchestrator (not taken from the engineer's summary):

- **Tests**: `TOTAL: 5 FAILED, 215 SUCCESS` (220 executed). The 5 failing specs are, by name,
  exactly the baseline set: `FleetSetupComponent keeps the vehicle when the confirmation is
  dismissed`, `NavbarComponent navigates when a tab is clicked`, `CollectionCache starts a new
  request once the previous one settled`, `CollectionCache records the error message and
  rethrows`, `CollectionCache refetches on every load - there is no freshness window`.
  → 34 new tests, all passing; **no new failure**.
- **Build**: exit 1 with exactly **one** error, the pre-existing `login.component.scss` budget
  error. The `.scss` budget-violation set and the ERROR line are identical between the baseline
  and post-change logs (`exceeded maximum budget` occurrences: 11 in both). No warning mentions
  `pareto`. AOT compilation succeeded (the build reached the budget-review stage).
- **Initial bundle**: 665.41 kB vs baseline 665.23 kB → **+0.18 kB (0.03 %)**; still a WARNING
  only (error threshold 1 MB). Cause: the two mandated `ApiService` methods in the eagerly
  bundled service.
- **Scope integrity**: `git status` shows changes only inside `GVRP_Entry_client_v2/src/app/**`
  (plus the pre-existing `work-items/PARETO-002/*.md` edits and `.mcp.json`).
  `package.json`, `package-lock.json`, `angular.json`, `proxy.conf.json` untouched.
  `api.service.ts` gained exactly the two specified methods.
- **No new storage**: `localStorage`/`sessionStorage` appear only in pre-existing files
  (`storage.service.ts`, `language.service.ts`) and one pre-existing comment.
- **Defect caught and fixed during implementation** (worth reviewer attention): the first test
  run failed JIT compilation with `NG5002` because the template used
  `@else if (scenarioProgress(); as sp)`, which Angular allows only on the primary `@if`. Fixed
  by moving the alias to the top-level `@if (scenarioProgress(); as sp)` block and referencing
  `scenarioProgress()!.…` in the `@else if`. This would also have broken the AOT build.

U-T coverage spot-checked by the orchestrator against the new specs:
U-T1 `pareto-chart.utils.spec.ts`; U-T2/U-T3/U-T4/U-T5 `pareto-view.component.spec.ts`
(status markers `point-marker--frontier` / `point-marker--dominated` / `point-cross`,
`active-ring`, `engine-star`; all four `not_applicable_reason` texts; `applyParetoCandidate`
called with `(9, 1)`; `setSolution`; one `reload`; 409 banner + "Load latest revision";
422 server message with the store untouched; 400/`0040402` → "Plan not found");
U-T6 `orders-section-pareto.component.spec.ts`; U-T7 `job-monitoring-pareto.component.spec.ts`
+ `route-planning-dialog-pareto.component.spec.ts`; U-T8 `send-route-dialog-pareto.component.spec.ts`.

---

## Modified Tests

**none.**

No existing spec file or anything under `src/testing/` was edited, renamed, deleted, disabled,
focused, widened or excluded. Evidence: `git diff --name-only -- "*/src/**/*.spec.ts"
"*/src/testing/*"` is empty; every spec change is a NEW untracked file. The 22 snapshotted
spec/testing files are byte-identical.

---

## Security-Relevant Changes

**none.** No new endpoint, no `permitAll`, no branch-owned data query, no new dependency, no
credential or configuration change, no `localStorage`/`sessionStorage`. The two new `ApiService`
calls target the existing PARETO-001 backend routes; the solution id used for apply is the id of
the solution already displayed (`solution().id`), not a value supplied by the user. No token or
customer data is logged.

---

## Deviations From Design

1. **`npm run build` does not exit 0.** Required by `task-opencode.md` Step 3, but impossible
   here: the only build error is the pre-existing `login.component.scss` budget error and
   `angular.json` is forbidden. Same pre-existing condition as DRIVER-DELIVERY-001. The
   acceptance used instead is "no new error, no new/worsened warning"; both hold. **This is the
   one requirement in the dispatch that could not be satisfied literally.**
2. **Two `.scss` files deliberately left unchanged**: `job-monitoring-dialog.component.scss` and
   `orders-section.component.scss`. Both already exceed the 4 kB `anyComponentStyle` warning
   budget, so adding rules would have grown existing warnings. Consequence: `.scenario-dots` /
   `.scenario-dot(.done/.current)` (PUI-007 dots) and `.pareto-note` render as **unstyled
   text / unicode glyphs** (`●◉○`). The dots are structurally present and asserted by U-T7; only
   their visual sizing is unstyled. Flagged for the Reviewer / user as a fidelity gap.
3. **Initial bundle +0.18 kB** (WARNING only), cause above.
4. `handleApplyError` additionally maps a generic HTTP 400 (other than code `0040402`) to a toast
   with `errors[0].message`, which `design.md` §3.5 only specifies for 422. Additive, not
   conflicting.
5. `design.md` and `request.md` show as modified in `git status`; this pre-dates the run and was
   not touched by this implementation.
6. The design artefact at `C:\Users\ACER\Downloads\GVRP Pareto UI.html` (Claude design canvas
   "GVRP Pareto UI", 4 artboards) is a 428 kB bundled HTML whose artboard payloads were **not
   decoded**; `design.md` §3–§5 was used as the authoritative visual specification, as
   `task-opencode.md` states.

---

## Remaining Uncertainty

Written at Step 4; the live browser run below (`## Live End-to-End Browser Verification`)
resolves the first four items. Kept here as the record of what was uncertain before that run.

- **No live end-to-end run.** ~~PUI-005 and the real `scenario`/`scenarioCount` payload are not
  verified end to end.~~ → **resolved live** (apply 200/409/cancel; real scenario progress).
- **Scenario dots and `.pareto-note` are unstyled** (deviation 2). → **confirmed live**: the dots
  render as plain unicode glyphs. Still a fidelity gap; the text/`title` semantics are correct.
- **Chart/point geometry** rendered pixel layout unverified in a browser. → **resolved live**
  (markers, ring, star, frontier path, ticks, legend inspected in the DOM and on screenshots).
- Real `JobPollingService` payload shape for the two new progress keys, previously exercised only
  with a synthetic object. → **resolved live** against the running engine (job #87).
- Baseline build log for the engineer's session was not persisted at the time it started; the
  orchestrator's own baseline logs are the reference used above. (unchanged)

---

## Live End-to-End Browser Verification (orchestrator, 2026-10-18)

**Provenance caveat.** The run was executed against the **dirty working tree at `4e2f928`
(nothing committed)** through `ng serve` on `:4200` (proxy → Entry API `:8080`, Engine `:8081`,
MySQL dev DB `:3306`), i.e. the code under review, not a commit. It therefore demonstrates that
the working tree behaves as claimed; it does **not** establish that `4e2f928` alone reproduces the
result. Tools: browser-skill (`bsk`) driven sessions `tbjv` and `zqsp`; DB reads via the read-only
`agent_reader` account. No credentials, tokens, customer names, addresses or coordinates are
recorded here.

### PUI-001 / PUI-002 — tab gating and direct load

- `GET /api/v1/solutions/{id}/pareto` is fetched for both the ordinary (job-completion) path and
  the direct-load path `/?solution=<id>`; the **Pareto** tab appears only for ids whose response
  carries candidates, and stays hidden otherwise. Tabs render as `listOrders | Route | Timeline |
  Pareto`; the Pareto panel is not constructed until its tab is activated (Angular Material lazy
  tab body), so a first click can land before the tab is ready.

### PUI-003 — chart geometry (verified against DB, job 85 chain)

- 5 points, statuses from DB: 4 `FRONTIER` + 1 `DOMINATED`. `path.frontier-line` runs
  K3 → K4 → K2 → K0 (cost-ascending) and **excludes the dominated K1**; `.active-ring` (r=11) sits
  on the active candidate's coordinates; `.engine-star` sits on the `engine_selected` candidate
  (K3 in this chain, matching `pareto_candidates.engine_selected`).
- Axis ticks `12.5 / 15.0 / 17.5` (CO₂ kg) and `1.75M / 2.00M / 2.25M` (VND), titles
  "Cost (VND)" / "CO₂ (kg)", the "↙ better" marker and the four legend entries all present.
- Selection deltas recomputed from DB `total_*` and equal to the displayed values within the
  shown rounding: K1 = −756,522 VND (−30.5 %), +7.6 kg (+68.6 %), −43.9 km (−25.9 %), −2 vehicles,
  −14h 54m (DB `total_time` 48.23 → 33.33 h = 14.90 h); K3 = −898,379 VND (−36.3 %), +8.2 kg
  (+73.4 %), −36.8 km (−21.7 %), −3 vehicles, −20h 26m (48.23 → 27.79 h = 20.44 h).

### PUI-004 — selection panel and applicability

- Dominated candidate (K1): "Dominated — another point is better on both cost and CO₂",
  **Apply disabled**. Frontier candidate (K3): no reason, **Apply enabled**. A candidate equal to
  the chain head shows **Apply disabled** (`ALREADY_ACTIVE` semantics observed on the solution-64
  page once revision 67 existed).

### PUI-005 — apply: all three outcomes observed on the real stack

| Case | Request | Result |
| --- | --- | --- |
| Cancel | confirm dialog → `Cancel` | **No mutation**: `SELECT MAX(id) FROM solutions` = 64 before and after; newest row still `created_at` 18:02:29 |
| 409 | `POST /api/v1/solutions/61/pareto/4/apply`, `POST /api/v1/solutions/64/pareto/3/apply` | **409**, banner "A newer revision exists. \| Load latest revision" |
| Recovery | banner button | `GET /solutions/64/latest-revision` → 200 `{id:67}` → `GET /solutions/67` + `GET /solutions/67/pareto` → banner cleared, K3 row active, prior selection kept with deltas recomputed against the new head |
| 200 | `POST /api/v1/solutions/64/pareto/4/apply` | **200**, toast "Plan PARETO_K3_OF_4 applied (revision #67)" |

Confirm-dialog copy (verbatim): "Apply plan PARETO_K3_OF_4? / A new revision is created from this
plan. The current plan stays in the revision history. Routes already sent to drivers must be sent
again. / Cancel / Apply". The 409 body is produced by `GlobalExceptionHandler` (code
`RESOURCE_CONFLICT`, message + " Latest revision id: 67").

**Revision 67 verified in the DB:** `job_id` NULL, `PARETO_ALTERNATIVE`, `PARTIAL_SUCCESS`,
`pareto_candidate_id` 4, `parent_solution_id` 64, metrics identical to candidate 4
(1,578,146.30 VND / 19.26 kg / 132.86 km / 4 vehicles / 34 served / 6 unserved), 4 routes
materialised, 42 stop rows over 34 distinct orders, and **all 34 orders still `ON_ROUTE`** —
i.e. applying a plan does not touch order status (matches `ParetoService` L210-211). The UI
switched to "Active plan: PARETO_K3_OF_4 · cost-weight 0.56 / CO₂-weight 0.44" and the active
ring moved to K3's point.

### PUI-007 / planning dialog — scenario progress and the request payload

- "Enable Pareto Analysis" checkbox and its hint are present in the route-planning dialog; the
  request actually sent (captured in-page) contains `preferences.enable_pareto_analysis: true`
  with `speed: "FAST"`.
- **Job #86** (5 orders + 8 vehicles) → COMPLETED 21:02:17→21:02:29, 5/5 served,
  476,587.44 VND / 4.53 kg, 1 route, solution 65, note "Pareto: 1 plan on the frontier — open the
  Pareto tab to compare."
- **Job #87** (5 orders) → COMPLETED 21:11:06→21:11:15, 4 served / 1 unserved,
  494,139.94 VND / 5.19 kg, solution 66. **Scenario dots observed live**:
  "Scenario 2 of 5 · Solving…" 31 % `DCPPP` → "Scenario 4 of 5" 71 % `DDDCP` →
  "Scenario 5 of 5" 100 % `DDDDC` (`.scenario-dots` → 5 `.scenario-dot` spans with
  `done` / `current`), confirming the real polling payload, not only the synthetic one.

### Degenerate frontier (5 identical candidates)

Solution 66 (job 87) produced five candidates with equal metrics (494,139.94 / 5.19 / 1 vehicle /
4 served): K0 (id 11) `FRONTIER` with `engine_selected = 1`; K1–K4 `DOMINATED` with
`not_applicable_reason = NOT_ON_FRONTIER`. The tab renders "5 scenarios · 1 on the frontier ·
recommended PARETO_K0_OF_4" plus the ★, a single-point frontier path (`M 300 142`), ticks
`5.2 / 494K`, and 0.0 % deltas on the dominated rows — no crash, no mislabelled recommendation.

### Observations handed to review (none is a verified defect)

1. **Active plan label vs. displayed revision.** Opening a historical revision by deep link
   (`/?solution=64` while the chain head is 67) renders revision 64's routes in the Route tab while
   the Pareto tab states "Active plan: PARETO_K3_OF_4" (chain-head semantics, per `design.md`
   §active-candidate). The two can disagree; the mitigation is the 409 + banner when the user then
   applies. The user confirmed the underlying behaviour is intended; **whether the label should be
   qualified when the viewed solution is not the head is a review/UX judgement.**
2. **Dominated copy on exact ties.** In the solution-66 case the reason text reads "another point
   is better on both cost and CO₂" although the deltas shown are 0.0 %. The user confirmed the
   *classification* (a tie is dominated) is intended; only the wording is imprecise for exact
   ties. Low severity, wording only.
3. **`ON_ROUTE` orders cannot be planned** (`POST /jobs/plan` → 400, code `0040901`,
   `Resource conflict`) — correct domain rule, confirmed by the user; SCHEDULED orders were used
   instead. Noted only because the Orders list does not visually distinguish unroutable rows, so
   the conflict surfaces at submit. Outside PARETO-002 scope.
4. **Dots/note styling** — confirmed unstyled in the live UI (deviation 2), as predicted.

### Not covered by this run (still unverified)

- `SERVICE_DOMINATED` ("× Serves fewer orders") marker, chip and reason text: no live Pareto run
  produced one.
- 422 paths (`NO_PLAN`, non-frontier, `ORDER_SET_DIFFERS`) and the 400 `0040402` "Plan not found"
  toast: not triggered.
- The clustered / mono-theme chip in a live run (only "Full problem" observed).
- The `PARETO_ALTERNATIVE` → "Pareto plan" label in the send-route dialog (not opened live).
- **Dev-DB side effects of this run are additive and were not rolled back**: jobs #86/#87 and
  solutions 65/66/67 (plus their `pareto_candidates` rows 6–15 and revision 67), and the orders
  served by those jobs are now `ON_ROUTE`.

---

## Handoff to Reviewer

Claim under review: "PARETO-002 implements PUI-001..PUI-009 in `GVRP_Entry_client_v2` per
`design.md`; all 34 new specs pass; the 5 remaining test failures are exactly the pre-existing
ones; the build introduces no new error or warning; no existing spec or `src/testing/` file was
modified; no forbidden file was touched."

Evidence to inspect independently:
- `git diff` of the 11 tracked client files and the 5 new files listed above. Highest-signal diffs:
  the two `ApiService` methods, the `effect` + `queueMicrotask` loader in `orders-section.component.ts`,
  the `@if`/`@else if` fix in `job-monitoring-dialog.component.html`, `typeLabel` in `send-route-dialog`.
- New specs: `pareto-chart.utils.spec.ts`, `pareto-view.component.spec.ts`,
  `orders-section-pareto.component.spec.ts`, `job-monitoring-pareto.component.spec.ts`,
  `route-planning-dialog-pareto.component.spec.ts`, `send-route-dialog-pareto.component.spec.ts`.
- Re-run `npm test -- --watch=false --browsers=ChromeHeadless` and `npm run build` (cwd
  `GVRP_Entry_client_v2`) and compare the failure names and the ERROR/WARNING sets against the
  baseline in this document.
- Check that `design.md` §3.1–§3.5 layout and copy are present in `pareto-view.component.html`,
  and that the clustered note (PUI-006) states the approximation explicitly.
- Judge whether deviations 1 and 2 are acceptable, and whether the unstyled dots/note must be
  fixed before acceptance.
- The live browser run recorded in `## Live End-to-End Browser Verification` is **orchestrator-run
  evidence against the dirty working tree**, not a guarantee that `4e2f928` alone reproduces it.
  Re-run the checks you consider decisive (especially the 200 apply and the 409 + "Load latest
  revision" recovery) rather than relying on the summary above; the two observations in that
  section (active-plan label on a historical revision; dominated copy on exact ties) are the
  items needing a judgement, and neither is a proven defect.

How to open the Pareto tab locally (**executed live on 2026-10-18**, see the section above; these
steps were the ones followed):
1. Backend: start `GVRP_Entry_API` and `GVRP_Engine_API`. PARETO-001 must be deployed and its
   migration `2026-10-18-pareto-candidates.sql` applied to the dev database. **Both now verified
   in practice**: the PARETO-001 endpoints answer 200 and `pareto_candidates` rows were written by
   real jobs, so the migration is applied on this dev database.
2. Client: `npm start` in `GVRP_Entry_client_v2` (proxy via `proxy.conf.json`), then log in.
3. In the **Orders** screen, select orders and open the route-planning dialog, tick
   **"Enable Pareto Analysis"** (the estimated-time line then reads "… × 5 scenarios (Pareto)"),
   and submit. The **job monitoring dialog** should show "Scenario k of 5 · Solving…" with the
   dots row.
4. After the job completes, reopen the **Orders** section. A 4th tab **"Pareto"** (insights icon)
   appears next to Orders / Route / Timeline **only** when the active solution has Pareto
   candidates; otherwise it stays hidden. The tab itself is the screen that holds the chart,
   candidate list, selection panel and "Apply this plan".
5. In the **send-route** dialog (Route tab → send route), a `PARETO_ALTERNATIVE` revision should
   read "Pareto plan" in the revision picker.
