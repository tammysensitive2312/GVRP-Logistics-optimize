# Task for the OpenCode engineer — LEZ-001, increment P1a

You are `gvrp-engineer`. Implementation of P1 is authorized by the user (2026-10-26 22:54 +07:00:
"hãy bắt đầu triển khai P1"). This task is **increment P1a only** (`design.md` §15). Follow
`AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root.

## Read first
1. `work-items/LEZ-001/request.md`
2. `work-items/LEZ-001/context.md`
3. `work-items/LEZ-001/design.md`, the specification. For P1a: §2, §3, §6, §7 (zones, templates, permits, vehicle types), §10, §11, §15
4. `work-items/LEZ-001/business-flows.md` BF-01…BF-03
5. `docs/engineering/LESSONS.md`: L-2026-001, -002, -003, -004, -007, -009, -012, -013, -014

If the design is wrong or impossible, stop and report. Do not redesign.

## Step 0 — preconditions and branch
1. Run `node tools/session-brief.mjs` and include the output in your report.
2. Run `git status --short`. If anything is modified or untracked **outside** the paths below, stop and report the list:
   - `work-items/LEZ-001/`
   - `docs/engineering/LESSONS.md`
   - `GVRP_Engine_API/.claude/`, `GVRP_Entry_API/.claude/`
   - `diff1.txt`, `diff1_final.txt`, `.mcp.json`
3. Run `git switch -c feature/lez-001`.

## Step 1 — workflow ledger (before editing code)
The run already exists. It was created in `intake`, then advanced to `discover` and `design`. Its `root` was corrected to the Windows path with the user's permission (L-2026-014).
```
node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json
node tools/feature-workflow/workflow.mjs step work-items/LEZ-001/run.json ready work-items/LEZ-001/03-proof.json
```
- `recover` must report state `design`, revision 3, with no blocking warnings. If it reports warnings, include them in your report and continue only if they concern the files of `work-items/LEZ-001/`.
- `step` must print state `implement`, revision 4. On failure, stop and report the output.

## Step 2 — baseline
In `GVRP_Entry_API`, run `mvnw.cmd -B test` (JAVA_HOME = JDK 21, L-2026-007). Record:
- tests run, failures, errors, skipped;
- the name of every failing test.

Pre-existing failures are recorded, not fixed. The Engine API and the client are **not touched** in P1a.

## What to build (P1a)

All of it lives in `GVRP_Entry_API/src/main/...` unless stated otherwise.

### A. Enums (new, `entity/enums/`)
- `LegalClass`: `MOTORBIKE, CAR, TRUCK, BUS, NON_MOTORIZED`
- `EnergyType`: `ELECTRIC, HYBRID, PETROL, DIESEL, OTHER`
- `BranchZoneMode`: `LINKED, COPIED`
- `BoundaryConfidence`: `SURVEYED, DIGITISED_APPROX, WARD_PROXY`

### B. Entities (design §2)
- New `ZoneTemplate`: no branch. `geometry` is a JTS `MultiPolygon`, SRID 4326, mapped like `Order.location` (x = longitude, y = latitude).
- New `ZoneRuleTemplate`: `templateVersion`, `selector` (JSON), `schedule` (JSON), `effectiveFrom`, `effectiveTo` (nullable), `legalBasis`, `note`.
- New `BranchZone`: `branch` (not null); `mode`; `sourceTemplate` (not null); `sourceTemplateVersion`; `acknowledgedTemplateVersion`; `name`; `geometry`; `enabled`; timestamps.
- New `BranchZoneRule`: `branch` (not null), `branchZone`, the same rule columns as `ZoneRuleTemplate`.
- New `VehiclePermit`: `branch` (not null); `vehicle`; `branchZone`; `permitNo`; `schedule` (JSON, nullable); `validFrom`; `validTo`; `note`.
- `VehicleType` gets nullable columns `legal_class`, `gross_weight_kg`, `energy_type`, `emission_standard`. **Do not** change `capacity`.
- Store JSON columns the way existing JSON columns are stored (see `Order.requiredSkills`).

### C. Migration (new) `resources/database/migrations/2026-10-27-lez-zones.sql`
- Follow the header style of `2026-10-18-pareto-candidates.sql` and say the user runs it manually.
- `CREATE TABLE IF NOT EXISTS` for the five tables, with FKs to `branches`, `vehicles`, `vehicle_types`, and indexes on `branch_id`.
- Unique key on (`branch_id`, `source_template_id`) for `mode = 'LINKED'`. If MySQL cannot express a partial unique key, enforce it in the service and write a comment explaining why.
- `ALTER TABLE vehicle_types ADD COLUMN …` for the four legal attributes.
- **No seed data. No real coordinates anywhere in `src/main`** (P1-seed is a later increment).

### D. Rule model and evaluation (design §3). New package `service/lez/`; pure Java, no Spring dependencies in the evaluator
- `RuleSelector` (record): `legalClasses`, `energyTypes`, `weightMeasure` (`GROSS|PAYLOAD`), `weightKg` (`gt`, `gte`, `lt`, `lte`), `emissionStandardBelow`, `appBased`. All fields are optional, and every present field must match (AND).
- `RuleSchedule` (record): `type` (`ALWAYS_FORBIDDEN|FORBIDDEN_DURING|ALLOWED_ONLY`), `daysOfWeek` (optional), `intervals` (`["HH:mm","HH:mm"]`; `24:00` is allowed as an end).
- `ScheduleNormalizer` returns a forbidden set of minutes in [0,1440) as sorted, merged half-open intervals:
  - `ALLOWED_ONLY` becomes the complement;
  - start > end means the interval crosses midnight and is split;
  - start == end is invalid.
- `RuleEvaluator.evaluate(VehicleFacts, List<RuleView>, List<PermitView>, LocalDate date, boolean branchAppBased)` returns `ZoneVerdict{kind: ALLOWED|ALL_DAY|PARTIAL, forbidden, missingAttributes, appliedPermitIds}`.
- `VehicleFacts` holds `legalClass`, `energyType`, `grossWeightKg`, `capacity` (used by `PAYLOAD`) and `emissionStandard`.
- Semantics, exactly as in design §3.3:
  - Filter rules by effective date. Filter by weekday when `daysOfWeek` is present.
  - A **null attribute needed by a present selector field counts as a match** and is added to `missingAttributes`.
  - `appBased: true` matches only when `branchAppBased` is true. In P1 the service always passes `false`: internal fleets only (request §1, 23:24).
  - Forbidden = union of the matching rules' sets, minus the windows of valid permits (`validFrom ≤ date ≤ validTo`; a null permit schedule covers the whole day).
  - Result: empty → `ALLOWED`; full day → `ALL_DAY`; otherwise `PARTIAL`.
- Validation used on writes:
  - selector and schedule parse and are well-formed;
  - `effectiveTo ≥ effectiveFrom`;
  - at most 50 rules per zone.

### E. Geometry
- `GeoJsonGeometryParser` (new): Jackson → JTS. It accepts a GeoJSON `Polygon` or `MultiPolygon` (a `Feature` wrapping one is also accepted) and always returns a `MultiPolygon`, SRID 4326.
- It rejects with 400:
  - other geometry types;
  - invalid geometry (`!isValid()`);
  - more than 5,000 vertices;
  - coordinates outside the valid lon/lat ranges.
- A writer for responses produces the same GeoJSON shape.
- Point-in-zone uses JTS `covers` (the boundary counts as inside).

### F. Services and API (design §6, §7). Repositories, services and controllers are new except where noted
Every branch-side lookup takes the branch **only** from the authenticated principal and uses a branch-scoped query (`findByIdAndBranchId` style). Another branch's ID answers exactly like a missing one: 404. Copy the scoping and role style of `VehicleService` / `RouteSendService` (SEC-TENANT-001, SEC-ROLE-001).

| Method and path | Behaviour |
|---|---|
| `GET /api/v1/zone-templates` | Summaries: id, code, name, jurisdiction, version, boundaryConfidence, boundarySource, legalBasis |
| `GET /api/v1/zone-templates/{id}` | Detail: GeoJSON geometry and the rules **of the current version** |
| `GET /api/v1/zones` | The caller's branch zones: id, mode, templateId, templateCode, name, enabled, effectiveVersion, `updateAvailable`, geometry, rules. For LINKED, geometry and rules come from the template's current version. For COPIED, they come from the branch's own copy. `updateAvailable` = COPIED and `template.version > max(sourceTemplateVersion, acknowledgedTemplateVersion or 0)` |
| `GET /api/v1/zones/{id}` | One zone, same shape |
| `POST /api/v1/zones` `{templateId, mode}` | LINKED: creates a row with `enabled = true`; a second LINKED row for the same template → 409. COPIED: copies the geometry and the rules of the template's current version and records `sourceTemplateVersion`. The response includes `warnings[]`, with one entry `{code: "DEPOT_IN_ZONE", depotId}` for each of the branch's depots covered by the zone (R09) |
| `PATCH /api/v1/zones/{id}` `{enabled}` | Enable or disable |
| `PUT /api/v1/zones/{id}/geometry` (GeoJSON body) | COPIED only; LINKED → 409. Returns the zone with the depot warnings |
| `PUT /api/v1/zones/{id}/rules` (list of rules) | COPIED only; replaces all rules; LINKED → 409 |
| `POST /api/v1/zones/{id}/acknowledge` `{version}` | COPIED only. Sets `acknowledgedTemplateVersion` (must equal the template's current version, otherwise 400). Geometry and rules unchanged |
| `DELETE /api/v1/zones/{id}` | 409 when a permit references it; otherwise deletes the zone (and its copied rules) |
| `GET /api/v1/zones/{id}/verdict?vehicleId=&date=` | Runs `RuleEvaluator` for one vehicle of the caller's branch on that date (`branchAppBased = false`). Returns the `ZoneVerdict`. This supports manual testing and is used by P1b |
| `GET/POST /api/v1/vehicles/{vehicleId}/permits`, `PUT/DELETE /api/v1/vehicles/{vehicleId}/permits/{permitId}` | The vehicle **and** `branchZoneId` must belong to the caller's branch (else 404). `validFrom ≤ validTo` (else 400). At most 50 permits per vehicle |
| existing vehicle-type endpoints | Input and output DTOs plus mapper gain `legalClass`, `grossWeightKg`, `energyType`, `emissionStandard` (all nullable, non-negative numbers). The output also gains read-only `suggestedLegalClass` from `VehicleCategory` (design §3.4). The suggestion is **never** written to `legalClass` |

**Roles.** All of the above are PLANNER-only, the same as the other planner APIs. Check how `SecurityConfig` and the role checks (SEC-ROLE-001) already treat new `/api/v1/**` paths. If the new paths would be reachable by DRIVER or CUSTOMER without changing `SecurityConfig`, **stop and report**. Do not edit `SecurityConfig`.

Errors use the existing exception classes and `ErrorCode` (constants may be added). Do not log customer names, phones, addresses or order coordinates. Zone polygons are not personal data.

## Tests to add (new files only; DB-free: Mockito and/or MockMvc standalone, like the existing tests)

| ID | Case | Expected |
|---|---|---|
| RE-T1 | Rules with effective ranges before, covering and after the date | Only the covering rule applies; `effectiveTo` null = open |
| RE-T2 | `daysOfWeek` [SAT, SUN] on a Wednesday and on a Saturday | Not applied / applied |
| RE-T3 | Selector with several fields, one not matching | No match (AND) |
| RE-T4 | Selector needs `grossWeightKg`; the vehicle has it null | Match; `missingAttributes` contains `grossWeightKg` |
| RE-T5 | `ALLOWED_ONLY` 21:00–06:00 | Forbidden = [360,1260) |
| RE-T6 | `FORBIDDEN_DURING` 22:00–02:00 | Split into [0,120) and [1320,1440) |
| RE-T7 | Two matching rules with overlapping intervals | Union, merged |
| RE-T8 | A whole-day permit; a 21:00–06:00 permit on an `ALWAYS_FORBIDDEN` rule; an expired permit | `ALLOWED`; `PARTIAL` [360,1260); ignored |
| RE-T9 | Hanoi-like rules (design §3.5) as **test fixtures**: petrol truck GROSS 4000; truck GROSS 1800; electric motorbike; petrol motorbike with null emission standard; app-based motorbike rule with `branchAppBased = false` | `ALL_DAY`; `PARTIAL` (peak hours); `ALLOWED`; restricted + missing; rule ignored |
| RE-T10 | `weightMeasure PAYLOAD` | Reads `capacity`, not `grossWeightKg` |
| RE-T11 | Invalid schedule (start == end, bad time, missing intervals) | Rejected by validation |
| GJ-T1 | Polygon, MultiPolygon, Feature(Polygon), LineString, self-intersecting polygon, 5,001 vertices | MultiPolygon ×3; 400 ×3 |
| ZS-T1 | Enable LINKED twice for the same template | Second call 409; the row carries the principal's branch |
| ZS-T2 | Copy a template whose version is 2 | Geometry and v2 rules copied; `sourceTemplateVersion` = 2 |
| ZS-T3 | `PUT` geometry/rules on a LINKED zone | 409 |
| ZS-T4 | Branch A calls get/patch/put/acknowledge/delete/verdict with branch B's zone ID | 404 each; nothing written |
| ZS-T5 | Template bumped to v3; the copy acknowledges v3 | `updateAvailable` true before, false after; geometry and rules unchanged |
| ZS-T6 | Enable a zone covering one of two branch depots | One `DEPOT_IN_ZONE` warning with that depot ID |
| ZS-T7 | Delete a zone referenced by a permit | 409 |
| VP-T1 | Permit with another branch's vehicle; with another branch's zone | 404 each |
| VP-T2 | `validFrom > validTo`; 51st permit | 400 each |
| VT-T1 | Vehicle-type mapping round trip of the four attributes; `suggestedLegalClass` for BOX_TRUCK = TRUCK while `legalClass` stays null | As stated |
| RA-T1 | DRIVER and CUSTOMER on `/api/v1/zones` and `/api/v1/zone-templates` | 403, same mechanism as the existing role tests |

If a DB-free test of any row is impossible, stop and report. Do not add a database dependency.

## Allowed changes
- New files under `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`:
  - `entity/`, `entity/enums/`, `repository/`, `service/lez/`, `controller/`, `dto/request/`, `dto/response/`
  - (`service/lez/` holds the zone and permit services as well as the evaluator)
- Edits:
  - `entity/VehicleType.java`
  - `dto/request/VehicleTypeInputDTO.java`, `dto/response/VehicleTypeDTO.java`, `mapper/VehicleTypeMapper.java`
  - `service/VehicleTypeService.java` (validation of the new fields only)
  - `util/AppConstant.java`, `util/ErrorCode.java` (new constants only)
  - `exception/GlobalExceptionHandler.java` only if a new exception type needs mapping
- New `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-27-lez-zones.sql`
- New test files under `GVRP_Entry_API/src/test/java/...`
- Work-item file you write: `work-items/LEZ-001/implementation.md`

## Forbidden
- Editing, renaming, disabling, skipping or excluding any existing test, fixture or test helper. The review compares every existing test file with `617b720`.
- `SecurityConfig`, `RouteSendService`, `OptimizationJobService`, `OptimizationCallbackService`, `RouteCustomizationService`, and any Engine API or client file (those belong to P1b/P1c).
- Tests needing MySQL, a Spring context with a datasource, a GraphHopper graph or any running service.
- Any dependency change (`pom.xml`), `application*.properties`, `.github/`, `AGENTS.md`, `docs/`.
- Real Hanoi coordinates or any seed data in `src/main`.
- Committing, pushing, rewriting history.
- Running the SQL migration against any database (the user does that).
- Logging or reporting tokens, passwords, API keys or customer data.

## Step 3 — implement
Implement sections A–F. Then, in `GVRP_Entry_API`, run `mvnw.cmd -B clean test` (L-2026-003). Everything must be green, new tests included. Report the counts and the new test class names.

## Step 4 — implementation record (no transition yet)
Write `work-items/LEZ-001/implementation.md` with a top-level section `## P1a` containing:
- Scope
- Baseline
- Changes Made (file by file)
- Commands Executed (with exit codes, including Steps 0–1)
- Verification Results
- Modified Tests (must be "none")
- Security-Relevant Changes (new endpoints, branch checks, no `permitAll`)
- Deviations From Design
- Remaining Uncertainty
- Lesson Candidates
- Handoff to Reviewer

Then save progress **without** leaving `implement`. Create `work-items/LEZ-001/checkpoint-p1a.json` from `docs/workflows/business-development/checkpoint-input.example.json`, using the current run revision. Set `next_action` to "Claude reviews P1a; then P1-seed/P1b task". Then run:
```
node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1a.json
```
**Do not** run the `ready` transition to `verify`. That happens after P1c. Tell the user you are done.
