# Design — UX-LEZ-001

Inputs: `request.md` (UL-R1…R5), `context.md`. Technical design by Claude, 2026-10-29. Client v2 only; no new dependency; UI text in English. Stacked on UX-ORDERS-001.

## 1. Zone drawing (UL-R1)

**D1 — Pure helpers** in new `features/admin/zones/zone-draw.utils.ts`:
- `polygonFromPoints(points: [number, number][] /* [lat, lng] */): GeoJsonPolygon | null` — fewer than 3 distinct points → null; else `{type:'Polygon', coordinates:[[ [lng,lat]…, first point repeated ]]}` with values rounded to 6 decimals.
- `EXAMPLE_ZONE` — a small rectangle Polygon in central Hà Nội (Hoàn Kiếm), as a constant.

**D2 — `ZoneMapComponent`** gains `@Input() editable = false` and `@Output() polygonDrawn = new EventEmitter<GeoJsonPolygon>()`. When `editable`:
- a small Leaflet control (plain DOM, no plugin) with buttons **Draw**, **Undo point**, **Finish**, **Cancel**;
- Draw: map clicks add points; a dashed polyline and small circle markers show the points; the cursor is a crosshair;
- Undo point removes the last point; Cancel clears the draft; Finish (enabled with ≥ 3 points) emits `polygonFromPoints(points)` and leaves draw mode;
- the read-only behaviour (`editable = false`, default) is unchanged for the zones page and other callers.
- Existing geometry keeps being drawn; the draft is drawn above it.

**D3 — Editor wiring** (`zone-editor-dialog.component.{ts,html,scss}`):
- `<app-zone-map [editable]="isCopied()" (polygonDrawn)="onPolygonDrawn($event)">`; `onPolygonDrawn` sets `geometry` and `geometryText` (pretty JSON) and clears geometry errors.
- Above the textarea a hint: "Draw the zone on the map (Draw → click the corners → Finish), or paste GeoJSON: a Polygon whose points are [longitude, latitude] and whose last point equals the first." and a link "Draw on geojson.io" (`https://geojson.io`, new tab, `rel="noopener"`).
- Button **Load example** fills `geometryText` with `EXAMPLE_ZONE` and previews it.

## 2. Rule form help (UL-R2)

**D4 — Help text** under each field (one line, `form-hint`):
- Legal classes: "Vehicles of any ticked class match. Empty = any class."
- Energy types: "Vehicles with any ticked energy type match. Empty = any."
- Weight bound reads: "GROSS = vehicle's legal gross weight; PAYLOAD = its load capacity. Needs a bound below."
- Weight > / ≤: "Kilograms. Example: trucks over 3.5 t → GROSS, Weight > 3500."
- Emission standard below: "Euro level 1–6. Vehicles with a LOWER standard match. Example: 4 bans Euro 1–3."
- Applies to app-based services only: "Leave unticked for an internal fleet."
- Time rule: "ALWAYS_FORBIDDEN: all day. FORBIDDEN_DURING: banned in the intervals. ALLOWED_ONLY: allowed only in the intervals."
- Days: "Empty = every day."
- Effective from/to: "From is required; empty To = no end date."
- Above the selector fields: "All filled fields must match (AND). A rule with no vehicle field applies to every vehicle."

**D5 — Presets** (pure, in `zone-rule-form.utils.ts`, new export `RULE_PRESETS: {label, apply(form): ZoneRuleFormModel}[]`), a select "Start from a preset" per rule card; applying replaces the selector and schedule fields and keeps `effectiveFrom` (sets it to `localDateIso()` if blank), `effectiveTo`, `legalBasis`, `note`:
1. "Trucks over 3.5 t, peak hours" — TRUCK, GROSS > 3500, FORBIDDEN_DURING 06:00–09:00 and 16:00–19:30;
2. "Below Euro 4, all day" — emission standard below 4, ALWAYS_FORBIDDEN;
3. "Only electric vehicles" — energy types HYBRID, PETROL, DIESEL, OTHER, ALWAYS_FORBIDDEN;
4. "Motorbikes, all day" — MOTORBIKE, ALWAYS_FORBIDDEN.

**D6 — Warnings** (pure, new export `ruleWarnings(form): string[]`, shown in amber under the card, never blocking save):
- emission standard below ≤ 1 → "No vehicle has an emission standard below 1; this rule never matches on emissions.";
- emission standard below > 6 → "Euro levels go up to 6; every vehicle with a known standard matches.";
- every legal class ticked → "All classes are ticked; this is the same as leaving them empty.";
- every energy type ticked → same wording for energy types;
- effective-to before today → "This rule has already ended.".
Existing exports and `validateRuleForm` stay unchanged.

## 3. Route and Timeline sections (UL-R3…R5)

**D7 — Scroll fix.** `orders-section.component.scss`: `.routes-tab-content` and `.timeline-tab-content` use `overflow-y: auto` (not `hidden`); the selector `app-routes-view` becomes `app-route-view` with `flex: none` so the tab content scrolls as a whole. No other layout change.

**D8 — Expand dialog.** New `features/main/orders/expanded-view-dialog/expanded-view-dialog.component.{ts,html,scss}` (standalone, `MatDialog`), data `{ view: 'route' | 'timeline', solution: SolutionDTO }`:
- header with the title ("Route" / "Timeline") and a **Collapse** button (icon `close_fullscreen`) that closes the dialog;
- `route`: its own `<app-compliance-panel>` + `<app-route-view>` wired exactly like the tab (compliance → route view, `complianceRecheckNeeded` → panel recheck); `timeline`: `<app-timeline-view>`;
- opened with `width: '95vw', maxWidth: '95vw', height: '90vh'`; the content scrolls.
- `orders-section.component.{ts,html}`: an **Expand** icon button (`open_in_full`, tooltip "Expand") at the top-right of the Route and Timeline tab contents → `openExpanded(view)`; after the dialog closes, `compliancePanel.load()` is called on the tab's panel so a send or recheck done in the dialog is reflected.
- `CompliancePanelComponent`: new optional output `violationFocused` emitted after `focusLeg`; the dialog closes on it so the planner sees the highlighted leg on the map.

**D9 — Grouped geometry findings.** `compliance-panel.component.{ts,html}`: per route, when there are **two or more** `LEZ_GEOMETRY_MISSING` violations, they are replaced by one non-clickable line "N legs have no stored road geometry — it is fetched and checked when the route is sent." placed where the first of them was; one such violation stays as today. Pure helper `groupGeometryMissing(rows)` exported from the component file or a new `lez-compliance-panel/compliance-rows.utils.ts`. Status chip, send rule and other violations unchanged.

## 4. Tests (new spec files only)

| ID | Case | Expected |
|---|---|---|
| UL-T1 | `polygonFromPoints`: 2 points; 3 points; duplicates; rounding | null; closed ring in [lng, lat]; null when < 3 distinct; 6 decimals |
| UL-T2 | Editor: `onPolygonDrawn(polygon)` | `geometry()` and `geometryText()` updated, errors cleared; save sends that polygon |
| UL-T3 | Editor: Load example | `geometryText` = `EXAMPLE_ZONE`, preview set |
| UL-T4 | `ZoneMapComponent` editable=false (default) | no draw control rendered; editable=true → control rendered (DOM check; clicks may be covered by UL-T1 if Leaflet events are impractical in Karma) |
| UL-T5 | `RULE_PRESETS` each | resulting form passes `validateRuleForm`; effective-from kept or set to today |
| UL-T6 | `ruleWarnings` per case | messages as listed; a normal rule → `[]` |
| UL-T7 | `groupGeometryMissing`: 0, 1, 3 geometry findings mixed with a ZONE_ACCESS | unchanged; unchanged; one summary line + the ZONE_ACCESS line |
| UL-T8 | Orders section: Expand on Route opens `ExpandedViewDialogComponent` with `{view:'route', solution}`; after close the tab panel's `load` is called | as stated |
| UL-T9 | Expanded dialog: route view renders panel + route view; `violationFocused` closes it; Collapse closes it | as stated |

## 5. Risks

- `orders-section.component.scss` is close to its budget warning; keep additions minimal, never exceed the 8 kB error.
- Two compliance panels exist while the dialog is open (one GET each); acceptable.
