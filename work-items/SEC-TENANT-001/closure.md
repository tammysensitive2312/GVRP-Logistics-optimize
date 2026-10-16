# Closure — SEC-TENANT-001

Accepted by the user on 2026-10-16 20:43 (+07:00) for handoff
`d1e4a1e876a23c9e80feab4005f4695d6db2aea0745fc831f3c03749c38bea25`.

## Evidence added after the verify node

Reported by the user; raw output was not shared with the agent.

- `mvnw.cmd clean test` in `GVRP_Entry_API`: passed (closes the "no clean build" gap in `verification.md`).
- Manual HTTP test per `handoff.md` with two branches: own solution readable;
  foreign solution, revision chain and latest revision answer like a missing ID;
  cross-branch `PUT /api/v1/vehicles/{id}` refused. Exercises the derived query
  `VehicleRepository.findByIdAndFleetBranchId` against MySQL.
- Legacy data check on the local database (2026-10-16 20:42):
  `SELECT … FROM solutions c JOIN solutions p ON p.id = c.parent_solution_id WHERE c.branch_id <> p.branch_id`
  returned 0 rows: no mixed-branch revision chains exist.

## Final requirement coverage

| Req | Status |
|---|---|
| TI-001, TI-002 | VERIFIED_BY_TEST + VERIFIED_AT_RUNTIME (manual, local) |
| TI-003, TI-005 | VERIFIED_BY_TEST (unit, mocks) |
| TI-004 | VERIFIED_BY_TEST + VERIFIED_AT_RUNTIME (manual, local) |
| TI-006 | VERIFIED_BY_TEST (unit, mocks) |
| TI-007 | PARTIALLY_VERIFIED (null-branch unit tests; static inspection of controllers) |
| TI-008 | VERIFIED_BY_TEST (69/69, clean build) |

## Remaining limitations and follow-ups

- `DepotRepository.findByIdAndBranchId` is safe for a null branch only because
  `depots.branch_id` is NOT NULL (Spring Data emits `IS NULL` for null parameters).
- No independent review by gvrp-reviewer or `/security-review` yet; an external
  agent review on 2026-10-16 agreed with the fix and raised the clean-build and
  integration-test points handled above.
- Open findings: SEC-001 (credential rotation), SEC-004..SEC-008 in
  `docs/security/SECURITY_CHECKLIST.md`. SEC-002 and SEC-003 are resolved by
  this run once merged.
- `GVRP_Entry_client_v2/package-lock.json` is untracked; CI client jobs need it
  (separate commit).
