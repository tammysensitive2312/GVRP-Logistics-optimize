# Design — SEC-TENANT-001

## Principle

The controller reads the branch from the principal
(`CurrentUserUtil.getCurrentBranchId()`) and passes it to the service, matching
the existing pattern of `OrderController`, `FleetController` and
`OptimizationJobController`. `RouteSendService` keeps its existing internal
pattern (`currentBranchId()`).

A resource of another branch, and a missing branch (`null`), are treated
exactly like a missing resource: `DataInvalidException` with
`RESOURCE_NOT_FOUND`. The response does not reveal that the ID exists.

## Changes

### Solutions (TI-001, TI-002)

- Each service compares `solution.getBranch().getId()` with the caller branch
  using `Objects.equals`, so a `null` branch fails closed. The existing
  repository lookups are kept so existing test stubs stay valid.
- `SolutionService.getSolutionDetail(id, branchId)` and
  `revisionChain(id, branchId)`.
- `RouteCustomizationService.evaluate(id, branchId, request)`,
  `saveRevision(id, branchId, request)`, `latestRevisionId(id, branchId)`.
  The check runs in `validateAndPrepare` before any engine call or persistence.
- `SolutionController` passes the branch to all five endpoints.

### Vehicles and fleets (TI-003, TI-004, TI-005)

- `DepotRepository.findByIdAndBranchId(Long, Long)` (new derived query).
- `VehicleRepository.findByIdAndFleetBranchId(Long, Long)` (new derived query
  over `vehicle.fleet.branch.id`).
- `VehicleService.createVehicle(input, fleetId, branchId)`: fleet via
  `FleetRepository.findByIdAndBranchId`, depots via
  `DepotRepository.findByIdAndBranchId`, type via
  `VehicleTypeRepository.findByIdAndBranchId`.
- `VehicleService.updateVehicle(id, input, branchId)`: vehicle via
  `findByIdAndFleetBranchId`; depots and type as above.
- `FleetService.createFleet`: depots and types via the branch-scoped lookups.
- `VehicleController` passes the branch.

### Route send history (TI-006)

- `RouteSendService.routeSends(routeId)`: after loading the route, compare
  `route.getSolution().getBranch().getId()` with `currentBranchId()`;
  mismatch gives `RESOURCE_NOT_FOUND`.

## Failure cases

- Unknown ID and foreign ID: same error code and message.
- No branch in context: same error (fail closed).
- Solution revisions inherit the base branch (`persistRevision`), so checking
  the requested solution is sufficient for the revision chain.

## Tests (TI-008)

New tests, each proving a cross-branch refusal with a branch-2 caller on
branch-1 data:

- `SolutionServiceTest`: detail and revision chain refuse another branch; null
  branch refused; own branch succeeds.
- `RouteCustomizationServiceTest` (new test methods): evaluate and saveRevision
  refuse another branch, with no engine call and no save.
- `VehicleServiceTest`: create refuses a foreign fleet, a foreign depot and a
  foreign type; update refuses a foreign vehicle.
- `FleetServiceTest`: create refuses a foreign depot and a foreign type.
- `RouteSendServiceTest` (new test methods): route history refuses another
  branch; own branch returns history.

Existing test edits are limited to adding the branch argument at call sites
and stubs in `RouteCustomizationServiceTest` and
`SolutionCustomizationControllerTest`.

## Not changed

Endpoint paths, request/response bodies, HTTP status mapping, the frontend.
