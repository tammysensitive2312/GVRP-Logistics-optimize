# Traceability — SEC-TENANT-001

Feature ID: SEC-TENANT-001
Inspected source revision: working tree 2026-10-16 (see context.md)
Request source: user message 2026-10-16; docs/security/SECURITY_CHECKLIST.md SEC-002, SEC-003

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| TI-001 | Solution reads refuse other branch (request.md) | Solutions | `SolutionService.getSolutionDetail`, `revisionChain`; `RouteCustomizationService.latestRevisionId`; `SolutionController` | `SolutionServiceTest` | Planned | NOT_VERIFIED |
| TI-002 | Evaluate / save revision refuse other branch, no persistence | Solutions | `RouteCustomizationService.validateAndPrepare` | `RouteCustomizationServiceTest` new methods | Planned | NOT_VERIFIED |
| TI-003 | Vehicle create refuses foreign fleet/depot/type | Vehicles and fleets | `VehicleService.createVehicle`, `VehicleController` | `VehicleServiceTest` | Planned | NOT_VERIFIED |
| TI-004 | Vehicle update refuses foreign vehicle/depot/type | Vehicles and fleets | `VehicleService.updateVehicle`, `VehicleRepository.findByIdAndFleetBranchId` | `VehicleServiceTest` | Planned | NOT_VERIFIED |
| TI-005 | Fleet create refuses foreign depot/type | Vehicles and fleets | `FleetService.createFleet`, `DepotRepository.findByIdAndBranchId` | `FleetServiceTest` | Planned | NOT_VERIFIED |
| TI-006 | Route send history refuses other branch | Route send history | `RouteSendService.routeSends` | `RouteSendServiceTest` new methods | Planned | NOT_VERIFIED |
| TI-007 | Branch from principal; null fails closed | Principle | controllers above | null-branch cases in `SolutionServiceTest` | Planned | NOT_VERIFIED |
| TI-008 | Cross-branch tests; existing assertions kept | Tests | — | full `mvnw.cmd test` | Planned | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Foreign IDs answer like missing IDs (`RESOURCE_NOT_FOUND`) | AGENTS.md section 15 ("prefer a not-found response") | TI-001..TI-006 | none |
| Role checks out of scope | Agent scoping; recorded as SEC-006 | — | separate run |
| Existing tests: call-site signature updates only | AGENTS.md section 14; listed in context.md | TI-008 | reviewer to confirm no assertion change |

## Synchronization review

- Requirements versus implementation: to be completed at verify.
- Design versus implementation: to be completed at verify.
- Test and documentation coverage: to be completed at verify.
- Stale links or changed assumptions: none yet.
- Remaining limitations: SEC-001 rotation; SEC-006..SEC-008 open.
