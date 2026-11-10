# Implementation — SEC-LOW-001

Branch `fix/sec-low-001` (worktree `E:\gvrp-wt\sec-low-001`), base `0b6b759`. Design: `design.md`
(D1–D3 = Claude defaults, pending user acceptance). Uncommitted.

## Lead verdicts (before any fix)

| Lead | Verdict | Source evidence | Failing test before the fix |
|---|---|---|---|
| #11 / SEC-007 | CONFIRMED | `VehicleService.java:60` global check before the branch-scoped lookups; `ValidationException` has no handler (`GlobalExceptionHandler.java:214` → 500); update path unchecked (`VehicleService.java:133`) | `VehicleServicePlateTest.createVehicle_existingPlate_foreignFleet_sameAnswerAsNewPlate` (got `jakarta.validation.ValidationException: License plate already exists` instead of `DataInvalidException` fleets); the 5 other tests in that class also failed |
| #10 | CONFIRMED | `OrderImportService.java:175-190` loops every parsed row and geocodes each address-only row; no row cap in parsers or service | `OrderImportLimitsTest.defaultRowCeiling_refusesBeforeGeocodingAndDatabase` (10,001 rows imported, 10,001 mocked geocoding calls, no refusal), `defaultGeocodingCeiling_refusesBeforeAnyGeocodingCall` (5,001) |
| #14 | CONFIRMED | `JobCleanupService.java:141` one level only; revisions chain by design (`RouteCustomizationService.java:116,399`, `ParetoService.java:155`); RESTRICT FKs in migrations; local DB holds 4 grandchild revisions | `JobCleanupRevisionTreeTest.nestedRevisionChain_deletesWholeTreeDeepestFirst` (R2's send and R2 never deleted), `oneFailingJob_doesNotStopTheSweep` (the exception of job 1 escaped `cleanOldJobs`) |

Log of that run: 13 tests, 9 failures, 4 errors (the 3 configured-ceiling tests and LOW-T8 errored because
the fields / isolation did not exist yet).

## Changed files

Paths under `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/` unless stated.

| File | Change | Req |
|---|---|---|
| `exception/ResourceConflictException.java` (new) | 409 conflict exception; message returned as is | R1, R3 |
| `exception/GlobalExceptionHandler.java` | `handleResourceConflict` → 409 `RESOURCE_CONFLICT` | R1 |
| `repository/VehicleRepository.java` | derived query `existsByVehicleLicensePlateAndIdNot` | R3 |
| `service/VehicleService.java` | create: plate check moved after the branch-scoped fleet/depot/type lookups, duplicate → `ResourceConflictException("License plate is not available.")`, `DataIntegrityViolationException` on save → same; update: plate taken by another vehicle → same 409 (before the mapper copies it), `saveAndFlush` with the same mapping | R1, R2, R3 |
| `service/orders/OrderImportService.java` | `enforceImportLimits` before the row loop: `gvrp.import.max-rows` (10000) and `gvrp.import.max-geocode-rows` (5000), field-injected with matching defaults, values < 1 treated as 1; 400 `DataInvalidException` (`VALIDATION_ERROR`, field `file`); geocoding predicate extracted to `needsGeocoding` (same rule) | R4, R5, R8 |
| `service/JobCleanupService.java` | whole revision tree collected breadth-first (visited set), sends of every solution deleted, revisions deleted deepest first, then root, then job; each job in its own `REQUIRES_NEW` transaction (setter-injected `PlatformTransactionManager`, no-transaction fallback for constructor-built tests); failing job logged (id + exception class), skipped, page re-read with room for skipped ids; `@Transactional` removed from `cleanOldJobs` | R6, R7, R8 |
| `GVRP_Entry_API/src/main/resources/application.properties` | `gvrp.import.max-rows=10000`, `gvrp.import.max-geocode-rows=5000` with a comment | R4, R5 |

No schema change, no SQL run, no new dependency, no `permitAll` rule, no existing test or fixture modified.
Every endpoint touched keeps its branch scoping (vehicle lookups `findByIdAndBranchId` /
`findByIdAndFleetBranchId`; import branch from the principal). The retention sweep stays global by design
(system job over all branches); the change only stops one branch's data from blocking the others.

## New tests

| File | Tests | ID |
|---|---|---|
| `GVRP_Entry_API/src/test/.../service/VehicleServicePlateTest.java` | 6 | LOW-T1…T4 |
| `GVRP_Entry_API/src/test/.../exception/GlobalExceptionHandlerConflictTest.java` | 1 | LOW-T5 |
| `GVRP_Entry_API/src/test/.../service/orders/OrderImportLimitsTest.java` | 5 | LOW-T6 |
| `GVRP_Entry_API/src/test/.../service/JobCleanupRevisionTreeTest.java` | 2 | LOW-T7, T8 |

The geocoder is a Mockito mock in every import test; Goong was never called.

## Residual risks

- SEC-007 residual bit (design D1): a planner with a valid own fleet, depot and vehicle type (or an own
  vehicle, on update) still distinguishes "plate exists somewhere" (409) from "free" (200 + a created or
  updated vehicle). Removing it needs the per-branch schema change in `design.md` D1 (user decision).
- Retention sweep: the FK/trigger behaviour of MySQL, the `REQUIRES_NEW` rollback and the Hibernate delete
  order are proven only with mocks here; a disposable-DB run (`NEEDS-VALIDATION.md` §14 plan) is not done.
  A run with many failing jobs reads a larger page each loop (size grows with the skipped count).
- Import: parsing still happens before the row ceiling; parse cost is bounded by bytes only (multipart
  defaults). The textData form field has no separate size cap.
