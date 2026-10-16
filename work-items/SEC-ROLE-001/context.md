# Context — SEC-ROLE-001

Inspected 2026-10-16 (working tree on branch `fix/sec-tenant-001`). Static
inspection only.

## Current behavior

- `config/SecurityConfig.filterChain`: `/api/v1/auth/**`, Swagger,
  `/actuator/health`, `/api/v1/solutions/callbacks/**` are `permitAll`; every
  other path only requires `.authenticated()`. No role rule exists.
- `@EnableMethodSecurity` is on, but no `@PreAuthorize` is used anywhere.
- Roles: `entity/enums/UserRole` = `PLANNER`, `CUSTOMER`, `DRIVER`.
- Authorities: `JwtAuthenticationFilter` loads the user with
  `CustomUserDetailsService.loadUserByUsername(username)`; `UserPrincipal`
  grants `ROLE_<role>` from the database row. `users.username` is globally
  unique (`User.username`, `unique = true`), so the authority reflects the
  user's current role in the DB, not only the token claim.
- `JwtAuthenticationFilter` also stores the token's `role` claim as a request
  attribute; only `RouteSendService.requireRole` / `requireDriver` read it.
- Callbacks: `ApiKeyAuthenticationFilter` authenticates engine callbacks as
  `ROLE_ENGINE_SERVICE`.
- 401 handling: `JwtAuthenticationEntryPoint`. No custom `AccessDeniedHandler`
  exists, so a Spring Security 403 today would have Spring's default body, not
  the project's error envelope.
- Error envelope for 403: `GlobalExceptionHandler.handleForbidden` →
  `ErrorResponse(List.of(ErrorDetail(code = "0040301", message)))`.

## Endpoints by controller

| Prefix | Controller | Intended role |
|---|---|---|
| `/api/v1/auth/**` | `AuthController` | public |
| `/api/v1/depots/**` | `DepotController` | PLANNER |
| `/api/v1/fleets/**` | `FleetController` | PLANNER |
| `/api/v1/vehicles/**` | `VehicleController` | PLANNER |
| `/api/v1/vehicle-types/**` | `VehicleTypeController` | PLANNER |
| `/api/v1/orders/**` | `OrderController` | PLANNER |
| `/api/v1/jobs/**` | `OptimizationJobController` | PLANNER |
| `/api/v1/solutions/**` (except callbacks) | `SolutionController` | PLANNER |
| `/api/v1/solutions/callbacks/**` | `OptimizationCallbackController` | engine API key |
| `/api/v1/routes/**` | `RouteSendController` | PLANNER |
| `/api/v1/drivers` | `DriverController.listDrivers` | PLANNER |
| `/api/v1/driver/**` | `DriverController.myRoutes`, `routeDetail` | DRIVER |

Note the near-identical prefixes `/api/v1/drivers` (planner directory) and
`/api/v1/driver/**` (driver self-service).

## Frontend usage

`GVRP_Entry_client_v2/src/app/features/driver/driver-view/driver-view.component.ts`
calls only `ApiService.myDriverRoutes()` (`/driver/routes`) and
`driverRouteDetail()` (`/driver/routes/{id}`), plus OSRM (external) and
logout. Restricting DRIVER to `/api/v1/driver/**` does not break the driver UI
(static inspection).

## Tests today

No test exercises `SecurityConfig`. Controller tests use standalone MockMvc
without security. `@SpringBootTest` classes need MySQL and are excluded in CI.

## Observations out of scope

- SEC-009 (new): `JwtAuthenticationFilter` does not check
  `UserDetails.isEnabled()`; a disabled user keeps API access until the token
  expires (24 h).
