# Context — SEC-CLIENT-002

Inspected 2026-11-10 by Claude Code (side session) at `0b6b759`. Paths relative to
`GVRP_Entry_client_v2/src/app/` unless stated.

## Verdict on audit lead #8: CONFIRMED (VERIFIED by unit spec, not yet in a browser)

### Source trace

| Step | Location | Fact |
|---|---|---|
| Singleton | `shared/services/map.service.ts:29-32` | `MapService` is `providedIn: 'root'`; it outlives logout/login in the same SPA instance. |
| Re-binding | `shared/services/map.service.ts:65-73` | `initializeMap(map)` replaces `this.map`, `this.routeLayers`, `this.zoneLayers`, `this.highlightLayer` with new groups on the new map. Nothing invalidates callbacks started for the previous map. |
| Zone sink | `shared/services/map.service.ts:177-202` | `drawZones()` subscribes to `lezApi.listZones()` (no stored subscription, no session check) and in `next` adds each zone polygon and its name tooltip to `this.zoneLayers`, read at callback time. |
| Route sinks | `shared/services/map.service.ts:148-168, 205-289` | `displaySolution` awaits `displayRoute` per route; `displayRoute` awaits `storedPath` (`lezApi.routeGeometry`, line 306) and then OSRM (`routingService.getRouteGeometry`, line 262) and writes polylines, arrows, tooltips (plate, order count) and the next routes' stop markers into `this.routeLayers` / `this.map`, read after each await. The final `fitBounds` also uses the current `this.map`. |
| Reset gap | `core/services/session-reset.service.ts:30-37` | `resetBranchState()` (logout, 401, login: `auth.service.ts:161,182`, `core/interceptors/auth.interceptor.ts:39`, `navbar.component.ts:134`) resets the stores and polling but not `MapService`. |
| Data ownership | `GVRP_Entry_API/.../controller/BranchZoneController.java:33-36`, `LezComplianceController.java:42` | `/zones` and `/routes/{id}/geometry` are scoped to the caller's JWT branch: the late response is branch A's data. |

### Failing specs before the fix (new file `shared/services/map.service.session.spec.ts`)

`npx ng test --watch=false --browsers=ChromeHeadless` at `0b6b759` + the new spec only:
exit 1, **Executed 490 of 490 (5 FAILED), 485 SUCCESS** — all 484 existing specs passed (no
pre-existing failures); MC-T5 (control) passed.

| Spec | Failure on unfixed code | Meaning |
|---|---|---|
| MC-T1 zone list for A not drawn on B's map | `paths = 1` on map B | A's zone polygon (with its name tooltip) is drawn on B's map — **the lead** |
| MC-T2 A's stored geometry and remaining routes | `paths = 3, markers = 3` on map B | A's route 1 polyline + arrows and route 2's stop markers (customer names in popups) on B's map |
| MC-T3 A's OSRM geometry after reset | `paths = 2` on map B | same through the OSRM path |
| MC-T4 reset clears an open map | `paths = 3, markers = 3` remain | reset did not touch the map at all |
| MC-T6 clear while geometry in flight | `paths = 3, markers = 3` | same root cause within one session: a cleared solution is redrawn by its own late callbacks |

## Other map layers checked

| Layer / map | Path | Gap? |
|---|---|---|
| Depot markers | `MapService.loadDepots` (sync), fed by `MapComponent` inputs from `DepotStore` | No: synchronous; `DepotStore` (via `CollectionCache`) already drops responses from before a reset (SEC-011). |
| Order markers | `MapService.loadOrders` (sync), fed by component inputs | No: synchronous, no request of its own. |
| Branch LEZ zones on the solution map | `MapService.drawZones` | **Yes** (MC-T1). |
| Routes (stored geometry, OSRM, straight line, stop markers of later routes, `fitBounds`) | `MapService.displaySolution` / `displayRoute` | **Yes** (MC-T2, MC-T3). |
| Violation highlight | `MapService.focusLeg` (sync, from compliance panel) | No async step; uses `focusable`, cleared by `initializeMap`/`clearRoutes`. |
| Admin zone map | `features/admin/zones/zone-map/zone-map.component.ts` | No: Leaflet map owned by the component instance and removed in `ngOnDestroy`; no root state. |
| Driver map | `features/driver/driver-view/driver-view.component.ts` | No: component-owned map; late OSRM callbacks check `this.map` of the old instance, set to `null` by `destroyMap()` on destroy. |
| Pickers | `map-picker-dialog`, `location-picker` | No branch data loaded asynchronously into a shared map. |

`RoutingService`, `MarkerService`, `GeocodingService` hold no cached branch data.

## Constraints from existing specs

- `shared/services/map.service.xss.spec.ts` calls the private `drawZones()` without arguments and `drawStraightLine(coordinates, color, route)` by bracket access after `initializeMap`; both must keep drawing synchronously into the current layers.
- `core/services/session-reset.spec.ts` uses the real `SessionResetService` with `provideHttpClient`; the other specs that touch logout use a spy `SessionResetService` or a spy `AuthService`, so resolving `MapService` inside `resetBranchState()` adds no new DI requirement to them.
- Lessons: `docs/engineering/LESSONS.md` client entries (lockfile, stop dev server before `npm ci`).
