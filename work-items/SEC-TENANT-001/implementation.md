# Implementation — SEC-TENANT-001

Edited remotely through file tools on 2026-10-16. Nothing was compiled or run
by the agent: the agent session has no shell on the user's machine and Maven
Central is not reachable from the agent environment. A Java syntax parse of
the changed files passed (text-block files skipped by the parser). Compilation
and tests are done by `run-verify.ps1`.

## Changes by requirement

| Req | File | Change |
|---|---|---|
| TI-001, TI-007 | `service/SolutionService.java` | `getSolutionDetail(id, branchId)`, `revisionChain(id, branchId)`; new package-private `belongsTo(Solution, Long)` (null branch fails closed) and `solutionNotFound()` |
| TI-001 | `service/RouteCustomizationService.java` | `latestRevisionId(id, branchId)` filters by `belongsTo` |
| TI-002 | `service/RouteCustomizationService.java` | `evaluate(id, branchId, request)`, `saveRevision(id, branchId, request)`; `validateAndPrepare` filters the base solution by `belongsTo` before any validation, engine call or save |
| TI-001, TI-002 | `controller/SolutionController.java` | passes `CurrentUserUtil.getCurrentBranchId()` to all five endpoints |
| TI-003, TI-004 | `repository/VehicleRepository.java` | new `findByIdAndFleetBranchId(Long, Long)` |
| TI-003..TI-005 | `repository/DepotRepository.java` | new `findByIdAndBranchId(Long, Long)` |
| TI-003, TI-004 | `service/VehicleService.java` | `createVehicle(input, fleetId, branchId)`, `updateVehicle(id, input, branchId)`; fleet, vehicle, depots and type loaded only through branch-scoped lookups |
| TI-003, TI-004 | `controller/VehicleController.java` | passes the principal's branch |
| TI-005 | `service/FleetService.java` | depots and types loaded through branch-scoped lookups |
| TI-006 | `service/RouteSendService.java` | `routeSends` filters the route by its solution's branch (`belongsTo`) |

All paths are under `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

## Tests

New test classes (TI-008):

- `service/SolutionServiceTest` (6 tests): other branch, null branch, same error as unknown ID, own branch; revision chain other/own branch.
- `service/VehicleServiceTest` (6 tests): create with foreign fleet, depot, type; create own branch; update foreign vehicle, foreign depot.
- `service/FleetServiceTest` (2 tests): foreign type, foreign depot.

New test methods in existing classes:

- `RouteCustomizationServiceTest`: `evaluate_otherBranch_notFoundWithoutEngineCall`, `evaluate_missingBranch_notFound`, `saveRevision_otherBranch_notFoundWithoutSaving`, `latestRevisionId_otherBranch_notFound`, `latestRevisionId_ownBranch_returnsBaseWhenUnedited`.
- `RouteSendServiceTest`: `routeSends_otherBranch_notFound`, `routeSends_ownBranch_returnsHistory`.

## Modified existing tests (AGENTS.md section 14)

| File | Change | Reason |
|---|---|---|
| `RouteCustomizationServiceTest` | 8 call sites: `evaluate(9L, …)` / `saveRevision(9L, …)` become `evaluate(9L, 1L, …)` / `saveRevision(9L, 1L, …)`; `1L` is the fixture's own branch | signature gained `branchId` |
| `SolutionCustomizationControllerTest` | 6 stubs: `evaluate(eq(9L), any())` / `saveRevision(eq(9L), any())` become `(eq(9L), any(), any())` | signature gained `branchId` |

No assertion, expected value, fixture or test exclusion was changed.

## Security-relevant changes

- New repository queries: `VehicleRepository.findByIdAndFleetBranchId`, `DepotRepository.findByIdAndBranchId`.
- No new endpoint, no `permitAll` change, no new dependency, no configuration change.

## Limitations

- The new service tests run on mocks. They prove the services use only
  branch-scoped lookups and refuse when those return empty. They do not
  execute the Spring Data derived queries against a database.
- Role-based authorization (SEC-006) is unchanged.
