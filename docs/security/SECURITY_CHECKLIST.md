# Security Checklist and Open Findings

Project rules: `AGENTS.md` section 14 (Test Integrity) and section 15 (Security and Tenant Isolation).

Tenancy model: each `Branch` is a separate company. Cross-branch data exposure is the highest-severity defect class.

Never write secret values into this file. Name the file and property only.

---

## 1. Open findings

| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| SEC-001 | Critical | Credentials committed to git. `GVRP_Entry_API/src/main/resources/application.properties` holds literal values for `spring.datasource.password`, `spring.gvrp.jwt.secret-key`, `spring.mail.password`, `engine.api.key`, `geocoding.goong.api-key`. `GVRP_Engine_API/src/main/resources/application.properties` holds `entry.api-key`. Remote: GitHub. | File present in git index (checked 2026-10-16); values are literals, not `${...}` placeholders. Impact: with the JWT key anyone can mint a token for any branch, which bypasses every tenant check. | OPEN — rotation deferred by user decision 2026-10-16; values still unchanged in local files on that date. Must be done before Gate B. |
| SEC-002 | High | Possible cross-branch access to solutions (IDOR). `SolutionController` endpoints `GET /{id}`, `GET /{id}/latest-revision`, `GET /{id}/revision-chain`, `POST /{id}/evaluate`, `POST /{id}/revisions` load the solution by ID only (`SolutionRepository.findWithDetailsById`, `findById`) and do not compare its branch with the caller's branch. No tenant filter, aspect or interceptor was found. | Static inspection of `SolutionController`, `SolutionService`, `RouteCustomizationService`, `SolutionRepository` (2026-10-16). | FIXED on branch `fix/sec-tenant-001` (`work-items/SEC-TENANT-001`, done 2026-10-16; unit tests + manual cross-branch test) |
| SEC-003 | High | Audit of all Entry API controllers (2026-10-16) found the same pattern in vehicle create/update, fleet create (foreign depots/types) and route send history. | `work-items/SEC-TENANT-001/context.md` | FIXED on branch `fix/sec-tenant-001` (`work-items/SEC-TENANT-001`) |
| SEC-004 | Low | Engine callback API key is compared with `String.equals` (not constant-time) in `ApiKeyAuthenticationFilter`. The callback path is `permitAll` in `SecurityConfig` but is protected by this filter. | Static inspection. | OPEN |
| SEC-005 | Low | `work-items/DRIVER-MAP-004/request.md` contains local test-account credentials, now tracked in git. | File content (2026-10-16). | OPEN: change those test passwords; never reuse them |
| SEC-006 | Medium | No role checks outside `RouteSendService`: a DRIVER token can call planner endpoints of its own branch (orders with customer data, vehicles, solutions). Conflicts with the intent of DRIVER-DELIVERY-001 DD-022. | Static inspection of all controllers (2026-10-16). | FIXED on branch `fix/sec-role-001` (`work-items/SEC-ROLE-001`, done 2026-10-16; policy: DRIVER only driver API, CUSTOMER blocked) |
| SEC-007 | Low | `VehicleService.createVehicle` checks license-plate uniqueness globally, revealing whether a plate exists in another branch. | Static inspection. | OPEN Runtime-confirmed 2026-11-04 on the local stack (`work-items/SEC-AUDIT-001/runtime-validation.md`). Existing foreign plate → 500, new plate → 400. |
| SEC-008 | Low | `POST /api/v1/auth/register` (public) returns "User registered successfully" without creating a user. | Static inspection of `AuthController`. | OPEN Runtime-confirmed 2026-11-04 on the local stack (`work-items/SEC-AUDIT-001/runtime-validation.md`). Also a cross-branch username/email existence oracle (400 "Username is already taken" vs 200). |
| SEC-009 | Medium | `JwtAuthenticationFilter` does not check `UserDetails.isEnabled()`: a disabled user keeps API access until the token expires (24 h). | Static inspection (2026-10-16). | FIXED on branch `fix/sec-session-001` (`work-items/SEC-SESSION-001`, done 2026-10-17) |
| SEC-010 | Medium | Engine API has no request authentication and no rate limit. Every `GVRP_Engine_API` endpoint (`/api/v1/optimization/**`: submit, evaluate, progress, and since LEZ-001 P1b the synchronous `POST /api/v1/optimization/legs`, up to 1,000 routing queries per request) answers anyone who can reach its port, so routing and solver capacity can be consumed by any host on the network. SEC-004 covers only the key check on the Entry callback path, not the engine's inbound API. | Static inspection 2026-10-27: no Spring Security dependency use, no authentication or key filter class under `GVRP_Engine_API/src/main/java`; `work-items/LEZ-001/implementation.md` §P1b Security, `review-p1b.md` §6. | OPEN — accepted posture until Gate B: the engine must be reachable only from the Entry API host (firewall or private network). Remediation: a shared service-key filter on inbound engine calls (constant-time compare, key from the environment, see SEC-001/SEC-004) or mTLS, plus a request-size and rate limit on `/legs`. Runtime-confirmed 2026-11-04 on the local stack (`work-items/SEC-AUDIT-001/runtime-validation.md`). All Engine endpoints reach their handlers without credentials; see also SEC-012/013. |
| SEC-011 | High | Client-side cross-branch exposure in `GVRP_Entry_client_v2`. Root singleton stores (`SolutionStore`, `DepotStore`, `VehicleTypeStore`, `StateService`, `VehicleSelectionService`, `JobPollingService`) outlived logout and login: the navbar logout cleared only the stored auth/app-state keys, and login reset nothing. A planner of branch A who loaded solution 92, then signed in as a planner of branch B in the same tab without reload, still saw solution 92. The server was not involved: `SolutionService.getSolutionDetail` filters by the JWT branch (SEC-002). | Reported by the user 2026-11-01; code inspection of `auth.service.ts`, `navbar.component.ts`, `auth.interceptor.ts`, `solution.store.ts`. | FIXED on `master` 2026-11-01: central `SessionResetService.resetBranchState()` called on every logout path, on 401 and on every login; stores ignore responses of requests started before a reset; explicit logout keeps no `lastUrl`. Specs: `core/services/session-reset.spec.ts`, `navbar/navbar-logout.spec.ts`. |
| SEC-012 | Critical | `ApiKeyAuthenticationFilter` decides whether to require the Engine callback key with `request.getRequestURI().startsWith("/api/v1/solutions/callbacks/")` on the raw URI, while `SecurityConfig` permits `/api/v1/solutions/callbacks/**` and Spring decodes the path. A percent-encoded path such as `/api/v1/solutions/%63allbacks/complete` skips the key check and reaches every callback handler unauthenticated. | SEC-AUDIT-001: source trace (Spring Security 6.5.6 / Framework 6.2.12) and runtime 2026-11-04: plain path 401, encoded path 200 with the handler executed (`work-items/SEC-AUDIT-001/runtime-validation.md`). | OPEN — fix item `work-items/SEC-CALLBACK-001` |
| SEC-013 | Critical (with SEC-012) / High | `OptimizationCallbackService.saveSolution` resolves order and vehicle IDs from the callback with unscoped `findAllById` and changes order status without checking the job's branch, so a callback for a job of branch A writes and re-statuses orders and vehicles of branch B. Through SEC-012 (or SEC-010 / SEC-001) no credential is needed. | SEC-AUDIT-001 runtime 2026-11-04: unauthenticated forged completion for job 70 (branch 1000) moved order #1 of branch 1 to `UNASSIGNED` and created solution 77 referencing it. | OPEN — fix item `work-items/SEC-CALLBACK-001` |
| SEC-014 | Medium | `OrderService.bulkUpdateStatus` (`PATCH /api/v1/orders/status`) loads ids with unscoped `findAllById` and returns the foreign order's `order_code` in `failed[]`; `null` vs a code also reveals whether an id exists in any branch. | SEC-AUDIT-001 runtime 2026-11-04: planner of branch 1 received branch-1000 codes `TEST-0331`, `TEST-0423`; no write. | OPEN — fix item `work-items/SEC-ORDERS-001` |
| SEC-015 | High | Stored XSS in `GVRP_Entry_client_v2`: depot, order, stop and zone names stored verbatim by the API are interpolated unescaped into Leaflet `bindTooltip` / `bindPopup` HTML strings (`zone-map.component.ts`, `marker.service.ts`, map and driver views); Leaflet assigns them with `innerHTML`; `index.html` has no CSP. Any planner can run script in the browser of every user who views that branch's maps. | SEC-AUDIT-001 runtime 2026-11-04: depot 9005 named with an `<img onerror>` payload; hovering it on `/admin/zones` executed the handler. | OPEN — fix item `work-items/SEC-XSS-001` |

### Required actions for SEC-001 (user decision)

1. Rotate every listed credential: Gmail app password, JWT secret, engine/entry API key, Goong API key, DB password. Removing the file does not remove values from git history.
2. Replace the literals with environment placeholders, for example `spring.gvrp.jwt.secret-key=${GVRP_JWT_SECRET}`, and keep local values in a gitignored file or environment variables.
3. Optional and destructive: rewrite history with `git filter-repo` and force-push. Rotation is still required either way.
4. Enable GitHub secret scanning and push protection if available for the repository.

### Actions taken for SEC-002 and SEC-003

Done in `work-items/SEC-TENANT-001` (see `closure.md` for coverage and limitations). Original plan:

* Add branch-scoped lookups (for example `findWithDetailsByIdAndBranchId`).
* Take the branch from the authenticated principal (`CurrentUserUtil.getCurrentBranchId()`).
* Add tests proving that branch A gets not-found for branch B's solution on every endpoint listed above.
* Audit every controller under `GVRP_Entry_API/.../controller` for the same pattern.

---

## 2. Stage gates

### Gate A: every change (enforced by CI and review)

- [ ] CI green: build, unit tests, gitleaks
- [ ] No existing test modified, disabled or excluded without recorded user approval (AGENTS.md section 14)
- [ ] New endpoints authenticated; no unjustified `permitAll`
- [ ] Branch-owned data scoped to the principal's branch, with a cross-branch test
- [ ] No secrets, tokens or personal data in code, logs or evidence
- [ ] New dependencies named and verified

### Gate B: before any deployment, including staging

- [ ] SEC-001 resolved: credentials rotated and loaded from the environment
- [ ] SEC-002 and SEC-003 fixed, with cross-branch tests for every controller
- [ ] `logging.level.org.springframework.security` and `org.springframework.web` not at `DEBUG`
- [ ] HTTPS everywhere; CORS restricted to known origins
- [ ] Database user for the application has only the privileges it needs; `ddl-auto=update` replaced by migrations
- [ ] Request size limits on bulk upload and optimization inputs; solver time limit verified
- [ ] Rate limiting on login and optimization submission
- [ ] Dependabot alerts reviewed; no known critical vulnerabilities
- [ ] OWASP ASVS Level 1 checklist walked through once

### Gate C: before real users or real customer data

- [ ] Data inventory: which personal data is stored (customer name, phone, address, coordinates, driver data), where, who can read it, retention
- [ ] Legal review against Vietnam's personal data protection rules (Decree 13/2023/ND-CP and the Personal Data Protection Law; confirm current status with a qualified person)
- [ ] Tests and demos use synthetic data only
- [ ] Backup and restore tested; encryption at rest for the database
- [ ] External penetration test, if budget allows

---

## 3. Automated checks

| Check | Where | Notes |
|---|---|---|
| Build and unit tests | `.github/workflows/ci.yml` | Tests needing MySQL or the GraphHopper graph are excluded in CI; see the workflow comments. |
| Secret scanning | `.github/workflows/ci.yml` (gitleaks) | Scans pushed commits. Existing history already contains SEC-001. |
| Dependency updates | `.github/dependabot.yml` | Maven (both APIs), npm (client v2), GitHub Actions. |
| Static security analysis | Not yet configured | Candidate: Semgrep or CodeQL. |
