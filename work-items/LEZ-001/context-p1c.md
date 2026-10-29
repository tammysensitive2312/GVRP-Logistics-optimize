# Context addendum — LEZ-001, increment P1c

Inspected on 2026-10-28 by Claude, branch `feature/lez-001` (P1a + P1b + P1b fix, uncommitted). Static reading through the file bridge; nothing executed, no database touched.
Adds to `context.md` and `context-p1b.md` (both are recorded evidence and stay unchanged).

## 1. Client v2 facts (`GVRP_Entry_client_v2/src/app/`)

| Fact | Source |
|---|---|
| Angular 18.2 standalone components, signals, Angular Material 18, Leaflet 1.9.4 + `leaflet-polylinedecorator` 1.6.0, `@asymmetrik/ngx-leaflet`, Transloco 7. No drawing plugin, no GeoJSON or polyline library. | `package.json` |
| Admin area is `/admin` (`AdminLayoutComponent`) with child routes `vehicle-types`, `vehicles`, `depots`; menu items are a static array in `admin-layout.component.ts`. Admin pages use hard-coded English text (a few Transloco keys in the vehicle-type dialog). | `app.routes.ts`, `features/admin/admin-layout/*` |
| `ApiService` holds every HTTP call; snake_case DTOs; base URL `environment.apiUrl`. No LEZ call exists. | `core/services/api.service.ts`, `core/models/*` |
| `VehicleTypeDTO`/`VehicleTypeInputDTO` have no legal attributes. The vehicle-type dialog sends a full payload on PUT; the server PUT is a full replacement, so today every edit **clears** `legal_class`, `gross_weight_kg`, `energy_type`, `emission_standard` (P1a Deviation 9). | `core/models/vehicle.model.ts`, `features/admin/vehicle-types/vehicle-type-dialog/*`, `implementation.md` §P1a Deviation 9 |
| Vehicles admin is read-only (list + search). No permit UI. | `features/admin/vehicles/*` |
| Orders: `OrdersSectionComponent` loads a page (`getOrders`, default filter = today's date), selection is single-page; `onPlanRoutesClick` builds `RoutePlanningRequest` without any date check and shows `err.message` (the generic `HttpErrorResponse` text, not the server message) on failure. | `features/main/orders/orders-section-view/orders-section.component.ts` |
| Orders table columns are fixed in `displayedColumns`; order code cell has no badge. | `components/orders-table/*` |
| Route tab: `RouteViewComponent` (cards, edit session, send). Send opens `SendRouteDialogComponent`, then `api.sendRoute`; 4xx message is read from `error.error.errors[0].message`. No compliance state. | `route-view/*`, `send-route-dialog/*` |
| Planner map: `MapService.displayRoute` sorts stops by arrival time and draws OSRM geometry (`RoutingService.getRouteGeometry`, public `router.project-osrm.org`) with a dashed straight-line fallback and direction arrows. | `shared/services/map.service.ts`, `shared/services/routing.service.ts` |
| Driver view: mini map draws OSRM geometry and builds the maneuver list from OSRM `getRouteLegs`; `DriverStopDTO` client model has no geometry, instructions or zone fields. | `features/driver/driver-view/*`, `core/models/driver.model.ts` |
| Existing specs mock `ApiService` with `jasmine.createSpyObj` and a fixed method list (orders-section: `getOrders`, `bulkUpdateOrderStatus`[, `getPareto`]; route-view: `evaluateRoutes`, `saveRouteRevision`, `latestRevisionId`, `routeSends`, `sendRoute`; driver-view: `myDriverRoutes`, `driverRouteDetail` + `RoutingService` spy; send dialog: `listDrivers`, `revisionChain`, `routeSends`). A new call on these mocks fails the spec → user decision D8. | the five spec files named in `user-decision-02.md` |
| Driver-view specs expect OSRM calls when the detail has no geometry (e.g. `routing.getRouteGeometry` called once; maneuvers from `getRouteLegs`). A fallback that keeps today's behaviour for geometry-free details keeps them valid. | `driver-view.component.spec.ts` |
| Client test command (CI, blocking): `npx ng test --watch=false --browsers=ChromeHeadless`; last recorded full run 245/245 (2026-10-23). Build budgets: `anyComponentStyle` warning 4 kB / **error 8 kB**; initial bundle warning already exceeded (warning only). | `.github/workflows/ci.yml`, `angular.json`, `DRIVER-MAP-004/verification-rereview.md` |
| `public/i18n/*.json` and `angular.json`, `package*.json` are outside the run scope (`GVRP_Entry_client_v2/src`). | `run.json` scope |

## 2. Entry API contracts the client will consume (all PLANNER, snake_case on the wire)

| Endpoint | Shape (key facts) | Source |
|---|---|---|
| `GET /api/v1/zone-templates` / `{id}` | `id, code, name, jurisdiction, version, boundary_confidence, boundary_source, legal_basis`; detail adds `geometry` (GeoJSON MultiPolygon) and `rules` | `ZoneTemplateDTO` |
| `GET/POST /api/v1/zones`, `GET/PATCH/DELETE /zones/{id}` | `id, mode, template_id, template_code, name, enabled, effective_version, source_template_version, acknowledged_template_version, template_version, update_available, geometry, rules[], warnings[]?` (warnings only on writes: `{code: DEPOT_IN_ZONE, depot_id}`); POST body `{template_id, mode}`; PATCH `{enabled}`; DELETE 409 when a permit references the zone; LINKED twice → 409 | `BranchZoneDTO`, `ZoneService` |
| `PUT /zones/{id}/geometry` | body = raw GeoJSON Polygon / MultiPolygon / Feature; COPIED only (LINKED → 409); 400 on invalid/too large (> 5,000 vertices) | `BranchZoneController`, `GeoJsonGeometryParser` |
| `PUT /zones/{id}/rules` | body = list of `{selector, schedule, effective_from, effective_to, legal_basis, note}`; `selector`/`schedule` inner keys **camelCase** (`legalClasses, energyTypes, weightMeasure, weightKg{gt,gte,lt,lte}, emissionStandardBelow, appBased`; `type, daysOfWeek, intervals[["HH:mm","HH:mm"]]`); `{}` selector matches every vehicle; unknown keys → 400; ≤ 50 rules; ≤ 48 intervals; start == end invalid; `24:00` only as end | `ZoneRuleInputDTO`, `RuleValidator`, `RuleSelector`, `RuleSchedule` |
| `POST /zones/{id}/acknowledge` | `{version}` must equal the template's current version; COPIED only | `ZoneService.acknowledge` |
| `GET /zones/{id}/verdict?vehicleId=&date=yyyy-MM-dd` | `{kind: ALLOWED/ALL_DAY/PARTIAL, forbidden[[start, end]] (minute pairs, `MinuteInterval` serialises as a 2-element array), missing_attributes[], applied_permit_ids[]}` | `ZoneVerdict`, `MinuteInterval` |
| `GET/POST /api/v1/vehicles/{vehicleId}/permits`, `PUT/DELETE …/{permitId}` | `{id, vehicle_id, branch_zone_id, permit_no, schedule, valid_from, valid_to, note}`; input same without ids; schedule null = whole day; `ALWAYS_FORBIDDEN` refused | `VehiclePermitDTO`, `VehiclePermitInputDTO`, `RuleValidator.parsePermitSchedule` |
| Vehicle types (existing) | input adds `legal_class, gross_weight_kg, energy_type, emission_standard`; output adds those + read-only `suggested_legal_class` | `VehicleTypeInputDTO`, `VehicleTypeDTO` |
| `GET /api/v1/solutions/{id}/compliance`, `POST …/recheck` | `{solution_id, routes[{route_id, vehicle_id, status: UNCHECKED/COMPLIANT/VIOLATION/STALE, checked_at, violations}]}`; `violations` is stored JSON written with the Spring `ObjectMapper` bean (SNAKE_CASE, `WebConfig.objectMapper`) from `ComplianceChecker.Violation(code, seq, orderId, zoneKey, detail)` → expected keys `code, seq, order_id, zone_key, detail` (**engineer verifies**); `seq` = 0-based plan index of the stop | `SolutionComplianceDTO`, `ComplianceReadService`, `RouteComplianceService.violationsJson` |
| `GET /api/v1/routes/{id}/geometry` | `{route_id, legs[{from_seq, to_seq, polyline, instructions[{text, distance_m, time_ms, sign, street_name}]}]}`; polyline precision 1e5, `[lat, lon]`; null when not routed | `RouteGeometryDTO` |
| Driver detail (existing) | each stop adds `geometry_to_next`, `instructions_to_next[]`, `zone_names[]` | `DriverStopDTO` |
| Solution detail (existing) | only `unassigned_order_ids`; **no unassigned reason or reason code** reaches the client | `SolutionDetailResponseDTO` |
| Orders (existing) | `OrderDTO` has **no zone field** (design §7 "order list gains `zoneCodes[]`" was not implemented in P1b) | `OrderDTO`, `OrderService` |
| Job submit (existing) | mixed delivery dates → 400 listing the dates (D4) | `OptimizationJobService` (P1b N4) |

## 3. Gaps that need server work in P1c

1. **Order badge (R15):** no zone membership on orders. `OrderService`, `OrderController` and `OrderDTO` are covered by historical tests that construct them directly (`OrderServiceTest` via `@InjectMocks`, `OrderBulkControllerTest`), so a new constructor dependency or a mapper change there risks touching accepted tests. A separate read endpoint avoids that.
2. **Compliance panel (R15, design §8 "unassigned for LEZ by zone"):** the client cannot see unassigned reasons, and violations carry zone keys (`Z<id>`) without names. `ComplianceReadService` already loads the solution with its routes; its constructor need not change to add these fields.

## 4. Lessons that apply

L-2026-001, -003, -004, -007, -009, -010 (no lock-file change is allowed here), -011 (stop `ng serve` before tests/`npm ci`), -012, -014. P1a/P1b lesson candidates (in `implementation.md`): local `@SpringBootTest` needs the migrations; "not found" is 400 elsewhere (LEZ uses 404); standalone MockMvc principal attributes.
