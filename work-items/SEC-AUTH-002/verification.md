# Verification — SEC-AUTH-002

Verifier: Claude Code subagent, 2026-11-10. Version: worktree `E:\gvrp-wt\sec-auth-002`, branch
`fix/sec-auth-002`, uncommitted changes on top of `0b6b759` (listed in `implementation.md`).
Environment: Windows 11, JDK 21 (`C:\Program Files\Java\jdk-21`), Maven offline,
`SPRING_PROFILES_ACTIVE=ci`. No database, no running servers.

This file records the **automated** checks only. Runtime verification on the live stack and the
handoff are done by the parent agent; the run is left in state `verify`.

## Automated

| Check | Command (in `GVRP_Entry_API`) | Outcome |
|---|---|---|
| New tests on unfixed code (defect proof) | `mvnw.cmd -B -o test -Dtest=JwtClaimBindingTest,TenantBindingAccessTest,RegisterEndpointTest` | 8 run, 6 failed as expected, 2 controls passed (details in `implementation.md`) |
| New + related existing tests after fix | `mvnw.cmd -B -o test -Dtest=JwtClaimBindingTest,TenantBindingAccessTest,RegisterEndpointTest,JwtAuthenticationFilterTest,RoleAccessTest` | 25 run, 0 failures (existing `JwtAuthenticationFilterTest` 6/6, `RoleAccessTest` 11/11 unchanged and green) |
| Entry suite, CI style | `mvnw.cmd -B -o verify -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` | **355 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (baseline 347 + 8 new) |
| Secret check (repo root) | `node tools/check-secrets.mjs` | OK (5 tracked config files) |
| Test integrity | `git status` / `git diff --stat` | no existing test or fixture modified; 3 new test files only |

## Requirement coverage

| Req | Evidence | Status |
|---|---|---|
| AU-R1 | AU-T1, T2, T5 green after fix, failed before | VERIFIED (unit / web slice) |
| AU-R2 | AU-T4 (`branchId` attribute = user branch), AU-T6 (`getOrderById(7, 2L)`) | VERIFIED (unit / web slice) |
| AU-R3 | existing `JwtAuthenticationFilterTest`, `RoleAccessTest`, full suite | VERIFIED (automated) |
| D1.2 role | AU-T3 | VERIFIED (unit) |
| AU-R4 | AU-T7: identical status and body for unused / existing username / existing e-mail | VERIFIED (web slice) |
| AU-R5 | AU-T8: 403 for valid, existing, `{}` and malformed bodies; no "success"; `UserRepository` never called | VERIFIED (web slice) |
| AU-R6 | `RoleAccessTest.anonymous_postLogin_notBlocked`; no user-creation code existed | PARTIALLY_VERIFIED — login on the live stack not run here |

## Limitations (not verified here)

- Live stack not started (shared ports). The lazy `User.branch` proxy is read outside a transaction
  (`open-in-view=false`); `BranchAwareAuthenticationProvider` already does this at login, but the
  filter path is NOT_VERIFIED at runtime.
- Real JWT signing/parsing is mocked in all new tests.

## Recommended runtime checks (parent, live stack, dummy data only)

Let `$E = http://localhost:8080`. Use a dummy PLANNER user `U` in branch A (id `a`) and a second
branch B (id `b`); `O` = an order id owned by branch A.

1. Normal login still works: `POST $E/api/v1/auth/login` `{"branch_name":"<A>","username":"U","password":"…"}` → **200** with `access_token`; keep it as `T`.
2. Token with matching claims: `GET $E/api/v1/orders/O` with `Authorization: Bearer T` → **200**; `GET $E/api/v1/orders?page=0&size=5` → **200** (no Hibernate `LazyInitializationException` in the Entry log).
3. Reassign out-of-band (user/ops action with a write-capable DB account, not `agent_reader`): `UPDATE users SET branch_id = b WHERE username = 'U';`
4. Stale token: `GET $E/api/v1/orders/O` with `T` → **401** (before the fix: 200 with branch A data). Entry log shows the WARN "token claims (user id, branch, role) do not match the current user".
5. Fresh login into B: `POST $E/api/v1/auth/login` with `branch_name` B → **200**; with the new token `GET $E/api/v1/orders/O` → **400/404 not-found envelope** (O belongs to A).
6. Restore: `UPDATE users SET branch_id = a WHERE username = 'U';` → old token `T` works again (**200**), documented in design D1 (no revocation).
7. Register oracle, anonymous, three bodies (snake_case): existing username + new e-mail, new username + existing e-mail, both new, plus `{}` → all **403** with the identical body `{"errors":[{"code":"0040301","message":"Self-registration is disabled. Accounts are provisioned by an administrator."}]}`; `SELECT COUNT(*) FROM users` unchanged.
8. Optional: in the Angular client, after step 3 any planner page with the old session redirects to `/login` (interceptor handles 401).
