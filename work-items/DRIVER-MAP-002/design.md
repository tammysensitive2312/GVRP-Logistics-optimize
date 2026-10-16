# Design — DRIVER-MAP-002

All in `GVRP_Entry_client_v2/src` (driver-view + `RoutingService` additive method). No backend change.

## RoutingService: `getRouteLegs(coordinates)`

- New method (existing `getRouteGeometry` untouched): same OSRM URL + `&steps=true`; maps `routes[0].legs[]` → `RouteLeg[]`: `{ summary, distanceKm, durationMin, steps: ManeuverStep[] }`, where `ManeuverStep = { instruction, distanceM }`.
- Pure exported `maneuverInstruction(maneuver, name): string` (unit-covered):
  - depart → `Head out` (+ `onto {name}` if named); turn + modifier → `Turn {modifier} onto {name|ref|the road}` (`modifier` words: `uturn→Make a U-turn`, else title-cased); `new name` → `Continue onto {name}`; `continue` → `Continue straight` (+ `onto`); roundabout/rotary → `Enter the roundabout` (+ name); arrive → `Arrive at destination`; else `{type} {modifier}` + name. Street names pass through verbatim.
- Distance text helper: `<1000 m` → `X m`, else `X.X km`.
- OSRM failure → `of(null)` (same catchError semantics); view falls back to no-maneuver list (map fallback unchanged).

## Driver view

- `maneuvers = signal<ManeuverStep[]>` per opened detail (cleared on back/list change); "🧭 Route guidance" section lists instruction + distance in stop order. Empty (fallback) → section hidden, no error.
- Markers: divIcon html becomes number + name label (CSS ellipsis), depot keeps 🏢 with distinct color.
- Direction arrows on the polyline via `leaflet-polylinedecorator` (own side-effect import in driver-view component).
- GPS: `watchPosition` on detail open (high accuracy, 10 s timeout); blue dot + accuracy circle; `clearWatch` on back/destroy/open-change; denial/unavailable → silent (no dot, no error). Not asserted on position accuracy — display only.

## Verification

- Specs: instruction matrix (all branches), distance text, legs mapping incl. failure→null, GPS success/denial paths (mocked geolocation), arrows/markers presence via DOM classes, full suite + tsc + budgets, live browser (maneuver list + dot with real route).
