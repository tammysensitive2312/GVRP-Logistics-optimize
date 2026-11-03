# Feature traceability — SEC-ORDERS-001

Inspected source revision: `c0cdfd5` (2026-11-04). Request: `request.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| OR-R1 | Foreign = unknown, no code | D1 | `OrderService.bulkUpdateStatus` | OR-T1; manual re-probe | NOT_VERIFIED |
| OR-R2 | Own rows unchanged | D1 | — | OR-T2; existing `OrderServiceTest` | NOT_VERIFIED |
| OR-R3 | No write to foreign orders | D1 | — | OR-T3 | NOT_VERIFIED |
