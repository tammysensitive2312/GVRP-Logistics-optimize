# Request — SEC-AUTH-002 (JWT branch binding and public register oracle)

Source: Claude Code session 2026-11-10 (+07:00). The parent agent (Claude) delegated this work item
with explicit implementation authority: "fix security findings ... For each lead: first confirm or
refute it from source AND with a new failing test that demonstrates the defect; only then fix."
Branch `fix/sec-auth-002`, worktree `E:\gvrp-wt\sec-auth-002`, based on `master` @ `0b6b759`.

## Findings addressed

| Finding | Severity | Evidence |
|---|---|---|
| Audit lead #9 `SEC-AUDIT-001:jwt-branch-claim-current-user-binding` | needs_validation | `JwtAuthenticationFilter` stores the token's `branchId` claim as the caller's tenant scope without comparing it with the reloaded user's persisted branch. Source trace `work-items/SEC-AUDIT-001/audit/NEEDS-VALIDATION.md` §9. Not runtime-validated. |
| SEC-008 | Low | `POST /api/v1/auth/register` (public) returns "User registered successfully" without creating a user. Runtime-confirmed 2026-11-04 (`work-items/SEC-AUDIT-001/runtime-validation.md`). |
| Audit lead #12 `GVRP_Entry_API.AuthController.register.global-user-enumeration` | (part of SEC-008) | Same endpoint answers 400 "Username is already taken" / "Email is already in use" vs 200: a cross-branch username/e-mail existence oracle for anonymous callers. Runtime-confirmed (same file). Audit §12. |

## Requirements

| ID | Requirement |
|---|---|
| AU-R1 | A bearer token is usable only if its `branchId` claim equals the branch of the user reloaded from the database at request time. Otherwise the request stays unauthenticated (protected endpoints answer 401) and no branch scope is set. |
| AU-R2 | The tenant scope used by controllers (`CurrentUserUtil.getCurrentBranchId()`) is the reloaded user's current branch, never a claim that disagrees with it. |
| AU-R3 | Tokens whose claims match the current user behave exactly as before (existing tests stay green). |
| AU-R4 | An anonymous caller of `POST /api/v1/auth/register` cannot learn whether a username or e-mail exists in any branch: the response is identical for existing and unused values. |
| AU-R5 | The register endpoint no longer reports success for an account it did not create. |
| AU-R6 | No user-provisioning path that works today is broken. |

## Exclusions

- Token revocation lists / token versioning, refresh tokens, logout (separate decisions).
- Rate limiting of login (separate gap, `GVRP_Document/02-system-design/04-security-and-tenancy.md` §9).
- Implementing real self-registration (a product decision; see `design.md`).
- Rotating secrets (SEC-001).

## Acceptance expectations

- Each lead is confirmed by a new test that fails on the unfixed code, then passes after the fix.
- Full Entry suite green (`SPRING_PROFILES_ACTIVE=ci`, baseline 347 tests, 0 failures).
- The run stops in `verify`; runtime verification on the live stack and the handoff are done by the
  parent agent.

## Open questions (resolved by documented defaults, pending user acceptance)

- Should public self-registration exist at all? → `design.md` D2.
- On a branch mismatch, reject (401) or silently re-scope to the current branch? → `design.md` D1.
