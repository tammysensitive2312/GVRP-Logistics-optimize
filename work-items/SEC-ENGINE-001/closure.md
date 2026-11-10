# Closure — SEC-ENGINE-001

Closed 2026-11-10 by Claude Code on the user's acceptance (`user-acceptance.json`). Fix commit `2b1167b`
on `fix/sec-engine-001`, fast-forwarded into `master`.

Closes: SEC-010; audit leads #6 `EngineApiController.no-auth-boundary` and #7
`EngineOptimization.unbounded-configured-work`.

## Requirement coverage

| Req | Evidence |
|---|---|
| EN-R1 | `ServiceKeyFilterTest`, `ServiceKeyFilterMockMvcTest`; runtime: every endpoint 401 without key, health 200 |
| EN-R2 | `ServiceKeyFilterTest.blankConfiguredKeyRefusesEverythingButHealth` |
| EN-R3 | constant-time compare (code review); placeholders only in tracked files; `check-secrets`; key found 0 times in logs/files |
| EN-R4 | `EngineApiClientKeyTest`; job 96 accepted by the Engine |
| EN-R5 | `OptimizationControllerLimitsTest`; runtime 400 for 100,000,000 iterations / 64 threads / 0 iterations |
| EN-R6 | `OptimizationControllerLimitsTest` (413) |
| EN-R7 | `SolverDeadlineTest`; runtime log of the 7200 s cap |
| EN-R8 | presets accepted; Engine 142 and Entry 347 tests green; job 96 ran with 2000 it / 4 threads |

## Limitations carried forward

Async job concurrency not verified (D5); matrix build not under the runtime cap; no body-size limit
before JSON parsing; port 8081 exposure unchanged. No existing test modified; no new dependency.

## Memory updates

- `docs/security/SECURITY_CHECKLIST.md`: SEC-010 → FIXED; new inbound key recorded in the SEC-001 table.
- `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `LESSONS.md`, `AGENTS.md`, `CLAUDE.md`: no change (no research
  claim, no new trap; rule files untouched). The benchmark runbook still says the request timeout is not
  enforced, which remains true (decision D3 option B).
