# Implementation — DEPOT-FIX-001

## Scope

Fix `POST /api/v1/depots` always answering `400`: `GeometryMapper.validateCoordinates` ended with an
unconditional `throw new DataInvalidException(errorsInfo)`, so even valid coordinates threw with an empty
detail list. Single change S1 per `design.md` §1, plus tests DF-T1…DF-T4.

## Baseline

- Branch: `master` @ `081640a` (recorded in task-claude-code.md Step 0). Fix branch: `fix/depot-fix-001`.
- `.\mvnw.cmd -B clean test`: **314 tests, 0 failures, 0 errors, 0 skipped** (BUILD SUCCESS, 50.0 s).
- Assertion check (task-claude-code.md Step 2): no existing test references `GeometryMapper`,
  `DepotService.createDepot`, the `/depots` endpoint or `DepotInputDTO` in a way that asserts the
  buggy `400`; no client code asserts a depot-create `400`. `DepotMapperTest` is `@SpringBootTest` and
  covers `toDTO` only, never `toEntity`. No conflict with the fix.
- `openpyxl`/live Entry API not relevant to this item.

## Changes Made

- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/mapper/GeometryMapper.java`
  - `validateCoordinates`: the `throw new DataInvalidException(errorsInfo)` is now guarded by
    `if (!errorsInfo.isEmpty())`. Valid coordinates no longer throw; out-of-range values still throw
    with one detail per offending coordinate (`lat`, `long`).
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/mapper/GeometryMapperTest.java` (new)
  - DF-T1 valid coordinates → point, `getY()==21.02`, `getX()==105.85`, SRID 4326, no exception.
  - DF-T2 latitude 95.0 → `DataInvalidException` with exactly one detail, field `lat`.
  - DF-T3 longitude -181.0 → one detail, field `long`; both out of range → two details `lat`, `long`.
  - plus null-DTO and null-coordinate cases (return null).
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/DepotServiceCreateTest.java` (new)
  - DF-T4: `DepotService.createDepot` with mocked `BranchRepository`/`DepotRepository` and the generated
    `DepotMapperImpl` wired with a real `GeometryMapper` via `ReflectionTestUtils`; verifies
    `depotRepository.save` receives a `Depot` whose `location` is the point and whose `branch` is the
    caller's branch, and that `toDTO` returns the coordinates.

No other file touched. Tenant scoping unchanged (branch taken from the principal).

## Commands Executed

- `node tools/feature-workflow/workflow.mjs init …` → state `intake`, rev 0.
- `… step … ready 01-proof.json` → `discover`, rev 1.
- `… step … ready 02-proof.json` → `design`, rev 2.
- `… step … ready 03-proof.json` → `implement`, rev 3.
- `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\mvnw.cmd -B clean test` → BUILD SUCCESS, 314 tests.
- `$env:JAVA_HOME=…; .\mvnw.cmd -B test -Dtest="GeometryMapperTest,DepotServiceCreateTest"` →
  7 tests, 0 failures.
- `$env:JAVA_HOME=…; .\mvnw.cmd -B clean test` → BUILD SUCCESS, **321 tests**, 0 failures, 0 errors, 0 skipped.

## Verification Results

- DF-R1: valid body creates the depot and returns `201` — covered by DF-T1 (mapper no longer throws) and
  DF-T4 (service save receives the point + branch, `toDTO` returns coordinates). HTTP status itself is not
  exercised by these unit tests (no Spring context, L-2026-001); the controller already returns
  `ResponseEntity.status(HttpStatus.CREATED)`.
- DF-R2: out-of-range coordinate refused with `400` and a detail naming the field — covered by DF-T2/T3
  (GeometryMapper still throws `DataInvalidException` with field `lat`/`long`); the bean-validation path
  (`@DecimalMin/@DecimalMax`) also rejects out-of-range with field `latitude`/`longitude` before the mapper
  runs.
- Full suite green: 321 tests (baseline 314 + 7 new).

## Modified Tests

None modified. Two new test classes added (allowed). No existing test, fixture or helper edited, disabled,
skipped or weakened.

## Security-Relevant Changes

None. Branch scoping unchanged; branch is still taken from `CurrentUserUtil.getCurrentBranchId()`. No new
endpoint, no new `permitAll`, no secret handling.

## Deviations From Design

1. Field name in the GeometryMapper error detail. `design.md` DF-T2/DF-T3 expect the detail field to be
   `latitude`/`longitude`, but `AppConstant.LATITUDE`/`LONGITUDE` are `"lat"`/`"long"`, and
   `GeometryMapper` uses those constants. The tests therefore assert `"lat"`/`"long"`. The design's wording
   matches the bean-validation path (which uses the Java field name `latitude`/`longitude`), not the
   GeometryMapper path. `AppConstant` is out of the allowed change set and S1 touches only
   `GeometryMapper`, so the constant was left unchanged. If the reviewer wants the two paths to agree, that
   is a separate, explicit change to `AppConstant` (and its callers, none exist today).
2. DF-T4 uses the generated `DepotMapperImpl` wired via `ReflectionTestUtils` instead of a Spring context,
   as permitted by design §2 ("If the generated mapper cannot be built without Spring, record why…"). It
   could be built without Spring, so DF-T4 is implemented rather than skipped.

## Remaining Uncertainty

- The live check (design §3) was **not performed**: no Entry API was reachable on port 18080 during this
  run, and no credentials/branch were available to create a depot row. The behaviour change is covered by
  unit tests (DF-T1…DF-T4) plus the unchanged controller status code (`201`). The user can run the live
  check against their own instance; any depot created will be a test row in the shared database.

## Documentation Now Stale

- `GVRP_Document/03-basic-design/D02-master-data/README.md` still describes `POST /api/v1/depots` as
  succeeding (it did not, before this fix). Left untouched per the user's decision of 2026-10-30 23:27
  ("documentation left for DOC-SYSTEM-001 corrections"). To be corrected when DOC-SYSTEM-001 resumes.

## Lesson Candidates

- (L-2026-015 candidate) A `throw` placed unconditionally at the end of a validation helper defeats the
  guard even when all checks pass; the empty-detail case becomes a spurious `400`. Worth a one-line note
  in `docs/engineering/LESSONS.md` if the user wants it. Not added (rule files need user approval).

## Handoff to Reviewer

- Claim: `POST /api/v1/depots` with a valid body now creates the depot and answers `201`; out-of-range
  coordinates are still refused with `400` and a detail naming the field.
- Verification type: IMPLEMENTATION (unit tests, no Spring context) + static trace of the controller status
  code.
- Evidence: `GeometryMapper.java` (S1), `GeometryMapperTest` (6 tests), `DepotServiceCreateTest` (1 test),
  full suite 321/0.
- Scope of the claim: unit-level; the HTTP status and the live DB write are not exercised by these tests
  and are recorded as remaining uncertainty.