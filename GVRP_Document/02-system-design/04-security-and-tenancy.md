# 04 — Security and tenancy

What the code enforces, in the order a request meets it. This document states behaviour; the
findings derived from it belong to `docs/security/SECURITY_CHECKLIST.md` and are referenced, not
duplicated.

Diagram: [sequence-auth.svg](diagrams/sequence-auth.svg) ([source](diagrams/sequence-auth.puml)).

---

## 1. Model in one paragraph

The API is stateless. A browser posts a branch name, a username and a password to
`/api/v1/auth/login`; the server resolves the branch by name, authenticates the user within that
branch, and returns a JWT carrying `userId`, `branchId` and `role` with a 24-hour lifetime. Every later
request carries `Authorization: Bearer <token>`. The filter verifies the signature, reloads the user
row, refuses the token if the account is disabled or locked or the user id does not match, and puts
`userId`, `branchId` and `role` into request attributes. Controllers never accept a branch id from the
client: they read `CurrentUserUtil.getCurrentBranchId()`.

A second, unrelated authentication path exists for the optimiser: `X-API-Key` on callback paths.

---

## 2. Filter chain

| Order | Filter | Scope | Effect |
|---|---|---|---|
| 1 | `ApiKeyAuthenticationFilter` | path starts with `/api/v1/solutions/callbacks/` | compares `X-API-Key` with `engine.api.key`; on match authenticates as principal `ENGINE_SERVICE` with `ROLE_ENGINE_SERVICE`; on mismatch answers `401` |
| 2 | `JwtAuthenticationFilter` | every request | if `Authorization: Bearer` is present and valid, authenticates and sets the request attributes |
| 3 | `SecurityFilterChain` authorisation | every request | path rules below |
| 4 | `JwtAuthenticationEntryPoint` | unauthenticated | `401 {status, error, message, path}` |
| 5 | `RoleAccessDeniedHandler` | authenticated but wrong role | `403` in the standard error envelope |

Both custom filters are registered `before(UsernamePasswordAuthenticationFilter)`, so the API key
filter runs first and the JWT filter cannot overwrite its authentication — a callback request carries
no `Authorization` header in any case.

### Token validation details

* Signature and expiry are checked with `spring.gvrp.jwt.secret-key`.
* The user is loaded **by the token's username**, then the token's `userId` is compared with the loaded
  user's id. A renamed user therefore invalidates existing tokens before their expiry.
* `enabled` and `accountNonLocked` are re-checked on every request, so disabling a user is effective
  immediately (BR-008).
* The **branch id comes from the token**, not from the user row. See §5.

### Login

`POST /api/v1/auth/login` resolves the branch by name (`404` when unknown), then authenticates with a
branch-aware provider so that the same username can exist in two branches at the authentication step.
Because `users.username` is globally unique in the schema, that provider's flexibility is not
exercised by real data — see §6.

---

## 3. Authorisation rules

| Rule | Path matcher | Requirement |
|---|---|---|
| Public | `/api/v1/auth/**` | none |
| Public | `/swagger-ui/**`, `/v3/api-docs/**` | none — but `springdoc` is not a dependency, so these are 404s |
| Public | `/actuator/health` | none — actuator is not a dependency either |
| Public | `/api/v1/solutions/callbacks/**` | none at the chain level; the API key filter is the only gate |
| Driver | `/api/v1/driver/**` | `ROLE_DRIVER` |
| Planner | `/api/v1/**` | `ROLE_PLANNER` |
| Everything else | `anyRequest()` | authenticated |

Consequences of this shape:

* `CUSTOMER` can authenticate but reaches nothing: it matches neither role rule.
* A `DRIVER` cannot read any planner endpoint, including the send history of their own route; the
  driver view uses `/api/v1/driver/**` only.
* The API key principal has `ROLE_ENGINE_SERVICE`, which matches neither rule, so a callback request
  that reaches the chain without a JWT would be denied — the `permitAll` rule is what lets it through,
  and the filter has already authenticated it.
* `hasRole("PLANNER")` means authority `ROLE_PLANNER`; the role column value `PLANNER` is mapped
  accordingly by the user-details adapter.

The 401 body from the entry point is **not** the standard error envelope: it has
`status/error/message/path` and no `errors[]` array, and its `message` is the library's exception
message. Clients that parse errors uniformly must special-case it. The API key filter's 401 is a third
shape: `{error, message}` as a hand-written string.

---

## 4. Two authentication schemes, one key

| Direction | Header | Property holding the expected value |
|---|---|---|
| Engine → Entry callback | `X-API-Key` | Entry: `engine.api.key` |
| Entry → Engine request | none | — the engine has no security layer |

The engine's `entry.api-key` property holds the value it sends. So the same secret is configured in two
files under two names, and only Entry validates it. Rotating it requires changing both files and
restarting both services; a mismatch makes every callback `401`, which the engine treats as a
permanent failure and poisons the result — a rotated key does not lose results, it parks them in
`poison/` for an operator.

The engine being unauthenticated is a real exposure: anything that can reach port 8081 can submit
optimisation work, cancel other jobs, read spooled results through `/result`, and force a re-post
through `/resend`. Its endpoints are also where the heavy CPU work happens.

---

## 5. Tenant isolation

A branch is the tenant. The enforcement pattern is uniform and mechanical:

1. The branch id is taken from the token, never from the body, path or query.
2. Branch-owned rows are loaded with a branch predicate in the repository where one exists
   (`findByIdAndBranchId`, `findAllByBranchId…`), or loaded and checked
   (`findById(...).filter(found -> belongsTo(found, branchId))`).
3. Another branch's id produces **404**, not 403, so the caller cannot tell the row exists.
4. Batch operations resolve each id inside the branch and report foreign ids as failures rather than
   silently skipping them.
5. Rows that hang off a branch-owned parent are reached through the parent: a solution through its job,
   a route through its solution, a stop through its route.

Repositories with branch-scoped finders: `DepotRepository`, `VehicleRepository`, `VehicleTypeRepository`,
`FleetRepository`, `OrderRepository`, `OptimizationJobRepository`, `VehiclePermitRepository`,
`BranchZoneRepository`, `BranchZoneRuleRepository`, `ParetoCandidateRepository`, `UserRepository`.
Repositories **without** one: `SolutionRepository`, `RouteRepository`, `RouteStopRepository`,
`RouteSendRepository`, `ZoneTemplateRepository`, `ZoneRuleTemplateRepository`, `BranchRepository`. For
the first four, isolation rests on the load-then-check pattern in the service; a new query added to
those repositories has no compile-time reminder to scope it, which is how the pattern is usually
broken.

### Where isolation depends on the token rather than the row

Because `branchId` is a token claim, a user whose `branch_id` is changed in the database keeps the old
branch until the token expires (up to 24 h), and continues to read and write the old branch's data.
The user id check does not catch it, because the id does not change. Status: `PARTIALLY_VERIFIED`
(static trace of `JwtAuthenticationFilter` and `CurrentUserUtil`); no test covers it.

### Cross-tenant tests

Isolation is exercised by repository and service tests for master data, orders, jobs, solutions, routes
and sends (`FleetServiceTest`, `OrderServiceTest` and the equivalent classes named in
`04-test-design/test-cases.csv`). The category that is not uniformly covered is "every branch-owned
resource": the test design treats it as a required case per resource rather than relying on the
existing set.

---

## 6. Identity model problems visible in the schema

| Observation | Consequence |
|---|---|
| `users.username` is globally `UNIQUE` | BR-009 says a username is unique per branch. The schema is stricter, so two branches cannot reuse a username, and the branch-aware login provider has nothing to disambiguate. Contradiction between rule and schema — unresolved. |
| `users.email` and `users.slack_user_id` are globally `UNIQUE` | The same person cannot exist in two branches with the same e-mail. |
| `users.slack_user_id` is never read | Stored, unique, unused. |
| `branches.branch_webhook_url` has no writer | Slack routing is configured by direct SQL. |
| No `@Version` on any entity | Two planners editing the same solution or order produce last-write-wins; there is no optimistic locking anywhere, and only the route-send path has an explicit concurrency guard. |

---

## 7. Secrets

Secret values are configured in tracked property files and are not reproduced in this documentation.
The property names are:

| File | Property |
|---|---|
| Entry `application.properties` | `spring.datasource.password`, `spring.gvrp.jwt.secret-key`, `spring.mail.password`, `engine.api.key`, `geocoding.goong.api-key` |
| Engine `application.properties` | `entry.api-key` |
| Entry `application-local.properties` | the same database, JWT, mail and geocoding keys |

This is historical security finding SEC-001: the values are in git history, so changing them in the
working tree does not remove them from a clone. `AGENTS.md` §15 forbids history rewriting without a
user decision, and CI's gitleaks job scans only the commits of a push, not the history, which the
workflow comments acknowledge.

Handling rules the code already follows:

* Geocoding logs only the exception class, because the message can contain the URL with the key.
* The API key filter logs a warning without the value on mismatch.
* No secret is written to the database.

Handling rules the code breaks:

* `EmailService` logs the recipient's address at INFO, and the personal data in an e-mail body is not
  the only thing in the log — the address is a direct identifier (`AGENTS.md` §15 forbids it).

---

## 8. Transport and browser policy

| Aspect | As implemented |
|---|---|
| TLS | not configured in the application; no proxy, no certificate, no redirect in the repository |
| CSRF | disabled — sound, because no cookie session exists and the token is a header |
| CORS | `/api/**` from `http://localhost:*`, `http://127.0.0.1:*`, `http://192.168.*:*`; methods `*`, headers `*`, credentials allowed, preflight cached 3600 s |
| Cookies | none set by the API |
| Storage of the token | client-side `StorageService` (SSR-safe wrapper over browser storage) |
| CSP / security headers | none configured in the client or the API |

The 192.168 pattern admits any host on a typical office LAN as a credentialed origin. Combined with
TLS being absent from the repository, the practical deployment assumption is a trusted internal
network, which is not written down anywhere.

---

## 9. Gaps that a security review must treat as open

1. `POST /api/v1/auth/register` returns success and writes nothing; the method body is a comment. It is
   public. It is not an account-creation hole (nothing is created) but it is a misleading, unauthenticated
   endpoint that will look like provisioning to the next developer.
2. No server-side logout or revocation. A leaked token is valid until it expires.
3. No rate limiting on login. Passwords are BCrypt-hashed with library defaults; brute force is
   unthrottled.
4. The engine is unauthenticated and accepts cancellations, re-posts and spool reads.
5. Secrets in tracked property files (SEC-001) and inherited in the local profile.
6. CORS admits the whole private class-C range with credentials.
7. Spring Security and Spring Web loggers ship at `DEBUG`.
8. Personal data (e-mail addresses) in logs.
9. No request-size bound beyond framework defaults on the import endpoint.
10. Suspicious asymmetry between BR-009 (username unique per branch) and the global unique index.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/security/**`
(`SecurityConfig`, `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`,
`JwtAuthenticationEntryPoint`, `RoleAccessDeniedHandler`, `CustomUserDetailsService`,
`BranchAwareAuthenticationProvider`), `.../util/CurrentUserUtil.java`,
`.../config/WebConfig.java`, `.../controller/{AuthController,OptimizationCallbackController}.java`,
`.../service/SolutionService.java`, `.../repository/*.java`,
`GVRP_Entry_API/src/main/resources/application.properties`,
`GVRP_Entry_API/src/main/resources/application-local.properties`,
`GVRP_Engine_API/src/main/resources/application.properties`, `GVRP_Engine_API/pom.xml`,
`information_schema.statistics` for `gvrp_db.users` (read-only),
`docs/security/SECURITY_CHECKLIST.md` (finding SEC-001).
