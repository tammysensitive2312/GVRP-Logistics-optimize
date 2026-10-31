# Task for Claude Code — LEZ-FIX-001

You are the implementing engineer. Implementation is authorized by the user (2026-10-30 23:27 +07:00, "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm"). Follow `AGENTS.md` in full (sections 14, 15, 16) and `CLAUDE.md`. Run every command yourself from the repository root, on Windows (L-2026-014).

Run order: DEPOT-FIX-001 → ORDERS-FIX-001 → **LEZ-FIX-001**, one at a time.

## Read first
1. `work-items/LEZ-FIX-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `work-items/UX-LEZ-001/review.md`, `design.md`, `implementation.md`, `checkpoint-blocked-ng0303.json`
5. `docs/engineering/LESSONS.md` (all client entries; L-2026-014)
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/feature-workflow/workflow.mjs status work-items/ORDERS-FIX-001/run.json` → state `verify` (or later); otherwise stop and ask the user.
2. The user commits `fix/orders-fix-001` first. Then `git switch master`, `git log -1 --oneline` (record), `git status --short`. Allowed uncommitted: `work-items/LEZ-FIX-001/`, `work-items/*/review.md`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report.
3. `node tools/session-brief.mjs` — include the output.
4. `git switch -c fix/lez-fix-001` (from `master`; independent of the two other fixes).
5. Do not stop the user's `ng serve`; record it if it is running.

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/LEZ-FIX-001/run.json . LEZ-FIX-001 GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/LEZ-FIX-001/run.json ready work-items/LEZ-FIX-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/LEZ-FIX-001/run.json ready work-items/LEZ-FIX-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/LEZ-FIX-001/run.json ready work-items/LEZ-FIX-001/03-proof.json
```

## Step 2 — baseline
- Client (in `GVRP_Entry_client_v2`): `npx ng test --watch=false --browsers=ChromeHeadless` (last recorded 435 SUCCESS), `rm -rf dist && npm run build` (12 warnings). Record counts and any pre-existing failure by name.
- **Assertion check (record):** search specs for `rule-preset`, `applyPreset`, `ngModel` on the preset select, `Choose a preset`, `doubleClickZoom`, `finishDraw`, `updateFinishButton`, `zone-draw-btn`, `polygonFromPoints`, `RULE_PRESETS`, `.label`. Also list the inputs/outputs of every `ZoneMapStubComponent`. An existing assertion the design would break (for example a spec driving the preset select through `ngModel`, or counting the control's child elements) → **stop and report**.

## Allowed changes
Client (`GVRP_Entry_client_v2/src/app/features/admin/zones/`): `zone-draw.utils.ts`, `zone-map/zone-map.component.ts`, `zone-editor-dialog/zone-editor-dialog.component.{ts,html,scss}`, `zone-rule-form.utils.ts` (D4 only); new spec files.
Work item: `work-items/LEZ-FIX-001/implementation.md`.

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture, stub or helper (including both `ZoneMapStubComponent`s). A failing existing test → stop and report.
- New `@Input`/`@Output` on `app-zone-map` (design constraint).
- Any other file: `GVRP_Document/**` and other documentation (user 23:27), server code, `package*.json`, `angular.json`; new dependencies (no drawing or geometry library).
- Committing, pushing; DB writes (a manual browser check may save one test zone — record it).

## Step 3 — implement per `design.md`, then verify
Order: D2 utility + LF-C1 → D1 → D2 control → LF-C2, LF-C3 → D4 → D3 → LF-C4, LF-C5.
- Client all green, count = baseline + new; build EXIT 0, no new warning.
- `git status --short` shows only allowed files.
- Optional browser check (`bsk`, per the business workflow README): UL-M2 with a normal and a bow-tie outline, UL-M4 (no zoom now), UL-M7 (select resets). Record steps and observations; if `bsk` is unavailable, say so.

## Step 4 — record and hand off
`work-items/LEZ-FIX-001/implementation.md`: Scope, Baseline (incl. assertion check and stub inputs), Changes Made, Commands Executed (exit codes), Verification Results, Modified Tests ("none"), Security-Relevant Changes (none expected), Deviations From Design, Remaining Uncertainty, Documentation Now Stale (D07 — not updated by decision of 23:27), Lesson Candidates, Handoff to Reviewer. Then:
```
node tools/feature-workflow/workflow.mjs step work-items/LEZ-FIX-001/run.json ready work-items/LEZ-FIX-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done, in Vietnamese.
