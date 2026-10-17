# Request — SEC-SESSION-001 (disabled users must lose API access)

Source: user message 2026-10-16 23:57 (+07:00) "hợp lý giao tiếp đi", accepting
the proposal to fix SEC-009 as the second delegation trial. Origin:
`docs/security/SECURITY_CHECKLIST.md` SEC-009.

Delegation: Claude specifies and reviews; OpenCode `gvrp-engineer` implements
and, unlike SEC-ROLE-001, also runs the workflow commands itself.

## Requirements

- SS-001: a request with a valid JWT whose user is disabled (`users.enabled = false`)
  is not authenticated: protected endpoints answer 401 through the existing
  `JwtAuthenticationEntryPoint`.
- SS-002: a valid JWT whose user no longer exists is not authenticated (401).
- SS-003: a valid JWT whose `userId` claim differs from the id of the user loaded
  by username is not authenticated (401).
- SS-004: enabled users with a consistent token behave exactly as today.
- SS-005: the filter never throws to the client and never logs the token value.
- SS-006: DB-free unit tests cover SS-001..SS-004 and run in CI.

## Exclusions

Token revocation lists, refresh tokens, shorter expiry, logout invalidation,
role changes during a session (authorities are already re-read from the DB on
every request), any other SEC finding.

## Acceptance expectations

- `mvnw.cmd clean test` green, new tests included.
- Manual: log in as a test user, disable it in the DB, the same token gets 401;
  re-enable it, the token works again.
