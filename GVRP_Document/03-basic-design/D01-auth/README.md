# D01 — Authentication and session

Who may enter, how identity is established, and what the client does with the answer.

Screens: `/login` only. Everything else is gated by the session this domain creates.
Diagram: [sequence-login.svg](diagrams/sequence-login.svg) ([source](diagrams/sequence-login.puml)),
[class-auth.svg](diagrams/class-auth.svg) ([source](diagrams/class-auth.puml)).

Use cases: UC-D01-01 (log in to a branch), UC-D01-02 (register a user — stub), UC-D01-03 (role-based
access), UC-D01-04 (a disabled user's access is revoked). Requirement sources: BR-001…BR-010
(`01-requirements/03-business-rules.md`), NFR-SEC-01…05.

---

## 1. Screens

| Route | Component | Guard | Purpose |
|---|---|---|---|
| `/login` | `LoginComponent` | none | branch name + username + password + "remember me" |
| every other route | — | `AuthGuard` | redirects to `/login?returnUrl=…` and remembers the attempted URL |
| `/driver`, `/driver/**` | `DriverViewComponent` | `DriverGuard` | `DRIVER` only; an authenticated `PLANNER` is sent to `/main`, an anonymous user to `/login` |
| `/main` | dashboard | `setupGuard` | if the branch has no depot, no vehicle type or no vehicle, the guard redirects into `/setup/…` instead |

The setup guard is not an authentication check: it runs after `AuthGuard`, calls three read endpoints, and
**lets the navigation through when the call fails** rather than trapping the user in a redirect loop.

### Screen flow

```
anonymous ──open any guarded route──> AuthGuard ──> /login?returnUrl=<url>
                                                        │
                    login ok ───────────────────────────┘
                        │
                        ├── role DRIVER, target not /driver ──> /driver
                        ├── stored lastUrl (deep link, guard, 401) ──> that URL
                        └── otherwise ──> /main   (then setupGuard may redirect to /setup/*)
```

`lastUrl` is written in three places — the auth guard, the driver guard and the 401 interceptor — and is
consumed once by `getRedirectUrl()`, which clears it. `returnUrl` in the query string is display-only: the
login component reads `lastUrl` from storage, not the parameter.

---

## 2. API

Two endpoints. `/api/v1/auth/**` is `permitAll`.

| Method | Path | Request | Response | Notes |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | `LoginRequest` | `200 LoginResponse` | the only way to obtain a token |
| POST | `/api/v1/auth/register` | `RegisterRequest` | `200` `"User registered successfully"` | **stub** — nothing is created |

### `POST /auth/login`

```json
{ "branch_name": "…", "username": "…", "password": "…" }
```

All three are `@NotBlank`, so an empty field is `400` + `0040001` with the field name.

Response:

```json
{
  "access_token": "eyJ…",
  "user_id": 42,
  "username": "planner1",
  "branch_id": 7,
  "branch_name": "…",
  "role": "PLANNER"
}
```

The token is signed with `spring.gvrp.jwt.secret-key` and carries `userId`, `branchId` and `role` as claims;
lifetime is `spring.gvrp.jwt.expiration-ms` = 24 h. Claims are taken from the **user row**, not from the
request, so they cannot be chosen by the client.

Steps, in order:

1. resolve the branch by name (`branchRepository.findByName`);
2. authenticate with `BranchUsernamePasswordAuthenticationToken` through a `ProviderManager` holding a
   `DaoAuthenticationProvider` and a `BranchAwareAuthenticationProvider`;
3. re-read the user by `(username, branchId)` — the branch that authenticated, not the one in the token;
4. issue the token and return the response above.

### `POST /auth/register`

| Condition | Answer |
|---|---|
| username already exists | `400` with the **plain-text** body `Username is already taken` |
| e-mail already exists | `400` with `Email is already in use` |
| otherwise | `200` with `User registered successfully`, **no row written** |

Two properties matter. The failure bodies are not the error envelope, so a client parsing `errors[]` sees an
unparseable 400. And the success path is a comment (`// ... implementation`): the endpoint is public and
reports success without persisting anything. It is documented as a stub, not as provisioning. No screen
calls it — `/login` has no registration form.

`branchId` in `RegisterRequest` is accepted and ignored; nothing reads it.

---

## 3. Errors

| Situation | Status | Body |
|---|---|---|
| unknown branch name | **400** | `0040402`, `resource: branches` — *verified at runtime* |
| blank field | 400 | `0040001`, one detail per field |
| wrong password / disabled / locked account | **500** | `0050001` — *static derivation, `PARTIALLY_VERIFIED`* |
| user row missing after a successful authentication | 400 | `0040402`, `resource: users` |
| `register` duplicate username or e-mail | 400 | plain text, not the envelope |
| any request without a token | 401 | entry-point body `{path, error, message, status}` |

The wrong-password case is the important one. `AuthController` never converts `AuthenticationException` into
a detail, and `GlobalExceptionHandler` has no handler for it, so `@ExceptionHandler(Exception.class)` answers
`500`. `ErrorCode.INCORRECT_LOGIN_INFORMATION` (`0040101`) exists and is unreachable: the only branch that
could use it requires an authenticated principal, which is impossible when authentication just failed.

Consequence for the client: a mistyped password is indistinguishable from a server fault. The login screen
shows `error.message` from the response, which for a 500 is the fixed string *Internal server error.* — the
user is never told the password was wrong.

---

## 4. Session on the client

| Concern | Implementation |
|---|---|
| Storage | `rememberMe` checked → `localStorage`; unchecked → `sessionStorage` |
| Shape | `{token, user: {…}, rememberMe, timestamp}` kept as one `auth_session` item |
| Expiry | client-side cap: 7 days with remember-me, 24 h without; the token itself always expires at 24 h |
| Attach to requests | `authInterceptor` adds `Authorization: Bearer <token>` to every request to `apiUrl` |
| 401 handling | the interceptor clears the session, stores the current URL as `lastUrl`, navigates to `/login` |
| Logout | `AuthService.logout()` POSTs `/api/v1/auth/logout`, then clears the session — **on failure too** |

Two defects follow from that table:

1. **`/api/v1/auth/logout` does not exist.** `AuthController` has two mappings and neither is `logout`. The
   request reaches a path with no handler, so the generic exception handler answers `500`. Because
   `logout()` clears the session in a `catchError` as well as in `tap`, the user is logged out locally
   anyway and the failure surfaces only as an error notification. There is no server-side revocation of any
   kind: the token stays valid until it expires, so a copied token survives logout. Status
   `PARTIALLY_VERIFIED` — the missing mapping is verified in the source, the resulting status is inferred
   from the documented behaviour of unmapped paths (`/actuator/health` was observed answering `500`).
2. **The "session expired" experience is silent.** The interceptor redirects on any 401, including one caused
   by a role mismatch or by a callback filter, without distinguishing the causes.

---

## 5. Server-side session checks

There is no session object. Each request re-establishes identity from the token:

| Step | Behaviour |
|---|---|
| signature + expiry | verified by `JwtTokenProvider` |
| user reload | `CustomUserDetailsService.loadUserByUsername(token.username)` |
| id check | token `userId` must equal the loaded user's id, otherwise the token is discarded |
| account state | `enabled`, `accountNonLocked` re-checked on every request |
| branch | taken from the **token claim**, not from the user row — see `02-system-design/04-security-and-tenancy.md` §5 |

A disabled user is therefore cut off at the next request. A user whose branch was changed in the database
keeps the old branch until the token expires.

---

## 6. Data touched

| Table | Operation |
|---|---|
| `branches` | read by `name` |
| `users` | read by `(username, branch_id)` at login; read by `username` on every authenticated request; `exists` checks in the stub |

Nothing is written by this domain. `users.last_login` does not exist, so login leaves no trace beyond
application logs.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/AuthController.java`,
`.../security/{SecurityConfig,JwtAuthenticationFilter,ApiKeyAuthenticationFilter,JwtAuthenticationEntryPoint,RoleAccessDeniedHandler,CustomUserDetailsService,BranchAwareAuthenticationProvider,BranchUsernamePasswordAuthenticationToken}.java`,
`.../security/jwt/JwtTokenProvider.java`, `.../util/CurrentUserUtil.java`,
`.../exception/GlobalExceptionHandler.java`, `.../util/ErrorCode.java`,
`.../dto/request/{LoginRequest,RegisterRequest}.java`, `.../dto/response/{LoginResponse}.java`,
`.../repository/{UserRepository,BranchRepository}.java`,
`GVRP_Entry_API/src/main/resources/application.properties` (JWT secret and lifetime keys),
`GVRP_Entry_client_v2/src/app/features/auth/login/login.component.{ts,html}`,
`GVRP_Entry_client_v2/src/app/core/services/{auth,storage}.service.ts`,
`GVRP_Entry_client_v2/src/app/core/guards/{auth,driver,setup}.guard.ts`,
`GVRP_Entry_client_v2/src/app/core/interceptors/auth.interceptor.ts`,
`GVRP_Entry_client_v2/src/app/app.routes.ts`,
runtime probes of 2026-10-30 recorded in `03-basic-design/common/README.md` §10.
