# System Test Plan

* **Document ID**: `STP-GVRP-01`
* **Baseline**: `master @ cebf7f3` (post PR #43–#48: `SEC-017..019`, `R-1..R-6`, `E-1..E-7`, `Q-1..Q-6`)
* **Date**: `2026-11-23`
* **Sources of truth**: [system-test-cases.csv](system-test-cases.csv) (`ST-*` system cases) and [test-cases.csv](test-cases.csv) (`TC-*` unit/web/client/manual cases), linked to [02-use-case-catalogue.md](../01-requirements/02-use-case-catalogue.md).

---

## 1. Purpose and Scope

This document defines the end-to-end **System Test Plan** for the **GVRP Logistics Optimize** platform as an integrated local deployment. While existing `UNIT`, `WEB`, and `CLIENT` test suites verify isolated components with Mockito or `HttpTestingController` stubs, there is currently no automated end-to-end suite, cross-service contract test, or live-browser verification suite covering the running stack (`01-test-strategy.md`).

### 1.1 In-Scope Components
| Component | Runtime Role | Default Port / Target | Notes |
|---|---|---|---|
| `GVRP_Entry_API` | REST gateway, auth/JWT, RBAC, tenancy, persistence, job orchestration, LEZ governance | `:8080` (`/api/v1/**`) | 17 REST controllers; Spring Boot + Spring Security |
| `GVRP_Engine_API` | GraphHopper + jsprit VRP solver, Pareto analysis, route evaluation, leg routing, result spool | `:8081` (`/api/v1/optimization/**`) | `ServiceKeyFilter` (`X-Engine-Key`) + `CallbackService` (`X-API-Key`) |
| MySQL 8 | Relational persistence + JTS spatial columns + sequence tables | `:3306` (`gvrp_test_db` primary / `gvrp_db` fallback) | `ddl-auto=validate`; 19 JPA tables + 3 `*_sequence` tables |
| `GVRP_Entry_client_v2` | Angular v2 planner/admin/driver web client | `:4200` (proxies `/api` → `:8080`) | Production UI under test |

### 1.2 Out-of-Scope
* `GVRP_Entry_client` (legacy client) — excluded from active testing.
* `GVRP_Benchmark` and long-running solver convergence benchmarks — excluded here (`docs/research/`).
* Scientific/mathematical validation of GVRP emission models, Solomon anchors, and objective calibrations — governed separately by `docs/research/METHODOLOGY.md` and `docs/research/EXPERIMENT_PROTOCOL.md`.
* Production deployment environments and production customer data (`AGENTS.md` §15).

---

## 2. System Architecture Under Test and Known Risks

### 2.1 Trust, Auth, and Transport Boundaries
1. **Browser → `GVRP_Entry_API`**:
   * `POST /api/v1/auth/login` issues a 24-hour JWT (`spring.gvrp.jwt.expiration-ms=86400000`).
   * `JwtAuthenticationFilter` validates claims once per request (`R-5`), reloads the user, enforces `isEnabled()`, `isAccountNonLocked()`, `userId`, `branchId`, and `role` (`R-4`/`R-6`), and scopes tenant queries strictly to the reloaded principal's `branchId`.
   * Role URL chain (`SecurityConfig`): `permitAll` on `/api/v1/auth/**` (with `/register` disabled `403`), `ENGINE_SERVICE` on `/api/v1/solutions/callbacks/**`, `DRIVER` on `/api/v1/driver/**`, and `PLANNER` on all other `/api/v1/**` routes (`CUSTOMER` is refused everywhere).
2. **`GVRP_Entry_API` → `GVRP_Engine_API`**:
   * `EngineApiClientImpl` sends `X-Engine-Key` (`${engine.inbound-key}`) over a shared `RestTemplate` (`connectTimeout=1m`, `readTimeout=3m`; `spring.optimization.engine.timeout` is currently not wired into `RestTemplateConfig`).
   * `POST /optimization` and `PUT /jobs` (cancel) execute Engine HTTP calls after DB commit via `TransactionSynchronization.afterCommit()` (`R-3`). `GET /progress` runs non-transactionally after `existsByIdAndBranchId`.
   * `ServiceKeyFilter` blocks every Engine endpoint except exact `GET /api/v1/optimization/health` with `401 {"status":"UNAUTHORIZED"}`.
3. **`GVRP_Engine_API` → `GVRP_Entry_API` Callbacks**:
   * `CallbackService` sends `X-API-Key` (`${entry.api-key}`) with `connectTimeout=5s`, `readTimeout=300s`.
   * Completion payloads are written to `.data/results/pending/` before HTTP POST: `2xx` moves to `sent/`, `4xx` moves immediately to `poison/`, and `5xx`/timeout stays in `pending/` for `CallbackRetryScheduler` (exponential backoff up to 20 attempts). Failure and cancel callbacks are sent once without spooling.
4. **Error Envelope Convention (`docs/engineering/EXCEPTION_CONVENTIONS.md`)**:
   * Entry errors return `{ "errors": [ ErrorDetail... ] }` (except `406` empty body and wrong-`X-API-Key` direct filter JSON).
   * Not-found & cross-branch ID lookups return neutral `404 0040402`; domain/state/plate conflicts return `409 0040901`; pagination/limit violations return `400 0040002` (`UNSUPPORTED_VALUE`).

### 2.2 Verified Risks and Doc-vs-Code Discrepancies (Test Watchlist)
The following facts were verified against `master @ cebf7f3` during test-plan design and directly shape `ST-*` cases:

| ID / Area | Observed Behavior / Risk | Relevant `ST-*` Case(s) |
|---|---|---|
| `pareto-view.component.ts:490` vs `errorInterceptor` | `errorInterceptor` replaces `HttpErrorResponse` with `{ message, status, originalError }`, whereas `pareto-view` reads `error.error?.errors?.[0]`. Unit tests stub `apiService` directly, hiding the wrapper. At runtime, `404 0040402` may fall through to `'Could not apply the plan.'` instead of `'Plan not found'` + reload. | `ST-RES-012` |
| `OrderService.createOrder` duplicate `order_code` vs Import | `POST /api/v1/orders` does not translate `uk_branch_order_code` `DataIntegrityViolationException`, still surfacing `500 0050001` (tracked in `TC-D03-008`). By contrast, `OrderImportService` checks existing branch codes and increments `skipped_count=1` (`errors=[]`) when `overwriteExisting=false`, or reports per-row `errors[]` (`field="Database"`) when two rows in the same file duplicate a new code. | `ST-LIM-010`, `ST-E2E-014` |
| Client-vs-Server optional `emissionFactor` trap | `/setup/vehicle-types` and `/admin/vehicle-types` leave `emissionFactor` optional in the UI form, but when left blank they still send `vehicle_features: { skills: [] }` with `emissionFactor = null`, which violates `@NotNull` on `VehicleFeaturesDTO.emissionFactor` (`400 0040001`, `field: features.emissionFactor`). | `ST-E2E-001`, `ST-RES-014` |
| Unhandled DB unique constraints in Master Data | `VehicleTypeService` `(branch_id, type_name)` and `FleetService.createFleet` `(branch_id, fleet_name)` / duplicate license plates inside `POST /api/v1/fleets` lack `DataIntegrityViolationException` translation and surface `500 0050001` (`D02-master-data/README.md:106`). | `D02` watchlist |
| `DriverViewComponent.logout()` unmapped `/auth/logout` | Unlike `NavbarComponent.onLogout()` (local clear), `DriverViewComponent.logout()` calls `AuthService.logout()` → `POST /api/v1/auth/logout`, which hits `404 0040402` (`NoResourceFoundException`) before clearing the session in `catchError`. | `ST-E2E-015` |
| `BR-025` / `04-non-functional.md` vs Import code | Requirements docs still mention `.xlsx` import support, whereas Entry `OrderImportService` and Client `ImportOrdersDialog` enforce CSV/JSON/pipe-text only and reject `.xlsx` with `400 0040002`. | `ST-E2E-003`, `ST-LIM-004` |
| Stale design docs (`D08-engine/README.md`, `03-integration.md`) | Older design notes still state Engine has no auth layer and Pareto uses dense matrices; code enforces `X-Engine-Key` (`SEC-ENGINE-001`) and applies cluster-first sparse matrices to Pareto jobs ≥ 1000 orders too. | `ST-SEC-014` |
| Dead UI action `DELETE /vehicle-types/{id}` | Admin vehicle-types Delete button invokes an unimplemented endpoint and receives `405 0040501` (`TC-D02-007`, `ST-RES-010`). | `ST-RES-010` |
| Missing Entry-side `@Size(max)` bounds | `RoutePlanningRequest`, `BulkUpdateOrderStatusRequest`, `BulkEditJobRequest`, and `CustomizeRoutesRequest` do not bound list sizes at Entry MVC layer (Engine enforces `EngineLimits`). | `ST-LIM-006`, `ST-LIM-007` |
| Multipart servlet default vs `FileValidationUtils` | `spring.servlet.multipart.max-file-size` is commented out in Entry `application.properties`, meaning servlet defaults (1 MB / 10 MB) may trigger before `FileValidationUtils.MAX_FILE_SIZE` (10 MB) unless configured locally. | `ST-LIM-004` |
| Select All > 10,000 orders | `OrdersSectionComponent` requests `size = totalElements`; if a branch filter matches > 10,000 orders (`AppConstant.MAX_ORDER_PAGE_SIZE`), Entry enforces `400 0040002`. | `ST-LIM-002` |
| Open security checklist items (`SEC-001`, `SEC-005`) | Local dev stack may still run MySQL `root` (`rotate-db-app-user.ps1`) and Spring Security `DEBUG` logging (`application.properties`); `SECURITY_CHECKLIST.md` Gate B items must be cleared before deployment. | Pre-release gate |

---

## 3. Test Environment, Database Isolation, and Seed Fixture

### 3.1 Database Strategy (`AGENTS.md` §15, `L-2026-012`)
1. **Primary (Recommended): Isolated `gvrp_test_db` schema**
   * Provision a separate schema (`gvrp_test_db`) in the local Docker MySQL instance initialized strictly from tracked DDL scripts (`init.sql`, `v2.0.sql`, and `migrations/*.sql`).
   * Because Entry runs `spring.jpa.hibernate.ddl-auto=validate` and uses allocation-50 sequence tables (`route_sequence`, `route_stop_sequence`, `unassigned_order_sequence`), manual seed SQL must never leave `next_val` behind inserted IDs.
   * System-test runs point `GVRP_Entry_API` at `gvrp_test_db` via gitignored `application-local.properties` or environment variables (never tracked files).
2. **Fallback: Shared `gvrp_db` with `ST-` Isolation Prefix and Cleanup Ledger**
   * When an isolated schema cannot be mounted, tests run against `gvrp_db` creating **only** fresh entities prefixed with `ST-` (e.g., `ST-BRANCH-A`, `ST-ORD-001`, `ST-29A-1001`).
   * **Mandatory `L-2026-012` Rule**: Every test execution writes a row-creation ledger recording primary keys/IDs created across `branches`, `users`, `depots`, `vehicle_types`, `fleets`, `vehicles`, `orders`, `optimization_jobs`, `solutions`, `routes`, `route_sends`, and `branch_zones`, and deletes or cleans them up (accounting for FK/revision order) after run verification.
   * Never copy or export production or pre-existing non-test customer rows into test reports (`AGENTS.md` §15).

### 3.2 Fictional Seed Fixture (Required by `SEC-*`, `E2E-*`, `RES-*`, `LIM-*`)
All seed data uses strictly fictional values and non-secret placeholder passwords (`<test-password>` supplied only via runtime env/local config; never committed to git or artifacts per `L-2026-017` / `L-2026-019`):

| Entity Group | Branch A (`ST-BRANCH-A`) | Branch B (`ST-BRANCH-B`) | Extra / System |
|---|---|---|---|
| Users | `planner.a` (`PLANNER`), `driver.a1` (`DRIVER`), `driver.a2` (`DRIVER`), `customer.a` (`CUSTOMER`), `disabled.a` (`PLANNER`, disabled) | `planner.b` (`PLANNER`), `driver.b1` (`DRIVER`) | `planner.new` in empty `ST-BRANCH-NEW` (`ST-E2E-001`) |
| Depots | `ST-Depot-A1` (`21.0285, 105.8542`), outside test LEZ | `ST-Depot-B1` (`21.0400, 105.8300`) | — |
| Vehicle Types & Fleet | `ST-Van-1t` (`DIESEL`, gross `3500`, Euro `4`), `ST-EV-1t` (`ELECTRIC`), Fleet `ST-Fleet-A` + vehicles `ST-29A-1001`..`1002` | `ST-Van-B1`, Fleet `ST-Fleet-B`, vehicle `ST-29B-2001` | — |
| LEZ Zones & Permits | `ST-Zone-A1` (`COPIED` polygon around `21.035, 105.845`) + system `zone_templates` | `ST-Zone-B1` (`COPIED`) | System templates seeded |
| Orders | Scheduled test orders `ST-ORD-*` (mix of coords + geocodable Hanoi test addresses) | `ST-ORD-B1` (no coords, geocodable address) | Large generated batches (temporary) for `LIM-*` |

### 3.3 External Dependencies Matrix
| Dependency | Required By | Fallback / Behavior if Unavailable |
|---|---|---|
| GraphHopper `.osm.pbf` map graph | `ST-E2E-002`, `006`, `007`, `008`, `ST-LIM-007`, `008` | Engine `/legs` returns `200 ROUTE_NOT_FOUND`; real solve cases marked `BLOCKED` if `.data` graph absent |
| Goong Geocoding API key | `ST-E2E-004` (and un-geocoded `createOrder`/`import`) | Cases supplying explicit `lat`/`lon` do **not** call Goong; `ST-E2E-004` requires valid local key |
| Local `.data/results/` directory | `ST-RES-003`, `ST-RES-004` | Created automatically by Engine spool |
| Public OSRM / Google Tiles | `ST-E2E-002` Driver map UI | Stored Engine leg geometry avoids OSRM; only network-fallback needs internet |

---

## 4. Hybrid Execution Strategy

System test cases (`ST-*`) are partitioned into two execution modes documented in `system-test-cases.csv`:

1. **Layer A — `API_AUTO_TARGET` (37 cases)**:
   * Covers authentication (`ST-SEC-001..003`), full RBAC & cross-branch isolation (`ST-SEC-005..014`), callback spool/poisoning & state machine (`ST-RES-003..004, 013`), concurrency caps & races (`ST-RES-005, 007, 008`), error envelopes & domain validation matrix (`ST-RES-009..011, 014`), LEZ dispatch gating (`ST-E2E-009..010`), geocoding (`ST-E2E-004`), and all boundary/resource limits (`ST-LIM-001..010`).
   * Designed with deterministic HTTP inputs, exact status/error-code assertions (`004xxxx`), and explicit `expected_db_state` queries so they can be automated cleanly into an HTTP integration runner (REST Assured / JUnit / Node/Python HTTP harness) without brittle UI selectors.
2. **Layer B — `BROWSER_E2E` (17 cases)**:
   * Covers critical user journeys through `GVRP_Entry_client_v2` on `:4200`: onboarding wizard (`ST-E2E-001`), full Dispatch-to-Driver journey (`ST-E2E-002`), CSV/JSON/pipe-text import + flags + edit + Select All (`ST-E2E-003, 014`), bulk status dialog (`ST-E2E-005`), Pareto chart & apply (`ST-E2E-006`, `ST-RES-012`), manual customization & stale revision conflict (`ST-E2E-007`, `ST-RES-006`), LEZ admin + template preview + toggle/acknowledge/delete + compliance UI (`ST-E2E-008, 016`), admin master data & vehicle permits CRUD (`ST-E2E-013`), `NORMAL` `BackgroundJobDialog` → `/jobs` View Solution → `ExpandedViewDialog` → Logout (`ST-E2E-015`), deep-link returnUrl (`ST-E2E-011`), job monitor & cancel (`ST-E2E-012`, `ST-RES-002`), and 401/branch-switch `SessionResetService` (`ST-SEC-004`).
   * Executable either manually via browser runbook or automated via Playwright / browser subagent.

---

## 5. Four System Test Pillars Overview

All **54 test cases** in [system-test-cases.csv](system-test-cases.csv) cover every use case in `02-use-case-catalogue.md` across four pillars:

| Pillar Code | Focus Area | Total | `API_AUTO_TARGET` | `BROWSER_E2E` | Priority (`H / M / L`) | Key Risks Targeted |
|---|---|---:|---:|---:|---|---|
| `E2E` | End-to-end business workflows (`D01`–`D09`) | 16 | 3 | 13 | `12 / 4 / 0` | Setup wizard, full dispatch, admin master data & permits, order edit & import flags, `FAST` & `NORMAL` job flows, Pareto, customization, LEZ lifecycle & gate, re-dispatch supersession, deep link, cancel, logout |
| `SEC` | Security, RBAC, JWT & multi-tenant isolation | 14 | 13 | 1 | `13 / 1 / 0` | Branch oracle neutrality (`404 0040402`), premature route lock (`SEC-018`), `SEC-017` order/vehicle leak, callback `X-API-Key` & `requireSameBranch` (`SEC-012/013`), Engine `X-Engine-Key` (`SEC-ENGINE-001`), JWT revocation (`R-4..R-6`) |
| `RES` | Resilience, concurrency & unified error contract | 14 | 11 | 3 | `9 / 5 / 0` | Engine outages (`204`/`502`), outbox `pending/sent/poison` spool, callback state machine & `0040902`, `PENDING+PROCESSING` cap `3` (`R-1`), `409` revision/send/plate races, transaction rollback (`R-3`), `SEC-019` Jackson/406/null-element sanitization, domain validation & `emissionFactor` trap, `pareto-view` `errorInterceptor` probe |
| `LIM` | Boundaries, pagination/limit & resource ceilings | 10 | 10 | 0 | `5 / 5 / 0` | `UNSUPPORTED_VALUE` `0040002` bounds (`vehicles 1000`, `orders 10000`, `jobs 200`), import `10MB / 10k / 5k` caps, LEZ `5000 / 50 / 48 / 200` caps, Engine `400/413` ceilings, `SolverDeadline`, PII redaction, duplicate `order_code` defect |
| **Total** | | **54** | **37** | **17** | **`39 / 15 / 0`** | Covers all 48 use cases (`UC-D01-01`..`UC-D09-01`) and all 59 Entry + 8 Engine endpoints |

---

## 6. Entry Criteria, Exit Criteria, and Reporting Protocol

### 6.1 Entry Criteria (Must Pass Before System Test Execution)
1. **Branch & Secret Scan**: `node tools/check-secrets.mjs` exits `0`; no untracked secrets or `skip-worktree` flags (`L-2026-019`).
2. **CSV Catalogue Integrity**: `py GVRP_Document/04-test-design/tools/build_test_cases.py --validate-only` exits `0`.
3. **Entry API Clean Build & Suite** (JDK 21 per `L-2026-003`, `L-2026-007`):
   ```powershell
   mvnw.cmd -B clean verify "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest" "-Dsurefire.failIfNoSpecifiedTests=false"
   ```
4. **Engine API CI-Subset Clean Build**:
   ```powershell
   mvnw.cmd -B clean test "-Dtest=!GvrpEngineApiApplicationTests,!KMeansClustererTest,!VehicleClusterAssignerTest,!BridgeCrossingWorstCaseTest,!CircuityFactorMeasurementTest,!GeoUtilsTest,!JspritConvergenceBenchmarkTest,!JspritPerformanceBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false"
   ```
5. **Client v2 Build & Unit Suite** (Node 22):
   ```powershell
   npm run build && npx ng test --watch=false --browsers=ChromeHeadless
   ```
6. **Local Stack Healthy**: MySQL schema ready (`gvrp_test_db` or `gvrp_db` + `L-2026-012` ledger), Entry `:8080`, Engine `:8081` (`GET /api/v1/optimization/health` → `200 UP`), Client v2 `:4200`.

### 6.2 Exit Criteria
* **Zero** open `P0` / Critical defects and **zero** unresolved tenant-isolation (`ST-SEC-006..013`) or authentication-bypass (`ST-SEC-001..005, 014`) failures.
* **100%** of `H`-priority `ST-*` cases executed and `PASS` (or explicitly documented with environment `BLOCKED` reason or user-approved waiver).
* Known tracked defects encountered during execution (e.g., `TC-D03-008` / `ST-LIM-010` duplicate `order_code` `500`, `TC-D02-007` dead Delete `405`, or any defect confirmed by `ST-RES-012`) logged with HTTP/DB/UI evidence rather than hidden or worked around.
* Test database cleaned per `L-2026-012` ledger; no credentials or customer PII written to tracked artifacts (`AGENTS.md` §15, `L-2026-017`).

### 6.3 Test Integrity & Epistemic Reporting Rules (`AGENTS.md` §5, §10, §14)
* **No silent test weakening**: Never relax assertions, skip tests, or edit existing `UNIT`/`WEB`/`CLIENT` specs to make a system run appear green without explicit user approval for that specific test.
* **Observed evidence only**: Each executed case records `PASS`, `FAIL`, or `BLOCKED` along with exact commands, HTTP status/error code, DB verification query outcome, and screenshots/logs. Unexecuted or environment-blocked cases remain strictly `NOT_VERIFIED` (`NOT_RUN` / `BLOCKED`).
* **Pre-deployment checklist alignment**: Before any deployment sign-off, cross-check `docs/security/SECURITY_CHECKLIST.md` Gate B items (`SEC-001` DB `gvrp_app` rotation, `SEC-015` CSP, `logging.level.org.springframework.security=DEBUG`, `WebConfig` CORS `SEC-E`).

---

## 7. Catalogue Validation and Workbook Generation

Use `GVRP_Document/04-test-design/tools/build_test_cases.py` from the repository root:

```powershell
# Validate both test-cases.csv and system-test-cases.csv (no openpyxl required)
py GVRP_Document/04-test-design/tools/build_test_cases.py --validate-only

# Generate GVRP_Test_Cases.xlsx (requires openpyxl in local Python environment)
py GVRP_Document/04-test-design/tools/build_test_cases.py

# Generate GVRP_System_Test_Cases.xlsx (requires openpyxl)
py GVRP_Document/04-test-design/tools/build_test_cases.py --system
```
