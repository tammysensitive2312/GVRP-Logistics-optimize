# Design — SEC-SESSION-001

## Change in `JwtAuthenticationFilter.doFilterInternal`

After loading `userDetails` and reading `userId` from the token, authenticate
only if all hold:

1. `userDetails.isEnabled()` and `userDetails.isAccountNonLocked()`;
2. `userDetails` is a `UserPrincipal` and
   `Objects.equals(principal.getUser().getId(), userIdFromToken)`.

Otherwise: do not set the authentication or the request attributes, log one
WARN line with the username and the reason (never the token), and continue the
chain. The request then reaches the authorization rules unauthenticated and
`JwtAuthenticationEntryPoint` answers 401 (existing behavior for bad tokens).

Keep the existing try/catch (SS-005). Prefer a small private helper
(for example `isUsable(UserDetails, Long)`) over nested conditions.

## Tests (SS-006)

New `src/test/java/org/truong/gvrp_entry_api/security/jwt/JwtAuthenticationFilterTest.java`,
plain JUnit 5 + Mockito (no Spring context): mock `JwtTokenProvider` and
`UserDetailsService`, use `MockHttpServletRequest`, `MockHttpServletResponse`,
`MockFilterChain`; clear `SecurityContextHolder` after each test.

| Case | Setup | Expected |
|---|---|---|
| enabled, ids match | `UserPrincipal` of enabled user id 5, token userId 5 | authentication set; `branchId`/`role`/`userId` attributes set; chain called |
| disabled | same user with `enabled=false` | no authentication, no attributes; chain called |
| user missing | `loadUserByUsername` throws `UsernameNotFoundException` | no authentication; chain called; no exception escapes |
| id mismatch | user id 5, token userId 6 | no authentication; chain called |
| invalid token | `validateToken` false | no authentication; `loadUserByUsername` never called |
| no header | — | no authentication; chain called |

A 401 at HTTP level is already covered by `RoleAccessTest.anonymous_getOrders_unauthorized`;
no new web-slice test needed.
