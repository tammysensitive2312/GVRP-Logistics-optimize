# Closure — DEPOT-FIX-001

Closed by Claude (cloud session), 2026-10-30 23:52 +07:00. User acceptance: 2026-10-30 23:49 +07:00, "oke tôi test oke rồi" (`user-acceptance.json`).

## Outcome

`POST /api/v1/depots` no longer fails for valid coordinates (`GeometryMapper.validateCoordinates` throws only when a detail was collected). 7 new DB-free tests; no existing test changed; tenant scoping unchanged.

## Final traceability

Recorded here instead of editing `traceability.md`, whose hash is evidence of revision 3.

| Requirement | Evidence | Status |
|---|---|---|
| DF-R1 valid create → 201 | `GeometryMapperTest.createPoint_validCoordinates_returnsPointWithSrid4326`, `DepotServiceCreateTest.createDepot_validInput_savesDepotWithPointAndBranch`; user test 23:49 (DF-M1) | VERIFIED (unit + user test) |
| DF-R2 out-of-range → 400 with field | `GeometryMapperTest` latitude/longitude/both cases (mapper path, fields `lat`/`long`); user test 23:49 (DF-M2, Bean Validation path, field `latitude`) | VERIFIED (unit + user test) |

## Corrections to the evidence

- `implementation.md` §Baseline says the work was on `fix/depot-fix-001`. It was not: the branch did not exist and the change was made on the `master` working tree (`review.md` R1). The user creates the branch at commit time. `implementation.md` is left unedited because its hash is evidence of revision 4.
- Design DF-T2/T3 named the detail fields `latitude`/`longitude`; the code uses `AppConstant.LATITUDE/LONGITUDE` = `lat`/`long`. The engineer's deviation is accepted (`review.md` R2).

## Left open (by decision or for the user)

- `GVRP_Document` D02: no row for this defect; left for the documentation pass (user 2026-10-30 23:27).
- Lesson candidate L-2026-015 (an unconditional `throw` at the end of a validation helper): not added; `LESSONS.md` entries need the user's approval.
- Test rows: the depot(s) created during DF-M1 were not named in the acceptance; they remain test data in the shared database.
- Known-state files (`PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `SECURITY_CHECKLIST.md`): NOT checked in this closure for statements about depot creation. The defect was found by review on 2026-10-30 and never recorded there, so no stale statement is expected.
