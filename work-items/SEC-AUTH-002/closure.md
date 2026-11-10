# Closure — SEC-AUTH-002

Closed 2026-11-11 by Claude Code on the user's acceptance (`user-acceptance.json`). Fix commit
`ea49455`; merged into `master` together with the other batch-2 items (SEC-AUTH-002, SEC-LOW-001,
SEC-CLIENT-002), tested merged in `test/sec-batch2`.

Closes: SEC-008 (with audit lead #12) and the new SEC-016 (audit lead #9).

## Evidence

JWT branch/role reconciliation (`JwtClaimBindingTest`, `TenantBindingAccessTest`); uniform 403 register (`RegisterEndpointTest`; runtime 403 ×4). Merged suites: Entry 369/369, client 490/490, build unchanged. Details: `verification.md`.

## Limitations carried forward

Branch change while a token is live not exercised at runtime (needs a DB write). `TC-D01-005` in `GVRP_Document/04-test-design/test-cases.csv` still expects 200; the proposed change to 403 awaits a separate user decision. A user without a branch keeps the token claim (stricter rule needs a fixture change).

## Memory updates

- `docs/security/SECURITY_CHECKLIST.md`: statuses updated in the batch-2 merge commit on `master`.
- `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `LESSONS.md`, `AGENTS.md`, `CLAUDE.md`: no change (security
  fixes; no research claim; no new workflow trap beyond what is recorded in the work items).
