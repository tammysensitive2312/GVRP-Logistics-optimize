# Implementation — DRIVER-MAP-001

- `driver-view.component.ts`: exported pure `googleMapsUrl(lat, lon)` (null-safe); `renderMiniMap()` (Google tile layer same as planner map, numbered divIcons, OSRM polyline via `RoutingService` or dashed straight fallback, fitBounds; all guarded + try/catch); `ngAfterViewChecked` triggers once per opened send; `OnDestroy` cleanup.
- `driver-view.component.html`: `#driver-mini-map` container, guidance-only note, per-ORDER-stop Navigate anchor (`target=_blank rel=noopener`), `sent_at | date:'dd/MM/yyyy HH:mm'`.
- `driver-view.component.scss`: map height, note, pin styles.
- Tests: 4 new specs (URL encoding/nulls, container+note+link href/target, sent formatting). Driver-view 8/8; full suite 147 + same 5 pre-existing; tsc 0; budgets unchanged (driver styles under budget).
- No backend, auth, or routing-service change (DM-005).

## Repair 1 — empty mini-map (user-test finding, 2026-10-08, `user-feedback-01.md`)

- Cause (author's bug): `destroyMap()` cleared the render-once flag while `renderMiniMap()` called it first → every change-detection cycle re-rendered; re-creating `L.map` on an initialized container throws, swallowed by try/catch → permanently empty map.
- Fix: flag owned solely by `ngAfterViewChecked` (set after render; cleared only on back/list/open change); `destroyMap()` touches only the Leaflet object. New spec pins exactly one geometry fetch per opened send. No requirement/design change.
