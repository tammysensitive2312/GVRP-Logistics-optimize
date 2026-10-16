# Discovery — BULK-EDIT-001 (read-only + live browser check 2026-10-11)

## Filter report (VERIFIED live + code)

- Symptom confirmed live as hien: setting Status=COMPLETED re-requests
  `GET /api/v1/orders?date=...&page=0&size=5&status=COMPLETED` (200) but renders
  the identical 5 rows — filter has no effect.
- Root cause (code): `OrderController.getListOrders` binds only `page/size`;
  `OrderService.getAllOrdersPaginated(branch, page, size)` filters nothing.
  `date/status/search` params are sent by the client and silently ignored.
- Related: table has row checkboxes + Select All; toolbar emits plan/export/bulk actions.

## Bulk status today (VERIFIED)

- No bulk order endpoint exists (only single `PUT /orders/{id}`).
- Toolbar "Bulk Update Status/Priority" are `Coming soon!` toasts (`orders-section.component.ts:286-295`); no backend behind them.
- Backend reuse available: `OrderStatusTransitionService.bulkTransition` (strict, all-or-nothing 400) and `bulkTransitionLenient` (partial + `BulkTransitionResult`), both over the guarded `OrderStatus.canTransitionTo` graph:
  SCHEDULED→{ON_ROUTE,UNASSIGNED,REJECTED}; ON_ROUTE→SERVICING;
  SERVICING→{COMPLETED,REJECTED}; COMPLETED/UNASSIGNED/REJECTED→SCHEDULED.
- Order statuses observed in test data include ON_ROUTE (orders locked in plans).

## Scope consequence

Bulk edit = new endpoint + toolbar wiring + per-row result reporting. Whether filters get fixed server-side in the same run is a scope decision (Q-3).
