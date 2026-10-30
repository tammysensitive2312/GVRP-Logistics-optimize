# BULK-EDIT-001 — bulk order status edit and server-side filters

**Problem.** Planners edited order status one order at a time. The UI also appeared to filter
nothing: filters were applied in the browser only, so paging and counts disagreed with the list.

**Decision.**

- One endpoint, `PATCH /api/v1/orders/status`, takes a list of order ids plus a target status. It is
  lenient: rows that are allowed to move are updated, rows that are not are reported per row, and
  the call still returns 200 with a partition of `updated` and `rejected` entries.
- The target status must be reachable in the order status graph (`BR-021`); an illegal transition on
  a row rejects that row, not the batch.
- The batch is scoped to the caller's branch on the server. An id from another branch behaves like
  a missing id (`BR-002`).
- Filters (date range, status, free-text search) are parsed and applied in the query, not in the
  browser. A cleared date field must not leave a stale bound behind.
- "Select all" means the current page, never the whole filtered set.
- Other bulk fields and bulk delete were excluded from v1 by user acceptance.

**Date.** Requested 2026-10-11; last commit `7a0c4ce` 2026-10-16; state `done` rev 18.

**Source.** `work-items/BULK-EDIT-001/` (deleted; `git log -- work-items/BULK-EDIT-001`):
`requirements-draft.md`, `traceability.md`, `verification.md`, `closure.md`. Backend evidence:
`OrderServiceTest`, `OrderBulkControllerTest`; client: bulk dialog, section and filter specs.

**Still valid in this documentation.** `03-basic-design/D03-orders/README.md` (API rows, errors),
`BR-021`, `BR-031`…`BR-034`, `TC-D03-014`…`TC-D03-016`, `TC-D03-020`…`TC-D03-022`.

**Not carried over.** The design document cited by the closure (`GVRP_Document/BULK_EDIT_ORDERS.md`)
is absent from the tree; the behaviour above is stated from the code that exists today.
