# Implementation — SEC-SESSION-001

## Scope

Make `JwtAuthenticationFilter` refuse an already-issued JWT when the user is
disabled/locked or when the token's `userId` claim does not match the loaded
user (SS-001..SS-006). Only the files listed under "Allowed changes" in
`task-opencode.md` were touched.

## Baseline (before editing)

Step 2, `./mvnw.cmd -B test` in `GVRP_Entry_API` (2026-10-16 22:xx +07:00,
`JAVA_HOME=C:\Program Files\Java\jdk-21`): BUILD SUCCESS,
**Tests run: 80, Failures: 0, Errors: 0, Skipped: 0**. No failing tests.

## Changes Made

| File | Symbol | Req |
|---|---|---|
| `security/jwt/JwtAuthenticationFilter.java` | `doFilterInternal`: authentication + request attributes are now set inside `if (isUsable(userDetails, userId))`; the `else` logs one WARN with the username and reason (never the token) and leaves the request unauthenticated so the chain reaches the authorization rules | SS-001, SS-002, SS-003, SS-004, SS-005 |
| `security/jwt/JwtAuthenticationFilter.java` (new) | `isUsable(UserDetails, Long)`: false unless `isEnabled()` && `isAccountNonLocked()`, the principal is a `UserPrincipal`, and `Objects.equals(principal.getUser().getId(), userIdFromToken)` | SS-001, SS-003 |
| `security/jwt/JwtAuthenticationFilterTest.java` (new) | 6 unit tests, one per row of the design table | SS-006 |

The existing `try`/`catch` is unchanged (SS-005); a missing user still throws
`UsernameNotFoundException` at `loadUserByUsername`, is caught, and the chain
continues. No controller, properties, other source file or existing test was
touched.

## Commands Executed

All from the repository root unless noted; `JAVA_HOME` was set to
`C:\Program Files\Java\jdk-21` for the Maven runs.

| Step | Command | Exit | Output summary |
|---|---|---|---|
| 0.1 | `git status --short` | 0 | `M docs/security/SECURITY_CHECKLIST.md`, `?? .claude/`, `?? .mcp.json`, `?? work-items/SEC-SESSION-001/` — all within the allowed exceptions |
| 0.2 | `git switch -c fix/sec-session-001` | 0 | Switched to a new branch 'fix/sec-session-001' |
| 1 | `node tools/feature-workflow/workflow.mjs init work-items/SEC-SESSION-001/run.json . SEC-SESSION-001 GVRP_Entry_API/src docs/security` | 0 | `{"state":"intake","revision":0}` |
| 1 | `... step ... ready work-items/SEC-SESSION-001/01-proof.json` | 0 | `{"state":"discover","revision":1}` |
| 1 | `... step ... ready work-items/SEC-SESSION-001/02-proof.json` | 0 | `{"state":"design","revision":2}` |
| 1 | `... step ... ready work-items/SEC-SESSION-001/03-proof.json` | 0 | `{"state":"implement","revision":3}` |
| 2 | `./mvnw.cmd -B test` (baseline, in `GVRP_Entry_API`) | 0 | BUILD SUCCESS, 80 tests |
| 3 | `./mvnw.cmd -B test -Dtest=JwtAuthenticationFilterTest -DfailIfNoTests=false` | 0 | BUILD SUCCESS, 6 tests |
| 3 | `./mvnw.cmd -B clean test` | 0 | BUILD SUCCESS, 86 tests, 0 failures |
| 4 | `... step ... ready work-items/SEC-SESSION-001/04-proof.json` | 0 | recorded transition to `verify` |

## Verification Results

- New tests: **6/6 pass** (`JwtAuthenticationFilterTest`): enabled+matching id
  authenticates and sets `userId`/`branchId`/`role`; disabled → no
  authentication/attributes; user missing → no authentication, no exception
  escapes; id mismatch → no authentication; invalid token → user never loaded;
  no header → no authentication. Chain invoked in every case.
- Full suite after change: **Tests run: 86, Failures: 0, Errors: 0,
  Skipped: 0**, compiled from clean sources (80 pre-existing + 6 new).
- The new class needs no database or running service: plain JUnit 5 + Mockito
  with `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`.
- One expected `ERROR` log line appears in the missing-user test (the existing
  catch logs the caught `UsernameNotFoundException`); the test asserts no
  exception escapes.

## Modified Tests

none. `git diff --name-only` lists only
`.../security/jwt/JwtAuthenticationFilter.java` and
`docs/security/SECURITY_CHECKLIST.md`; the only test change is the new
untracked file
`src/test/java/org/truong/gvrp_entry_api/security/jwt/JwtAuthenticationFilterTest.java`.

## Security-Relevant Changes

- A valid JWT is now bound to the loaded user: disabled/locked accounts and
  `userId`-claim mismatches no longer authenticate, closing the SEC-009 window
  where a disabled user kept access for up to the 24 h token lifetime.
- The filter does not log token values; the WARN contains only the username and
  the reason category.
- No new endpoint, no `permitAll` change, no dependency change, no secret
  copied anywhere.

## Deviations From Design

None. The guard is a private helper `isUsable(UserDetails, Long)` as suggested;
condition order and behavior match the design, including reusing the existing
`JwtAuthenticationEntryPoint` 401 path.

## Remaining Uncertainty

- Manual end-to-end check (log in, disable the user in the DB, same token gets
  401; re-enable, token works again) was not performed; left for the reviewer /
  user. This is a read-only note, not a claim of execution.
- SS-002 (user deleted) relies on the pre-existing catch path; the new test
  covers it at the unit level, not at HTTP level.
- `spring.gvrp.jwt.expiration-ms` (24 h) is unchanged; shortening it is out of
  scope per `request.md`.

## Handoff to Reviewer

Diff for review: `security/jwt/JwtAuthenticationFilter.java` and the new
`security/jwt/JwtAuthenticationFilterTest.java`. Verify: (1) the guard rejects
disabled/locked and id-mismatched tokens and sets nothing on rejection;
(2) enabled, consistent tokens behave exactly as before (SS-004);
(3) no token value is logged; (4) `clean test` green (logged above);
(5) no existing test modified (`git diff --name-only`).
