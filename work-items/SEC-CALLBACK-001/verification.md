# Verification — SEC-CALLBACK-001

Verifier: Claude Code, 2026-11-04. Version: `1dd8312` (master after merge).

| Check | Command | Outcome |
|---|---|---|
| Entry unit/web tests | `.\mvnw.cmd -B clean test` (JDK 21) | 344 tests, 0 failures; 3 pre-existing environment errors (missing local JWT secret in the worktree: `DepotMapperTest` ×2, `contextLoads`); CI suite 341 green, exit 0 |

## Requirement coverage

| Requirement | Evidence |
|---|---|
| CB-R1, CB-R2 | `CallbackAuthenticationTest`; runtime: encoded paths without key → 401 |
| CB-R3 | constant-time compare in `ApiKeyAuthenticationFilter` (code review) |
| CB-R4 | `OptimizationCallbackBranchScopeTest`; runtime: valid key + foreign order → 422, nothing changed |
| CB-R5 | existing callback tests green |

## Limitations

Fixture-only edits in three existing callback tests, approved by the user (option A, 2026-11-04). SEC-010 (Engine has no auth) and SEC-001 (committed credentials) remain open.
