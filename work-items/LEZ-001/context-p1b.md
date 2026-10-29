# Context addendum — LEZ-001, increment P1b

Inspected on 2026-10-27, branch `feature/lez-001` (P1a applied, uncommitted). Static reading through the file bridge; nothing executed by Claude.
This file adds to `context.md` and corrects it. `context.md` is recorded evidence of the `discover` transition and is therefore left unchanged.

## 1. Correction to `context.md` §1.2

- **Wrong:** "No custom `HardActivityConstraint` / `HardRouteConstraint` exists today."
- **Correct:** `OptimizationService` already defines and registers `NoPrunedEdgeConstraint` (HardActivity, CRITICAL), `MaxDistanceConstraint` (HardActivity, HIGH) and `ClusterRouteConstraint` (HardRoute) in `createAlgorithm` (`OptimizationService.java` ≈ lines 683–760, 954–1102).
- The earlier grep missed them. Not a design impact: P2's time-window constraint will follow the same pattern.

## 2. Facts for P1b (with sources)

| Fact | Source |
|---|---|
| Every jsprit vehicle starts at **08:00** (`earliestStart = 8*3600`) and must arrive by 08:00 + `maxDuration` (≤ 24 h). Order time windows are times of day only (`HH:mm:ss`), and the engine receives **no date**. A job is therefore planned as one day. | `OptimizationService.buildJspritVehicle`, `buildJspritService` |
| `OptimizationJobService.submitJob` does **not** check that orders share one `deliveryDate`. A job mixing dates puts orders of different days into the same-day routes, which is a business defect (user decision D4). | `OptimizationJobService.java` `submitJob`, `validateAndLoadOrders` |
| Vehicle skills come from the **vehicle type** (`addVehicleSkills(vehicleBuilder, vt.getSkills())`); order skills from `Order.requiredSkills` (`Set<String>`, upper-cased). jsprit vehicles are individual (`vehicle-<id>`). | `OptimizationService` lines 602, 654, 659–679 |
| Entry `EngineOrderDTO.requiredSkills` is `Set<VehicleSkill>` (enum). Arbitrary `LEZ_<key>` strings cannot travel in it. `EngineVehicleDTO` has no skills field. | `dto/request/EngineOrderDTO.java`, `EngineVehicleDTO.java` |
| Engine `Stop` carries `latitude`, `longitude`, `arrivalTime`, `departureTime`, `distanceToNext`, `timeToNext`. `RouteDetail.stops` is used by the optimisation result and by evaluate. | `model/Stop.java`, `model/RouteDetail.java`, `RouteEvaluateResponse.java` |
| The Pareto branch extracts routes for every FRONTIER candidate. Entry stores them in `pareto_candidates.plan_json` as `EngineCallbackRequest.RouteData`, and `EnginePlanMaterializer` builds `Route`/`RouteStop` for both the callback and the Pareto apply path. A new field on `StopData` therefore flows through both. | `OptimizationService.optimizeMultiObjective`, `EnginePlanMaterializer`, `EngineCallbackRequest` |
| `GraphHoperDistanceProvider.fetch` already calls `graphHopper.route(...)` (profile `car`, `Locale.ENGLISH`) and keeps only time and distance. `getBest()` also exposes points and instructions. | `distance_matrix/Impl/GraphHoperDistanceProvider.java:128-146` |
| `RouteCustomizationService.saveRevision` **already refuses (422)** any revision with hard violations; `evaluate` (preview) never persists. Revisions are built from the engine evaluate response in `persistRevision`. | `RouteCustomizationService.java` |
| `RouteSendService.sendRoute` takes a pessimistic lock on the route (`findWithLockById`), checks solution status, branch, order stops and driver, then supersedes the CURRENT sends. There is no feasibility or compliance step. | `RouteSendService.java` |
| `EngineApiClient` has `submitOptimizationAsync`, `getProgress`, `requestCancel`, `evaluateRoutes`. `evaluateRoutes` throws `EngineUnavailableException` (→ 502) or `DataInvalidException`. | `integration/external_api/EngineApiClient.java` |
| `DriverStopDTO` has no geometry or zone fields. The driver and planner clients draw OSRM paths (`context.md` §1.4). | `dto/response/DriverStopDTO.java` |
| CI excludes Engine `@SpringBootTest` and GraphHopper-graph tests (`GvrpEngineApiApplicationTests`, `KMeansClustererTest`, `VehicleClusterAssignerTest`, `BridgeCrossingWorstCaseTest`, `CircuityFactorMeasurementTest`, `GeoUtilsTest`, `JspritConvergenceBenchmarkTest`, `JspritPerformanceBenchmarkTest`). Locally they run (the graph exists on the user's machine). | `.github/workflows/ci.yml` |
| `MeasuredLawsTest` pins carbon price 100000 against the constant 150000; 4 tests failed when last recorded (KI-030). This is a possible **pre-existing failure** to record, not to fix. | `docs/research/PROJECT_STATE.md` STATE-CARBON-001; `KNOWN_ISSUES.md` KI-030 |

## 3. Lessons that apply
L-2026-001 (no MySQL or GraphHopper in CI → the path provider must be mockable), L-2026-003, L-2026-004, L-2026-007, L-2026-008 (named exclusions, approved: D6), L-2026-009, L-2026-012, L-2026-014. Also the P1a lesson candidates (in `implementation.md`): local `@SpringBootTest` needs the migration; "not found" is 400 elsewhere; standalone MockMvc principal attributes.
