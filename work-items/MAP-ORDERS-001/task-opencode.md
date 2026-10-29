# Task for the OpenCode engineer — MAP-ORDERS-001

You are `gvrp-engineer`. Implementation is authorized by the user (2026-10-29 00:15 +07:00, "bạn viết task luôn đi"). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root, on Windows (L-2026-014).

## Read first
1. `work-items/MAP-ORDERS-001/request.md`  2. `context.md`  3. `design.md` (the specification)
4. `docs/engineering/LESSONS.md`: L-2026-003, -009, -010, -011, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
This task is **stacked on LEZ-001**: it edits `map.service.ts`, which the LEZ-001 review fix also edits.
1. `work-items/LEZ-001/checkpoint-p1c-review-fix.json` must exist (the LEZ-001 review fix is done). If not, stop and report.
2. The user commits the current `feature/lez-001` state before you start (the LEZ-001 work stays on that branch). Check with `git log -1 --oneline` on `feature/lez-001` and `git status --short`: the only uncommitted paths allowed are `work-items/MAP-ORDERS-001/`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report the list.
3. `node tools/session-brief.mjs` — include the output in your report.
4. `git switch -c fix/map-orders-001` (from `feature/lez-001`).
5. Stop `ng serve` if it runs (L-2026-011).

## Step 1 — workflow ledger (before editing code)
```
node tools/feature-workflow/workflow.mjs init work-items/MAP-ORDERS-001/run.json . MAP-ORDERS-001 GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/MAP-ORDERS-001/run.json ready work-items/MAP-ORDERS-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/MAP-ORDERS-001/run.json ready work-items/MAP-ORDERS-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/MAP-ORDERS-001/run.json ready work-items/MAP-ORDERS-001/03-proof.json
```
Each must print a new state (`discover` → `design` → `implement`); on failure stop and report.

## Step 2 — baseline
From `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` and `npm run build`. Record counts, every failing spec by name and the build warnings. Entry/Engine are not touched; do not run them.

## Allowed changes (all under `GVRP_Entry_client_v2/src/app/`)
- `features/main/main.component.{ts,html}` (D1, D2)
- `features/main/orders/orders-section-view/orders-section.component.ts` (D1 output, D2 default date) — **not** its `.html`
- `features/main/orders/components/orders-filters/orders-filters.component.ts` (D2)
- `shared/components/map/map.component.ts` (D3)
- `shared/services/map.service.ts` (D3 `clearSolution`, D4 filter in `loadOrders` only)
- `shared/utils/date.utils.ts` (new), `shared/utils/coordinate.utils.ts` (add `hasMapCoordinates`; keep the existing export unchanged)
- New spec files for MO-T1…MO-T6 (e.g. `orders-section-map.component.spec.ts`, `date.utils.spec.ts`, `coordinate.utils.spec.ts`, `map.component.spec.ts`, `main.component.spec.ts`; the filters case may go in a new `orders-filters-date.component.spec.ts`)
- `work-items/MAP-ORDERS-001/implementation.md`

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture or helper (including the LEZ-001 specs and the D8 specs). If an existing spec fails because of this change, stop and report the spec, the failure and the minimal change you propose.
- Any other file; `package*.json`, `angular.json`, `public/**`; any dependency.
- Changing sidebar stats, `maxDate` semantics (only its computation becomes local), the LEZ overlay/focus behaviour, or hiding orders while a solution is shown.
- Logging order ids, codes, addresses, customer data or coordinates (the D4 warning carries a count only).
- Committing, pushing, rewriting history; any database write.

## Step 3 — implement per `design.md`, then verify
- `npx ng test --watch=false --browsers=ChromeHeadless`: all green, count = baseline + new.
- `npm run build`: EXIT=0, no ERROR, no new budget warning compared with the baseline.
- Static: `git status --short` shows only allowed files; list every other `toISOString().split('T')[0]` in `src/app` (file and purpose) without changing it.
- Optional live check if the stack is running (planner account the user names): pick a date with orders, change page and date, clear a solution; record what you saw. Not required.

## Step 4 — record and hand off
Write `work-items/MAP-ORDERS-001/implementation.md` with sections: Scope, Baseline, Changes Made, Commands Executed (with exit codes, incl. Steps 0–1), Verification Results, Modified Tests (must be "none"), Security-Relevant Changes, Deviations From Design, Remaining Uncertainty (browser behaviour is NOT_VERIFIED unless you ran the live check), Lesson Candidates ("none" if none), Handoff to Reviewer.
Then:
```
node tools/feature-workflow/workflow.mjs step work-items/MAP-ORDERS-001/run.json ready work-items/MAP-ORDERS-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done.
