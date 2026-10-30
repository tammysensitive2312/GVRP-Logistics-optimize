# SEC-ROLE-001 — role-based access to the Entry API

**Problem.** Every authenticated user could call every business endpoint. A driver account could
reach planner endpoints (SEC-006), and the driver endpoints themselves were not restricted to drivers.

**Decision.**

- Two role rules in `SecurityConfig`, evaluated in this order:
  `/api/v1/driver/**` requires `ROLE_DRIVER`; every other `/api/v1/**` path requires `ROLE_PLANNER`.
- Public paths stay public: `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`,
  `/actuator/health`, and `/api/v1/solutions/callbacks/**` (the Engine API cannot present a user
  token; the callback path is protected by the engine API-key filter instead).
- Anything not matched by a rule still requires authentication.
- Denied requests get the configured access-denied handler; the response shape matches the common
  error format.
- The rule is path-based, not annotation-based: adding a driver-facing endpoint under a different
  prefix would silently place it under the planner rule.

**Date.** Accepted 2026-10-16 23:48 (+07:00); state `done` rev 7; closes SEC-006 once merged.

**Source.** `work-items/SEC-ROLE-001/` (deleted): `design.md`, `verification.md`, `review.md`,
`closure.md`. Code: `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`,
`security/RoleAccessDeniedHandler` — see `02-system-design/04-security-and-tenancy.md`.

**Still valid in this documentation.** `01-requirements/01-actors-and-roles.md` (permission matrix),
`02-system-design/04-security-and-tenancy.md`, `BR-006`, `BR-007`, `TC-D01-006`…`TC-D01-009`.

**Open follow-ups recorded at closure.** Replace `@MockBean` with `@MockitoBean` in `RoleAccessTest`
(deprecation only). Optionally pin `/api/v1/solutions/callbacks/**` in a test as explicitly outside
the planner rule.
