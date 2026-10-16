# Design — DRIVER-MAP-001

All inside `GVRP_Entry_client_v2/src/app/features/driver/driver-view/` (+ `ApiService` untouched, `RoutingService` reused as-is).

## UI (`driver-view.component.html`)

- Detail view gains, in order: mini-map `<div class="driver-map">` (~260px), the DM-003 note, then the existing stop list.
- Each ORDER stop with non-null lat/lon gets a "🧭 Navigate" anchor: `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>`, `target="_blank"`, `rel="noopener"`. Built by a pure exported helper `googleMapsUrl(lat, lon): string | null` (null-safe → spec-covered).
- List `sent {{ send.sent_at | date:'dd/MM/yyyy HH:mm' }}` (DM-004).

## Map (`driver-view.component.ts`)

- On detail open (after view render): create `L.map` once per detail session (destroy on back/list change), Google tile layer (same URL as planner map), numbered `L.divIcon` markers in stop order (depots 🏢, orders sequence number), polyline from `RoutingService.getRouteGeometry` (OSRM) or dashed straight-line fallback, `fitBounds`.
- All Leaflet work behind null/empty guards (no coords → no map, list still renders); map code isolated in `renderMiniMap()` so specs can assert container presence without asserting Leaflet internals.

## Verification

- Specs: URL builder (encoding, null coords), sent_at formatting, Navigate link per order stop with own coords, map container present on detail; fallback when geometry null (service mock).
- `tsc` + full Karma (5 pre-existing failures baseline) + `ng build` budgets; live browser check on a real assigned route.
