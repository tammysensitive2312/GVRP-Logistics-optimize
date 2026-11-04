# Closure — SEC-CALLBACK-001

Closed 2026-11-04 by Claude Code on the user's acceptance (`user-acceptance.json`). Version `1dd8312`.

Closes: SEC-012, SEC-013, SEC-004. Status in `docs/security/SECURITY_CHECKLIST.md` updated in `1dd8312`.

## Requirement coverage

| Requirement | Evidence |
|---|---|
| CB-R1, CB-R2 | `CallbackAuthenticationTest`; runtime: encoded paths without key → 401 |
| CB-R3 | constant-time compare in `ApiKeyAuthenticationFilter` (code review) |
| CB-R4 | `OptimizationCallbackBranchScopeTest`; runtime: valid key + foreign order → 422, nothing changed |
| CB-R5 | existing callback tests green |

## Limitations carried forward

Fixture-only edits in three existing callback tests, approved by the user (option A, 2026-11-04). SEC-010 (Engine has no auth) and SEC-001 (committed credentials) remain open.

## Memory updates

- `docs/security/SECURITY_CHECKLIST.md`: findings marked FIXED (`1dd8312`).
- `docs/engineering/LESSONS.md`: L-2026-015…018 added (`f095ef4`).
- `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `AGENTS.md`, `CLAUDE.md`: no change (security fixes, no research or rule change).
