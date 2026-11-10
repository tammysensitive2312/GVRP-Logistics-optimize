# Implementation — SEC-AUTH-002

Branch `fix/sec-auth-002` (worktree `E:\gvrp-wt\sec-auth-002`), base `0b6b759`. Design: `design.md`
(D1, D2; decisions are Claude defaults pending user acceptance).

## Test-first confirmation of both leads

The new tests were written and run against the unfixed code first
(`mvnw.cmd -B -o test -Dtest=JwtClaimBindingTest,TenantBindingAccessTest,RegisterEndpointTest`,
`SPRING_PROFILES_ACTIVE=ci`, 2026-11-10): 8 run, **6 failed**, 2 passed (the two "matching" controls).

| Test | Failure on unfixed code | Lead |
|---|---|---|
| `JwtClaimBindingTest.branchClaimDiffersFromUserBranch_doesNotAuthenticate` | authentication set (`ROLE_PLANNER`) for claim branch 1, user branch 2 | #9 |
| `JwtClaimBindingTest.missingBranchClaim_doesNotAuthenticate` | authentication set with no branch claim | #9 |
| `JwtClaimBindingTest.roleClaimDiffersFromUserRole_doesNotAuthenticate` | authentication set with role claim DRIVER, user PLANNER | #9 (role) |
| `TenantBindingAccessTest.staleBranchToken_getOrder_unauthorizedAndServiceNotCalled` | `Status expected:<401> but was:<200>`: `GET /api/v1/orders/7` served with the former branch scope | #9 |
| `RegisterEndpointTest.existingAndUnusedValues_getIdenticalResponses` | `status reveals an existing username ==> expected: <200> but was: <400>` | #12 |
| `RegisterEndpointTest.register_neverReportsSuccessAndNeverTouchesUsers` | `expected: <403> but was: <200>` for an unused username (success reported, nothing created) | SEC-008 |

Both leads are therefore **confirmed** (lead #9 at unit and web-slice level; reachability in
production still requires an out-of-band branch reassignment, `context.md`).

## Changed files

Paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

| File | Change | Req |
|---|---|---|
| `security/jwt/JwtAuthenticationFilter.java` | `isUsable(userDetails, userId, branchId, role)`: also requires branch claim == reloaded user's branch id (when the user has a branch) and role claim == reloaded role; `branchId` attribute set from the reloaded user's branch; helper `currentBranchId` reads only the id of the lazy branch; WARN message names the claim mismatch (username only, as before) | AU-R1, R2, R3 |
| `controller/AuthController.java` | `register()` takes no body, queries nothing and always throws `ForbiddenException("Self-registration is disabled. Accounts are provisioned by an administrator.")` → 403 `0040301` envelope via `GlobalExceptionHandler`; unused `RegisterRequest` import removed | AU-R4, R5, R6 |

Documentation updated to the new behaviour (superseded text kept and dated):
`docs/security/SECURITY_CHECKLIST.md` (SEC-008 status; new row **SEC-016** for lead #9 — the ID may
collide with rows added in parallel worktrees and must be checked at merge),
`GVRP_Document/01-requirements/01-actors-and-roles.md`, `01-requirements/02-use-case-catalogue.md`
(UC-D01-02), `02-system-design/04-security-and-tenancy.md` (§5 token dependence, §9 item 1),
`03-basic-design/common/README.md`, `03-basic-design/D01-auth/README.md`.

Not changed on purpose: `GVRP_Document/04-test-design/test-cases.csv` row `TC-D01-005` still expects
`200 "User registered successfully"`. It is a test-case specification; changing its expected value
needs user approval (AGENTS.md §14). Proposed change: expected → `403 0040301 "Self-registration is
disabled..."`, identical for existing and unused values.

No new dependency. No `permitAll` rule added (`/api/v1/auth/**` stays public for login). No existing
test or fixture modified.

Supporting evidence for D2 (keep route, 403 instead of deleting it):
`GVRP_Document/03-basic-design/common/README.md` records that an unmapped path (`GET /api/v1/nope`)
answers 401, not 404, through the error dispatch; a deleted route would therefore behave in an
untested way, while the explicit 403 is covered by `RegisterEndpointTest`.

## New tests

| File (under `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/`) | Tests | ID |
|---|---|---|
| `security/jwt/JwtClaimBindingTest.java` | 4 | AU-T1…T4 |
| `security/TenantBindingAccessTest.java` (`@WebMvcTest`, real `SecurityConfig` + filter) | 2 | AU-T5, T6 |
| `security/RegisterEndpointTest.java` (`@WebMvcTest`, real `SecurityConfig`) | 2 | AU-T7, T8 |

## Remaining work / limitations

- `dto/request/RegisterRequest` and `UserRepository.existsByUsername/existsByEmail` are now unused by
  the endpoint; left in place (no unrelated deletion).
- A loaded user without a branch keeps the claim (design D1; impossible for persisted users because
  `users.branch_id` is NOT NULL). Stricter alternative needs a fixture change in the existing
  `JwtAuthenticationFilterTest.principal()` — user decision.
- Lazy-proxy id access outside a transaction is not exercised by unit tests (no DB in CI); runtime
  check by the parent (`verification.md`).
- No token revocation/versioning; the Angular client caches `branchId` from login and will get 401
  (→ login) after a reassignment.
