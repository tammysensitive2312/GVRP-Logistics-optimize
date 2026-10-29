# Implementation — UX-LEZ-001

Engineer: Claude Code (acting as `gvrp-engineer` for `task-opencode.md`; OpenCode could not run — invalid provider credential — and the user asked Claude to do the task), 2026-10-29, Windows host shell.
Branch: `feat/ux-lez-001`, created from `feat/ux-orders-001` @ `64073c0`. Nothing in this task is committed.

## Scope

Client v2 only, design D1–D9: draw a COPIED zone on the editor map (no plugin), format hint / geojson.io link / Load example, one-line help for every rule field, four rule presets, non-blocking rule warnings, Route/Timeline tab scroll fix, Expand/Collapse dialog for the Route and Timeline tabs, repeated `LEZ_GEOMETRY_MISSING` findings shown as one line. No dependency, no server code, send rule and compliance statuses unchanged.

## Baseline

On `feat/ux-lez-001` before any code edit (tree = `64073c0`), from Git Bash.

| Command | Result |
|---|---|
| `npx ng test --watch=false --browsers=ChromeHeadless` | EXIT=0, **405 SUCCESS** |
| `rm -rf dist && npm run build` | EXIT=0, 12 warnings (same set as UX-ORDERS-001); `orders-section.component.scss` 5.08 kB (warning budget 4.10 kB, 986 bytes over) |

**Assertion check (Step 2)** over `**/*.spec.ts`: `LEZ_GEOMETRY_MISSING` appears in `map-focus.utils.spec.ts` and `route-view-lez.component.spec.ts` (route view, not the compliance panel); **no compliance-panel spec** uses it. `app-routes-view`, `routes-tab-content`: no match. `zone-rule-form.utils`: only its own spec (existing exports unchanged). `ZoneMapComponent`: no direct match, **but** the two zone-editor specs replace it with a local `ZoneMapStubComponent` (`selector: 'app-zone-map'`) — see Modified Tests.

## Changes Made (all under `GVRP_Entry_client_v2/src/app/`)

| Item | File | Change |
|---|---|---|
| D1 | `features/admin/zones/zone-draw.utils.ts` (new) | `GeoJsonPolygon` type; pure `polygonFromPoints([lat,lng][])` (non-finite skipped, duplicates removed, < 3 distinct → null, `[lng,lat]` rounded to 6 decimals, ring closed); `EXAMPLE_ZONE` (rectangle in Hoàn Kiếm) |
| D2 | `features/admin/zones/zone-map/zone-map.component.ts` | `@Input() editable = false`, `@Output() polygonDrawn`. When editable: a plain-DOM `L.Control` (`.zone-draw-control`) with Draw / Undo point / Finish / Cancel; Draw sets a crosshair cursor and map clicks add points; draft = dashed polyline + circle markers in its own layer kept above the geometry; Finish (disabled below 3 distinct points) emits and leaves draw mode. Public `startDraw`, `addPoint`, `undoPoint`, `finishDraw`, `cancelDraw` (the buttons call them). Read-only behaviour unchanged when `editable` is false; click handler removed on destroy |
| D3 | `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.{ts,html}` | `<app-zone-map [editable]="isCopied()" (polygonDrawn)="onPolygonDrawn($event)">`; `onPolygonDrawn` sets `geometry`, pretty `geometryText`, clears errors; `loadExample()` fills the text with `EXAMPLE_ZONE` and previews it; hint text + link `https://geojson.io` (`target="_blank" rel="noopener"`); **Load example** button. The existing paste/upload hint stays |
| D4 | `zone-editor-dialog.component.html` | the design's one-line `form-hint` under legal classes, energy types, weight measure, weight bounds, emission standard, app-based, time rule, days, effective dates, plus the AND line above the selector fields |
| D5 | `features/admin/zones/zone-rule-form.utils.ts` | new export `RULE_PRESETS` (4 presets of the design; replace selector + schedule, keep `effectiveFrom` or set it to `localDateIso()`, keep `effectiveTo`, `legalBasis`, `note`); editor: "Start from a preset" select per rule card → `applyPreset(index, presetIndex)` |
| D6 | `zone-rule-form.utils.ts`, editor `.ts/.html/.scss` | new export `ruleWarnings(form, today = localDateIso())` with the five messages; shown as `.form-warning` (amber) under each card via `warningsFor(index)`; never blocks save. `validateRuleForm` and all existing exports unchanged |
| D7 | `features/main/orders/orders-section-view/orders-section.component.scss` | `.routes-tab-content` and `.timeline-tab-content`: `overflow-y: auto`; the dead `app-routes-view` selector → `app-route-view { flex: none; }` |
| D8 | `features/main/orders/expanded-view-dialog/expanded-view-dialog.component.{ts,html,scss}` (new) | standalone dialog, data `{view, solution}`; header with title and Collapse button (`close_fullscreen`); route = own compliance panel + route view wired like the tab, `(violationFocused)` closes the dialog; timeline = `<app-timeline-view>`; content scrolls |
| D8 | `orders-section.component.{ts,html,scss}` | Expand button (`open_in_full`, tooltip "Expand") at the top of the Route and Timeline tab contents → `openExpanded(view)` opens the dialog at `95vw × 90vh`; after close, the tab's panel (`@ViewChild('compliancePanel')`) is reloaded. `MatTooltipModule` imported. SCSS: one `.btn-expand` rule |
| D8 | `features/main/orders/lez-compliance-panel/compliance-panel.component.ts` | new output `violationFocused`, emitted after `focusLeg` |
| D9 | `lez-compliance-panel/compliance-rows.utils.ts` (new) + panel `.ts/.html/.scss` | pure `groupGeometryMissing(rows)` (≥ 2 `LEZ_GEOMETRY_MISSING` → one summary line where the first was; 0 or 1 → unchanged) and `isGeometrySummary`; panel rows use it; summary rendered as a non-clickable italic `.violation-summary`, `focusViolation` ignores it; `ComplianceViolationRow` gains optional `summary` |

New spec files (30 tests): `features/admin/zones/zone-draw.utils.spec.ts` (UL-T1, 5), `features/admin/zones/zone-map/zone-map-draw.component.spec.ts` (UL-T4, 4), `features/admin/zones/zone-editor-dialog/zone-editor-draw.component.spec.ts` (UL-T2/UL-T3, 3), `features/admin/zones/zone-rule-presets.utils.spec.ts` (UL-T5/UL-T6, 10), `features/main/orders/lez-compliance-panel/compliance-rows.utils.spec.ts` (UL-T7, 3), `features/main/orders/orders-section-view/orders-section-expand.component.spec.ts` (UL-T8, 2), `features/main/orders/expanded-view-dialog/expanded-view-dialog.component.spec.ts` (UL-T9, 3).

## Commands Executed

| Step | Command | Exit / result |
|---|---|---|
| (user) | `git commit` of UX-ORDERS-001 on `feat/ux-orders-001` | `64073c0` |
| 0.1 | `workflow.mjs status work-items/UX-ORDERS-001/run.json` | `verify` |
| 0.2 | `git status --short` | only `work-items/UX-LEZ-001/` |
| 0.3 | `node tools/session-brief.mjs` | 0 (summary below) |
| 0.4 | `git switch -c feat/ux-lez-001` | 0 |
| 1 | `workflow.mjs init …` + `step … ready 01/02/03-proof.json` | 0 each: `intake` 0 → `discover` 1 → `design` 2 → `implement` 3 |
| 2 | client tests / build | see Baseline |
| 3 | `npx ng test …` after D1–D9, before new specs | 1: **10 FAILED, 395 SUCCESS**, all with `NG0303: Can't bind to 'editable' since it isn't a known property of 'app-zone-map'` in the two zone-editor specs → stopped and reported |
| 3 | `workflow.mjs checkpoint … checkpoint-blocked-ng0303.json` | 0, `implement` rev 4 |
| 3 | user decision 2026-10-29: "Đồng ý, sửa stub" (option A) | stubs extended (see Modified Tests) |
| 3 | `npx ng test …` (new specs, first runs) | 1: a TypeScript error in the new UL-T8 spec (Pareto mock type), then 2 FAILED in UL-T8 (`No provider for HttpClient` via `MapService`), then an `afterAll` error (`solutionCompliance` spy returned undefined); all fixed in that new spec's setup |
| 3 | `npx ng test --watch=false --browsers=ChromeHeadless` | 0, **435 SUCCESS** |
| 3 | `rm -rf dist && npm run build` | 0, 0 ERROR, 12 warnings, same set as baseline |
| 3 | `git status --short` | 13 tracked files modified, 10 new files/dirs, all allowed (2 are the approved spec stubs) |
| 4 | `workflow.mjs step … ready 04-proof.json` | run after this file; result in the handoff message |

Session brief (2026-10-29T15:37Z): branch `feat/ux-orders-001` @ `64073c0`; open: BROWSER-FIX-001, MAP-ORDERS-001, UX-ORDERS-001 at `verify` (no checkpoint), LEZ-001 `implement` rev 10; security not FIXED: SEC-001 (Critical), SEC-010 (Medium), SEC-004/005/007/008 (Low).

## Verification Results

- Client tests: 435 = 405 + 30 new, all SUCCESS (the 10 previously failing zone-editor tests pass with the approved stubs). VERIFIED.
- Build: EXIT=0, no ERROR, no new budget warning, no budget error. Existing warnings that grew: `orders-section.component.scss` 5.08 → 5.16 kB (error budget 8 kB); initial bundle 665.96 → 666.03 kB. `zone-editor-dialog.component.scss` and `compliance-panel.component.scss` stay under the warning budget. VERIFIED.
- Only allowed files changed. VERIFIED.
- Live check (drawing on a real map, presets in the UI, Expand/Collapse, scrolling with a long list): not run. NOT_VERIFIED.

## Modified Tests

Two existing spec files, approved by the user on 2026-10-29 ("Đồng ý, sửa stub"):

| File | Change | Reason |
|---|---|---|
| `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.spec.ts` (LEZ-001) | `ZoneMapStubComponent` gains `@Input() editable = false;` and `@Output() polygonDrawn = new EventEmitter<unknown>();` (+ `EventEmitter`, `Output` imports) | D3 binds `[editable]` on `app-zone-map`; the stub lacked the input, so every test failed with NG0303 |
| `features/admin/zones/zone-editor-dialog/zone-editor-rename.component.spec.ts` (BROWSER-FIX-001) | same two lines | same |

No assertion, expected value or test case was changed, removed, skipped or focused.

## Security-Relevant Changes

- External link `https://geojson.io` opens with `target="_blank" rel="noopener"` (asserted in UL-T2). Nothing is sent to geojson.io by the app.
- No new request, endpoint or stored data; drawing produces the same GeoJSON the existing PUT geometry call already accepts (server still validates).

## Deviations From Design

1. **Test stubs** extended with the user's approval (see Modified Tests); the design did not foresee them.
2. **D2:** the drawing methods are public (`startDraw`, `addPoint`, …) so the control buttons and UL-T4 share one path; Leaflet click events are not simulated in Karma.
3. **D6:** `ruleWarnings` takes an optional `today` argument (default `localDateIso()`) so the "already ended" case is testable; the interface `RulePreset` is internal (not exported).
4. **D9:** the helper lives in `compliance-rows.utils.ts` (the design allowed either place); it also exports `isGeometrySummary`.
5. **D8:** UL-T8 calls `openExpanded` directly and injects a panel double; the Expand button is only rendered inside the (lazy) Route/Timeline tab, which is not selected in that spec.

## Remaining Uncertainty

- Drawing UX on a real map (click accuracy, cursor, control position) and the Expand dialog layout at 95vw × 90vh: NOT_VERIFIED in a browser.
- The preset select keeps showing the chosen preset label after applying it (its model is a constant `null`); harmless, not reset.
- While the expanded dialog is open there are two compliance panels (one GET each), as the design accepts.

## Lesson Candidates

- When a component gains an input, search the specs for stubs with the same selector (`selector: 'app-…'`) before implementing: Angular's NG0303 makes every test of the host component fail. Candidate for `LESSONS.md`.

## Handoff to Reviewer

- Read first: `zone-map.component.ts` (drawing state and listener cleanup), `compliance-rows.utils.ts`, `orders-section.component.ts#openExpanded`.
- Re-run: `cd GVRP_Entry_client_v2 && npx ng test --watch=false --browsers=ChromeHeadless` (435) and `rm -rf dist && npm run build` (12 warnings).
- Suggested live check: draw a 4-corner zone and save; apply each preset; expand Route, click a violation (dialog closes, map highlights), collapse; scroll the Route tab with a long violation list; a route with many missing geometries shows one summary line.
- `traceability.md` is not in this task's allowed files.
