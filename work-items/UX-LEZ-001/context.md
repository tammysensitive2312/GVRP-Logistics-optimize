# Context — UX-LEZ-001

Inspected 2026-10-29 22:05–22:30 by Claude (`fix/browser-fix-001` working tree). Paths relative to `GVRP_Entry_client_v2/src/app/`.

- `features/admin/zones/zone-map/zone-map.component.ts`: read-only Leaflet preview (`L.map`, Google tiles, `preferCanvas`), inputs `geometry`, `depots`, `height`; draws `L.geoJSON` and depot markers, `fitBounds`. No click handling.
- `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.{ts,html,scss}` (after BROWSER-FIX-001): Name input; Geometry textarea (`geometryText`), "Preview geometry" (`applyPastedGeometry` → `extractPolygon` → `geometry` signal), file upload ≤ 2 MB; `<app-zone-map [geometry]="geometry()" height="260px">`; rules via `zone-rule-form.utils` (`emptyRuleForm`, `fromRuleDTO`, `isSelectorEmpty`, `toRuleInput`, `validateRuleForm`, `ZoneRuleFormModel`); save = rename (if changed) → PUT geometry → PUT rules. Server stores x = longitude, y = latitude.
- Rule semantics (Entry `RuleSelector`, `RuleEvaluator`, `RuleSchedule`): selector fields are ANDed, absent = no constraint, list fields match any listed value; `weightKg` bounds read `grossWeightKg` (GROSS) or `capacity` (PAYLOAD); `emissionStandardBelow` matches vehicles with `emissionStandard < value` (Euro level); unknown vehicle facts count as a match and are reported as missing; schedule types `ALWAYS_FORBIDDEN`, `FORBIDDEN_DURING` (intervals forbidden), `ALLOWED_ONLY` (only intervals allowed), `daysOfWeek` empty = every day, `24:00` allowed as end, start > end crosses midnight. Client validation already blocks: start == end, `24:00` as start, interval-less FORBIDDEN_DURING/ALLOWED_ONLY, to < from, > 50 rules, a weight measure without a bound, blank effective-from.
- `features/main/orders/orders-section-view/orders-section.component.html`: Route tab = `<div class="routes-tab-content">` with `<app-compliance-panel #compliancePanel [solution] (complianceChange)>` then `<app-route-view [solution] [compliance] (complianceRecheckNeeded)="compliancePanel.recheck()">`; Timeline tab = `<app-timeline-view [solution]>`.
- `orders-section.component.scss`: `.routes-tab-content { display:flex; flex-direction:column; height:100%; overflow:hidden; … app-routes-view { flex:1; min-height:0 } }` — the selector names `app-routes-view`, the element is `app-route-view`, and the container hides overflow → content below the fold is unreachable (UL-R3 cause). `.timeline-tab-content` has the same `overflow:hidden` with a correct `app-timeline-view` selector.
- `features/main/orders/lez-compliance-panel/compliance-panel.component.{ts,html}`: `rows()` maps every violation to a row (`violationSummary`), each clickable (`focusViolation` → `MapService.focusLeg`). Old routes produce one `LEZ_GEOMETRY_MISSING` per leg (screenshot 22:03: 9 and 16 lines).
- `MapService` is a root singleton; the planner map stays behind any dialog.

## Not verified

- Existing compliance-panel specs' fixtures with several `LEZ_GEOMETRY_MISSING` of one route (engineer checks; stop if an assertion would break).
- Leaflet click events in Karma (tests target pure helpers where needed).
