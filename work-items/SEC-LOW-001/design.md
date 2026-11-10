# Design — SEC-LOW-001

Base: `master` @ `0b6b759`. Context: `context.md`. Requirements: `request.md`.
No schema change is applied by this item; no new dependency.

## D1 — Vehicle plate: no 500, branch checks first, 409 conflict (LOW-R1, R2, R3)

### Change (no schema change)

1. `VehicleService.createVehicle`: resolve fleet, start/end depot and vehicle type with the existing
   branch-scoped lookups **first**; only then check the plate. A caller without valid own references
   gets the same 400 `RESOURCE_NOT_FOUND` whether or not the plate exists anywhere.
2. Duplicate plate → new `exception/ResourceConflictException` → `GlobalExceptionHandler` 409
   `RESOURCE_CONFLICT`, fixed message `License plate is not available.` The message is the same for a
   plate of the caller's own branch and of another branch; no branch, id or vehicle data is returned.
3. `updateVehicle`: after the existing ownership check, if the request carries a plate, refuse with the
   same 409 when `existsByVehicleLicensePlateAndIdNot(plate, id)` is true (new derived query; excluding the
   vehicle itself avoids a false conflict under the case-insensitive `utf8mb4_unicode_ci` collation when
   only the letter case changes). Then `saveAndFlush` so a constraint error surfaces inside the method.
4. Race between check and insert/update: `DataIntegrityViolationException` from `save` (create) or
   `saveAndFlush` (update) → the same 409. The plate is the only unique key on `vehicles` besides the
   primary key; every FK target was resolved before the save.

### Residual (stated, not fixed here)

With the global unique index, a planner who owns a valid fleet, depot and vehicle type still learns one bit
per guessed plate: 409 (plate exists somewhere) vs 200 (and a vehicle is created in their branch, which is
visible and must be cleaned up by them). The response cannot be made independent of other branches'
data while the database enforces one global namespace.

### Decision (Claude default, pending user acceptance)

Keep global plate uniqueness (today's accepted behaviour and the physical-world meaning of a plate);
apply the no-schema change above. **Alternative:** per-branch plate namespace, which removes the residual
bit and the cross-branch registration block. It needs the schema change below plus code; the user runs the
SQL, an agent does not.

### Alternative SQL (NOT applied — for the user, only if per-branch uniqueness is chosen)

`Vehicle` has no `branch_id`; per-branch enforcement in the database needs one. Checked on local `gvrp_db`
(2026-11-10, read-only): 0 plates duplicated within a branch, so step 3 succeeds on today's data.

```sql
-- 1. Denormalise the branch onto vehicles (fleet -> branch).
ALTER TABLE vehicles ADD COLUMN branch_id BIGINT NULL AFTER fleet_id;
UPDATE vehicles v JOIN fleets f ON f.id = v.fleet_id SET v.branch_id = f.branch_id;
-- 2. Must return 0 before continuing.
SELECT COUNT(*) FROM vehicles WHERE branch_id IS NULL;
-- 3. Swap the global unique keys for a per-branch one.
ALTER TABLE vehicles
    MODIFY COLUMN branch_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_vehicle_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    DROP INDEX vehicle_license_plate,
    DROP INDEX idx_vehicle_license,
    ADD UNIQUE INDEX uk_vehicle_branch_plate (branch_id, vehicle_license_plate);
```

Code that must ship in the same change (otherwise `ddl-auto=update` re-creates a global unique index from
the annotations at the next start): `Vehicle` gets `branch` (`@ManyToOne`, `branch_id`, set from
`fleet.getBranch()` on create), `unique = true` and the unique `idx_vehicle_license` are removed and
`@UniqueConstraint(columnNames = {"branch_id", "vehicle_license_plate"})` is added; the service checks
`existsByVehicleLicensePlateAndBranchId` / `...AndBranchIdAndIdNot`. Not implemented in this item.

## D2 — Order import ceilings (LOW-R4, R5, R8)

`OrderImportService.processParsedData`, before the row loop (so before any geocoding call or DB access):

| Property | Default | Rule | Rationale |
|---|---|---|---|
| `gvrp.import.max-rows` | 10000 | parsed rows + rows with parse errors > ceiling → 400 | equals the Engine order ceiling (`gvrp.limits.max-orders=10000`, SEC-ENGINE-001 D4): a larger import cannot be optimised in one job anyway; about 3.6× the largest observed real day of branch 1 (~2,750) |
| `gvrp.import.max-geocode-rows` | 5000 | rows that would be geocoded (blank lat or lng, non-blank address) > ceiling → 400 | about 1.8× the largest observed real day; bounds paid Goong calls per request |

Refusal: `DataInvalidException` (400), code `VALIDATION_ERROR`, field `file`, message naming the count and the
ceiling, e.g. `Import has 10001 rows; the limit is 10000.` / `Import needs geocoding for 5001 rows; the limit
is 5000. Add latitude/longitude or split the file.` No row is geocoded or saved. Both values are
field-injected (`@Value` on non-final fields with the same defaults as initialisers) so the existing
9-argument constructor used by `OrderImportRowsTest` and `OrderImportDeliveryDateTest` is unchanged.
A value < 1 is treated as 1 (misconfiguration guard). Both properties are added to `application.properties`.

### Decision (Claude default, pending user acceptance)

Refuse the whole request above a ceiling (no partial import). **Alternatives:** a lower geocoding ceiling
(e.g. 1000) to cap spend harder; a per-request geocoding cache for repeated addresses; a per-branch daily
quota. Not done: they change accepted behaviour or need product input.

## D3 — Retention sweep: whole revision tree, per-job transaction and isolation (LOW-R6, R7, R8)

1. `deleteJobChain` collects the full revision tree breadth-first with `findByParentSolutionId`
   (with a visited set against a malformed cycle), deletes the route sends of every solution in the tree,
   then `deleteAll(revisions)` with revisions in reverse breadth-first order (every child before its
   parent), then the root, then the job. For a tree of depth one this is the same call sequence as today.
2. Each job runs in its own transaction (`TransactionTemplate`, `PROPAGATION_REQUIRES_NEW`), so a failure
   rolls back that job's partial deletes (today the self-invocation at `scheduledCleanup` bypasses the
   `@Transactional` and earlier deletes stay committed). `@Transactional` is removed from `cleanOldJobs`
   (it was bypassed on the scheduled path and would otherwise hold a second connection open around the
   per-job transactions). The transaction manager is setter-injected; without one (unit tests built with
   `@InjectMocks`) the work runs without a transaction, as today.
3. A job whose deletion throws is logged at WARN with its id and the exception class only (no customer
   data), counted as not deleted and skipped for the rest of the run. The batch is read as page 0 with
   size `batch-size + skipped`, skipped ids are filtered out, and the loop continues while the page is full,
   so skipped jobs never hide later ones and the loop ends (each full page contains at least one id not yet
   seen).
4. Dry-run, retention periods, status order and the `CleanupCounts` record are unchanged.

### Decision (Claude default, pending user acceptance)

Skip and log a job that cannot be deleted. **Alternative:** stop the sweep on the first failure (today's
behaviour, without the partial commit). Rejected: one tenant's data would block every other tenant's
retention.

## Tests (new files only; no existing test changed)

| ID | Test | Req |
|---|---|---|
| LOW-T1 | `VehicleServicePlateTest.createVehicle_existingPlate_foreignFleet_sameAnswerAsNewPlate` — plate exists, fleet not the caller's → `DataInvalidException` (fleets), plate never queried | R2 |
| LOW-T2 | `createVehicle_existingPlate_ownReferences_conflict` → `ResourceConflictException`, nothing saved | R1 |
| LOW-T3 | `createVehicle_saveRaceOnUniqueKey_conflict` — `save` throws `DataIntegrityViolationException` → conflict | R3 |
| LOW-T4 | `updateVehicle_plateTakenByOtherVehicle_conflict`, `updateVehicle_keepsOwnPlate_saves`, `updateVehicle_flushRaceOnUniqueKey_conflict` | R3 |
| LOW-T5 | `GlobalExceptionHandlerConflictTest` — `ResourceConflictException` → 409 `RESOURCE_CONFLICT` with the message | R1 |
| LOW-T6 | `OrderImportLimitsTest` — rows above `max-rows` → 400, geocoder and repository never called; address-only rows above `max-geocode-rows` → 400, geocoder never called; at the ceiling → imported as today | R4, R5, R8 |
| LOW-T7 | `JobCleanupRevisionTreeTest.nestedRevisionChain_deletesWholeTreeDeepestFirst` — root → R1 → R2 with a send on R2 | R6 |
| LOW-T8 | `oneFailingJob_doesNotStopTheSweep` — job 1 delete throws, job 2 and a later status still deleted; with page size 1 the loop terminates | R7 |
