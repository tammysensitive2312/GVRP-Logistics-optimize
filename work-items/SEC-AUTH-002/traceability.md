# Traceability — SEC-AUTH-002

Inspected source revision: `0b6b759` (2026-11-10). Request: `request.md`. Design: `design.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| AU-R1 | Token refused when branch claim differs from reloaded user's branch | D1.1 | `security/jwt/JwtAuthenticationFilter.isUsable` | AU-T1, T2, T5 | NOT_VERIFIED |
| AU-R2 | Tenant scope taken from reloaded user | D1 | `JwtAuthenticationFilter.doFilterInternal` | AU-T4, T5, T6 | NOT_VERIFIED |
| AU-R3 | Matching tokens unchanged | D1 | — | AU-T4, T6, T9 | NOT_VERIFIED |
| (D1.2) | Token refused when role claim differs from reloaded role | D1.2 | `JwtAuthenticationFilter.isUsable` | AU-T3 | NOT_VERIFIED |
| AU-R4 | Register response independent of existing username/e-mail | D2 | `controller/AuthController.register` | AU-T7, T8 | NOT_VERIFIED |
| AU-R5 | Register never reports success | D2 | same | AU-T8 | NOT_VERIFIED |
| AU-R6 | Provisioning (out-of-band) and login unchanged | D2 | — | AU-T9 (`RoleAccessTest.anonymous_postLogin_notBlocked`) | NOT_VERIFIED |

Decisions (Claude default, pending user acceptance): D1 reject on mismatch (alt: re-scope);
D1 user without branch keeps claim (alt: fail closed, needs fixture change in an existing test);
D2 uniform 403 (alt: delete route / implement registration).

Documents to update in implement: `docs/security/SECURITY_CHECKLIST.md` (SEC-008 row, new row for
lead #9), `GVRP_Document/01-requirements/01-actors-and-roles.md`, `02-use-case-catalogue.md`
UC-D01-02, `02-system-design/04-security-and-tenancy.md` §9 item 1,
`03-basic-design/common/README.md` register shape.
