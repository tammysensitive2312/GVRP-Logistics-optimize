# Discovery — DRIVER-MAP-002 (read-only + correction ref)

See `correction-01.md` for the rev-1 evidence-pointer fix (state change itself is valid).

- OSRM v5 `/route` supports `steps=true`: response `routes[0].legs[].steps[]` with `maneuver{type, modifier?, location}`, `name`, `distance` (m), `duration` (s). Our `RoutingService.getRouteGeometry` today requests `overview=full&geometries=geojson` WITHOUT steps and maps only geometry/distance/duration (VERIFIED `routing.service.ts:25-52`).
- Direction arrows: planner `map.service addDirectionArrows` uses `leaflet-polylinedecorator` (side-effect import); driver bundle does not load it yet — needs its own side-effect import (package already a dependency) (VERIFIED).
- Geolocation: `navigator.geolocation.watchPosition` works on localhost (secure context); denial must fail silent.
- Numbered divIcon markers already render; DG-001 extends them with name labels + depot styling (CSS-only + label text).
