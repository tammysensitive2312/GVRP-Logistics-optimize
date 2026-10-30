# 01 — Actors and roles

## Actors

| Actor | Type | Description | Entry point |
|---|---|---|---|
| Planner | Human, role `PLANNER` | Employee of a branch who prepares master data, imports orders, plans routes, reviews solutions, checks LEZ compliance and sends routes to drivers | `/login` → `/main`, `/jobs`, `/admin/*`, `/setup/*` |
| Driver | Human, role `DRIVER` | Employee of a branch who receives a sent route and reads it on a phone | `/login` → `/driver` |
| Customer | Human, role `CUSTOMER` | Role exists in the model. No customer screen exists in Client v2 and every business endpoint refuses the role | `/login` only |
| Anonymous visitor | Human, no token | Can log in and open the solution deep link, which redirects to login | `/login`, `/?solution=<id>` |
| Optimisation Engine | System | Computes plans and calls the Entry API back | `POST /api/v1/solutions/callbacks/*` with the engine API key |
| Geocoding provider | System | Resolves an address to coordinates when an order has none | called by the Entry API |
| OSRM routing/tiles | System | Leg geometry and ETA for the planner map | called by the client |
| SMTP server | System | Delivers success, failure and cancellation e-mails | called by the Entry API |
| GraphHopper graph | Data | Road network used by the Engine API | read by the Engine API |

## Roles as implemented

`UserRole` has exactly three values: `PLANNER`, `CUSTOMER`, `DRIVER` (`entity/enums/UserRole.java`).
A user row carries `branch_id` (not null), `username`, `password`, `email`, `full_name`,
`role` and `enabled`.

Authentication is stateless JWT:

1. `POST /api/v1/auth/login` takes `branchName`, `username`, `password`.
2. The branch is resolved by name; an unknown branch answers `0040402` (resource not found).
3. The credentials are checked against the user of that branch only
   (`findByUsernameAndBranchId`), so the same username may exist in two branches.
4. The token carries `username`, `userId`, `branchId`, `role`.
5. Every later request re-loads the user and accepts the token only while the account is enabled,
   not locked, and the token's user id still matches (`BR-008`).

Because the branch is part of the login, a user cannot silently switch branch by changing a request
body: the branch used by the server always comes from the token (`BR-001`).

## Permission matrix

Authorisation is path-based, in this order (`SecurityConfig.filterChain`):

```text
/api/v1/auth/**                  permitAll
/swagger-ui/**, /v3/api-docs/**  permitAll
/actuator/health                 permitAll
/api/v1/solutions/callbacks/**   permitAll  (engine API key checked by a filter)
/api/v1/driver/**                hasRole(DRIVER)
/api/v1/**                       hasRole(PLANNER)
any other request                authenticated
```

| Function (API group) | Anonymous | `CUSTOMER` | `DRIVER` | `PLANNER` |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | allow | allow | allow | allow |
| `POST /api/v1/auth/register` | allow (stub, see below) | allow | allow | allow |
| `/api/v1/driver/routes`, `/api/v1/driver/routes/{sendId}` | deny 401 | deny 403 | **allow** | deny 403 |
| `/api/v1/drivers` (planner driver list) | deny 401 | deny 403 | deny 403 | **allow** |
| `/api/v1/orders/**`, `/api/v1/jobs/**`, `/api/v1/solutions/**` (except callbacks), `/api/v1/routes/**`, `/api/v1/pareto` paths | deny 401 | deny 403 | deny 403 | **allow** |
| `/api/v1/depots`, `/api/v1/vehicle-types`, `/api/v1/vehicles`, `/api/v1/vehicles/{id}/permits`, `/api/v1/fleets` | deny 401 | deny 403 | deny 403 | **allow** |
| `/api/v1/zones/**`, `/api/v1/zone-templates/**`, `/api/v1/lez/order-zones` | deny 401 | deny 403 | deny 403 | **allow** |
| `POST /api/v1/solutions/callbacks/**` | allow with engine API key; otherwise rejected by the key filter | | | |
| `/actuator/health`, Swagger UI | allow | allow | allow | allow |

The matrix is asserted by existing tests: `RoleAccessTest` (11 cases), `LezRoleAccessTest` (10),
`LezComplianceRoleAccessTest` (9), `LezMembershipRoleAccessTest` (4).

### Consequences of the path rule

- A driver-facing endpoint must live under `/api/v1/driver/`. The planner driver list deliberately
  lives at `/api/v1/drivers`.
- Every `/api/v1/**` path not under `/api/v1/driver/**` is planner-only, including any future
  customer-facing path. A `CUSTOMER` role therefore has no server-side capability today.
- The engine callback path is public at the HTTP security layer and protected by an API-key filter;
  the engine cannot present a user token.

## Client-side guards

The client enforces routing, not authority: a user who bypasses the guard still meets the server rule.

| Guard | Applies to | Behaviour |
|---|---|---|
| `AuthGuard` | `/main`, `/jobs`, `/admin`, `/setup` | no token → `/login` |
| `setupGuard` | `/main`, `/jobs`, `/admin` | if the branch has no depot → `/setup/depot`; no vehicle type → `/setup/vehicle-types`; no vehicle → `/setup/fleet`. On an API error it lets navigation through instead of looping |
| `DriverGuard` | `/driver` | requires the driver role |

## Registration is not implemented

`POST /api/v1/auth/register` validates that the username and e-mail are unused, then returns
`"User registered successfully"` without writing a user row. The body of the method is a comment
(`// Create new user  // ... implementation`). No other endpoint creates users.

Consequence for this documentation: `UC-D01-02` describes the intended behaviour and is marked
`NOT_VERIFIED` in the test design; user provisioning is therefore an out-of-band database operation
today. `ASSUMPTION:` operators insert users directly, since no migration seeds them.

## Sources

`controller/AuthController.java`, `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`,
`security/UserPrincipal.java`, `entity/User.java`, `entity/enums/UserRole.java`,
`GVRP_Entry_client_v2/src/app/app.routes.ts`, `core/guards/*.ts`,
`src/test/java/.../security/*RoleAccess*Test.java`.
