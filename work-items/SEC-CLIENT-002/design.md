# Design — SEC-CLIENT-002

Follows the SEC-011 pattern: the reset is central (`SessionResetService.resetBranchState()`), and the
holder of branch state ignores results of work started before the reset (`SolutionStore` /
`CollectionCache` `generation` counter).

## D1 — Drawing generation in `MapService` (MC-R2, MC-R3)

`private generation = 0`. It is incremented by:

- `initializeMap(map)` — a new `<app-map>`; nothing started for the previous map may draw;
- `clearRoutes()` — and therefore `displaySolution`, `clearSolution`, `clearAll`; a cleared or
  replaced solution may not be redrawn by its own late callbacks (MC-T6);
- `reset()` (D2).

Every asynchronous step captures the generation when it starts and drops its result when the value
has changed:

- `drawZones()` captures it before subscribing to `lezApi.listZones()`; `next` returns without
  drawing when stale. Signature unchanged (no parameters): `map.service.xss.spec.ts` calls it by
  bracket access.
- `displaySolution` captures it after `clearRoutes()`, passes it to `displayRoute`, stops the route
  loop when stale and skips the final `fitBounds` when stale.
- `displayRoute(route, color, generation)` checks after `await storedPath(...)` and after the OSRM
  `await` (including the `catch` fallback) before writing to `routeLayers` or `focusable`.
- `drawStraightLine` stays synchronous and unchanged; it is only reached after a check.

No `takeUntil` / unsubscribe: one mechanism, the same as the stores. The HTTP request itself still
completes; only its effect on the map is dropped (request exclusion).

## D2 — `MapService.reset()` called by the session reset (MC-R2)

```ts
/** Drops everything drawn for the previous session; late callbacks of it are ignored (SEC-CLIENT-002). */
reset(): void
```

Increments `generation`; when a map is bound, calls `clearAll()` (depot and order markers, routes,
zones, highlight, focus registry). Safe before any `initializeMap` and on a map already removed by
Leaflet (`removeLayer` of a layer not on the map is a no-op).

`SessionResetService.resetBranchState()` adds `this.injector.get(MapService).reset()` (resolved
lazily, as the other entries). The only spec with a real `SessionResetService`
(`session-reset.spec.ts`) provides `HttpClient`, which `MapService`'s dependencies need.

**Revised during implement (2026-11-10):** the static `import { MapService }` in
`session-reset.service.ts` moved `MapService` and Leaflet into the initial bundle (measured: initial
total 670.96 kB → 847.35 kB raw). The dependency is inverted instead:
`SessionResetService.registerResetHook(hook)` keeps a list of resets run at the end of
`resetBranchState()`, and `MapService`'s constructor registers `() => this.reset()`. A `MapService`
that was never created holds no branch state, so nothing is lost. Same behavior, no bundle change.

## D3 — Other layers (MC-R4)

No change: depot and order markers are drawn synchronously from store-fed inputs; component-scoped
maps (`zone-map`, `driver-view`, pickers) die with their component (see `context.md`).

## D4 — Tests (new spec file only)

`shared/services/map.service.session.spec.ts`, real Leaflet maps and real `SessionResetService`;
`LezApiService` / `RoutingService` answers are Subjects.

| ID | Case | Expected |
|---|---|---|
| MC-T1 | `displaySolution` under A, zones pending; reset; map A removed; `initializeMap(B)`; zones arrive | no path/marker on map B |
| MC-T2 | two routes, stored geometry of route 1 pending; reset; new map B; geometry arrives | nothing on B (no polyline, no route-2 markers) |
| MC-T3 | OSRM geometry pending; reset; new map B; geometry arrives | nothing on B |
| MC-T4 | solution fully drawn on A; reset | map A has no path/marker |
| MC-T5 | control: within one session, zones + stored route + straight-line route | 1 zone, 6 stop markers, ≥3 paths |
| MC-T6 | `clearSolution` while stored geometry pending; geometry arrives | nothing drawn |

## Risks

- A `displaySolution` interrupted by a new one (same solution re-emitted) now stops drawing the
  first; the second draws completely (MC-T5 path). Previously both drew, duplicating layers.
- `reset()` instantiates `MapService` on the first logout/login even if no map was shown (Leaflet
  is already in the main bundle; no request is made).
