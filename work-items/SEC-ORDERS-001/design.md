# Design — SEC-ORDERS-001

## D1 (OR-R1, OR-R2)
In `OrderService.bulkUpdateStatus`, for the "missing or other branch" case build the failed row with `.orderCode(null)` always. Treat a foreign order exactly like a missing one (the loaded entity is not used further). Own-branch failures from `bulkTransitionLenient` keep their code and reason.

## D2 — Tests (new file `OrderBulkTenantIsolationTest.java`, Mockito, DB-free)
| ID | Case | Expected |
|---|---|---|
| OR-T1 | caller branch 1; ids = [foreign 13 (branch 2), unknown 99] | both failed rows: `orderCode == null`, same reason, ids 13 and 99; failed rows are field-for-field equal except id |
| OR-T2 | caller branch 1; own order 11 legal transition | updated [11]; `bulkTransitionLenient` receives only own orders |
| OR-T3 | foreign order present | never passed to `bulkTransitionLenient`; no save of it |
