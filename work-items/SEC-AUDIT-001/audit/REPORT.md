# Security Audit Report — GVRP Logistics Optimize (SEC-AUDIT-001, run-2)

## 1. Run

- Skill: Cloudflare `security-audit` (commit c1c8a8c), full audit mode, profile **standard**, no budget set.
- Source ref: `[object Object]` (branch `audit/sec-audit-001`, clean apart from the work item).
- Scope: `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `.github`, root config (`.gitignore`, `.mcp.json`, `.cbmignore`). Out of scope: legacy `GVRP_Entry_client`, the rest of the repository, `work-items/DRIVER-MAP-004` (SEC-005).
- Execution: **source only**. No target build, test, process, browser, network or database access. No result in this report was observed at runtime.
- Prior runs: `run-1` (OpenCode) discarded — stopped in Phase 5 with broken write isolation; none of its coverage or findings is used.
- Agents: 4 reconnaissance, 21 hunters, 4 coverage-critic waves (Codex as parent); 19 Phase 3 verifiers (one per candidate plus one for a root cause raised during Phase 3) and 3 Phase 5 record verifiers covering 15 records (Claude Code as parent after Codex ran out of quota). No verifier hunted or earlier validated the record it checked.
- Status: **complete**. Both skill validators pass (run in `node:22-alpine`, `--network none`).

## 2. Posture summary

No finding could be **confirmed**: every lead needs a runtime or deployment fact that a source-only audit cannot observe. The most serious leads are on the Engine→Entry callback boundary (an encoded callback path that may skip the API key, and callbacks that write orders and vehicles of any branch) and on unscoped order lookups that disclose another branch's order codes. Credentials committed in configuration (SEC-001) make every Engine-side lead easier to exploit. Several earlier findings are confirmed still present in source (SEC-001, 004, 007, 008, 010).

## 3. Confirmed findings

None. A source-only run cannot supply the bounded observed result the skill requires for `confirmed`.

## 4. Needs validation (15, priority order, no severity)

| # | Lead | Entry point | Blocker |
|---|---|---|---|
| 1 | Engine callback API-key check can be skipped with a percent-encoded unreserved character in the callback path (`SEC-AUDIT-001:callback-api-key-filter-encoded-path-bypass`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/config/SecurityConfig.java:78` | Source-only audit: no server, MockMvc or embedded Tomcat run was allowed, so the decisive behavior (Tomcat decoding %63 into servletPath, firewall acceptance, MVC dispatch to the callback handler and a handler response without X-API-Key) was not observed. Cross-branch order effect in saveSolution (f |
| 2 | Engine completion callback materializes and re-statuses orders and vehicles of other branches into a job's solution (`SEC-AUDIT-001:callback-cross-branch-materialization`) | `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/controller/OptimizationController.java:43` | Source-only policy: no two-branch dummy run was performed, so persistence of foreign orders/vehicles into another branch's solution and the status change were not observed. Reachability of the lower-trust source is a deployment fact: whether any party other than the Entry host can reach Engine port  |
| 3 | Bulk order status update discloses another branch's order codes in the failed-row response (`OrderService:bulk-update-cross-branch-order-code`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/OrderController.java:122` | Source-only policy: no request against a running Entry API was made, so the serialized HTTP response containing a foreign order_code was not observed (Jackson serialization of the Lombok getter is inferred from source, not observed). Whether order IDs are sequential and guessable in a real database  |
| 4 | Stored business text (depot, order, stop, zone names) is interpolated unescaped into Leaflet popup/tooltip HTML strings (`GVRP_Entry_client_v2.leaflet.raw-api-text-html`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/dto/request/OrderInputDTO.java:34` | Source-only policy forbids running the client in a browser, so rendering of a harmless synthetic marker string through Leaflet popups/tooltips was not observed. Server persistence and JSON response path were not executed; no source-visible escaping was found, but round-trip of markup characters thro |
| 5 | Tracked Entry/Engine application.properties contain literal JWT signing key, service API key, DB, mail and geocoding credentials (`SEC-AUDIT-001:config-literal-credential-material`) | `GVRP_Entry_API/src/main/resources/application.properties:44` | Source-only policy: whether the committed values are currently valid (DB, Gmail app password, Goong API key), whether any deployed environment uses them or overrides them via environment variables, and whether they are reused elsewhere cannot be established without credential testing or deployment i |
| 6 | GVRP Engine API exposes optimize/progress/cancel/result/resend/evaluate/legs with no caller authentication or job ownership check (`EngineApiController.no-auth-boundary`) | `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/controller/OptimizationController.java:43` | Source does not establish who can reach the Engine listener on port 8081: no server.address, firewall, private network, gateway or service-mesh configuration exists in the repository, and deployment topology was not inspected under the source-only policy. Cross-tenant integrity impact via a forged o |
| 7 | Engine optimization accepts caller-chosen solver iterations and thread count with no upper bound, and ignores timeoutSeconds (`EngineOptimization.unbounded-configured-work`) | `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/controller/OptimizationController.java:43` | Reachability: whether any host other than the Entry API can reach the engine port 8081 depends on firewall, private networking or ingress controls that are not in the repository (SEC-010 accepted posture). Executor behavior: whether Spring Boot 4.0.0 with spring.threads.virtual.enabled=true actually |
| 8 | Delayed branch-zone response from a prior session can be drawn on the next principal's map (`GVRP_Entry_client_v2.session-reset.map-service-stale-zone-callback`) | `GVRP_Entry_client_v2/src/app/shared/components/map/map.component.ts:54` | Source-only policy prohibits browser execution. It is not established that a listZones() or route-geometry response can stay in flight across logout, login of a second principal, and creation of the new <app-map>. This needs a delayed response in a real Angular/Leaflet runtime. Whether the stale lay |
| 9 | JWT branchId claim is trusted for tenant scope without reconciling it with the reloaded user's current branch (`SEC-AUDIT-001:jwt-branch-claim-current-user-binding`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/security/jwt/JwtAuthenticationFilter.java:44` | Source contains no in-application branch reassignment path; whether operators reassign existing users between branches out-of-band (DB/admin tooling) while tokens are live is a deployment/process fact not visible in source. Token lifetime (spring.gvrp.jwt.expiration-ms) lives in application properti |
| 10 | Order import has no row-count bound and fans out one synchronous paid geocoding call per address-only row (`OrderImportService:import-row-amplification`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/OrderController.java:33` | Effective deployed multipart/request size limits (Spring Boot defaults vs. overrides via environment/profile, Tomcat maxPostSize/maxPartCount, any reverse proxy body cap) are outside the repository; they set the achievable row count per request. Goong geocoding pricing, per-key quota, rate limits an |
| 11 | Vehicle create uses a global (cross-branch) license-plate existence check that acts as a cross-tenant existence oracle and namespace collision (`GVRP-vehicle-global-license-plate-check`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/VehicleController.java:31` | Source-only policy: the actual HTTP status/body for the duplicate case (expected 500 via the catch-all handler versus 400 for the non-duplicate case) was not observed, and no request was made against a running Entry API. Whether globally unique license plates are an accepted product requirement (phy |
| 12 | Anonymous register endpoint is a global username/email existence oracle across branches (`GVRP_Entry_API.AuthController.register.global-user-enumeration`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/config/SecurityConfig.java:75` | No documented requirement establishes that username, email or account existence is confidential across branches; global uniqueness may be an accepted design, which decides whether this is a vulnerability or a low-severity hardening issue. Source-only policy forbids running the Entry API to observe t |
| 13 | Engine callback shared API key compared with non-constant-time String.equals (`callback-api-key-timing-comparison`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/config/SecurityConfig.java:78` | Whether the String.equals timing difference (length check plus JIT-intrinsified chunked byte comparison) is statistically observable over the network path to Entry cannot be determined from source; requires a timing measurement that this source-only run forbids. Deployment reachability of Entry's ca |
| 14 | Retention sweep deletes only direct revisions, so a nested revision chain from any branch can stall global job cleanup (`EntryAPI.JobCleanupService:incomplete-revision-descendant-delete-chain`) | `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/SolutionController.java:59` | Source-only policy forbids a local MySQL plus Hibernate run, so these are not observed: when the FK violation is raised (at deleteAll or at flush), whether it propagates out of scheduledCleanup, and whether earlier route_sends deletes are committed (no transaction because of self-invocation) or roll |
| 15 | Tracked MCP configs launch an unpinned @bytebase/dbhub@latest via npx with the developer's authority and DB credential environment (`mcp.json/dbhub/latest-execution`) | `.mcp.json:9` | Source cannot establish whether Claude Code and OpenCode on the developer machines start this project-scoped server automatically or only after an explicit per-server trust approval (host version and user/managed settings such as enableAllProjectMcpServers are outside the repository). Effective npm  |

Details, traces and validation plans: `NEEDS-VALIDATION.md`.

## 5. Rejected candidates (4)

- `GVRP-Entry-RouteSend-Driver-BranchTransfer-History` — No lower-trust principal can reach the precondition: the application has no branch-transfer or user-administration path, so moving a driver between branches is an out-of-band operator database edit. The only data exposed is the driver's own sends, which were l
- `GVRP-Entry-Slack-BranchWebhook-Unvalidated-Destination` — No realistic lower-trust principal can set the destination: branchWebhookURL has no application writer (no endpoint, DTO mapping, setter call or seed code in source), and the documented writer is a trusted operator using direct database SQL, who already holds 
- `GVRP_Entry_API:lez:geometry:preparse-resource-bound` — The candidate requires an input-to-cost path with amplification or shared impact. Source shows the cost is linear in bytes supplied by an authenticated PLANNER, Jackson default structural constraints remain in force, and all parser and JTS work after materiali
- `callback-retry-metadata-write-failure-bypasses-attempt-cap` — The code path exists, but it is not a security vulnerability. The trigger is a persistent local filesystem fault on Engine-owned spool metadata (disk, permissions or corruption), and no lower-trust principal (anonymous user, planner, driver or callback peer) c

## 6. Known findings SEC-001…SEC-011

| ID | Status in audited source | Evidence |
|---|---|---|
| SEC-001 | Still present | `SEC-AUDIT-001:config-literal-credential-material`: tracked Entry/Engine `application.properties` hold literal credential values (JWT HMAC key, DB, mail, geocoding, service API key); names only. |
| SEC-002 | No regression found | Solution read paths reviewed under the SolutionController / SolutionService units; no unscoped solution lookup was retained as a candidate. |
| SEC-003 | No regression found | Master-data units (Depot, Fleet, VehicleType, ZoneTemplate) covered with no candidate. |
| SEC-004 | Still present, extended | `callback-api-key-timing-comparison` (String.equals). New related lead `callback-api-key-filter-encoded-path-bypass`: raw-URI prefix check may let an encoded callback path skip the key. |
| SEC-005 | Not assessed | `work-items/DRIVER-MAP-004` is outside the declared scope (ledger `out_of_scope`). |
| SEC-006 | No regression found | `SecurityConfig` keeps DRIVER on `/api/v1/driver/**` and PLANNER on `/api/v1/**`. |
| SEC-007 | Still present | `GVRP-vehicle-global-license-plate-check`. |
| SEC-008 | Still present, changed | `GVRP_Entry_API.AuthController.register.global-user-enumeration`: register still creates no user and now also discloses username/email existence across branches. |
| SEC-009 | Fixed in audited source | JWT filter reloads the user. Residual lead `jwt-branch-claim-current-user-binding`: branch comes from the token claim, not the reloaded user. |
| SEC-010 | Still present | `EngineApiController.no-auth-boundary`, `EngineOptimization.unbounded-configured-work`; also the lower-trust source for `callback-cross-branch-materialization`. |
| SEC-011 | Fixed in audited source, residual lead | `GVRP_Entry_client_v2.session-reset.map-service-stale-zone-callback`: `MapService` is not reset by `SessionResetService`. |

## 7. Hardening notes

- Action refs use mutable major tags; pinning reviewed SHAs is defense in depth. Maven wrapper distribution has no visible checksum.
- The callback sender spools before delivery and Entry completion is idempotent by job ID; source review found retry scheduling limited to pending payloads.
- Failed stores may leave .tmp files outside this sweep; attacker control and shared-service impact were not established.
- No aggregate spool byte/file-count cap is visible in this unit; attacker-controlled throughput or result sizes were not established.
- Consider replacing broad local/private-network CORS patterns with explicitly required deployment origins. Deployment reachability and control of matching origins were not verified; ApiKeyAuthenticationFilter was outside this unit source scope.
- The public registration route returns success without creating an account; a claim that it provisions an arbitrary-branch user is unsupported by source.
- Consider explicit request-size and per-import row-count bounds and bounded processing.
- Callback secret provisioning and deployed network reachability are not established by this source-only review.
- The cleanup cutoff uses job createdAt; source does not establish whether retention is intended to start at creation or terminal transition.
- Callback error text is emailed to the job creator; source does not establish that it contains protected data or reaches an unauthorized recipient.
- Planner dispatch checks target-driver branch membership; that check does not enforce current-branch ownership on the driver detail path.
- Validate branch webhook destinations against an explicit HTTPS/host policy and enforce outbound network restrictions; source passes the stored URL directly to the HTTP client.

## 8. Coverage

Ledger units: 47 — blocked 1, candidate 29, covered 13, not_applicable 1, out_of_scope 3. The `blocked` unit is the CI pull-request workflow (runner token and untrusted code cannot be settled from source). The final coverage critic (wave 4) found no further unit to add. One run is not exhaustive; repeated runs usually find more.
