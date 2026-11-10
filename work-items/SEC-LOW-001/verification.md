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
