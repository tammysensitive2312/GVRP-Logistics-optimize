# Closure — SEC-LOW-001

Closed 2026-11-11 by Claude Code on the user's acceptance (`user-acceptance.json`). Fix commit
`e6edc43`; merged into `master` together with the other batch-2 items (SEC-AUTH-002, SEC-LOW-001,
SEC-CLIENT-002), tested merged in `test/sec-batch2`.

Closes: SEC-007 (audit lead #11), audit leads #10 and #14.

## Evidence

plate conflict 409 after own references (`VehicleServicePlateTest`, `GlobalExceptionHandlerConflictTest`; runtime 400/409); import ceilings (`OrderImportLimitsTest`; runtime 400 for 10,001 rows, no write, no geocoding); revision-tree cleanup (`JobCleanupRevisionTreeTest`). Merged suites: Entry 369/369, client 490/490, build unchanged. Details: `verification.md`.

## Limitations carried forward

Plates stay globally unique: a planner with valid own references can still learn that a plate exists elsewhere (409). Per-branch plates need the SQL in `design.md` (not applied). Cleanup with dry-run off not run at runtime.

## Memory updates

- `docs/security/SECURITY_CHECKLIST.md`: statuses updated in the batch-2 merge commit on `master`.
- `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `LESSONS.md`, `AGENTS.md`, `CLAUDE.md`: no change (security
  fixes; no research claim; no new workflow trap beyond what is recorded in the work items).
