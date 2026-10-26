# Context — LEZ-001 (discover)

Inspected revision: `master @ 617b720` (2026-10-25). The working tree had only the untracked
`GVRP_*/.claude/` folders and `diff1*.txt`, none of them product code.
Method: direct file inspection and `grep` on the user's machine through the session shell.
A code index was not used. Everything below is **static inspection**; nothing was executed.
Status vocabulary follows AGENTS.md §5.

## 1. Current behavior relevant to LEZ

### 1.1 Data model (Entry API)

| Fact | Source | Status |
|---|---|---|
| `Order` has `location` as `POINT SRID 4326`, `timeWindowStart/End` (`LocalTime`), `deliveryDate` (`LocalDate`, not null), `requiredSkills` (JSON `Set<VehicleSkill>`), `status`, `priority`, `branch` (not null). | `GVRP_Entry_API/.../entity/Order.java` | VERIFIED (static) |
| `VehicleType` has `branch` (not null), `typeName`, `vehicleFeatures` (JSON string), `capacity` (`Integer`, no unit recorded), `fixedCost`, `costPerKm`, `costPerHour`, `maxDistance`, `maxDuration`. It has **no** gross weight, energy type or emission standard. | `.../entity/VehicleType.java` | VERIFIED (static) |
| `vehicleFeatures` JSON = `VehicleFeaturesDTO{category: VehicleCategory, emissionFactor (required, ≥0), skills: Set<VehicleSkill>}`. | `.../dto/request/VehicleFeaturesDTO.java` | VERIFIED (static) |
| `VehicleCategory` = `BICYCLE, MOTORCYCLE, CAR, VAN, PICKUP_TRUCK, BOX_TRUCK, FLATBED_TRUCK, TANKER_TRUCK, SEMI_TRUCK`. | `.../entity/enums/VehicleCategory.java` | VERIFIED (static) |
| `VehicleSkill` = `ELECTRIC, HYBRID, PETROL, DIESEL, REFRIGERATED, FRAGILE_CAPABLE, URBAN_ACCESS, HAZMAT_CAPABLE`. Energy type is currently expressed only as a skill. | `.../entity/enums/VehicleSkill.java` | VERIFIED (static) |
| `Branch` fields: `name`, `branchWebhookURL`, depots, fleets, users, orders, solutions. There is no zone concept anywhere in the Entry API. | `.../entity/Branch.java`; grep for zone/geofence/restrict found none | VERIFIED (static) |
| `UserRole` = `PLANNER, CUSTOMER, DRIVER`. There is **no system-administrator role**. | `.../entity/enums/UserRole.java` | VERIFIED (static) |
| `OptimizationJob` has `inputData` (String), status, timestamps, `branch`, `solution`. There is no dedicated snapshot field for rules. | `.../entity/OptimizationJob.java` | VERIFIED (static) |
| `Route` holds `distance`, `co2Emission`, `serviceTime`, `orderCount`, `loadUtilization` and `segments` (`RouteStop`). There is **no stored geometry**. | `.../entity/Route.java` | VERIFIED (static) |
| `RouteStop` holds `sequenceNumber`, `type` (DEPOT/ORDER), `location`, `arrivalTime`, `departureTime`, `distanceToNext`, `timeToNext`, `loadAfter`. These are a natural place for per-leg geometry. | `.../entity/RouteStop.java` | VERIFIED (static) |
| `UnassignedOrder` has a free-text `reason`. | `.../entity/UnassignedOrder.java` | VERIFIED (static) |
| JTS `Point` uses x = longitude, y = latitude, through `GeometryFactory(PrecisionModel, 4326)`. `jts-core 1.20.0` and `hibernate-spatial` are dependencies. | `.../mapper/GeometryMapper.java`; `GVRP_Entry_API/pom.xml` | VERIFIED (static) |
| Schema changes are applied as dated SQL files, latest `2026-10-18-pareto-candidates.sql`. | `GVRP_Entry_API/src/main/resources/database/migrations/` | VERIFIED (static); the apply mechanism was not inspected |

### 1.2 Engine (jsprit 1.8, GraphHopper 11.0)

| Fact | Source | Status |
|---|---|---|
| Vehicle skills are added with `builder.addSkill`, and order skills with `addRequiredSkill`, after upper-case normalisation. | `GVRP_Engine_API/.../service/OptimizationService.java:602,654,659-679` | VERIFIED (static) |
| `maxDistance` per vehicle type is enforced, with a post-solve `assertMaxDistances`. This is a pattern for a post-solve LEZ assert. | `OptimizationService.java:316,410,691-728` | VERIFIED (static) |
| No custom `HardActivityConstraint` / `HardRouteConstraint` exists today (grep found none). | grep over `GVRP_Engine_API/src/main` | VERIFIED (static) |
| GraphHopper is embedded with **one profile `car`**, a custom model (speed statements) and a **CH profile `car`**. `fetch()` routes A→B with `Locale.ENGLISH` and keeps only time and distance. The path points are discarded. | `.../distance_matrix/Impl/GraphHoperDistanceProvider.java:79-146` | VERIFIED (static) |
| All vehicle categories (motorbike, truck) are routed with the same `car` profile. | same | VERIFIED (static) |
| `POST /api/v1/optimization/evaluate` → `RouteEvaluationService.evaluate` returns `RouteViolation{code, routeIndex, orderId, detail}`. Current codes: `OVER_CAPACITY, TIME_WINDOW, SKILL_MISMATCH, MAX_DISTANCE, ROUTING_UNAVAILABLE`. | `.../controller/OptimizationController.java:231`; `.../service/RouteEvaluationService.java:45-210`; `.../model/RouteViolation.java` | VERIFIED (static) |
| The engine request carries depots, orders (lat/lon, TW, demand, skills), vehicle types (capacity, costs, maxDistance, emissionFactor, skills), vehicles and config. There is **no date or zone information**. | `GVRP_Entry_API/.../dto/request/EngineOptimizationRequest.java`, `EngineOrderDTO.java`, `EngineVehicleTypeDTO.java` | VERIFIED (static) |
| The engine `UnassignedOrder` has `orderId`, `orderCode`, `reason`. `UnassignedOrderDiagnosticService` exists. | `.../model/UnassignedOrder.java`; file list | VERIFIED (static); diagnostic logic not read |

### 1.3 Manual edits and sending

| Fact | Source | Status |
|---|---|---|
| `RouteCustomizationService.evaluate` previews and never persists. `saveRevision` re-evaluates on the server through the engine and stores a **new solution revision**. | `GVRP_Entry_API/.../service/RouteCustomizationService.java:61-109` | VERIFIED (static) |
| `RouteSendService.send` checks branch ownership, that the route has order stops and that the driver is enabled and in the same branch, then supersedes earlier sends. **It does not check feasibility or compliance.** | `.../service/RouteSendService.java:95-135` | VERIFIED (static) |

### 1.4 Client v2 maps

| Fact | Source | Status |
|---|---|---|
| `RoutingService` calls the **public OSRM demo server** `https://router.project-osrm.org` for geometry (`getRouteGeometry`) and turn-by-turn legs (`getRouteLegs`, `OsrmManeuver`). | `GVRP_Entry_client_v2/src/app/shared/services/routing.service.ts:26-112` | VERIFIED (static) |
| The driver view uses both geometry and maneuvers from OSRM. | `.../features/driver/driver-view/driver-view.component.ts:35,236,257-263` | VERIFIED (static) |
| The planner map (`map.service.ts`) also draws OSRM geometry. | `.../shared/services/map.service.ts:159` | VERIFIED (static) |
| Map library: `leaflet` and `leaflet-polylinedecorator`. There is no drawing/editing plugin. | `GVRP_Entry_client_v2/package.json` | VERIFIED (static) |

## 2. Gaps against the request

1. There is no zone, rule, permit or legal-attribute model (LEZ-R01…R05).
2. The engine receives neither the delivery date nor any zone data (LEZ-R06, R07).
3. Path geometry is computed by GraphHopper but thrown away. The paths shown to users come from a **different router (OSRM)** (LEZ-R08, R12).
4. Sending has no compliance guard (LEZ-R11).
5. There is no administrator role for system templates. P1 keeps templates migration-only (request §6).
6. `capacity` has no recorded unit. The legal weight must not be inferred from it (request §4).

## 3. Contradictions and risks

- **Matrix path ≠ displayed path.** The optimiser's travel times come from GraphHopper `car`, while both maps show OSRM. Any "the driver saw a different route" report today has this cause. LEZ makes it a compliance defect, so it is in scope (R12).
- **One `car` profile for every vehicle.** Motorbike and truck paths are approximated by a car path. Transit detection inherits this approximation. Record it as a known limitation.
- **CH profile.** Contraction Hierarchies fix the weighting when the graph is prepared. P3 avoidance therefore needs additional prepared profiles (or a non-CH mode), and a graph rebuild when zones change. This does not affect P1/P2.
- **Spatial predicates.** MySQL behaviour for SRID 4326 (geographic axis order and the predicates it supports) was not verified for the local MySQL version. Containment and intersection in Java (JTS, planar lon/lat, adequate at city scale) avoid that risk. Chưa xác minh for MySQL.
- **Tenant isolation.** Templates are the first data with no `branch_id` that branches still read. Every branch-side table must carry `branch_id`, and lookups must use `findByIdAndBranchId`, as with the other branch data (AGENTS.md §15; SEC-TENANT-001).

## 4. Affected areas (planned)

- Entry API: new entities and migrations, `VehicleType` legal attributes, a restriction compiler, a compliance checker, a send guard, job snapshot, result DTOs, new controllers.
- Engine API: request model (zones, compiled restrictions, service date), skill injection, post-solve assert, per-leg geometry and instructions, evaluate violations; P2 hard activity constraint.
- Client v2: zone admin page, legal fields in the vehicle-type form, permits, order badge, compliance panel, server-geometry maps (driver and planner).
- Docs: this work item. `PROJECT_STATE.md` gets new claims only after verification.

## 5. Unanswered decisions carried to design

- Whether the planner map should also switch to server geometry. Recommended: yes, for consistency (design §9 D1).
- Template authoring without an admin role: migration-only in P1 (request §6). An admin role is deferred.
