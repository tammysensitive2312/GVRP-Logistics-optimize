# Implementation — SEC-ORDERS-001

Date: 2026-11-04. Finding: SEC-014 (Medium).

## Change
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/orders/OrderService.java`, `bulkUpdateStatus`: the failed row for an id that is unknown or belongs to another branch now always has `orderCode(null)` (was the foreign order's code). The `findAllById` call, the branch check and own-branch handling are unchanged (OR-R1, OR-R2).

## Tests
New file `service/orders/OrderBulkTenantIsolationTest.java` (Mockito, DB-free, 3 tests):
- foreign id 13 and unknown id 99: both failed, `orderCode` null, same reason (OR-T1, OR-R1);
- own order 11 still updated, only own orders reach `bulkTransitionLenient` (OR-T2, OR-R2);
- foreign order never transitioned or saved, status unchanged (OR-T3, OR-R3).

## Modified Tests
none (existing tests, fixtures and helpers untouched).

## Verification
`.\mvnw.cmd -B clean test` in `GVRP_Entry_API` (JDK 21):
- Baseline (before change): 331 run, 0 failures, 3 errors.
- After change: 334 run, 0 failures, 3 errors (+3 = the new tests, all pass).
- The 3 errors are pre-existing and unrelated: `DepotMapperTest.testToDTO`, `DepotMapperTest.testMapperBeanIsCreated`, `GvrpEntryApiApplicationTests.contextLoads` — Spring context fails at `jwtTokenProvider` with `WeakKeyException` (JWT key property empty in this shell). Not fixed here.
- Manual re-probe (PATCH /api/v1/orders/status as planner of branch 1, ids 331, 423, 999999999): NOT performed in this run (running servers not touched); remains for the user. Expected: all three failed with `order_code` null.

## Remaining risks
Other order endpoints and `bulkTransitionLenient` own-branch messages unchanged by design.
