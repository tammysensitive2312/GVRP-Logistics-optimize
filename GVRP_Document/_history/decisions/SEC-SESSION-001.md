# SEC-SESSION-001 — disabled users lose API access

**Problem.** Authentication was evaluated once, when the JWT was issued. Disabling a user stopped a new
login but left every already-issued token working until it expired, so an administrator could not
actually revoke access (SEC-009).

**Decision.**

- The JWT filter loads the user on each request and rejects the token when the account is disabled or
  locked, or when the token's user id does not match the loaded user.
- Rejection is silent from the client's point of view: the request continues unauthenticated and the
  normal authentication entry point answers, so a revoked user sees the same `401` as an anonymous
  caller. No new error code is introduced.
- Sessions stay stateless; there is no server-side session store to invalidate.

**Date.** Accepted 2026-10-17 09:42 (+07:00); state `done` rev 7; closes SEC-009 once merged.

**Source.** `work-items/SEC-SESSION-001/` (deleted): `design.md`, `verification.md`, `review.md`,
`closure.md`. Code: `security/JwtAuthenticationFilter.java` (the enabled/locked check),
`security/UserPrincipal.java`.

**Still valid in this documentation.** `02-system-design/04-security-and-tenancy.md`, `BR-008`,
`TC-D01-010`, `TC-D01-011`.

**Limitations recorded at closure.** One low test-strength note in review; no behavioural defect found.
