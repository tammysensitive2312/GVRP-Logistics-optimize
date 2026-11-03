# Runtime validation — SEC-AUDIT-001 leads

Run by Claude Code on 2026-11-04 (+07:00) on the user's request ("chạy thật trên app để xác nhận đúng tình trạng của các phát hiện"). Local stack only: Entry API 8080 and Engine API 8081 started with `mvnw spring-boot:run` (JDK 21) from branch `audit/sec-audit-001` (source = `c0cdfd5`), MySQL container `sembackend-db-1`, client `ng serve` 4200. User decisions: scope "top 4 + easy leads"; accounts = existing branches 1 and 1000; no DB backup needed.

Authenticated calls were made from the client page with the session the user opened (planner of branch 1); no password or token was typed, printed or stored. DB reads used `agent_reader` (read-only). The audit records in `audit/` are unchanged: they remain the source-only result; this file is separate runtime evidence.

## Results

| Lead (fingerprint) | Runtime check | Observed | Status |
|---|---|---|---|
| `SEC-AUDIT-001:callback-api-key-filter-encoded-path-bypass` | `POST /api/v1/solutions/callbacks/progress` and `POST /api/v1/solutions/%63allbacks/progress`, body `{"job_id":999999999,"progress":1,"current_phase":"OPTIMIZING"}`, **no `X-API-Key`** | Plain path: **401** "Missing API key". Encoded path: **200**; Spring Security logged "Secured POST /api/v1/solutions/callbacks/progress" and the handler ran ("Failed to process progress update for job #999999999"). `%73olutions` behaves the same; `%70rogress` (last segment) is still rejected. | **CONFIRMED** |
| `SEC-AUDIT-001:callback-cross-branch-materialization` (chained with the bypass) | Unauthenticated `POST /api/v1/solutions/%63allbacks/complete` for job **70** (branch 1000, `FAILED`, no solution) with no routes and one unassigned order **#1 of branch 1** | **200**. Order #1 (branch 1): `SCHEDULED` → `UNASSIGNED`. Job 70 (branch 1000): `FAILED` → `COMPLETED`. New solution **77** (branch 1000) with unassigned row **3502** pointing at the branch-1 order. | **CONFIRMED** |
| `OrderService:bulk-update-cross-branch-order-code` | Planner of branch 1: `PATCH /api/v1/orders/status` `{"ids":[331,423,999999999],"status":"SCHEDULED"}` (331, 423 belong to branch 1000) | **200**, `updated: []`, `failed` lists `order_code` **TEST-0331** and **TEST-0423** (branch 1000) and `null` for the unknown id — cross-branch disclosure plus an existence oracle; no write. | **CONFIRMED** |
| `GVRP_Entry_client_v2.leaflet.raw-api-text-html` | Planner of branch 1 created depot **9005** named `AUDIT<img src=x onerror="window.__gvrpXss=(window.__gvrpXss||0)+1">`; reloaded `/admin/zones`; mouseover on the depot markers | Server stored the name verbatim (201). Tooltip contained the injected `<img>`; `window.__gvrpXss` went **0 → 1** (script ran). Stored XSS for every user who views that branch's maps. | **CONFIRMED** |
| `GVRP-vehicle-global-license-plate-check` | Planner of branch 1: `POST /api/v1/vehicles?fleetId=…` with a plate that exists only in branch 1000 vs a new plate | Existing foreign plate: **500** "Internal server error" (own fleet or foreign fleet). New plate + foreign fleet: **400** "Resource not found (fleets)". Distinct responses = cross-branch plate existence oracle; no vehicle created. | **CONFIRMED** (SEC-007) |
| `GVRP_Entry_API.AuthController.register.global-user-enumeration` | Anonymous `POST /api/v1/auth/register` (snake_case body, `branch_id: 1`) with a username of branch 1000 vs a new username | Existing: **400** "Username is already taken". New: **200** "User registered successfully". `users` count 4 before and after — no user created. | **CONFIRMED** (SEC-008) |
| `EngineApiController.no-auth-boundary` | No credentials to 8081: `GET /health`, `GET /{id}/progress`, `GET /{id}/result`, `POST /{id}/cancel` for an unknown job | `health` **200**; the others **404** from the handlers (no authentication layer at all). Exploitability still depends on who can reach port 8081 in deployment. | **CONFIRMED locally** (SEC-010) |

Not run (outside the agreed scope or needing paid/destructive operations): `jwt-branch-claim-current-user-binding`, `OrderImportService:import-row-amplification` (paid Goong geocoding), `EngineOptimization.unbounded-configured-work`, `JobCleanupService` delete chain, `session-reset.map-service-stale-zone-callback`, `callback-api-key-timing-comparison`, `config-literal-credential-material`, `mcp.json/dbhub/latest-execution`. They stay `needs_validation`.

## Data changed in the local database (restore SQL for the user)

```sql
UPDATE orders SET status = 'SCHEDULED' WHERE id = 1;
DELETE FROM unassigned_orders WHERE id = 3502;
DELETE FROM solutions WHERE id = 77;
UPDATE optimization_jobs SET status = 'FAILED', completed_at = NULL WHERE id = 70;
DELETE FROM depots WHERE id = 9005;
```

The register probes and the vehicle/bulk-update probes changed no data.

## Severity proposal (for the checklist; user decision)

| Proposed ID | Finding | Proposed severity |
|---|---|---|
| SEC-012 | Engine callback API key skipped via encoded callback path | **Critical** — unauthenticated, reaches state-changing callbacks |
| SEC-013 | Completion callback writes orders/vehicles of other branches (exploitable by anyone through SEC-012) | **Critical** in chain / High alone |
| SEC-014 | Bulk order status discloses other branches' order codes and existence | Medium |
| SEC-015 | Stored XSS in Leaflet tooltips/popups (depot, order, stop, zone names), no CSP | High |
| SEC-007 / SEC-008 / SEC-010 | Now runtime-confirmed | keep, add evidence |
