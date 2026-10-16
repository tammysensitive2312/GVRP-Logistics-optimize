# Implementation — BULK-EDIT-001

## Backend (`GVRP_Entry_API`)

- `OrderRepository.searchOrders` (JPQL, nullable date/status/search; branch-scoped; `LOWER LIKE` on code/customer/address); `OrderService.getAllOrdersPaginated` gains filter params (blank search → null); `OrderController` binds + parses them (400 on bad date/status, case-insensitive status).
- `PATCH /api/v1/orders/status` (`BulkUpdateOrderStatusRequest` @Valid: non-empty ids + status): unknown status → 400; orchestration in `OrderService.bulkUpdateStatus` — missing/foreign ids → failed rows, branch-valid subset through existing `bulkTransitionLenient`, result mapped to `{updated[], failed[]}` with per-row reasons reusing the transition message format. Always 200 (lenient); every input id in exactly one list.
- Tests: `OrderServiceTest` (4: filter forwarding, flip+report incl. foreign/missing, real lenient partition writes only legal, empty→empty report) + `OrderBulkControllerTest` (5: filter forward + bad date/status 400s, partitioned 200, bad status 400; SNAKE_CASE mapper pins wire contract).

## Frontend (`GVRP_Entry_client_v2`)

- `OrderStatus` type completed to 6 backend values; `BulkStatusPayload/Result/Row` models (snake_case wire).
- New `BulkStatusDialogComponent` (6 statuses, count, confirm; blocked without choice).
- `orders-section`: wired `bulkEditClick` → dialog → `api.bulkUpdateOrderStatus` → report panel (failed rows + reasons, dismissible) + toast summary + table reload + selection clear; filter/page change clears selection (single-page rule; table already clears its own model on new data).
- `orders-table`: added SERVICING/REJECTED icon + color (bulk can now surface them).
- Tests: dialog 3, section 4 (no-op empty, send+report+reload+clear, transport failure, clear-on-change). Full suite 172 + same 5 pre-existing; tsc 0; budgets unchanged (only pre-existing login ERROR).

## Repair 1 — cleared date still sent today (user-reported, 2026-10-11, `user-feedback-01.md`)

- Root cause: `buildFilterObject` defaulted empty date to today and `OrderFilter.date` was required, so `api.service` always sent it. Backend already treats missing date as "no filter".
- Fix: `date?` optional end-to-end (model, builder omits when cleared, api omits param). Initial default (today) on first load unchanged. New filters specs (omit vs keep). No requirement change.

## Repair 2 — stale merged date (user-reported live, 2026-10-11, `user-feedback-02.md`)

- Root cause (deeper): the builder correctly OMITTED a cleared date, but `onFilterChange` merged `{...currentFilters, ...newFilters}`, so the missing key kept the STALE date (same flaw for cleared status/search). Repair 1 was necessary but insufficient — lesson: test the merge, not just the builder.
- Fix: emitted filter IS the complete new state (full replace instead of merge); added missing SERVICING/REJECTED status options so all 6 statuses are filterable.
- Verified live on browser as hien: set date → `?page=0&size=5&date=2026-10-11`; clear → `?page=0&size=5` (no date); table renders 5 rows. No requirement change.

## Repair 3 — PATCH missing from CORS allowlist (user-reported live, 2026-10-11, `user-feedback-03.md`)

- Root cause: `WebConfig` allows GET/POST/PUT/DELETE/OPTIONS only; `PATCH /orders/status` (this run's first PATCH endpoint) fails browser preflight with "Invalid CORS request" before auth.
- Fix: one word — added `PATCH` to allowed methods. Compiles; no test harness covers container CORS mapping (standalone MockMvc bypasses it), so verification is a live preflight check after redeploy (recorded as blocker, not claimed).

## Repair 4 — error report UI redesign (user-requested, 2026-10-11)

- Trigger: live `bsk` check on a 100/page view (25,054 orders, Branch Huge 1): bulk-setting 100 SCHEDULED rows to COMPLETED produced 100 identical refusals rendered as 100 repetitive inline lines ("0 updated100 refused:" — missing space, unstyled `div`, no scroll bound), pushing the filters/table off screen. Evidence: `bsk` screenshots in session.
- Fix (frontend only, no requirement change — BE-002 still holds: exact rows + reasons reachable): `orders-section` report is now a compact banner (`N updated · M refused`, warning icon, amber accent) with a `View details` toggle and `Dismiss`. Details group refused rows by normalized reason (per-order codes stripped from the grouping key), each group expandable to its order codes, scrolling internally (`max-height: 160px`). Details auto-open for ≤5 refusals, collapsed above that. New `bulkGroups` computed + `bulkDetailsOpen` signal; styles added (previously the report `div` had no styles at all).
- Tests: 3 new section specs (100-row grouping incl. per-order reason text, auto open/collapse, toggle + dismiss reset).

## Not changed

Single PUT, import, planning, engine, delete flow, notification channels. BE-004/005 excluded per acceptance.
- No requirement/design change.
