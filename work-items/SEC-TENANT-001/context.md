# Context — SEC-TENANT-001

Inspected revision: working tree on 2026-10-16 after the user's commit of the
agent-rules/CI change (git revision not readable from this session; record it
when the run is initialized). Method: static inspection of every controller in
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller`, the services
they call, the repositories and `JwtAuthenticationFilter`. Nothing was executed.

## How the branch reaches the code

- `JwtAuthenticationFilter` validates the JWT and copies the `branchId`,
  `userId` and `role` claims into request attributes.
- `CurrentUserUtil.getCurrentBranchId()` reads that attribute; it returns
  `null` when there is no request context (for example in unit tests).
- The branch is therefore only as trustworthy as the JWT signing key. That key
  is in git history (SEC-001): anyone holding it can mint a token for any
  branch. Rotation is required independently of this run.
- Error convention: "not found" is `DataInvalidException` with
  `ErrorCode.RESOURCE_NOT_FOUND` (`0040402`), rendered by
  `GlobalExceptionHandler` as HTTP 400.

## Audit result per endpoint

| Endpoint | Data access | Branch check | Verdict |
|---|---|---|---|
| `POST /auth/login` | user by username + branch | yes | OK |
| `POST /auth/register` | none (stub) | n/a | SEC-008: returns success without creating a user |
| `POST/GET /depots` | branch from principal | yes | OK |
| `POST /fleets` | depots, vehicle types via `findById` | no | **TI-005**: can attach another branch's depots/types; their data is echoed in the response |
| `GET /fleets`, `GET /fleets/{id}` | `findAllByBranchId`, `findByIdAndBranchId` | yes | OK |
| `POST /jobs/plan` | orders, vehicles validated against branch; depots/types derived from vehicles | yes | OK |
| `GET /jobs`, `GET /jobs/{id}`, `GET /jobs/{id}/progress`, `PUT /jobs` | load then compare branch | yes | OK |
| `GET/POST /orders`, `GET/PUT /orders/{id}`, `PATCH /orders/status`, import | branch-scoped queries or compare | yes | OK |
| `GET /solutions/{id}` | `SolutionRepository.findWithDetailsById` | **no** | **TI-001** (SEC-002) |
| `GET /solutions/{id}/latest-revision` | `findById` + children | **no** | **TI-001** |
| `GET /solutions/{id}/revision-chain` | `findById` + parents/children | **no** | **TI-001** |
| `POST /solutions/{id}/evaluate` | `findWithDetailsById`; orders compared to the base solution's branch, not the caller's | **no** | **TI-002**: reads another branch's plan through the preview |
| `POST /solutions/{id}/revisions` | same as evaluate, then persists | **no** | **TI-002**: writes a revision into another branch |
| `POST /routes/{id}/send` | solution and driver compared with caller branch | yes | OK |
| `GET /routes/{id}/sends` | `routeRepository.findById` only | **no** | **TI-006**: send history (driver, sender) of another branch |
| `GET /drivers`, `GET /driver/routes`, `GET /driver/routes/{id}` | branch / own driver id | yes | OK |
| `GET /vehicles` | `findByFleetBranchId` | yes | OK |
| `POST /vehicles?fleetId=` | fleet, depots, type via `findById` | **no** | **TI-003**: adds a vehicle to another branch's fleet |
| `PUT /vehicles/{id}` | vehicle, depots, type via `findById` | **no** | **TI-004**: modifies another branch's vehicle |
| `POST/GET /vehicle-types`, `PUT /vehicle-types/{id}` | branch-scoped | yes | OK |
| `/solutions/callbacks/**` | engine API key (`ApiKeyAuthenticationFilter`) | n/a | OK for tenancy; key is in git history (SEC-001) |

## Other observations (out of scope, recorded)

- SEC-006: no role checks outside `RouteSendService`. A DRIVER token can call
  planner endpoints of its own branch (orders with customer data, vehicles,
  solutions). This conflicts with the intent of DRIVER-DELIVERY-001 DD-022.
- SEC-007: `VehicleService.createVehicle` checks license plates globally.
- `work-items/DRIVER-MAP-004/request.md` contains local test-account
  credentials; they are now tracked in git.

## Existing tests affected

- `RouteCustomizationServiceTest` and `SolutionCustomizationControllerTest`
  call methods whose signatures gain a `branchId` parameter. Only the call
  sites and stubs change; no assertion or expected value changes.
- `RouteSendServiceTest`: unchanged; new tests are added.
- No tests exist for `VehicleService`, `FleetService` or `SolutionService`.
