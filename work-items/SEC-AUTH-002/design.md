# Design — SEC-AUTH-002

Inputs: `request.md` (AU-R1…R6), `context.md`. Source: `master` @ `0b6b759`.
Paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

## D1 — Token claims must match the reloaded user (AU-R1, AU-R2, AU-R3)

`security/jwt/JwtAuthenticationFilter.isUsable` gains two checks next to the SEC-009 / userId checks:

1. **Branch.** If the reloaded user has a branch, the token's `branchId` claim must equal
   `user.getBranch().getId()`. A missing claim (`null`) does not equal a real branch, so it is
   refused too.
2. **Role.** The token's `role` claim must equal `user.getRole().name()`. Same root cause as the
   branch claim (authority frozen at issuance): the `role` request attribute is read by
   `RouteSendService` (`CurrentUserUtil.getCurrentRole()`), while authorities already come from the
   reloaded user. Including it keeps every request attribute consistent with the database.

A refused token leaves the request unauthenticated and sets no request attribute, exactly like the
SEC-009 path, so protected endpoints answer 401 through `JwtAuthenticationEntryPoint`. The WARN log
keeps its current shape (username only; no token, no branch of another tenant beyond ids).

When the token is accepted, the `branchId` attribute is taken from the reloaded user's branch (equal
to the claim by construction), so the tenant scope is sourced from server state (AU-R2).

**Decision (Claude default, pending user acceptance): reject with 401 on a mismatch.**
Alternative: accept the token and silently re-scope the request to the user's current branch. Not
chosen: a user moved between companies would keep a live session that suddenly shows another
company's data without logging in again (a confusing and audit-hostile switch), and the client
caches `branchId`/`branchName` from the login response, so the UI would label branch B's data as
branch A. With 401 the client's existing handling sends the user back to login, which issues a token
for the current branch.

**Decision (Claude default, pending user acceptance): a loaded user without a branch keeps the
token's claim.** Persisted users always have a branch (`users.branch_id` is `NOT NULL`,
`entity/User.java:21`), so in production the comparison always applies. A user without a branch only
exists in unit fixtures: the existing test
`JwtAuthenticationFilterTest.enabledAndMatchingId_authenticatesAndSetsAttributes` builds a principal
without a branch and requires authentication with a non-null `branchId` attribute. Failing closed
here would change that accepted test (AGENTS.md §14). Alternative (stricter, needs user approval
of a fixture change in that existing test: give `principal()` a `Branch` with id 1): refuse any
token whose user has no branch.

Lazy loading: `User.branch` is a lazy proxy and `open-in-view=false`. Reading only `getId()` of the
proxy does not initialize it; `BranchAwareAuthenticationProvider:43` already relies on this at login.
Runtime confirmation is part of the parent's verification.

Not included: token versioning/revocation. A token of a user who is moved and then moved back would
become valid again; that is acceptable because the scope then matches the current branch.

## D2 — Public self-registration refused uniformly (AU-R4, AU-R5, AU-R6)

`controller/AuthController.register` no longer reads the body and no longer queries the user table.
Every `POST /api/v1/auth/register` throws `ForbiddenException("Self-registration is disabled.
Accounts are provisioned by an administrator.")`, which `GlobalExceptionHandler.handleForbidden`
maps to `403` with the common error envelope
`{"errors":[{"code":"0040301","message":"Self-registration is disabled. ..."}]}`.

- The response is byte-identical for an existing username, an existing e-mail, unused values, an
  empty body and a malformed body, so it carries no existence signal (AU-R4).
- Nothing reports success any more (AU-R5).
- No working user-creation path exists in the application (`context.md`); users are provisioned in
  the database, which this change does not touch (AU-R6). Login is unchanged.
- No new `permitAll`: `/api/v1/auth/**` stays public because login needs it.

**Decision (Claude default, pending user acceptance): keep the route and refuse it uniformly with
403.** Alternatives:

| Option | Why not chosen (default) |
|---|---|
| Delete the mapping (404) | On a `permitAll` path an unmapped route goes through Spring Boot's `/error` dispatch, whose status under the security chain is not covered by any test; a deliberate 403 with a message is explicit and testable. Deleting is a valid follow-up if the product never wants registration. |
| Identical 200/202 "request received" for every input | Still reports success for an account that is not created (SEC-008). |
| Implement self-registration | Product decision: which branch an anonymous user may join and with which role. Letting anyone create an account in any company is a tenant-isolation risk; needs requirements first. |

`dto/request/RegisterRequest` and `UserRepository.existsByUsername/existsByEmail` become unused by
this endpoint; they are left in place (no unrelated deletions).

## Tests (all new; no existing test or fixture is modified)

| ID | Test | Req | Expected before fix |
|---|---|---|---|
| AU-T1 | `JwtClaimBindingTest.branchClaimDiffersFromUserBranch_doesNotAuthenticate` | R1 | FAIL (authenticated, branchId = claim) |
| AU-T2 | `JwtClaimBindingTest.missingBranchClaim_doesNotAuthenticate` | R1 | FAIL |
| AU-T3 | `JwtClaimBindingTest.roleClaimDiffersFromUserRole_doesNotAuthenticate` | D1.2 | FAIL |
| AU-T4 | `JwtClaimBindingTest.matchingClaims_authenticateWithUserBranch` | R2, R3 | pass |
| AU-T5 | `TenantBindingAccessTest.staleBranchToken_getOrder_unauthorizedAndServiceNotCalled` (`@WebMvcTest`, real `SecurityConfig` + filter) | R1, R2 | FAIL (200, service called with old branch) |
| AU-T6 | `TenantBindingAccessTest.currentBranchToken_getOrder_scopedToUserBranch` | R2, R3 | pass |
| AU-T7 | `RegisterEndpointTest.existingAndUnusedValues_getIdenticalResponses` | R4 | FAIL (400 vs 200) |
| AU-T8 | `RegisterEndpointTest.register_neverReportsSuccessAndNeverTouchesUsers` (incl. empty and malformed body) | R4, R5 | FAIL |
| AU-T9 | Existing Entry suite unchanged (347 tests) | R3, R6 | pass |

Runtime (parent): see `verification.md` "Recommended runtime checks".
