# Closure — SEC-CLIENT-002

Closed 2026-11-11 by Claude Code on the user's acceptance (`user-acceptance.json`). Fix commit
`ffb53a5`; merged into `master` together with the other batch-2 items (SEC-AUTH-002, SEC-LOW-001,
SEC-CLIENT-002), tested merged in `test/sec-batch2`.

Closes: audit lead #8 (`session-reset.map-service-stale-zone-callback`).

## Evidence

generation counter and reset hook (`map.service.session.spec.ts` MC-T1…T6); runtime: own solution's routes and zones drawn. Merged suites: Entry 369/369, client 490/490, build unchanged. Details: `verification.md`.

## Limitations carried forward

Two-planner throttled browser check not run (needs a second account).

## Memory updates

- `docs/security/SECURITY_CHECKLIST.md`: statuses updated in the batch-2 merge commit on `master`.
- `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `LESSONS.md`, `AGENTS.md`, `CLAUDE.md`: no change (security
  fixes; no research claim; no new workflow trap beyond what is recorded in the work items).
