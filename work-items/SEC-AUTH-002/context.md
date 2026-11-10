# Context — SEC-AUTH-002

Inspected source: `master` @ `0b6b759` (2026-11-10), worktree `E:\gvrp-wt\sec-auth-002`.
Paths below are relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

## Lead #9 — branch claim vs current user branch

Current behaviour (static inspection):

- `security/jwt/JwtAuthenticationFilter.java:44-55` validates the signature and expiry, reloads the
  user with `UserDetailsService.loadUserByUsername(sub)`, reads `userId`, `branchId` and `role`
  from the token.
- `isUsable` (`:94-102`) checks `isEnabled()`, `isAccountNonLocked()` (SEC-009 fix,
  `work-items/SEC-SESSION-001`) and `userId` claim == reloaded user id. It never looks at the
  reloaded user's branch.
- `:71` stores the **claim** as request attribute `branchId`; `security/CurrentUserUtil.java:45-53`
  returns that attribute. `CurrentUserUtil.getCurrentBranchId()` is called 49 times in 14 classes
  (controllers and services for orders, jobs, solutions, Pareto, LEZ, route send, fleet, depots,
  vehicles, vehicle types) and is the only tenant scope used by those queries.
- Authorities come from the reloaded user (`:61`), so the role path rules already follow the
  current role; only the tenant scope is frozen at issuance.
- `security/CustomUserDetailsService.java:25-31` loads the user inside a read-only transaction;
  `User.branch` is `@ManyToOne(fetch = LAZY)` with `nullable = false` (`entity/User.java:20-22`).
  `spring.jpa.open-in-view=false`. `security/BranchAwareAuthenticationProvider.java:43` already calls
  `user.getBranch().getId()` on the principal outside a transaction at login, so reading the id of
  the lazy branch proxy works today (Hibernate returns the identifier of an uninitialized proxy).
- Token lifetime: `spring.gvrp.jwt.expiration-ms=86400000` (24 h) in `application.properties:47`
  (non-secret property; value read by name only).
- `security/jwt/JwtTokenProvider.java:110-118` `getBranchIdFromToken` returns `null` when the claim
  is absent.
- No code path changes `User.branch` (`grep setBranch` finds only `VehicleTypeService` /
  LEZ entities). Reassignment is out-of-band (direct DB) only. No revocation or token-version claim.

Conclusion: the defect is real in source. A user moved to branch B keeps branch A scope for up to
24 h with an existing token. Reachability depends on out-of-band reassignment (operator process),
which is how users are provisioned today anyway (see below). To be proven by a failing test.

## Lead #12 / SEC-008 — register

- `controller/AuthController.java:97-111`: `register` calls the global `existsByUsername` and
  `existsByEmail` (`repository/UserRepository.java:48,55`, no branch predicate) and answers
  400 "Username is already taken" / 400 "Email is already in use" / 200 "User registered
  successfully". It creates nothing. `RegisterRequest.branchId` is ignored.
- `config/SecurityConfig.java:76` `/api/v1/auth/**` is `permitAll`, so the endpoint is anonymous.
- Runtime-confirmed 2026-11-04 (`work-items/SEC-AUDIT-001/runtime-validation.md:16`): existing
  username → 400, new → 200, `users` count unchanged.

How users are actually created:

- No endpoint creates users. `grep userRepository.save / User.builder` in `src/main` finds nothing.
  `GVRP_Document/01-requirements/01-actors-and-roles.md` §"Registration is not implemented" records
  that users are provisioned out-of-band in the database (`ASSUMPTION` there: operators insert users
  directly).
- Clients: `GVRP_Entry_client_v2` has a "Register" link with `href="#"`
  (`features/auth/login/login.component.html:99`) and no service call; the interceptor only lists
  the path as an auth endpoint (`core/interceptors/auth.interceptor.ts:18`). Legacy
  `GVRP_Entry_client/scripts/login.js:171-174` shows a toast "under development" and calls nothing.
  No client depends on the endpoint's responses.

## Tests

- `security/jwt/JwtAuthenticationFilterTest` (SEC-SESSION-001) builds `UserPrincipal` from a
  `User` **without a branch** and stubs the branch claim to `1L`. Its test
  `enabledAndMatchingId_authenticatesAndSetsAttributes` asserts authentication succeeds and the
  `branchId` attribute is non-null. A fix that fails closed when the loaded user has no branch would
  break this existing test (see design D1).
- `security/RoleAccessTest` is a `@WebMvcTest` slice with the real `SecurityConfig` and
  `JwtAuthenticationFilter`, mocked services; `AuthController` is in the slice. It only asserts that
  `POST /api/v1/auth/login` is not 401/403. No test covers `register`.
- Baseline (parent agent, master): 347 tests, 0 failures with
  `-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest`.

## Documents that describe today's behaviour (will become stale)

- `GVRP_Document/01-requirements/01-actors-and-roles.md` (register row and §"Registration is not
  implemented"), `02-use-case-catalogue.md` UC-D01-02, `02-system-design/04-security-and-tenancy.md`
  §9 item 1, `03-basic-design/common/README.md` (register success shape),
  `docs/security/SECURITY_CHECKLIST.md` row SEC-008.

## Unanswered decisions

- Whether public self-registration should exist (product). Default chosen in design D2.
- Reject vs re-scope on branch mismatch. Default chosen in design D1.
