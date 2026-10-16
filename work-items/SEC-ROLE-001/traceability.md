# Traceability — SEC-ROLE-001

Feature ID: SEC-ROLE-001
Inspected source revision: working tree 2026-10-16 on `fix/sec-tenant-001` (see context.md)
Request source: user messages 2026-10-16 22:12; SECURITY_CHECKLIST SEC-006

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| RA-001 | DRIVER limited to driver + auth endpoints (user decision) | URL rules | `SecurityConfig.filterChain` | slice test DRIVER rows; manual 403 on `/orders` | Planned | NOT_VERIFIED |
| RA-002 | PLANNER access unchanged | URL rules | `SecurityConfig.filterChain` | slice test PLANNER rows; manual web-client walkthrough | Planned | NOT_VERIFIED |
| RA-003 | CUSTOMER blocked (user decision) | URL rules | `SecurityConfig.filterChain` | slice test CUSTOMER rows | Planned | NOT_VERIFIED |
| RA-004 | 403 envelope `0040301` | 403 response | new `AccessDeniedHandler` | slice test body assertion | Planned | NOT_VERIFIED |
| RA-005 | Public/callback/401 behavior unchanged | URL rules | `SecurityConfig.filterChain` | anonymous rows | Planned | NOT_VERIFIED |
| RA-006 | DB-free tests run in CI | Tests | new test class | CI run | Planned | NOT_VERIFIED |
| RA-007 | `RouteSendService` checks kept | Not changed | `RouteSendService` | existing `RouteSendServiceTest` | Planned | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| DRIVER only driver API | user answer 2026-10-16 22:12 | RA-001 | none |
| CUSTOMER blocked | user answer 2026-10-16 22:12 | RA-003 | check CUSTOMER users in DB |
| Central URL rules, default deny | design.md | RA-001..RA-003 | none |
| Implementation by OpenCode engineer, review by Claude | user message 2026-10-16 | all | delegation trial notes in closure |

## Synchronization review

- Requirements versus implementation: to be completed at verify.
- Design versus implementation: to be completed at verify.
- Test and documentation coverage: to be completed at verify.
- Stale links or changed assumptions: none yet.
- Remaining limitations: SEC-009 open.
