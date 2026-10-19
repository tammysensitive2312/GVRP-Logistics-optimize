# Task for the OpenCode engineer — PARETO-002

You are `gvrp-engineer`. Implementation is authorized by the user. Follow `AGENTS.md` in
full (sections 14 and 15). Run every command yourself from the repository root unless a
step says otherwise.

## Read first
1. `work-items/PARETO-002/request.md`
2. `work-items/PARETO-002/context.md`
3. `work-items/PARETO-002/design.md` — the specification.
4. API contract (merged, do not change): `work-items/PARETO-001/design.md` B3–B5 and
   `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/dto/response/ParetoFrontierDTO.java`,
   `ParetoCandidateDTO.java`. JSON is snake_case. Not-found = HTTP 400 with
   `errors[0].code = "0040402"`.

If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `git status --short`. If anything is modified or untracked outside
   `work-items/PARETO-002/`, `.claude/`, `.mcp.json`, stop and report the list.
2. `git switch master` and confirm `git rev-parse --short HEAD` is `4e2f928` (or a
   descendant that contains it); then `git switch -c feature/pareto-002`.

## Step 1 — workflow ledger (before editing code)
```
node tools/feature-workflow/workflow.mjs init work-items/PARETO-002/run.json . PARETO-002 GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/PARETO-002/run.json ready work-items/PARETO-002/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/PARETO-002/run.json ready work-items/PARETO-002/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/PARETO-002/run.json ready work-items/PARETO-002/03-proof.json
```
Each must print a new state (`discover`, `design`, `implement`). On failure stop and report.

## Step 2 — baseline
In `GVRP_Entry_client_v2`:
- `npm test -- --watch=false --browsers=ChromeHeadless` — record total / failed and the
  names of failing specs. Known from DRIVER-MAP-004: 5 pre-existing failures
  (FleetSetup, Navbar, 3× CollectionCache) — confirm by name, do not fix.
- `npm run build` — record exit code and any budget warnings.

## Allowed changes (all under `GVRP_Entry_client_v2/src/app/`)
- new `core/models/pareto.model.ts`; `core/models/index.ts` (export line);
  `core/models/solution.model.ts` (`pareto_candidate_id?`, `type?`);
  `core/models/job.model.ts` (`scenario?`, `scenarioCount?`)
- `core/services/api.service.ts` (two methods)
- new `shared/utils/pareto-chart.utils.ts`
- new `features/main/orders/pareto-view/pareto-view.component.{ts,html,scss}`
- `features/main/orders/orders-section-view/orders-section.component.{ts,html,scss}`
- `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.{ts,html,scss}`
- `features/main/jobs/route-planning-dialog/route-planning-dialog.component.{ts,html}`
- `features/main/orders/route-view/send-route-dialog/send-route-dialog.component.{ts,html}`
- new spec files only (any name) for U-T1..U-T8.
- New UI text is literal English in templates, like the surrounding markup. Do **not** edit
  `GVRP_Entry_client_v2/public/i18n/*.json` (outside the run scope); reuse existing
  Transloco keys only where they already exist.

Visual reference: Claude design canvas "GVRP Pareto UI" (private link, may not be reachable
from your environment). `design.md` §3–§5 is authoritative for layout, shapes and copy; use
the app's existing Material theme for colours.

## Forbidden
- Editing, disabling (`xit`, `xdescribe`), focusing (`fit`, `fdescribe`) or deleting any
  existing spec or `src/testing/*` (22 files were snapshotted and will be byte-compared).
- Any npm dependency change (`package.json`, `package-lock.json`); chart is inline SVG.
- Backend code, `angular.json`, `proxy.conf.json`, environments, `.github/`, `AGENTS.md`, `docs/`.
- Storing anything in `localStorage` / `sessionStorage`.
- Committing, pushing, rewriting history. Logging tokens or customer data.

## Step 3 — implement
Implement per `design.md`. Then in `GVRP_Entry_client_v2`:
`npm test -- --watch=false --browsers=ChromeHeadless` (no new failures; the same
pre-existing failures by name; all new specs pass) and `npm run build` (exit 0).

## Step 4 — implementation record
Write `work-items/PARETO-002/implementation.md` with sections:
Scope, Baseline, Changes Made, Commands Executed (with exit codes, incl. Steps 0–1),
Verification Results, Modified Tests (must be "none"), Security-Relevant Changes,
Deviations From Design, Remaining Uncertainty, Handoff to Reviewer (include how to open the
Pareto tab locally: which job to run and which screen).
Then:
```
node tools/feature-workflow/workflow.mjs step work-items/PARETO-002/run.json ready work-items/PARETO-002/04-proof.json
```
It must print state `verify`. Do not go further than `verify`. Tell the user you are done.
