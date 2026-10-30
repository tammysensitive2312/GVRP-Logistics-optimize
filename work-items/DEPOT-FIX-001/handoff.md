# Handoff — DEPOT-FIX-001

Prepared by Claude (cloud session), 2026-10-30 23:52 +07:00, from `implementation.md` and `review.md`.

## Claim

`POST /api/v1/depots` with a valid body creates the depot in the caller's branch and answers `201`; an out-of-range coordinate is still refused with `400` naming the field.

## Changed files (scope `GVRP_Entry_API/src`)

- `main/java/org/truong/gvrp_entry_api/mapper/GeometryMapper.java` — `validateCoordinates` throws only when a detail was collected.
- `test/java/org/truong/gvrp_entry_api/mapper/GeometryMapperTest.java` (new, 6 tests).
- `test/java/org/truong/gvrp_entry_api/service/DepotServiceCreateTest.java` (new, 1 test).

## Evidence

- Engineer: `.\mvnw.cmd -B clean test` 314 → 321, 0 failures, 0 errors, 0 skipped (`implementation.md`; not re-run by the reviewer).
- Reviewer: code and tests read, verdict "code accepted" (`review.md`, R1–R4).
- User test cases: `review.md` §Manual test, DF-M1…DF-M3.

## Fingerprint

The handoff fingerprint is the scope snapshot recorded by the `pass` transition (`run.json` → `handoff`). The scope was not edited after the `implement → verify` transition (snapshot `4ec6b95b…`), so the two are expected to be equal.
