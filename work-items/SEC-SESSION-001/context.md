# Context — SEC-SESSION-001

Inspected 2026-10-16, static only.

## Current behavior

`security/jwt/JwtAuthenticationFilter.doFilterInternal`:

1. Reads `Authorization: Bearer <jwt>`; if `tokenProvider.validateToken(jwt)`:
2. `username = tokenProvider.getUsernameFromToken(jwt)`
3. `userDetails = userDetailsService.loadUserByUsername(username)` — returns
   `UserPrincipal` (`isEnabled()` / `isAccountNonLocked()` = `user.getEnabled()`).
4. Reads `userId`, `branchId`, `role` claims.
5. Sets a `UsernamePasswordAuthenticationToken(userDetails, null, authorities)`
   in the `SecurityContext` and the three request attributes.
6. Any exception is caught and logged; the chain continues unauthenticated.

`isEnabled()` is never consulted, so a disabled user keeps access until the
token expires (`spring.gvrp.jwt.expiration-ms` = 24 h). A missing user already
fails at step 3 (`UsernameNotFoundException`, caught) — SS-002 holds today but
is untested. The `userId` claim is never compared with the loaded user.

`users.username` is globally unique; `UserPrincipal.getUser()` exposes the entity
(`getId()`, `getEnabled()`).

Login (`AuthController.login`) already rejects disabled users through
`BranchAwareAuthenticationProvider` / `UserDetails.isEnabled` (see
DRIVER-DELIVERY-001 verification), so only already-issued tokens are affected.

## Tests today

No test for `JwtAuthenticationFilter`.
