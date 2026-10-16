# Discovery — DRIVER-MAP-001 (read-only)

- `RoutingService.getRouteGeometry` (`shared/services/routing.service.ts:25`): OSRM `router.project-osrm.org` road geometry + `getStraightLine` fallback; already used by the planner map with identical fallback semantics (VERIFIED).
- Planner map tiles: Google `mt1.google.com/vt/lyrs=m` (`map.component.ts:71`); driver mini-map reuses the same layer for a consistent look (VERIFIED).
- `marker.service.createRouteStopMarker` exists but is planner-styled; driver detail uses its own small numbered divIcons to stay independent of planner marker evolution (routine choice, documented).
- Universal Google Maps directions URL needs no key/billing: `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>` (public documented format; no repo dependency).
- `sent_at` arrives ISO (`2026-10-08T06:25:07.852145` seen live); Angular `DatePipe` (via existing `CommonModule` import) formats it — no helper needed.
- Leaflet in Karma: planner `map.component` renders Leaflet in the same headless suite, so a guarded mini-map init is test-safe; map init stays defensive (container/coord null guards) with pure-URL builder covered by specs.
