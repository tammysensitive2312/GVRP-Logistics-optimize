# Handoff — BULK-EDIT-001 (awaiting user test, Repair 4 included)

Bulk edit orders status + working server-side filters + redesigned error
report. No DB migration (query-only change).

## Deploy first

Rebuild + restart Entry API and frontend (no migration needed).

## Test steps (seeded branch, e.g. Branch Huge 1)

1. S-1: tick 3 SCHEDULED rows → Bulk Edit → ON_ROUTE → expect "3 updated", table shows ON_ROUTE.
2. S-2: mix a row that cannot transition (e.g. target COMPLETED while row is ON_ROUTE) → expect it listed as refused with reason "Cannot transition …", legal rows flipped.
3. S-3: set Status filter SCHEDULED → Select all → bulk → only filtered rows affected; change page/filter → selection clears.
4. Exceptional: unknown status / bad date in URL params → 400 (direct API check optional).
5. S-4 (Repair 4, error UI at scale): page size 100 → select all SCHEDULED →
   Bulk Edit → COMPLETED → expect compact banner "0 updated · 100 refused ·
   1 unique reason" + View details/Hide details + Dismiss; expanding shows
   one grouped reason with expandable order codes; table keeps its place.

## Automated verification

Entry 9/9, client 178 + 5 pre-existing, tsc clean, budgets unchanged. Details: `verification.md`.

## Links

Requirements: `requirements-draft.md` · Design: `GVRP_Document/BULK_EDIT_ORDERS.md` · Traceability: `traceability.md` · Run: `work-items/BULK-EDIT-001/run.json`.

Reply with acceptance or rejection; silence is not acceptance.
