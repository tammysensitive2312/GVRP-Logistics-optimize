# Implementation — DRIVER-MAP-002

- `RoutingService`: additive `getRouteLegs` (OSRM `steps=true`; legs → `{summary, distanceKm, durationMin, steps}`), pure `maneuverInstruction` (depart/arrive/turn/new-name/continue/roundabout/end-of-road/fork/merge/ramps + fallback) and `formatLegDistance`. Old `getRouteGeometry` untouched.
- `DriverViewComponent`: `maneuvers` signal (zero-distance depart/arrive noise filtered), "🧭 Route guidance" section, labeled divIcons (name + depot style), direction arrows via `leaflet-polylinedecorator` side-effect import, GPS `watchPosition` dot + accuracy ring (silent denial, cleared on back/destroy/re-open).
- Template/SCSS: map container (unchanged), note, guidance list, pin-name styles.
- Tests: routing 5 (legs mapping, error→null, <2 coords, instruction matrix, distance text) + driver GPS 2 (dot + clearWatch, denial silence) + maneuver list/fallback specs. Full suite 157 + same 5 pre-existing; tsc 0; budgets unchanged.
- No backend, auth, billing, voice, or rerouting (DG-005).

## Repair 1 — render-once flag (user-test finding; see DRIVER-MAP-001 records)

Covered under DRIVER-MAP-001 repair history; the single-fetch spec lives here.

## Repair 2 — marker visibility + responsive map (user feedback 2, 2026-10-09)

- Root cause of invisible markers: `.driver-pin` rules in component SCSS never match Leaflet-created DOM (emulated encapsulation); planner markers use inline styles for this reason. Fixed by inline-styled pins (34px, white border, shadow); dead SCSS rules removed.
- Follow-up from live screenshot: inline name pills overlapped in dense clusters → dropped to number-only pins (names in stop list + tap tooltips, Google-Maps-like).
- `.driver-detail-grid`: mobile stacked full-bleed 320px map; PC ≥900px grid 2fr/1fr with sticky 520px map.
- Verified live via screenshots (v6 numbered pins, v7 clean pins).
- Maneuver polish: `end of road`/`fork`/`merge`/`ramp` templates + zero-distance step filter.
- New specs: GPS dot + denial, maneuver list/fallback, single-render pin. No requirement change.
