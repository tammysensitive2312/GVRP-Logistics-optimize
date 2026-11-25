# GVRP Local System-Test Harness (Isolated Test DB + Postman API Suite)

Provides an isolated MySQL 8.0 test database (`gvrp_test_db` on port `3307`),
fictional multi-branch seed data (`ST-BRANCH-A`, `ST-BRANCH-B`, `ST-BRANCH-NEW`),
an importable Postman Collection v2.1 covering the `API_AUTO_TARGET` cases of
[STP-GVRP-01](../../GVRP_Document/04-test-design/02-system-test-plan.md), and a
Node concurrency race runner.

## Quick Start

### 1. Provision Isolated Test Database (Port 3307)
Requires Docker Desktop running locally:
```powershell
powershell -ExecutionPolicy Bypass -File tools/system-test/db/setup-test-db.ps1
```
What this does:
- Starts container `gvrp-system-test-db` (`mysql:8.0`) on `localhost:3307`.
- Clones schema DDL only (`--no-data`) from `sembackend-db-1/gvrp_db` (or falls back to `GVRP_master_initial.sql` + `migrations/*.sql` if `sembackend-db-1` is not running).
- Loads [seed-system-test.sql](db/seed-system-test.sql) with freshly hashed fictional credentials.
- Writes gitignored `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json`.

### 2. Launch `GVRP_Engine_API` (:8081) & `GVRP_Entry_API` (:8080) Against `gvrp_test_db`
```powershell
powershell -ExecutionPolicy Bypass -File tools/system-test/db/start-test-stack.ps1
```

### 3. Run API Tests in Postman Desktop or Newman CLI
- **Postman Desktop**: Import:
  1. `tools/system-test/postman/GVRP-System-Test.postman_collection.json`
  2. `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json` (or `.template.json` filled manually)
  Then run folder `00 - Auth & Token Setup` first, followed by `01 - SEC` .. `04 - E2E API`.
- **Newman CLI**:
```powershell
npx --yes newman run tools/system-test/postman/GVRP-System-Test.postman_collection.json `
  -e tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json
```

### 4. Run Parallel Concurrency Races (`ST-RES-007`, `ST-RES-008`)
```powershell
node tools/system-test/scripts/run-concurrency-races.mjs
```

### 5. Run Playwright Browser E2E Suite (17 `BROWSER_E2E` Cases)
Requires the live local test stack running (`:4200` Client v2, `:8080` Entry API, `:8081` Engine API, `:3307` `gvrp_test_db`) and gitignored `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json`:
```powershell
cd tools/system-test/playwright
npm install
npx playwright install chromium
npx playwright test
```
Reports are written to `tools/system-test/reports/playwright-report.json` and `tools/system-test/playwright/playwright-report/`. Each spec resets `gvrp_test_db` automatically via `tools/system-test/db/reseed-test-db.ps1`.

### 6. Teardown
```powershell
powershell -ExecutionPolicy Bypass -File tools/system-test/db/teardown-test-db.ps1
```
