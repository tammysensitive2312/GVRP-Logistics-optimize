# Task for the engineer — LEZ-001, increment P1b

You are `gvrp-engineer` (or a Claude Code session acting as the engineer). Implementation of P1 is authorized by the user (2026-10-26 22:54 +07:00). This task is **increment P1b** (`design.md` §15, §16). P1a is accepted (`review-p1a.md` §5). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root.

## Read first
1. `work-items/LEZ-001/design.md`: §4, §5, §7, §10, §11, §12, §15 and **§16 (P1b decisions)**
2. `work-items/LEZ-001/context-p1b.md` (corrects `context.md` §1.2)
3. `work-items/LEZ-001/review-p1a.md` (findings F6–F9 are **not** in P1b scope)
4. `work-items/LEZ-001/implementation.md` §P1a (what exists: `service/lez/*`, `RuleEvaluator`, `ZoneService`, …)
5. `docs/engineering/LESSONS.md`: L-2026-001, -003, -004, -007, -008, -009, -012, -014

If the design is wrong or impossible, stop and report. Do not redesign.

## Step 0 — preconditions
1. Run `node tools/session-brief.mjs` and include the output.
2. Run `git status --short` on branch `feature/lez-001`. Expected: the P1a changes, `work-items/LEZ-001/*` and `docs/engineering/LESSONS.md`. If anything else is modified or untracked (apart from `GVRP_*/.claude/`, `diff1*.txt`, `.mcp.json`), stop and report.
3. Run `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json`. State must be `implement`. Report any warning. Warnings on `work-items/LEZ-001/*` files are expected; continue.

## Step 1 — baseline (before code changes)
- `GVRP_Entry_API`: run `mvnw.cmd -B clean test`. Expected 190 run, 0 failures. The local DB has the P1a migration.
- `GVRP_Engine_API`: run `mvnw.cmd -B clean test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest" -Dsurefire.failIfNoSpecifiedTests=false`. This exclusion is user decision D6 (2026-10-27). Record:
  - counts;
  - **every failing test by name**. `MeasuredLawsTest` may fail (KI-030); record it and do not fix it.

If surefire ignores the `!` pattern or runs no tests, stop and report.

## What to build

### Part E — Engine API (`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/`)

**E1. LEZ skills.**
- `model/Order` and `model/Vehicle` gain `Set<String> lezSkills` (default empty).
- In `OptimizationService.buildJspritService`, add `lezSkills` with `addRequiredSkill`; in `buildJspritVehicle`, add them with `addSkill`. Reuse the existing `normalizedSkills` (upper-case, trimmed, distinct, sorted).
- Requests without the field behave exactly as today.

**E2. Path provider.**
- New interface `distance_matrix/RoutePathProvider` with `LegPath path(OptCoordinates from, OptCoordinates to, Locale locale)`.
- `LegPath` = encoded polyline + distance m + time ms + list of `Instruction{text, distanceM, timeMs, sign, streetName}`.
- GraphHopper implementation: reuse the GraphHopper instance and profile `car` already built in `GraphHoperDistanceProvider` (add the method there, or create a new bean that receives the same instance; **do not build a second graph**). It uses `getBest().getPoints()` and `getInstructions()`.
- New `utils/PolylineEncoder` implementing the Google encoded polyline algorithm (precision 1e5, `[lat, lon]`), with `encode` and `decode`.

**E3. Geometry on stops.**
- `model/Stop` gains `String geometryToNext`. After routes are extracted, fill it for each consecutive stop pair, for every stop except the last, in:
  - the single-objective result (`optimizeSingleObjective`);
  - every FRONTIER alternative (`optimizeMultiObjective`);
  - `RouteEvaluationService.evaluate` results.
- Use a small new `service/LegGeometryService` (one place) with `Locale.ENGLISH`, since instructions are not sent here.
- A leg that throws or cannot be routed: set null, `log.warn` with route index and sequence numbers **only** (no coordinates, AGENTS.md §15), and continue.
- Map `geometryToNext` into the completion callback payload. Find where `Stop` is converted for the callback (`SolutionConverter` / `CallbackService`) and add the field there only.

**E4. Legs endpoint.**
- `POST /api/v1/optimization/legs` on `OptimizationController`.
  - Body: `{ "points": [{"latitude": ..., "longitude": ...}, ...], "locale": "vi" }`.
  - Response: `{ "legs": [ {"polyline", "distanceM", "timeMs", "instructions": [ ... ]} ] }` with `points.size() - 1` legs.
- Validation:
  - 2 ≤ points ≤ 1,001, else 400;
  - lat/lon in range, else 400;
  - locale optional. If GraphHopper has no translation for `vi`, use `en` and set `"locale": "en"` in the response. **Check which locale GraphHopper 11 actually serves and record it.**
- A leg that cannot be routed is returned with `polyline: null` and an `error` code. The request does not fail.
- Follow the existing controller and error style of `/evaluate` (same JSON naming as the rest of the engine API).

### Part N — Entry API (`GVRP_Entry_API/src/main/...`)

**N1. Migration** (new) `resources/database/migrations/2026-10-28-lez-compliance.sql`, in the P1a header style; the user runs it manually.
- `routes`: `lez_status ENUM('UNCHECKED','COMPLIANT','VIOLATION') NULL`, `lez_checked_at DATETIME NULL`, `lez_ruleset_hash CHAR(64) NULL`, `lez_violations JSON NULL`. `STALE` is computed on read, not stored (design §16.2-6).
- `route_stops`: `geometry_to_next MEDIUMTEXT NULL`, `instructions_to_next JSON NULL`.
- `optimization_jobs`: `lez_snapshot JSON NULL`.
- `unassigned_orders`: `reason_code VARCHAR(40) NULL`.
- Map the new columns on the entities.

**N2. DTO plumbing.**
- `EngineOrderDTO.lezSkills` and `EngineVehicleDTO.lezSkills` (`Set<String>`, omitted when empty).
- `EngineCallbackRequest.StopData.geometryToNext`.
- Engine evaluate response stop DTO `geometryToNext`.
- `EnginePlanMaterializer.buildStop` and `RouteCustomizationService.persistRevision` copy `geometryToNext` to `RouteStop`.
- `EngineApiClient` (+ impl) gains `legs(List<point>, String locale)`, with the same error mapping as `evaluateRoutes` (engine down → `EngineUnavailableException`).

**N3. `service/lez/RestrictionCompiler`** (new). Input:
- branchId (from the principal, via the caller);
- `serviceDate`;
- the job's vehicles (with type, start/end depot);
- orders.

Output `CompiledRestrictions`:
- `serviceDate`;
- `zones` [key `Z<branchZoneId>`, branchZoneId, mode, templateId, effective version, GeoJSON geometry];
- per vehicle and zone a `ZoneVerdict` (from `RuleEvaluator`, permits included);
- `orderZones` (order id → keys, JTS `covers`);
- `depotZones`;
- `unusableVehicles` [vehicleId, zoneKey, `DEPOT_IN_ZONE_ALL_DAY`] for vehicles whose start or end depot is in a zone where their verdict is `ALL_DAY`;
- `hash` (design §16.2-6).

Rules:
- Only **enabled** branch zones. No zones → empty result, hash of empty.
- Reuse `ZoneService`'s rule loading. Refactor it into a package-private helper; behaviour and P1a tests unchanged.
- Read-only.

**N4. Job submit** (`OptimizationJobService.submitJob`):
- **D4:** after loading the orders, if their `deliveryDate`s are not all equal → 400 (`DataInvalidException`, `VALIDATION_ERROR`) whose message lists the dates. This applies to every branch.
- Compile for that date. Then:
  - add `LEZ_<key>` to `lezSkills` of every order in zone *key*;
  - add `LEZ_<key>` to every vehicle **not** `ALL_DAY` in *key*;
  - leave unusable vehicles out of the engine request. If **no** vehicle remains, refuse with 400 and name the zones and depots.
- Store the snapshot (compiled restrictions + hash) in `optimization_jobs.lez_snapshot` in the same transaction as the job row.

**N5. `service/lez/ComplianceChecker`** (new, pure: no Spring, no repositories). Input:
- one route as an ordered list of stops (type, order id, location point, arrival, departure, `geometryToNext`);
- the vehicle id;
- `CompiledRestrictions`.

Output: a list of `{code, routeIndex/seq, orderId?, zoneKey, detail}`. Codes (design §5.3, §16.2-5):
- **`ZONE_ACCESS`:** a stop (order, start depot, end depot or intermediate depot) is in *Z*, and the vehicle is ALL_DAY in *Z*, or [arrival, departure] overlaps its PARTIAL intervals. Use the depot's departure at the start and its arrival at the end.
- **`ZONE_TRANSIT`:** the decoded leg line (JTS `LineString`, x = lon) `intersects` *Z*, and the vehicle is ALL_DAY in *Z*, or [departure of stop i, arrival of stop i+1] overlaps its PARTIAL intervals.
- **`LEZ_DATA_MISSING`:** the vehicle is restricted in a zone it touches and its verdict lists `missingAttributes`. The detail names them.
- **`LEZ_GEOMETRY_MISSING`:** zones are enabled and some leg has null geometry.

Times are `LocalTime` minutes of the service date (one-day jobs, D4). An empty compilation → no violations.

**N6. Certification hooks.** Each hook stores `lez_status` (`COMPLIANT` if no violation, else `VIOLATION`), `lez_checked_at`, `lez_ruleset_hash` and `lez_violations` on each `Route`:
- (a) **Completion callback** (`OptimizationCallbackService` path that materialises routes), using the **job snapshot** restrictions. Also set `UnassignedOrder.reason_code = LEZ_NO_COMPLIANT_VEHICLE` (human `reason` naming the zones) for unassigned orders with zone keys where no vehicle in the request carried all their LEZ skills (design §16.2-2).
- (b) **Pareto apply** (the `ParetoService` path that materialises a candidate through `EnginePlanMaterializer`), using **current** restrictions for the plan's date.
- (c) **`RouteCustomizationService`:**
  - `evaluate` (preview) appends the LEZ violations to the returned list (current restrictions, engine geometry);
  - `saveRevision` treats them as **hard** (D5: add to the 422 summary) and certifies the saved routes.
  - Do **not** add LEZ skills to evaluate requests: LEZ is checked by Entry only.

**N7. Send guard** (`RouteSendService.sendRoute`). After the existing checks and **before** superseding sends:
1. Determine the route's service date from its orders. Mixed dates → 422 `LEZ_MIXED_DATES`; this can only come from routes made before D4.
2. Compile **current** restrictions.
3. If any leg lacks geometry, or any leg lacks instructions: call the engine `legs` endpoint with the route's stop points and locale `vi`, and store polylines and instructions on the `RouteStop`s. Engine unavailable → the send fails (existing 502 mapping). It fails closed.
4. Run `ComplianceChecker` and persist the result on the route.
5. Any violation → **422** `UnprocessablePlanException`, with the violation codes, sequence numbers and zone keys in the message.
6. Otherwise continue unchanged.

**N8. Read APIs** (PLANNER unless stated; branch from the principal; another branch → 404 as in P1a, using `LezNotFoundException`):
- `GET /api/v1/solutions/{id}/compliance`: per route `{routeId, vehicleId, status, checkedAt, violations}`. `status` is `STALE` when the stored hash ≠ the current hash for that date (computed, not written). `?recheck=true` recomputes with current restrictions and **writes**; it is a GET with a side effect, so make it `POST /api/v1/solutions/{id}/compliance/recheck` instead.
- `GET /api/v1/routes/{id}/geometry`: legs `[{fromSeq, toSeq, polyline, instructions}]` for the planner map (D1).
- Driver `GET /api/v1/driver/routes/{sendId}` (existing, DRIVER): `DriverStopDTO` gains `geometryToNext`, `instructionsToNext` and `zoneNames` (names of the branch's **enabled** zones covering the stop, current data). The existing ownership check is unchanged.

## Tests to add (new files only; DB-free; no GraphHopper graph: use fakes of `RoutePathProvider`)

| ID | Case | Expected |
|---|---|---|
| EB-T1 | `lezSkills` on order and vehicle become jsprit required and offered skills; absent field = today's behaviour | Exact skill sets |
| EB-T2 | Tiny VRP (fake costs): an order requiring `LEZ_Z1`, one vehicle with and one without | Assigned to the vehicle with the skill; without it → unassigned |
| EB-T3 | `PolylineEncoder` round trip on known vectors (the published Google example `_p~iF~ps|U_ulLnnqC_mqNvxq`@`) | Exact |
| EB-T4 | `LegGeometryService` with a fake provider on a 3-stop route; one leg throws | Two polylines set, one null, no exception |
| EB-T5 | `/legs` validation: 1 point, 1,002 points, out-of-range lat | 400 each |
| EB-T6 | `/legs` with a fake provider | n−1 legs, instructions present, locale echoed or `en` fallback |
| RC-T1 | Compiler: two zones, one disabled | Only the enabled one compiled |
| RC-T2 | Depot in an ALL_DAY zone for vehicle A, not for EV B | A unusable with `DEPOT_IN_ZONE_ALL_DAY`; B usable |
| RC-T3 | Hash stable for equal input; changes when a permit, a template version, a vehicle attribute or `enabled` changes | As stated |
| JS-T1 | Submit with orders on two dates (branch with **and** branch without zones) | 400 listing both dates; nothing saved, engine not called |
| JS-T2 | Submit with zones: request carries `LEZ_Z…` on in-zone orders and on allowed vehicles only; unusable vehicle absent; snapshot stored | As stated |
| JS-T3 | All vehicles unusable | 400 naming zone/depot; engine not called |
| CC-T1 | Stop in ALL_DAY zone | `ZONE_ACCESS` |
| CC-T2 | Both stops outside, leg line crosses the zone, ALL_DAY | `ZONE_TRANSIT` |
| CC-T3 | PARTIAL peak 06:00–09:00 / 16:00–19:30: stop 07:30 vs 10:00; leg 08:50→09:05 crossing | violation / none / `ZONE_TRANSIT` |
| CC-T4 | Restricted with `missingAttributes` | `LEZ_DATA_MISSING` naming the attribute |
| CC-T5 | Leg geometry null with zones; no zones at all | `LEZ_GEOMETRY_MISSING`; empty |
| CB-T1 | Callback: routes certified from the snapshot; LEZ-unassigned order gets `reason_code` | Status and reason as expected |
| CU-T1 | Preview with a transit violation | Listed, nothing persisted |
| CU-T2 | Save with a LEZ violation | 422; nothing persisted |
| SG-T1 | Send a compliant route that lacks instructions | Engine `legs` called once, instructions stored, send created |
| SG-T2 | Send where a permit expired since planning | 422 with `ZONE_ACCESS`; no `RouteSend` row; route status updated |
| SG-T3 | Engine unavailable during the legs fill | 502; no `RouteSend` row |
| SG-T4 | Route of another branch | Existing not-found behaviour unchanged |
| RA-T2 | DRIVER/CUSTOMER on `/solutions/{id}/compliance`, `/routes/{id}/geometry` | 403 |
| TI-T1 | Branch A calls compliance / recheck / geometry with branch B's ids | 404; nothing written |

If a DB-free test of any row is impossible, stop and report.

## Allowed changes
- **Engine:**
  - `model/Order.java`, `model/Vehicle.java`, `model/Stop.java`;
  - `service/OptimizationService.java` (E1, E3 only), `service/RouteEvaluationService.java` (E3 only);
  - the callback conversion class(es) for `Stop` (E3 mapping only);
  - `controller/OptimizationController.java` (E4 only);
  - `distance_matrix/Impl/GraphHoperDistanceProvider.java` (E2 only);
  - new files under `distance_matrix/`, `service/`, `model/`, `utils/`;
  - new tests.
- **Entry:**
  - `dto/request/EngineOrderDTO`, `EngineVehicleDTO`, `EngineCallbackRequest` (StopData field), the engine-evaluate response DTO (stop field);
  - `dto/response/DriverStopDTO` (+ new DTOs);
  - `entity/Route`, `RouteStop`, `OptimizationJob`, `UnassignedOrder` (new columns only);
  - `service/OptimizationJobService` (N4), `OptimizationCallbackService` (N6a), `ParetoService` (N6b), `RouteCustomizationService` (N6c), `RouteSendService` (N7, N8 driver DTO), `EnginePlanMaterializer` (geometry copy);
  - `service/lez/*` (new files; `ZoneService` refactor of rule loading only);
  - `integration/external_api/EngineApiClient` + `EngineApiClientImpl` (`legs`);
  - new controllers or methods on `SolutionController` / a new `RouteGeometryController` for N8;
  - `util/AppConstant`, `util/ErrorCode` (constants only);
  - the new migration;
  - new tests.
- Work-item file: `work-items/LEZ-001/implementation.md`, adding a `## P1b` section. Do not rewrite §P1a.

## Forbidden
- Editing, renaming, disabling, skipping or excluding any existing test, fixture or helper. The only exclusion allowed is D6's two Engine tests, **on the command line only**.
- `SecurityConfig`, `pom.xml`, `application*.properties`, `.github/`, `AGENTS.md`, `docs/`, any client file (P1c).
- Building a second GraphHopper instance or loading a graph in tests.
- Sending polygons or rule data to the engine (design §16.2-1, -8).
- Logging coordinates linked to orders, customer data, tokens or secrets.
- Committing, pushing, rewriting history. Running migrations against the database (the user does it).

## Step 2 — implement
Implement in this order: E1–E4 → N1–N2 → N3 → N5 → N4 → N6 → N7 → N8.

After Part E, run the Engine tests (Step 1 command) to green, apart from pre-existing failures recorded in the baseline.

## Step 3 — verification
- Engine: run the Step 1 command. Same pre-existing failures only. Report the new test names.
- Entry: run `mvnw.cmd -B clean test -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` (until the user applies N1). Report the counts. State that the two `@SpringBootTest` classes need the N1 migration (P1a lesson).

## Step 4 — record and checkpoint (no transition)
Add `## P1b` to `implementation.md` with the same sections as §P1a:
- Scope
- Baseline (both modules)
- Changes Made
- Commands Executed
- Verification Results
- Modified Tests ("none")
- Security-Relevant Changes
- Deviations From Design (must include D6)
- Remaining Uncertainty (must include the actual GraphHopper locale used and the size of a callback payload with geometry)
- Lesson Candidates
- Handoff to Reviewer

Then create `work-items/LEZ-001/checkpoint-p1b.json` (current run revision; `next_action`: "Claude reviews P1b; user applies 2026-10-28 migration") and run:
```
node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1b.json
```
**Do not** transition to `verify`. Tell the user you are done.
