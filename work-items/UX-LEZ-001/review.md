# Review — UX-LEZ-001

Reviewer: Claude (cloud session), 2026-10-30. Read against `master` @ `081640a` (branch `feat/ux-lez-001` @ `c52bb17` is merged).
Scope read: `zone-draw.utils.ts`, `zone-map.component.ts`, `zone-rule-form.utils.ts` (presets, warnings), `compliance-rows.utils.ts`, `expanded-view-dialog.component.{ts,html}`, `orders-section.component.{ts,html,scss}`, `lez.model.ts`, `checkpoint-blocked-ng0303.json`, `implementation.md`.
Not re-run: Karma and build (435 SUCCESS, 12 warnings as recorded).

## Verdict

**Accepted.** D1–D9 match the design. The NG0303 stop was handled correctly: the engineer stopped, checkpointed, offered A/B, and changed the two spec stubs only after the user chose A; no assertion changed. No server code, no dependency, no new request.

## Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| F1 | LOW | PLAUSIBLE (not run) | Drawing mode keeps Leaflet's `doubleClickZoom`. A double-click adds two points (often duplicates, which `polygonFromPoints` removes) and zooms in. Suggest `map.doubleClickZoom.disable()` in `startDraw` and `enable()` in `stopDrawing`. |
| F2 | LOW | VERIFIED by reading | `polygonFromPoints` does not reject a self-intersecting ring (a "bow tie" from clicking corners out of order). The server validation decides; if it accepts it, compliance results inside the zone are undefined. Suggest a client warning or a server check (JTS `isValid()`). |
| F3 | LOW | from `implementation.md` | The preset select keeps the last label after applying. Reset it to `null` after `applyPreset` so the same preset can be re-applied and the UI does not suggest the rule still equals the preset after edits. |
| F4 | INFO | VERIFIED by reading | Preset "Only electric vehicles" forbids HYBRID/PETROL/DIESEL/OTHER. Because unknown vehicle facts count as a match, vehicles with no energy type (e.g. non-motorized) are also forbidden and reported as missing data. Intended by LEZ semantics; worth one line in the preset help text. |
| F5 | INFO | design-accepted | While the expanded Route dialog is open there are two compliance panels (two GETs). A send or recheck in the dialog refreshes the tab panel only after closing. |
| F6 | INFO | VERIFIED by reading | D7: `.routes-tab-content` / `.timeline-tab-content` now `overflow-y: auto`; the dead `app-routes-view` selector is replaced by `app-route-view { flex: none; }`. The scroll defect from the browser check has its cause removed. |

## Lesson candidate (accepted for LESSONS.md at closure)

When a component gains an `@Input`, search specs for stubs with the same selector before implementing (NG0303 fails every test of the host).

## Manual test scenarios

| # | Steps | Expected |
|---|---|---|
| UL-M1 | Zones → a LINKED zone → Edit | No drawing control on the map |
| UL-M2 | A COPIED zone → Edit → Draw → click 4 corners → Finish | Red polygon replaces the old one; JSON text updated (pretty, `[lng, lat]`, closed ring); Save works |
| UL-M3 | Draw → 2 points | Finish disabled; Undo point removes the last; Cancel clears the draft |
| UL-M4 | Draw → double-click on the map | **F1:** map zooms in while drawing |
| UL-M5 | Load example | Rectangle around Hoàn Kiếm shown; Save stores it |
| UL-M6 | Click the geojson.io link | Opens a new tab; the app tab is unchanged |
| UL-M7 | Rule card → each of the 4 presets | Selector and schedule replaced; Effective from kept or set to today; legal basis and note kept |
| UL-M8 | Emission standard below = 1; then 7; tick all classes; Effective to in the past | Amber warnings for each; Save still allowed |
| UL-M9 | Route tab with a long violation list → scroll | List scrolls to the end |
| UL-M10 | Route tab → Expand → click a violation | Dialog closes; leg highlighted on the main map |
| UL-M11 | Expand → Recheck → Collapse | Tab panel shows the rechecked result |
| UL-M12 | Timeline tab → Expand | Timeline at ~95% × 90% of the window; Collapse returns |
| UL-M13 | Solution with ≥ 2 legs without geometry | One italic line "N legs have no stored road geometry…", not clickable |
