# Implementation — SEC-ROLE-001

## Scope

Role-based URL rules for the Entry API (RA-001..RA-007) exactly as specified
in `work-items/SEC-ROLE-001/design.md`. Only the files listed in
`task-opencode.md` "Allowed changes" were touched.

## Baseline (before editing)

`./mvnw.cmd -B test` in `GVRP_Entry_API` on 2026-10-16 22:43 (+07:00):
BUILD SUCCESS, Tests run: 69, Failures: 0, Errors: 0, Skipped: 0.
(Note: `JAVA_HOME` on this machine pointed at a wrong JDK; the runs below
used `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"`.)

## Changes Made

| File | Symbol | Req |
|---|---|---|
| `config/SecurityConfig.java` | `filterChain`: replaced the six per-prefix `.authenticated()` lines with `.requestMatchers("/api/v1/driver/**").hasRole("DRIVER")` then `.requestMatchers("/api/v1/**").hasRole("PLANNER")`; registered `.accessDeniedHandler(roleAccessDeniedHandler)` next to the existing entry point | RA-001, RA-002, RA-003, RA-005 |
| `security/RoleAccessDeniedHandler.java` (new) | `handle`: writes HTTP 403, `application/json`, body `{"errors":[{"code":"0040301","message":"Access forbidden."}]}` built from `ErrorCode.FORBIDDEN` + `ErrorResponse`/`ErrorDetail` with the application `ObjectMapper` | RA-004 |
| `src/test/.../security/RoleAccessTest.java` (new) | 11 tests, one per row of the design table | RA-006 |
| `GVRP_Entry_API/pom.xml` | `org.springframework.security:spring-security-test`, `<scope>test</scope>`, no version | test infra |

`RouteSendService` role checks untouched (RA-007). No controller, service,
properties, frontend, CI, docs or existing test touched.

## Commands Executed

| Command (in `GVRP_Entry_API`, `JAVA_HOME`=jdk-21) | Exit |
|---|---|
| `./mvnw.cmd -B test` (baseline) | 0, BUILD SUCCESS, 69 tests |
| `./mvnw.cmd -B test -Dtest=RoleAccessTest -DfailIfNoTests=false` | 0, BUILD SUCCESS, 11 tests |
| `./mvnw.cmd -B clean test` | 0, BUILD SUCCESS, 80 tests, 0 failures |

## Verification Results

- New tests: 11/11 pass (`RoleAccessTest`): DRIVER allowed on
  `GET /api/v1/driver/routes`; DRIVER 403 on orders, `/drivers`,
  `POST /routes/1/send`; PLANNER 200 on orders and `/drivers`; PLANNER 403 on
  driver routes; CUSTOMER 403 on orders and driver routes; anonymous 401 on
  orders; anonymous `POST /auth/login` not 401/403.
- Full suite after change: Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
  (69 pre-existing + 11 new), compiled from clean sources.
- The new class needs no database: `@WebMvcTest` slice with mocked services,
  repositories and supporting security beans; real `SecurityConfig`, real
  servlet filters, real controllers. Nothing excluded from CI.

## Modified Tests

none. `git diff --name-only` shows no file under `src/test` modified; the
only test change is the new file
`src/test/java/org/truong/gvrp_entry_api/security/RoleAccessTest.java`.

## Security-Relevant Changes

- URL authorization: `/api/v1/driver/**` requires `ROLE_DRIVER`;
  `/api/v1/**` requires `ROLE_PLANNER`. Authorities come from `UserPrincipal`
  (DB role), unchanged. Public paths (`auth`, Swagger, `/actuator/health`,
  engine callbacks) and `anyRequest().authenticated()` unchanged.
- New `RoleAccessDeniedHandler` converts every Spring Security 403 into the
  project envelope with code `0040301`.
- New test dependency `org.springframework.security:spring-security-test`
  (test scope, version managed by Spring Boot parent) for `@WithMockUser`.
- No new endpoint, no `permitAll` change, no secret copied anywhere
  (callback key in the test is the literal `"test-key"` via
  `@TestPropertySource`).

## Deviations From Design

None. Rule order, 403 body (`ErrorCode.FORBIDDEN` message
"Access forbidden."), and all 11 table cases implemented as specified.

## Remaining Uncertainty

- Manual check with real DRIVER/PLANNER tokens (RA acceptance) not performed
  here; left for the reviewer/user run.
- `SELECT role, COUNT(*) FROM users GROUP BY role;` (request.md open check)
  not run: no DB read tool was used in this session. If CUSTOMER users exist
  and are used, they lose business-endpoint access per RA-003.
- SEC-009 (disabled users keep access until token expiry) observed, out of
  scope, untouched.

## Handoff to Reviewer

Diff for review: `SecurityConfig.java`,
`security/RoleAccessDeniedHandler.java`, `pom.xml`, new
`security/RoleAccessTest.java`. Verify: (1) rule order and the
`/api/v1/drivers` vs `/api/v1/driver/**` distinction; (2) 403 envelope shape
against `GlobalExceptionHandler.handleForbidden`; (3) `clean test` green
(logged above); (4) no existing test modified (`git diff --name-only`).
