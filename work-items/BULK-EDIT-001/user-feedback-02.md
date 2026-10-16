# User feedback 2 — BULK-EDIT-001 (failure report, 2026-10-11)

Source: user live test after Repair 1 — cleared date picker, request STILL carries date.

- Root cause (deeper, in the merge — not the builder): `buildFilterObject` correctly OMITS a cleared date, but `onFilterChange` merges `{...currentFilters, ...newFilters}`, so the missing key keeps the STALE date. Same flaw affects cleared status/search (omitted keys never clear). Repair 1 was necessary but insufficient — lesson: test the merge, not just the builder.
- Fix: treat the emitted filter as the complete new state (full replace instead of merge); add missing SERVICING/REJECTED status options so all 6 statuses are filterable. No requirement change (BE-003/BE-014 already demand this behavior).
