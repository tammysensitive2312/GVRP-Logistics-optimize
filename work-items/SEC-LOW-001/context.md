# Context — SEC-LOW-001

Inspected source: `master` @ `0b6b759` (2026-11-10), worktree `E:\gvrp-wt\sec-low-001`.
Paths below are relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/` unless stated.

## Schema management

- `spring.jpa.hibernate.ddl-auto=update` (`src/main/resources/application.properties:15`). No Flyway or
  Liquibase; hand-written SQL under `src/main/resources/database/migrations/` is run manually
  (stated in `2026-10-03-revision-nullable-job.sql`).
- Consequence: `ddl-auto=update` adds missing tables, columns and unique constraints from the entity
  annotations at startup but never drops one. Changing a unique annotation is therefore itself a schema
  change and must ship together with SQL the user runs.

## Lead #11 / SEC-007 — vehicle plate

- `service/VehicleService.java:60` — first statement of `createVehicle` is
  `vehicleRepository.existsByVehicleLicensePlate(plate)` (global, `repository/VehicleRepository.java:33`),
  throwing `jakarta.validation.ValidationException`. The branch-scoped fleet/depot/type lookups
  (lines 65–102) come after it.
- `exception/GlobalExceptionHandler.java:214` — no handler for `ValidationException`; the catch-all
  answers 500 `INTERNAL_ERROR`. A non-duplicate plate with a foreign fleet answers 400
  `RESOURCE_NOT_FOUND` (fleets). Runtime-confirmed 2026-11-04 (`work-items/SEC-AUDIT-001/runtime-validation.md`).
- `service/VehicleService.java:133` — `updateVehicle` copies `vehicleLicensePlate` through
  `VehicleMapper.updateEntityFromDTO` with no check; a collision surfaces only at commit as a database
  unique-key error (outside the service method, generic handler → 500).
- `entity/Vehicle.java:20,46` — plate declared unique globally twice (index `idx_vehicle_license` and
  `unique = true`). Local DB (read-only `agent_reader`, 2026-11-10): `SHOW INDEX FROM vehicles` lists two
  unique indexes on the plate (`vehicle_license_plate`, `idx_vehicle_license`) plus a non-unique
  `idx_license_plate`; 100,004 vehicles, 0 duplicate plates. `Vehicle` has no `branch_id`; the branch is
  `fleet.branch`.
- With a global unique index, any create with valid own references is a cross-branch existence bit
  (refused vs created). Only a per-branch namespace removes it; that is a schema change (design D1).

## Lead #10 — order import

- `controller/OrderController.java:33` — multipart `POST /api/v1/orders/import`, synchronous, branch from
  the principal.
- Parsers (`service/integration/file/CsvFileParser`, `JsonFileParser`, `TextDataParser.java:31`) accumulate
  every row; no count cap. Bytes are bounded only by Spring Boot multipart defaults (properties commented
  out at `application.properties:63-64`) and `FileValidationUtils` (10 MiB, files only).
- `service/orders/OrderImportService.java:175-205` — inside one `@Transactional` method, every row with a
  blank lat/lng and a non-blank address calls `GeocodingService.geocode` (`service/GeocodingService.java:54`:
  uncached `RestTemplate` GET to Goong with the operator key; shared `RestTemplate` timeouts 1 min connect /
  3 min read). Geocoding happens before row validation, so even invalid rows spend a paid call.
- Lines 248–278 — per valid row: `existsByBranchIdAndOrderCode`, optional find, `save`.
- Existing tests build `OrderImportService` with its 9-argument constructor
  (`OrderImportRowsTest.java:93`, `OrderImportDeliveryDateTest`); a new constructor argument would break
  them, so new settings must be field-injected.
- Scale (read-only aggregate on local `gvrp_db`, 2026-11-10): 988,296 orders in 3 branches; the largest
  (branch, delivery_date) group is 25,054 orders (branch 1000, synthetic load data, 2026-08-21); branch 1's
  largest days hold about 2,750 orders. Engine ceiling `gvrp.limits.max-orders=10000`
  (`work-items/SEC-ENGINE-001/design.md` D4). How the existing orders were loaded (import vs SQL) is not
  recorded: NOT_VERIFIED.

## Lead #14 — retention sweep

- `service/JobCleanupService.java:141` — `findByParentSolutionId(root)` one level only; lines 146–151 delete
  sends of root + direct children, then the children, then the root.
- Revision chains are deeper than one level by design: `RouteCustomizationService.saveRevision` (line 116)
  requires the base to be the latest revision and sets `parentSolution(base)` (line 399);
  `ParetoService.apply` (line 155) does the same. `SolutionService.collectRevisions` walks the tree
  recursively.
- FKs: `fk_solution_parent ... ON DELETE RESTRICT` (`database/migrations/2026-10-02-manual-solution-revision.sql`),
  `fk_send_solution ... ON DELETE RESTRICT` (`2026-10-06-driver-delivery.sql`). `pareto_candidates.job_id`
  is `ON DELETE CASCADE` and `solutions.pareto_candidate_id` is `ON DELETE SET NULL`: not a blocker.
- Local DB (read-only aggregate, 2026-11-10): 8 revisions, of which 4 are grandchildren (parent is itself a
  revision). The defect is reachable with today's data once those jobs pass retention and
  `gvrp.cleanup.dry-run=false`.
- Lines 117–124 — `cleanStatus` has no per-job error isolation and always re-reads page 0 ordered by id, so a
  failing job would be first again on the next run. Line 83 — `scheduledCleanup` calls `cleanOldJobs()` on
  `this`, so its `@Transactional` (line 98) is bypassed: each repository call commits on its own and a later
  failure leaves earlier deletes (route sends) committed.
- `gvrp.cleanup.dry-run=true` ships in `application.properties:83`; in dry-run nothing is deleted.
- Existing `JobCleanupServiceTest` uses `@InjectMocks` with the 3-repository constructor and stubs
  `findExpiredIds(status, any, any(Pageable))`; batch size is 0 there (no `@Value`), i.e. page size 1.

## Pre-existing test state

Baseline on this worktree before any change: see `verification.md` (CI-style suite run at intake).
