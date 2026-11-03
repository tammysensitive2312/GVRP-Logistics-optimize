# Request — SEC-ORDERS-001 (bulk order status discloses other branches)

Source: Claude Code session 2026-11-04 (+07:00), user "Yes, do both" after `work-items/SEC-AUDIT-001/runtime-validation.md`.

Finding: **SEC-014 (Medium)** — planner of branch 1 called `PATCH /api/v1/orders/status` with ids 331, 423 (branch 1000) and 999999999; `failed[]` returned `order_code` `TEST-0331`, `TEST-0423` for the foreign ids and `null` for the unknown one. Nothing was written.

## Requirements

| ID | Requirement |
|---|---|
| OR-R1 | For an id that is unknown **or** belongs to another branch, the failed row carries only the id the caller sent and the same reason ("Order not found in this branch"); `order_code` is `null`. Foreign and unknown ids are indistinguishable. |
| OR-R2 | Own-branch rows behave as today (updated, or failed with their transition reason and code). |
| OR-R3 | No write to another branch's order (already true; keep it tested). |

## Exclusions

Changing the endpoint contract, the transition rules, or other order endpoints.

## Acceptance

New unit test(s) for OR-R1…R3; existing `OrderServiceTest` and `OrderBulkControllerTest` green unchanged; manual re-probe from `runtime-validation.md` returns `order_code: null` for 331 and 423.
