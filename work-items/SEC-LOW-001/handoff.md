# Handoff — SEC-LOW-001

Version: `e6edc43` on `fix/sec-low-001`; tested merged with the other two batch-2 items in `test/sec-batch2`.
Evidence: `verification.md` (automated checks by the implementing agent, runtime checks by Claude).

## User test steps

1. Admin → Vehicles: add a vehicle with a plate that already exists. Expected: an error saying the plate is not available (no 500 page).
2. Import an order file with more than 10,000 rows. Expected: refused at once with the row limit in the message; nothing imported.
3. Optional: on a disposable copy of the DB, run the cleanup with dry-run off and check that jobs with revision chains are removed.

Claude ran the runtime checks listed in `verification.md` § Runtime on 2026-11-11.

## Known limitations and decisions

Decisions pending your acceptance (design.md): plates stay globally unique, so a planner with valid own references still learns a plate exists elsewhere (409 vs 200); the full fix (per-branch plates) needs the SQL in design.md, not applied. Import ceilings 10,000 rows / 5,000 rows to geocode, configurable. Cleanup: one transaction per job, a failing job is skipped and logged.
