# Context — LEZ-FIX-001

Inspected 2026-10-30 23:35–23:50 +07:00 by Claude (cloud session), reading `master` @ `081640a` through the device bridge. Nothing was run. Client paths relative to `GVRP_Entry_client_v2/src/app/features/admin/zones/`.

## Drawing (`zone-map/zone-map.component.ts`, `zone-draw.utils.ts`)

- `L.map(host, { zoomControl: true, preferCanvas: true })` — Leaflet's `doubleClickZoom` handler is on by default.
- `startDraw()`: `drawing = true`, `points = []`, `map.on('click', onMapClick)`, crosshair cursor. `stopDrawing()` (from Finish, Cancel, and `syncDrawControl` when `editable` turns off) reverses it. `ngOnDestroy` removes the map.
- A double-click fires two `click` events (two points, usually identical; `polygonFromPoints` drops duplicates) **and** `dblclick` → zoom in (F1).
- The control is plain DOM built in `buildDrawControl()` (buttons Draw, Undo point, Finish, Cancel; `L.DomEvent.disableClickPropagation`). `updateFinishButton()` disables Finish when `!drawing || polygonFromPoints(points) === null`; it runs from `renderDraft()` after every change.
- `polygonFromPoints(points)` (pure): Leaflet `[lat, lng]` → GeoJSON ring `[lng, lat]`, rounded to 6 decimals, duplicates removed, closed; `< 3` distinct points → `null`. No crossing check (F2).
- `finishDraw()` emits `polygonDrawn` with the polygon. The editor (`zone-editor-dialog.component.ts#onPolygonDrawn`) sets `geometry` and the pretty JSON text.
- Server: `GVRP_Entry_API/.../service/lez/GeoJsonGeometryParser.parse` refuses `!isValid()` with 400, field `geometry` → the editor shows "Failed to save the geometry: …" only at Save.

## Presets (`zone-editor-dialog/zone-editor-dialog.component.{ts,html}`, `zone-rule-form.utils.ts`)

- Template: `<select [ngModel]="null" (ngModelChange)="applyPreset(ruleIndex, $event)">` with `<option [ngValue]="null">Choose a preset…</option>` and one option per `RULE_PRESETS` index. The bound model value never changes (`null` → `null`), so Angular does not write the select back and the DOM keeps the chosen label (F3); choosing the same preset again fires no change event.
- `applyPreset(index, presetIndex)` replaces the rule with `preset.apply(rule)` and clears `formErrors`.
- `RULE_PRESETS` (`zone-rule-form.utils.ts`): `{ label, apply }`; "Only electric vehicles" forbids energy types HYBRID, PETROL, DIESEL, OTHER; unknown vehicle facts count as a match (LEZ semantics), so vehicles with no energy type are banned too (F4).

## Tests that exist (by name)

`zone-draw.utils.spec.ts`, `zone-rule-form.utils.spec.ts`, `zone-rule-presets.utils.spec.ts`, `zone-map/zone-map-draw.component.spec.ts` (UL-T4: real `ZoneMapComponent`; asserts the control's buttons are exactly `['Draw', 'Undo point', 'Finish', 'Cancel']` and that a 3-point draft finishes), `zone-editor-dialog/zone-editor-draw.component.spec.ts`, `zone-editor-dialog.component.spec.ts` and `zone-editor-rename.component.spec.ts` (the last three use a `ZoneMapStubComponent` declaring `geometry`, `depots`, `height`, `editable`, `polygonDrawn` — verified in `zone-editor-draw.component.spec.ts`). Lesson candidate from UX-LEZ-001: a new `@Input`/`@Output` on `app-zone-map` bound in the editor template breaks those stubs (NG0303). This design adds none.

## Not verified

- Behaviour in the browser (Karma covers logic only); the review's UL-M2…M4 cases are the manual check.
