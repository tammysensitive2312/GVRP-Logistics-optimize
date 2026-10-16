# Design — SEC-ROLE-001

## Approach: URL rules in `SecurityConfig`, default deny for non-planners

Central URL rules are preferred over per-method `@PreAuthorize` because new
planner endpoints are then protected by default, and the rule set is one place
to review.

Rule order in `authorizeHttpRequests` (first match wins):

```text
/api/v1/auth/**                      permitAll          (unchanged)
/swagger-ui/**, /v3/api-docs/**      permitAll          (unchanged)
/actuator/health                     permitAll          (unchanged)
/api/v1/solutions/callbacks/**       permitAll          (unchanged; API-key filter)
/api/v1/driver/**                    hasRole("DRIVER")
/api/v1/**                           hasRole("PLANNER")
anyRequest                           authenticated      (unchanged)
```

- `/api/v1/driver/**` must precede `/api/v1/**`.
- `/api/v1/drivers` (planner directory) is matched by `/api/v1/**`, not by
  `/api/v1/driver/**`. A test must pin this.
- The old per-prefix `.authenticated()` lines become redundant and are removed.
- Authorities come from `UserPrincipal` (DB role), which is the current source.

## 403 response (RA-004)

Add an `AccessDeniedHandler` bean in the `security` package that writes HTTP
403, `Content-Type: application/json`, body:

```json
{"errors":[{"code":"0040301","message":"Access forbidden."}]}
```

Use `ErrorCode.FORBIDDEN` and the project's `ErrorResponse`/`ErrorDetail`
serialized with the application's `ObjectMapper` (so naming strategy matches the
rest of the API). Register it with
`.exceptionHandling(e -> e.authenticationEntryPoint(...).accessDeniedHandler(...))`.

## Tests (RA-006)

Requirement: no database, so the class runs in CI without being excluded.

Suggested shape (the engineer may choose another as long as the requirement
holds): a web-slice test such as `@WebMvcTest` with `@Import(SecurityConfig.class)`
plus mocks for the security beans' dependencies, and `@WithMockUser(roles = …)`
or `SecurityMockMvcRequestPostProcessors.user(...)` for each role. Mock the
controllers' services; only the HTTP status and error envelope are asserted.

Minimum cases:

| Role | Request | Expected |
|---|---|---|
| DRIVER | `GET /api/v1/driver/routes` | not 401/403 |
| DRIVER | `GET /api/v1/orders` | 403 with code `0040301` |
| DRIVER | `GET /api/v1/drivers` | 403 |
| DRIVER | `POST /api/v1/routes/1/send` | 403 |
| PLANNER | `GET /api/v1/orders` | not 401/403 |
| PLANNER | `GET /api/v1/drivers` | not 401/403 |
| PLANNER | `GET /api/v1/driver/routes` | 403 |
| CUSTOMER | `GET /api/v1/orders` | 403 |
| CUSTOMER | `GET /api/v1/driver/routes` | 403 |
| anonymous | `GET /api/v1/orders` | 401 |
| anonymous | `POST /api/v1/auth/login` | not 401/403 |

If a database-free slice test of `SecurityConfig` proves impossible, stop and
report why instead of adding a `@SpringBootTest` that needs MySQL.

## Not changed

Controllers, services, `RouteSendService` role checks, JWT format, frontend.
