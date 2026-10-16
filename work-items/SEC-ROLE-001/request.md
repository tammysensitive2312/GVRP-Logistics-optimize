# Request — SEC-ROLE-001 (role-based access to Entry API)

Source: user messages 2026-10-16 22:12 (+07:00): proceed to the next step
(SEC-006) with implementation delegated to the OpenCode engineer agent; role
policy chosen in the same session:

- DRIVER: "Chỉ API tài xế" — only driver endpoints.
- CUSTOMER: "Chưa dùng, chặn hết" — unused, blocked from all business endpoints.

Origin: `docs/security/SECURITY_CHECKLIST.md` SEC-006. Related accepted rule:
DRIVER-DELIVERY-001 DD-022 ("driver APIs never expose other drivers' routes,
planner screens, or customer lists beyond assigned stops").

Delegation trial: this is the first work item where Claude writes the
specification and reviews, and the OpenCode engineer implements (level 1:
the user relays between the two).

## Requirements

- RA-001: a DRIVER can call `/api/v1/driver/**` and `/api/v1/auth/**` only.
  Every other `/api/v1/**` endpoint answers HTTP 403.
- RA-002: a PLANNER keeps access to every business endpoint it has today
  (depots, fleets, vehicles, vehicle-types, orders, jobs, solutions, routes,
  drivers directory).
- RA-003: a CUSTOMER can log in but every business endpoint under `/api/v1/**`
  (including `/api/v1/driver/**`) answers HTTP 403.
- RA-004: the 403 body uses the existing error envelope with code `0040301`
  (`ErrorCode.FORBIDDEN`), same shape as `GlobalExceptionHandler.handleForbidden`.
- RA-005: unchanged: `/api/v1/auth/**`, `/actuator/health`, Swagger paths and
  `/api/v1/solutions/callbacks/**` (engine API key) keep their current access.
  Unauthenticated calls keep answering 401.
- RA-006: tests prove RA-001..RA-005 without a database, so they run in CI.
- RA-007: existing service-level role checks in `RouteSendService` stay
  (defense in depth).

## Exclusions

- Changing roles of existing users, a role-management UI, new roles.
- SEC-009 (disabled users keep access until token expiry), SEC-001, SEC-004,
  SEC-007, SEC-008.
- Frontend changes. The driver UI already calls only `/api/v1/driver/**`.

## Acceptance expectations

- `mvnw.cmd clean test` green, new tests included and not excluded from CI.
- Manual check: DRIVER token gets 403 on `GET /api/v1/orders` and 200 on
  `GET /api/v1/driver/routes`; PLANNER flows in the web client unchanged.

## Open check before implementation

Run on the local DB and report the result (no personal data needed):
`SELECT role, COUNT(*) FROM users GROUP BY role;`
If CUSTOMER users exist and are used, they will lose access (RA-003).
