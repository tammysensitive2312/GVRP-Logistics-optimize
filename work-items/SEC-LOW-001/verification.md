# Verification — SEC-LOW-001

Verifier (automated part): Claude Code sub-agent, 2026-11-10. Version: worktree `E:\gvrp-wt\sec-low-001`,
branch `fix/sec-low-001`, uncommitted changes on top of `0b6b759` (listed in `implementation.md`).
Environment: Windows 11, JDK 21 (`C:\Program Files\Java\jdk-21`), Maven offline,
`SPRING_PROFILES_ACTIVE=ci`. No server was started; no database write; Goong never called.

## Pre-existing state

| Check | Command | Outcome |
|---|---|---|
| Entry suite before any change | `mvnw.cmd -B -o verify -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` | 347 tests, 0 failures, 0 errors — no pre-existing failure |

## Automated

| Check | Command | Outcome |
|---|---|---|
| New tests before the fix (defect demonstration) | `mvnw.cmd -B -o test -Dtest=VehicleServicePlateTest,OrderImportLimitsTest,JobCleanupRevisionTreeTest` | 13 tests, 9 failures, 4 errors (expected; detail in `implementation.md`) |
| New + related tests after the fix | `mvnw.cmd -B -o test -Dtest='VehicleServicePlateTest,OrderImportLimitsTest,JobCleanupRevisionTreeTest,GlobalExceptionHandlerConflictTest,VehicleServiceTest,JobCleanupServiceTest,OrderImport*Test'` | 33 tests, 0 failures |
| Entry suite, CI style, after the fix | same command as the baseline | 361 tests, 0 failures, 0 errors (347 before + 14 new) |
| Secret check | `node tools/check-secrets.mjs` | OK (5 tracked config files) |

Existing tests modified: none. Existing tests that exercise the changed code and stay green unchanged:
`VehicleServiceTest` (6), `JobCleanupServiceTest` (4), `OrderImportRowsTest` (7),
`OrderImportDeliveryDateTest` (1), `OrderImportBindingTest` (1).

## Not verified here (left to the parent session)

- Runtime on the local stack (HTTP status codes through Spring Security and the real handler chain).
- MySQL behaviour of the retention sweep (RESTRICT FKs, trigger, `REQUIRES_NEW` rollback, Hibernate delete
  order) on a disposable database.
- Derived query `existsByVehicleLicensePlateAndIdNot` against MySQL (Spring Data parses it at startup; the
  CI suite excludes the context-loading test).

## Runtime (parent, 2026-11-11)

Runtime environment (Claude, 2026-11-11 +07:00): integration worktree `E:\gvrp-wt\sec-batch2` (branch `test/sec-batch2` = master `0b6b759` + `fix/sec-auth-002` + `fix/sec-low-001` + `fix/sec-client-002`), Engine + Entry + client dev server run from it with the git-ignored local property files; MySQL in Docker. User signed in as planner of branch 1000 ("Branch Huge 1"); authenticated calls were made from that page with its session (token never printed). Merged suites: Entry 369/369 (`SPRING_PROFILES_ACTIVE=ci`, CI exclusions), client 490/490 specs, client build exit 0 with the same 12 warnings (initial 670.87 kB).

| Check | Result |
|---|---|
| `POST /vehicles?fleetId=999999` with plate `29A-12345` (exists only in branch 1) | 400 `0040402` fleets |
| same with a new plate | 400 `0040402` fleets (same answer, no plate check before own references) |
| `POST /vehicles?fleetId=1000` (own fleet, depot 1000, type 9003) with plate `29A-12345` | 409 `0040901` "License plate is not available." (was 500) |
| `PUT /vehicles/1018` (own) with plate `29A-12345`, other fields unchanged | 409 `0040901` |
| `POST /orders/import` with a CSV of 10,001 rows (all rows deliberately invalid: no demand) | 400 "Import has 10001 rows; the limit is 10000." in 321 ms |
| After all probes (read-only `agent_reader`) | vehicles 100,004 (unchanged), vehicle 1018 plate unchanged, branch-1000 orders 25,055 (unchanged), 0 `PROBE-%` orders; Entry log: 0 geocoding/Goong lines |
| Cleanup sweep with `dry-run=false` | NOT RUN: destructive on the shared local DB. Covered by `JobCleanupRevisionTreeTest` |
