# Task for the OpenCode engineer — UX-LEZ-001

You are `gvrp-engineer`. Implementation is authorized by the user (2026-10-29 22:18 +07:00, "đồng ý, bạn viết đi"). Follow `AGENTS.md` in full (sections 14, 15, 16). Run every command yourself from the repository root, on Windows (L-2026-014).

## Read first
1. `work-items/UX-LEZ-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `docs/engineering/LESSONS.md`: L-2026-003, -009, -010, -011, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/feature-workflow/workflow.mjs status work-items/UX-ORDERS-001/run.json` → state `verify`; otherwise stop.
2. The user commits `feat/ux-orders-001` first. On it: `git status --short`; allowed uncommitted: `work-items/UX-LEZ-001/`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report.
3. `node tools/session-brief.mjs` — include the output.
4. `git switch -c feat/ux-lez-001` (from `feat/ux-orders-001`).

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/UX-LEZ-001/run.json . UX-LEZ-001 GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/UX-LEZ-001/run.json ready work-items/UX-LEZ-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/UX-LEZ-001/run.json ready work-items/UX-LEZ-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/UX-LEZ-001/run.json ready work-items/UX-LEZ-001/03-proof.json
```

## Step 2 — baseline
Client: `npx ng test --watch=false --browsers=ChromeHeadless` and `rm -rf dist && npm run build` (record counts and warnings; note the size of `orders-section.component.scss` output against its budget).
**Assertion check (record):** search specs for `LEZ_GEOMETRY_MISSING` in compliance-panel specs (row counts), `app-routes-view`, `routes-tab-content`, `ZoneMapComponent`, `zone-rule-form.utils`. An assertion the design would break → stop and report.

## Allowed changes (all under `GVRP_Entry_client_v2/src/app/`)
- `features/admin/zones/zone-map/zone-map.component.ts`; new `features/admin/zones/zone-draw.utils.ts`
- `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.{ts,html,scss}`
- `features/admin/zones/zone-rule-form.utils.ts` (new exports only: `RULE_PRESETS`, `ruleWarnings`)
- `features/main/orders/orders-section-view/orders-section.component.{ts,html,scss}`
- new `features/main/orders/expanded-view-dialog/**`
- `features/main/orders/lez-compliance-panel/compliance-panel.component.{ts,html,scss}`; optional new `lez-compliance-panel/compliance-rows.utils.ts`
- new spec files for UL-T1…UL-T9
- `work-items/UX-LEZ-001/implementation.md`

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture or helper. A failing existing test → stop and report.
- Any other file; `package*.json`, `angular.json`, `public/**`; any dependency (no `leaflet-draw`, no geometry library); server code.
- Changing the send rule, compliance statuses, `validateRuleForm` or existing exports' behaviour.
- Committing, pushing; DB writes (a live check may save a zone through the UI if you record it).

## Step 3 — implement per `design.md`, then verify
Order: D1 → D2 → D3 → D4–D6 → D7 → D9 → D8.
- Client tests all green, count = baseline + new; build EXIT 0, no new ERROR, no new budget **error**; list any new budget warning.
- `git status --short` shows only allowed files.
- Optional live check: draw a zone (4 corners) and save; apply each preset; expand Route, click a violation, collapse; scroll the Route tab with a long violation list.

## Step 4 — record and hand off
`work-items/UX-LEZ-001/implementation.md`: Scope, Baseline (incl. assertion check), Changes Made, Commands Executed (exit codes), Verification Results, Modified Tests ("none"), Security-Relevant Changes (external link `rel="noopener"`), Deviations From Design, Remaining Uncertainty, Lesson Candidates, Handoff to Reviewer. Then:
```
node tools/feature-workflow/workflow.mjs step work-items/UX-LEZ-001/run.json ready work-items/UX-LEZ-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done.
