# Implementation — LEZ-FIX-001

Engineer: Claude Code, 2026-10-31, Windows host. Branch `fix/lez-fix-001` from `master` @ `081640a`, uncommitted (task forbids committing).

## Scope

LF-R1…LF-R4 of `request.md`, per `design.md` D1–D4. Client v2 only (`GVRP_Entry_client_v2/src/app/features/admin/zones/`). No server change, no new dependency, no new `@Input`/`@Output` on `app-zone-map`, no documentation change (user 2026-10-30 23:27).

## Baseline

- Step 0: `workflow.mjs status work-items/ORDERS-FIX-001/run.json` → `done` rev 9 (committed on `fix/orders-fix-001` @ `a4590f9`, not merged). `git switch master` → `081640a`; `git status --short` showed only `work-items/LEZ-FIX-001/` and three `work-items/*/review.md` (allowed). `node tools/session-brief.mjs` run (open items LEZ-001, BROWSER-FIX-001, MAP-ORDERS-001, UX-LEZ-001, UX-ORDERS-001, DOC-SYSTEM-001; SEC-001/004/005/007/008/010 not FIXED; 14 lessons). No `ng serve` listening on port 4200.
- Client specs: `npx ng test --watch=false --browsers=ChromeHeadless` → EXIT 0, **435 SUCCESS**, no pre-existing failure.
- Client build: `rm -rf dist && npm run build` → EXIT 0, 0 ERROR, **12 warnings** (sorted list kept for comparison).

### Assertion check

Searched all `*.spec.ts` for `rule-preset`, `applyPreset`, `Choose a preset`, `doubleClickZoom`, `finishDraw`, `updateFinishButton`, `zone-draw-btn`, `polygonFromPoints`, `RULE_PRESETS`, `.label`:

| Hit | Effect of the design |
|---|---|
| `zone-draw.utils.spec.ts` — `polygonFromPoints` cases | unchanged function |
| `zone-rule-presets.utils.spec.ts` — `RULE_PRESETS` labels list, `apply` results | labels and `apply` unchanged; `hint` is a new optional field |
| `timeline-view.component.spec.ts` — `.label` of time markers | unrelated |
| `zone-map-draw.component.spec.ts` (UL-T4) — control `button`s exactly Draw, Undo point, Finish, Cancel; 3-point draft finishes | new message is a `<span>`, not a button; a triangle still finishes |

No spec drives the preset select through `ngModel` or counts the control's child elements. Nothing to stop for.

### `ZoneMapStubComponent` inputs/outputs

| Spec | Inputs | Outputs |
|---|---|---|
| `zone-editor-dialog.component.spec.ts` | `geometry`, `depots`, `height`, `editable` | `polygonDrawn` |
| `zone-editor-draw.component.spec.ts` | `geometry`, `depots`, `height`, `editable` | `polygonDrawn` |
| `zone-editor-rename.component.spec.ts` | `geometry`, `depots`, `height`, `editable` | `polygonDrawn` |
| `zones-admin.component.spec.ts` | `geometry`, `depots`, `height` | — |

The editor template still binds only `geometry`, `editable`, `polygonDrawn`, `height`.

## Changes Made

| Design | File | Change |
|---|---|---|
| D2 | `zone-draw.utils.ts` | New pure `ringSelfIntersects(ring)`: every pair of non-adjacent edges of the closed ring (first and last edge adjacent), orientation test with collinear touch/overlap, tolerance `1e-12` on the cross product (coordinates are rounded to 6 decimals, so real cross products are far larger) |
| D1 | `zone-map/zone-map.component.ts` | `startDraw()` → `map.doubleClickZoom.disable()`; `stopDrawing()` → `enable()` inside the existing `if (this.map)` |
| D2 | `zone-map/zone-map.component.ts` | `finishablePolygon()` = polygon of ≥ 3 points whose ring does not cross; `updateFinishButton()` disables Finish when it is null and shows/hides a `<span class="zone-draw-message">` "Edges cross — undo points or cancel and redraw." (red, hidden by default, built in `buildDrawControl`); `finishDraw()` uses `finishablePolygon()` so a stale button emits nothing. Draft colour unchanged |
| D4 | `zone-rule-form.utils.ts` | `RulePreset.hint?: string`; "Only electric vehicles" has `hint: 'Vehicles with no energy type recorded are banned too.'`. Labels and `apply` unchanged |
| D3 | `zone-editor-dialog.component.html` | Preset select: `ngModel` removed, `(change)="onPresetSelected(ruleIndex, $event)"`, `<option value="">Choose a preset…</option>`, `[value]="presetIndex"` |
| D3/D4 | `zone-editor-dialog.component.ts` | `onPresetSelected(index, event)`: empty value → nothing; otherwise `applyPreset(index, Number(value))` then `select.value = ''`. `applyPreset` unchanged. `presetsWithHint` (presets with a hint) and `lowerFirst` for the hint line |
| D4 | `zone-editor-dialog.component.html` | Under the select, one `form-hint preset-hint` line per preset with a hint: "Only electric vehicles: vehicles with no energy type recorded are banned too." |

`zone-editor-dialog.component.scss`: not changed (existing `form-hint` style is enough).

New specs:
- `zones/zone-draw-crossing.utils.spec.ts` — LF-C1 (5): square false; bow tie (1-3-2-4) true; triangle false; concave "L" false; vertex touching a non-adjacent edge true.
- `zones/zone-map/zone-map-draw-fix.component.spec.ts` — LF-C2 (2): double-click zoom off after `startDraw`, on after `cancelDraw`, on after a 3-point `finishDraw`; LF-C3 (2): bow tie → Finish disabled, message visible, `finishDraw()` emits nothing and stays in draw mode; `undoPoint()` → Finish enabled, message hidden, Finish emits; message hidden after Cancel. Real `ZoneMapComponent` (Leaflet initialises in Karma as in UL-T4); the private `map` read with bracket access in the spec only.
- `zones/zone-editor-dialog/zone-editor-preset.component.spec.ts` — LF-C4 (2): `change` with value `0` applies preset 0 (kept note and date), select back to `''` / "Choose a preset…"; applying `0` again after an edit re-applies it. LF-C5 (2): only the electric preset has a `hint`; the editor renders that hint once for one rule. Own stub of `app-zone-map` with the five current bindings.

## Commands Executed

| Step | Command | Exit / result |
|---|---|---|
| 0 | `workflow.mjs status work-items/ORDERS-FIX-001/run.json` | 0, `done` rev 9 |
| 0 | `git switch master`; `git log -1 --oneline`; `git status --short` | 0; `081640a`; allowed untracked only |
| 0 | `node tools/session-brief.mjs` | 0 |
| 0 | `netstat -ano` (port 4200) | no listener |
| 0 | `git switch -c fix/lez-fix-001` | 0 |
| 1 | `workflow.mjs init …` then `step … ready` with `01`, `02`, `03-proof.json` | 0; `implement` rev 3 |
| 2 | `npx ng test --watch=false --browsers=ChromeHeadless` | 0; 435 SUCCESS |
| 2 | `rm -rf dist && npm run build` | 0; 12 warnings |
| 3 | `npx ng test --watch=false --browsers=ChromeHeadless` | 0; **448 SUCCESS** (435 + 13) |
| 3 | `rm -rf dist && npm run build` | 0; 0 ERROR; 12 warnings, sorted text identical to the baseline |
| 3 | `git status --short` | only the allowed files (below) plus pre-existing untracked `work-items/*/review.md` |
| 4 | `workflow.mjs step … ready 04-proof.json` | see run.json |

## Verification Results

| Req | Evidence | Status |
|---|---|---|
| LF-R1 | LF-C2 | VERIFIED in Karma; browser UL-M4 NOT_VERIFIED |
| LF-R2 | LF-C1, LF-C3 | VERIFIED in Karma; browser UL-M2 (bow tie) NOT_VERIFIED |
| LF-R3 | LF-C4 | VERIFIED in Karma; browser UL-M7 NOT_VERIFIED |
| LF-R4 | LF-C5 | VERIFIED in Karma |

Browser check (`bsk`): not run in this session. No test zone was saved, no DB write.

`git status --short` after the change:

```
 M GVRP_Entry_client_v2/src/app/features/admin/zones/zone-draw.utils.ts
 M GVRP_Entry_client_v2/src/app/features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.html
 M GVRP_Entry_client_v2/src/app/features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.ts
 M GVRP_Entry_client_v2/src/app/features/admin/zones/zone-map/zone-map.component.ts
 M GVRP_Entry_client_v2/src/app/features/admin/zones/zone-rule-form.utils.ts
?? GVRP_Entry_client_v2/src/app/features/admin/zones/zone-draw-crossing.utils.spec.ts
?? GVRP_Entry_client_v2/src/app/features/admin/zones/zone-editor-dialog/zone-editor-preset.component.spec.ts
?? GVRP_Entry_client_v2/src/app/features/admin/zones/zone-map/zone-map-draw-fix.component.spec.ts
?? work-items/DOC-SYSTEM-001/review.md
?? work-items/LEZ-FIX-001/
?? work-items/UX-LEZ-001/review.md
?? work-items/UX-ORDERS-001/review.md
```

## Modified Tests

None. No existing spec, fixture, stub or helper was edited; both editor `ZoneMapStubComponent`s and the UL-T4 spec are untouched. Only the three new spec files above were added.

## Security-Relevant Changes

None. Client-only UI logic; no endpoint, query, auth, logging or dependency change. The server's `GeoJsonGeometryParser` `isValid()` check stays the authority for geometry.

## Deviations From Design

- D4: the hint line is built as `"{label}: {hint with lower-case first letter}"` through a small `lowerFirst` helper on the component, so the stored hint stays a sentence ("Vehicles … too.") and the rendered line matches the design text exactly.
- D2: the `1e-12` tolerance on "on the line" is an implementation choice the design left open.
- `zone-editor-dialog.component.scss` not touched (allowed, not needed).

## Remaining Uncertainty

- Real browser behaviour (UL-M2 normal and bow tie, UL-M4 double-click while drawing, UL-M7 select reset): NOT_VERIFIED.
- A Leaflet double-click still fires two `click` events, so it adds two identical points; `polygonFromPoints` drops the duplicate (unchanged, as before).
- An outline that doubles back along its own previous edge (collinear overlap of two *adjacent* edges, a "spike") is not flagged by `ringSelfIntersects`; the server check still refuses it at Save. Not in the design's case list.
- Files written by script with LF line endings; git warns it will convert to CRLF on the next touch (L-2026-009 if hashes are compared byte-for-byte).

## Documentation Now Stale

`GVRP_Document` D07 description of the zone editor (drawing control and preset select): not updated, by the user's decision of 2026-10-30 23:27.

## Lesson Candidates

None new. (The PowerShell 5.1 encoding candidate from ORDERS-FIX-001 still stands; files here were edited with Python in UTF-8.)

## Handoff to Reviewer

- Re-run in `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` (448) and `rm -rf dist && npm run build` (12 warnings).
- Read first: `ringSelfIntersects` in `zone-draw.utils.ts`, `finishablePolygon` / `updateFinishButton` in `zone-map.component.ts`, `onPresetSelected` and the preset select in the editor.
- Manual: UL-M2 (normal and bow-tie outline), UL-M4 (double-click while drawing does not zoom; outside drawing it does), UL-M7 (select resets, same preset twice). Record any test zone saved.
