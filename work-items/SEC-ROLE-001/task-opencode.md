# Task for the OpenCode engineer — SEC-ROLE-001

You are `gvrp-engineer`. This task is authorized for implementation by the
user. Follow `AGENTS.md` in full, especially section 14 (Test Integrity) and
section 15 (Security and Tenant Isolation).

## Read first

1. `work-items/SEC-ROLE-001/request.md` — requirements RA-001..RA-007
2. `work-items/SEC-ROLE-001/context.md` — current behavior, endpoint table
3. `work-items/SEC-ROLE-001/design.md` — rule order, 403 handler, test cases

Treat `design.md` as the specification. If something in it is wrong or
impossible, stop and report; do not redesign silently.

## Goal

Restrict Entry API endpoints by role: DRIVER only `/api/v1/driver/**`,
PLANNER every other business endpoint, CUSTOMER nothing under `/api/v1/**`,
with a 403 body in the project error envelope (`0040301`).

## Allowed changes

- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/config/SecurityConfig.java`
- one new class in `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/security/` for the `AccessDeniedHandler`
- new test class(es) under `GVRP_Entry_API/src/test/java/...`
- `work-items/SEC-ROLE-001/implementation.md` (your report)
- `GVRP_Entry_API/pom.xml`: add exactly one test dependency,
  `org.springframework.security:spring-security-test` with `<scope>test</scope>`
  and no `<version>` (managed by the Spring Boot parent). It is not in the
  pom today (checked 2026-10-16) and provides `@WithMockUser` /
  `SecurityMockMvcRequestPostProcessors`. Name it under
  "Security-Relevant Changes".

Anything else requires stopping and asking.

## Forbidden

- Editing, disabling, skipping or excluding any existing test.
- Adding a test that needs MySQL, the GraphHopper graph or any running service.
- Adding or upgrading any other dependency.
- Touching `application*.properties`, controllers, services, the frontend,
  `.github/`, `AGENTS.md`, `docs/`.
- Committing, pushing or rewriting git history.
- Copying any secret value from configuration files into code, tests or reports.

## Before editing

Run `mvnw.cmd test` in `GVRP_Entry_API` and record the result (pass count and
any failing test names) as the baseline.

## Done when

1. Every case in the table in `design.md` "Tests" is covered by a test and passes.
2. `mvnw.cmd clean test` in `GVRP_Entry_API` passes; no existing test changed.
3. The new test class does not require a database (it would run in CI).
4. `work-items/SEC-ROLE-001/implementation.md` is written in this format:

```text
## Scope
## Baseline (before editing)
## Changes Made            (file, symbol, requirement ID)
## Commands Executed       (exact commands and exit codes)
## Verification Results    (tests run / failures, new test names)
## Modified Tests          (must be "none" unless approved)
## Security-Relevant Changes
## Deviations From Design
## Remaining Uncertainty
## Handoff to Reviewer
```

Report only what you actually ran. If a command could not run, say so with the
error. Do not claim verification you did not perform.

When finished, tell the user the task is done. Claude will review the diff
independently and run the verify step.
