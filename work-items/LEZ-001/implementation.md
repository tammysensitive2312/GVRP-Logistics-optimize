# Implementation — LEZ-001

## P1a

Implemented by Claude (Claude Code session, 2026-10-26/27 +07:00) from `task-opencode-p1a.md`,
on branch `feature/lez-001` (created from `master @ b057218`). Nothing is committed.
Status vocabulary follows AGENTS.md §5.

### Scope

Increment P1a of design §15: schema and entities for zone templates, template rules, branch
zones, branch rules, vehicle permits and vehicle-type legal attributes; GeoJSON polygon
parsing; `RuleEvaluator` with schedule normalisation; zone, template and permit services and
controllers; legal attributes on the vehicle-type API; the `DEPOT_IN_ZONE` warning (R09).
Requirements: LEZ-R01–R05, R09 (warning only), R14.

Not touched: Engine API, client, `SecurityConfig`, `RouteSendService`,
`OptimizationJobService`, `OptimizationCallbackService`, `RouteCustomizationService`,
`pom.xml`, `application*.properties`, `.github/`, `docs/`, `AGENTS.md`. No seed data, no
coordinates in `src/main`. The migration was **not** run against any database.

### Baseline

`GVRP_Entry_API`, `mvnw.cmd -B test` on `feature/lez-001` before any code change
(JAVA_HOME = `C:\Program Files\Java\jdk-21`, `java 21.0.1`):

- Tests run 105, failures 0, errors 0, skipped 0. BUILD SUCCESS. Exit code 0.
- Pre-existing failing tests: **none**.
- `DepotMapperTest` and `GvrpEntryApiApplicationTests` are `@SpringBootTest` classes. They
  passed because they load a real context against the local MySQL (default profile,
  `spring.jpa.hibernate.ddl-auto=validate`). CI excludes them (`.github/workflows/ci.yml`).

### Changes Made

All paths under `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/` unless stated.

**A. Enums (new, `entity/enums/`)**
- `LegalClass` — `MOTORBIKE, CAR, TRUCK, BUS, NON_MOTORIZED`; `suggestFor(VehicleCategory)` implements design §3.4.
- `EnergyType`, `BranchZoneMode`, `BoundaryConfidence` — as specified.

**B. Entities**
- New `entity/ZoneTemplate` (`zone_templates`): code (unique), name, jurisdiction, `MultiPolygon geometry` (`MULTIPOLYGON SRID 4326`), version, boundaryConfidence, boundarySource, legalBasis. No branch.
- New `entity/ZoneRuleTemplate` (`zone_rule_templates`): template, templateVersion, `selector` and `schedule` as `@JdbcTypeCode(SqlTypes.JSON)` typed records (as `Order.requiredSkills`), effectiveFrom, effectiveTo (nullable), legalBasis, note.
- New `entity/BranchZone` (`branch_zones`): branch (not null), mode, sourceTemplate (not null), sourceTemplateVersion, acknowledgedTemplateVersion, name, geometry, enabled; timestamps from `BaseEntity`.
- New `entity/BranchZoneRule` (`branch_zone_rules`): branch (not null), branchZone, templateVersion (nullable: null for branch-written rules) and the template rule columns.
- New `entity/VehiclePermit` (`vehicle_permits`): branch (not null), vehicle, branchZone, permitNo, schedule (JSON, nullable), validFrom, validTo, note.
- Edited `entity/VehicleType`: nullable `legalClass`, `grossWeightKg`, `energyType`, `emissionStandard`. `capacity` unchanged.

**C. Migration (new)** `src/main/resources/database/migrations/2026-10-27-lez-zones.sql`
- Header in the style of `2026-10-18-pareto-candidates.sql`; says the user runs it manually and before starting the app.
- `CREATE TABLE IF NOT EXISTS` for the five tables; FKs to `branches`, `vehicles`, `zone_templates`, `branch_zones`; indexes on every `branch_id`.
- LINKED uniqueness: MySQL has no partial unique index. A stored generated column `linked_template_id` (= `source_template_id` when `mode = 'LINKED'`, else NULL) with `UNIQUE (branch_id, linked_template_id)` is the database backstop; `ZoneService` is the primary guard (comment in SQL and in `BranchZone`).
- `ALTER TABLE vehicle_types ADD COLUMN` × 4. No `INSERT`.

**D. Rule model and evaluation (new, `service/lez/`, no Spring imports)**
- `RuleSelector` (+ `WeightMeasure`, `WeightBounds`), `RuleSchedule` (+ `Type`, `Day`), `MinuteInterval`, `VehicleFacts`, `RuleView`, `PermitView`, `ZoneVerdict`.
- `ScheduleNormalizer`: forbidden minute set, merge, complement, subtract, midnight split, `24:00` as end only, start == end invalid.
- `RuleEvaluator.evaluate(VehicleFacts, List<RuleView>, List<PermitView>, LocalDate, boolean)`: design §3.3.
- `RuleValidator`: strict JSON parsing of selector and schedule, selector checks, effective range, ≤ 50 rules per zone, permit schedule parsing.
- `RuleValidationException`.

**E. Geometry (new, `service/lez/GeoJsonGeometryParser`)**: Polygon / MultiPolygon / Feature → `MultiPolygon` SRID 4326 (x = lon, y = lat); refuses other types, invalid geometry, > 5,000 vertices, out-of-range coordinates, unclosed or short rings (400 via `DataInvalidException`); `toGeoJson` writer; `covers` for point-in-zone.

**F. Services, API, errors**
- New `service/lez/ZoneTemplateService`, `ZoneService`, `VehiclePermitService`, `LezSupport` (guards and mapping), `LezNotFoundException` (404), `LezConflictException` (409).
- New repositories: `ZoneTemplateRepository`, `ZoneRuleTemplateRepository`, `BranchZoneRepository`, `BranchZoneRuleRepository`, `VehiclePermitRepository`. Every branch-data query carries `branchId`.
- New controllers: `ZoneTemplateController` (`/api/v1/zone-templates`), `BranchZoneController` (`/api/v1/zones`, including `/verdict`), `VehiclePermitController` (`/api/v1/vehicles/{vehicleId}/permits`).
- New DTOs: request `BranchZoneCreateRequest`, `BranchZoneUpdateRequest`, `ZoneAcknowledgeRequest`, `ZoneRuleInputDTO`, `VehiclePermitInputDTO`; response `ZoneTemplateDTO`, `ZoneRuleDTO`, `BranchZoneDTO`, `ZoneWarningDTO`, `VehiclePermitDTO`.
- Edited `dto/request/VehicleTypeInputDTO`, `dto/response/VehicleTypeDTO` (four attributes; output adds read-only `suggestedLegalClass`), `mapper/VehicleTypeMapper` (`@AfterMapping` sets the suggestion only), `service/VehicleTypeService` (non-negative check; `update` also copies the four fields, see Deviations).
- Edited `exception/GlobalExceptionHandler`: maps `LezNotFoundException` → 404 `RESOURCE_NOT_FOUND`, `LezConflictException` → 409 `RESOURCE_CONFLICT`.
- Edited `util/AppConstant`: `ZONE_TEMPLATE`, `BRANCH_ZONE`, `ZONE_RULE`, `VEHICLE_PERMIT`, `GEOMETRY`. `ErrorCode` unchanged (existing codes suffice).

**Tests (new files only)** under `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/`:

| Class | Tests | Task IDs |
|---|---|---|
| `service/lez/RuleEvaluatorTest` | 32 | RE-T1 … RE-T11, plus JSON format under the snake_case mapper |
| `service/lez/GeoJsonGeometryParserTest` | 10 | GJ-T1 (+ range, writer round trip, `covers` boundary) |
| `service/lez/ZoneServiceTest` | 15 | ZS-T1 … ZS-T7 (+ verdict, rule replacement, 51 rules) |
| `service/lez/VehiclePermitServiceTest` | 6 | VP-T1, VP-T2 |
| `mapper/VehicleTypeLegalAttributesTest` | 5 | VT-T1 |
| `security/LezRoleAccessTest` | 10 | RA-T1 (`@WebMvcTest`, same mechanism as `RoleAccessTest`, `@MockitoBean`) |
| `controller/BranchZoneControllerTest` | 7 | HTTP 404 / 409 / 400 and snake_case wire contract for ZS-T1, ZS-T4, ZS-T7, VP-T1 |

All are DB-free (plain JUnit, Mockito, standalone MockMvc, `@WebMvcTest`).

### Commands Executed

Run from the repository root unless noted; Maven inside `GVRP_Entry_API`.

| # | Command | Exit | Result |
|---|---|---|---|
| 0.1 | `node tools/session-brief.mjs` | 0 | LEZ-001 `design` rev 3 (issue: historical evidence changed); SEC-001 (Critical) OPEN, SEC-004/005/007/008 OPEN; 14 active lessons; `PROJECT_STATE.md` and `SECURITY_CHECKLIST.md` flagged with work items finished since their last change. No STALE work item. |
| 0.2 | `git status --short` | 0 | Only `docs/engineering/LESSONS.md` and `work-items/LEZ-001/*` changed or untracked: all inside the allowed list |
| 0.3 | `git switch -c feature/lez-001` | 0 | Branch created |
| 1.1 | `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json` | 0 | `state: design`, `revision: 3`; issue "Some historical evidence is changed or unavailable" for `work-items/LEZ-001/design.md` and `traceability.md` only (edited after the last checkpoint for acceptance and §15). Not blocking under the task rule |
| 1.2 | `node tools/feature-workflow/workflow.mjs step work-items/LEZ-001/run.json ready work-items/LEZ-001/03-proof.json` | 0 | `{"state":"implement","revision":4}` |
| 2.1 | `java -version`; `echo $JAVA_HOME` | 0 | 21.0.1; `C:\Program Files\Java\jdk-21` |
| 2.2 | `mvnw.cmd -B test` | 0 | 105 run, 0 failures, 0 errors, 0 skipped |
| — | dbhub `execute_sql` (read-only `SELECT VERSION()`, `information_schema.COLUMNS`) | — | MySQL 8.0.39; existing enum columns are MySQL `ENUM`, geometry columns `point`, JSON columns `json`. No write |
| 3.1 | `mvnw.cmd -B -q compile` | 0 | Compiles |
| 3.2 | `mvnw.cmd -B test -Dtest=<the 7 new classes>` | 0 | 84 run, 0 failures (before the JSON-format test was added) |
| 3.3 | `mvnw.cmd -B clean test` | **1** | 190 run, 0 failures, **3 errors**, 0 skipped. Errors: `DepotMapperTest.testToDTO`, `DepotMapperTest.testMapperBeanIsCreated`, `GvrpEntryApiApplicationTests.contextLoads` — `SchemaManagementException: Schema-validation: missing table [branch_zone_rules]` |
| 3.4 | `mvnw.cmd -B clean test -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` (the exclusion already in `ci.yml`; nothing was changed) | 0 | 187 run, 0 failures, 0 errors, 0 skipped |
| 4.1 | `node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1a.json` | see the chat handoff | — |

### Verification Results

- **New tests: VERIFIED** — 85 new tests, all passing in clean builds 3.3 and 3.4.
- **Existing DB-free tests: VERIFIED** — all 102 still pass (3.4).
- **Full clean build (3.3): NOT green.** The two `@SpringBootTest` classes fail only because the default profile validates the schema against the local database (`ddl-auto=validate`) and the new tables do not exist there. The task forbids me to run the migration. Expected to pass after the user applies `2026-10-27-lez-zones.sql`: **Chưa xác minh** until the user runs it and `mvnw.cmd -B clean test` is repeated.
- Static checks: no coordinates or `INSERT` in the new `src/main` code or the migration; one new log line (branch id, zone id, mode, template id; no personal data).
- Not exercised: any HTTP call against a running app, any database read or write of the new tables.

### Modified Tests

**None.** `git diff --name-status 617b720 -- GVRP_Entry_API/src/test` shows no change to existing files; `git status` shows only the new test files listed above. No fixture, helper or CI command was changed.

### Security-Relevant Changes

- New endpoints (all authenticated; PLANNER only through the existing `/api/v1/** hasRole("PLANNER")` rule; `SecurityConfig` unchanged; **no new `permitAll`**):
  - `GET /api/v1/zone-templates`, `GET /api/v1/zone-templates/{id}` (system data, no branch data)
  - `GET|POST /api/v1/zones`, `GET|PATCH|DELETE /api/v1/zones/{id}`, `PUT /api/v1/zones/{id}/geometry`, `PUT /api/v1/zones/{id}/rules`, `POST /api/v1/zones/{id}/acknowledge`, `GET /api/v1/zones/{id}/verdict`
  - `GET|POST /api/v1/vehicles/{vehicleId}/permits`, `PUT|DELETE /api/v1/vehicles/{vehicleId}/permits/{permitId}`
- RA-T1 proves DRIVER and CUSTOMER get 403 and anonymous gets 401 on the new paths, and that the service is not called.
- Branch scoping: the branch comes only from `CurrentUserUtil.getCurrentBranchId()` (request attribute set by the JWT filter from the principal). A null branch is refused with 403 before any query (L-2026-002). Lookups: `BranchZoneRepository.findByIdAndBranchId`, `VehicleRepository.findByIdAndFleetBranchId`, `VehiclePermitRepository.findByIdAndVehicleIdAndBranchId`, rule and permit lists filtered by `branchId`, depots by `findByBranchId`. A permit needs the vehicle **and** the zone of the caller's branch. Another branch's ID → 404, nothing written (ZS-T4, VP-T1).
- New rows take the branch from the principal (`branchRepository.findById(principal branch)`); copies are created in the caller's branch only.
- Bounded inputs: ≤ 5,000 vertices per polygon, ≤ 50 rules per zone, ≤ 48 intervals per schedule, ≤ 50 permits per vehicle, `permitNo` ≤ 100 characters.
- No secrets, tokens or customer data in code, logs, tests or this record. Test geometry is abstract (0–30° squares, unit circle).
- No dependency added.

### Deviations From Design

1. **404/409 exception types.** The codebase maps "not found" to **400** (`DataInvalidException` with `RESOURCE_NOT_FOUND`) and has no general 409 type. The task requires 404 and 409, so I added `LezNotFoundException` and `LezConflictException` and mapped them in `GlobalExceptionHandler` (allowed "if a new exception type needs mapping"). They live in `service/lez/` because `exception/` is not in the task's list of directories for new files.
2. **Permit schedule semantics** (the design does not define them). A null schedule covers the whole day. Otherwise the permit uses the rule vocabulary and covers the minutes its schedule does *not* forbid: `ALLOWED_ONLY 21:00–06:00` covers 21:00–06:00 (RE-T8). `ALWAYS_FORBIDDEN` is refused for a permit (400). A permit with `daysOfWeek` covers nothing on other days.
3. **Selector and schedule JSON stay camelCase** (`legalClasses`, `daysOfWeek`, …) in storage and on the wire, through explicit `@JsonProperty`, while the rest of the API is snake_case (`WebConfig`). This keeps design §3.1–3.2 and the P1-seed SQL independent of the mapper. Requests take them as raw JSON and parse them with a strict mapper, so a malformed rule is a 400.
4. **Validation beyond the task list:** `weightKg` and `weightMeasure` must come together; `weightKg` needs at least one bound; bounds and `emissionStandardBelow` ≥ 0; `daysOfWeek` non-empty when present; `ALWAYS_FORBIDDEN` must not carry intervals; ≤ 48 intervals; unknown JSON properties are refused.
5. **`missingAttributes`** collects only the unknown facts of rules that ended up matching (a rule that fails on a known fact reports nothing). They are reported even when a permit then lifts the restriction.
6. **Midnight crossing:** both parts of a split interval belong to the same service date, and the weekday filter is tested on that date (design §3.2 "on the service date"). The part after midnight is therefore not attributed to the next day.
7. **Warnings:** `POST /zones`, `PUT /zones/{id}/geometry` and `PATCH` with `enabled: true` return `warnings[]`; GET responses omit it.
8. **`ZoneTemplateDTO`** is one class for summary and detail (detail adds `geometry` and `rules`; null fields omitted). `BranchZoneDTO` also exposes `sourceTemplateVersion`, `acknowledgedTemplateVersion` and the template's current `templateVersion`, for the P1c notice.
9. **`VehicleTypeService.updateVehicleType`** now copies the four legal fields as well. The task allowed "validation of the new fields only", but without this a PUT could not store them. The existing PUT is a full replacement, so a client that omits the fields (today's client, until P1c) clears them.
10. **Enum columns** are MySQL `ENUM` in the migration, matching what the local database holds for comparable columns (e.g. `pareto_candidates.frontier_status`), although the pareto migration file declared `VARCHAR`.
11. **Verdict parameters**: a missing `vehicleId`/`date` or a malformed date is a 400 from the code, not Spring's default handling (which the generic handler would turn into a 500).
12. No in-service role check (as in `VehicleService`); roles are enforced by `SecurityConfig` and covered by RA-T1. `RouteSendService` additionally checks roles in the service.

### Remaining Uncertainty

- **Schema and persistence: Chưa xác minh.** The migration was not executed. Unverified: MySQL syntax of the `SRID` attribute and the generated column; `ddl-auto=validate` acceptance of `ENUM`, `MULTIPOLYGON` and `JSON` for the new mappings; Hibernate's JSON format mapper on the record types; polygon write/read through hibernate-spatial with SRID 4326 (axis order and ring orientation; only `POINT` has been exercised in this project, design §15).
- **Full clean build** stays red locally until the migration is applied (see Verification).
- **`PAYLOAD` unit:** reads `capacity` as kilograms. The unit of `capacity` is not recorded (context §2.6). Chưa xác minh.
- **Pre-existing behaviour observed by reading, not changed:** `GlobalExceptionHandler`'s catch-all `Exception` handler turns `@Valid` failures, unreadable JSON bodies (for example an unknown `mode` value) and type-mismatched path parameters into **500**. The new endpoints avoid `@Valid` and validate in code, but an unreadable body is still a 500. Not verified at runtime.
- **Concurrency of the LINKED check:** two simultaneous LINKED creates can pass the service check; the database key then rejects one (mapped to 409). Depends on the migration being applied before Hibernate creates the table.
- Depot warnings test the depot point only; the vehicles affected (R09 "listing the affected vehicles") are not listed, as specified by the task.

### Lesson Candidates

1. **New entities break the local `@SpringBootTest` classes until the migration runs.** The default profile uses `ddl-auto=validate` against the developer's MySQL, so `DepotMapperTest` and `GvrpEntryApiApplicationTests` fail with "missing table" in any change that adds an entity, while CI (which excludes them) stays green. A task spec that adds entities should say whether the user applies the migration before verification, or that the full local run is expected to show these two classes failing.
2. **"Not found" is HTTP 400 in this codebase.** `DataInvalidException` with `RESOURCE_NOT_FOUND` maps to 400. A spec that asks for 404 needs a new exception type and a handler mapping; say where it may live.
3. **Standalone MockMvc and `CurrentUserUtil`:** the dispatcher replaces `RequestContextHolder` per request, so principal attributes must be set on the request (`defaultRequest(get("/").requestAttr("branchId", …))`), not on a thread-bound request set up in the test.

### Handoff to Reviewer

- Review against `617b720` for tests (L-2026-009: normalise line endings) and against `b057218` for code. Nothing is committed; the branch is `feature/lez-001`.
- Start with `service/lez/RuleEvaluator`, `ScheduleNormalizer`, `ZoneService` (tenant scoping, LINKED/COPIED handling) and `VehiclePermitService`.
- Decide on Deviations 1, 2, 3 and 9; they affect P1b (evaluator reuse) and P1c (client wire format).
- Ask the user to apply `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-27-lez-zones.sql` to the local database, then repeat `mvnw.cmd -B clean test` in `GVRP_Entry_API` (expected 190/0/0/0) and a manual smoke test of one polygon write/read (Remaining Uncertainty).
- The run stays in `implement` (revision 4) with a checkpoint; the `ready` transition to `verify` happens after P1c.

## P1b

Implemented by Claude (OpenCode session, 2026-10-27 +07:00) from `task-opencode-p1b.md`, continuing
§P1a on branch `feature/lez-001`. Nothing is committed. Status vocabulary follows AGENTS.md §5.

### Scope

Increment P1b of design §15 / §16.2-1…-7: the engine carries LEZ skills and per-leg geometry, Entry
compiles restrictions once per job, checks compliance and certifies every route, guards the send, and
exposes the compliance panel and map geometry. Task rows EB-T1…TI-T1.

Not touched: `SecurityConfig`, `pom.xml`, `application*.properties`, `.github/`, `docs/`, `AGENTS.md`,
the Angular/legacy clients, `GVRP_Document`. No commit, no push, no migration executed, no second
GraphHopper graph (the local graph was loaded read-only once, for a payload measurement, see 3.3).

### Baseline

Both baselines were taken on `feature/lez-001`, `JAVA_HOME=C:\Program Files\Java\jdk-21` (L-2026-007),
before the P1b code was written:

| Module | Command | Result |
|---|---|---|
| `GVRP_Entry_API` | `mvnw.cmd -B clean test` | **190 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 1:40 (`baseline-entry-p1b.log`) |
| `GVRP_Engine_API` | `mvnw.cmd -B clean test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | **142 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 1:16 h (`baseline-engine-p1b.log`) |

Pre-existing failing tests: **none** in either module (`MeasuredLawsTest` passed; KI-030 did not
reproduce). The Engine baseline uses the same D6 exclusions as the verification step, so the two
excluded classes were never run (Deviation 1). The Engine baseline's 1:16 h is the 1001×1001 matrix
(53 min) plus the three slow jsprit benchmark runs of `JspritPerformanceBenchmarkTest` (~9 min each),
which is **not** excluded and does run again in verification.

### Changes Made

**Engine (`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/`)**

- **E1 — LEZ skills.** `model/Order.lezSkills`, `model/Vehicle.lezSkills` (`Set<String>`);
  `OptimizationService.addVehicleSkills` / `addRequiredSkills` add them beside the existing skills
  through `normalizedSkills` (trim, upper case, blank removed, deduplicated). A missing field adds
  nothing, so the pre-P1b problem is unchanged.
- **E2 — Geometry.** `model/Stop.geometryToNext`; new `distance_matrix/RoutePathProvider.path(from,
  to, locale)` returning `model/LegPath` (polyline, metres, milliseconds, instructions); new
  `utils/PolylineEncoder` (Google algorithm, precision 1e5, `[lat, lon]`, encode + decode);
  `service/LegGeometryService.fillGeometry(stops, routeIndex)` fills every leg except the last and
  leaves a failed leg null; `OptimizationService.fillLegGeometry` runs on the single-objective result
  and on every Pareto alternative; `RouteEvaluationService` fills the same field on `evaluate`
  (through a 2-arg constructor that creates `LegGeometryService`); `SolutionConverter` emits
  `geometry_to_next`; `GraphHoperDistanceProvider.path(...)` implements the interface with the same
  router, profile, custom model and CH as the matrix.
- **E3 — Turn instructions.** `model/LegInstruction`, `model/RouteLegsRequest` (2…1001 points +
  locale), `model/RouteLegsResponse` (locale + one leg per pair, per-leg `error`), `service/LegsService`
  (validation, per-leg routing, `ROUTE_NOT_FOUND` for a leg the router refuses, locale resolution),
  `POST /api/v1/optimization/legs` in `OptimizationController` (field-injected `LegsService` keeps the
  historical five-arg constructor). Optimisation results still carry no instructions.

**Entry (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`)**

- **N1 — Schema and entities.** Migration `resources/database/migrations/2026-10-28-lez-compliance.sql`:
  `routes.lez_status|lez_checked_at|lez_ruleset_hash|lez_violations`,
  `route_stops.geometry_to_next|instructions_to_next`, `optimization_jobs.lez_snapshot`,
  `unassigned_orders.reason_code`. Entities `Route`, `RouteStop`, `OptimizationJob`,
  `UnassignedOrder` gain the fields; new `entity/enums/LezStatus` (`UNCHECKED`, `COMPLIANT`,
  `VIOLATION`); `DriverStopDTO` gains `geometryToNext`, `instructionsToNext`, `zoneNames`.
- **N2 — Engine DTOs and client.** `EngineOrderDTO.lezSkills`, `EngineVehicleDTO.lezSkills`,
  `EngineOrderDTO`/`shared` stop `geometryToNext`, `EngineEvaluateResponseDTO` leg geometry,
  `EnginePlanMaterializer`/revision copy the geometry onto `RouteStop`, new
  `EngineLegsRequestDTO`/`EngineLegsResponseDTO`, `EngineApiClient.legs(points, locale)` and its
  `RestTemplate` implementation (400 → `DataInvalidException`, unreachable/5xx →
  `EngineUnavailableException` = 502).
- **N3 — Compiler.** `service/lez/ZoneRuleLoader` (repository reads extracted so `ZoneService`'s
  constructor is untouched), `service/lez/CompiledRestrictions` (zones, `vehicleRestrictions`,
  `orderZones`, `unusableVehicles`, warnings, SHA-256 `hash`), `service/lez/RestrictionCompiler`
  (enabled zones and their effective rules, evaluator reuse, `PARTIAL` and `ALL_DAY`, permits,
  membership of orders and depots, unusable vehicles, data-missing facts, canonical hash input).
- **N4 — Job submit.** `OptimizationJobService`: D4 single delivery date for every branch (400 listing
  both dates), compile for the job's date, drop unusable vehicles, 400 naming zone and depot when none
  is left, write `lez_snapshot` in the same transaction (empty compilation included), apply
  `LEZ_<zoneKey>` to in-zone orders and to allowed vehicles. Skills are not attached to `evaluate`.
- **N5 — Checker.** `service/lez/PolylineCodec` (decode `[lat, lon]`) and
  `service/lez/ComplianceChecker` (JTS; stop/leg-vs-polygon; `ALL_DAY` and `PARTIAL` minutes;
  `ZONE_ACCESS`, `ZONE_TRANSIT`, `LEZ_DATA_MISSING`, `LEZ_GEOMETRY_MISSING`; time windows from stored
  times; pure, no Spring).
- **N6 — Certification.** `service/lez/RouteComplianceService` (`compileFor`, `currentFor`,
  `check`, `certify`, `readSnapshot`/`writeSnapshot`, reason helpers) plus hooks:
  callback (a) certifies from the job snapshot and sets
  `UnassignedOrder.reason_code = LEZ_NO_COMPLIANT_VEHICLE` for an order with zone keys that no vehicle
  in the request could serve; Pareto apply (b) certifies the applied candidate's routes against
  current restrictions; `RouteCustomizationService` (c) appends LEZ violations to the preview and
  treats them as hard in `saveRevision` before certifying the saved routes.
- **N7 — Send guard.** `RouteSendService.enforceLezCompliance`: mixed dates → 422 `LEZ_MIXED_DATES`;
  current restrictions; `fillMissingLegs` calls the engine once per route when any leg lacks geometry
  or instructions; certify; any violation → `persistViolation` (own transaction) and 422 listing code,
  sequence and zone key. The driver read path returns the stored geometry, instructions and the names
  of the enabled zones covering each stop.
- **N8 — Read APIs.** `service/lez/ComplianceReadService` and `controller/LezComplianceController`:
  `GET /api/v1/solutions/{id}/compliance` (per route `{routeId, vehicleId, status, checkedAt,
  violations}`, `STALE` computed on read), `POST /api/v1/solutions/{id}/compliance/recheck`
  (recomputes and writes), `GET /api/v1/routes/{id}/geometry` (legs `{fromSeq, toSeq, polyline,
  instructions}`). New DTOs `SolutionComplianceDTO`, `RouteGeometryDTO`, `RouteLegInstructionDTO`.
  Branch always comes from the principal; another branch's id answers 404.
- **Shared helper** `service/lez/LegInstructionCodec` stores and reads the instruction list as the
  JSON text of `route_stops.instructions_to_next`.

**Tests (new files only)**

| Module | Class | Tests | Task rows |
|---|---|---|---|
| Engine | `utils/PolylineEncoderTest` | 4 | EB-T3 |
| Engine | `service/LegGeometryServiceTest` | 3 | EB-T4 |
| Engine | `controller/LegsEndpointTest` | 7 | EB-T5, EB-T6 |
| Engine | `service/LezSkillMappingTest` | 4 | EB-T1 |
| Engine | `service/LezSkillAssignmentTest` | 2 | EB-T2 |
| Entry | `service/lez/ComplianceCheckerTest` | 11 | CC-T1…CC-T5 |
| Entry | `service/lez/RestrictionCompilerTest` | 8 | RC-T1…RC-T3 |
| Entry | `service/OptimizationJobLezTest` | 5 | JS-T1…JS-T3 |
| Entry | `service/OptimizationCallbackServiceLezTest` | 5 | CB-T1 |
| Entry | `service/RouteCustomizationServiceLezTest` | 3 | CU-T1, CU-T2, N6c |
| Entry | `service/RouteSendServiceLezTest` | 4 | SG-T1…SG-T4 |
| Entry | `service/lez/ComplianceReadServiceTest` | 8 | TI-T1, read side |
| Entry | `security/LezComplianceRoleAccessTest` | 9 | RA-T2 |
| Entry | `service/lez/LezFixtures` (support, no tests) | — | shared polygons/polylines |

All are DB-free and graph-free: repositories, entity manager, engine client and `RoutePathProvider`
are Mockito substitutes or lambdas; the `@WebMvcTest` slice is the same one `LezRoleAccessTest` uses.

### Commands Executed

From the repository root unless stated; Maven inside the named module. `EXIT` is Maven's exit code
(the PowerShell pipeline that also filters the log returned 1 on success; the Maven line is quoted).

| # | Command | Exit | Result |
|---|---|---|---|
| 0.1 | `node tools/session-brief.mjs` | 0 | LEZ-001 `implement` rev 5; task state only |
| 0.2 | `git status --short` | 0 | Only LEZ-001 paths, `docs/engineering/LESSONS.md`, `GVRP_Entry_API`/`GVRP_Engine_API` sources — inside the allowed list |
| 0.3 | `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json` | 0 | `state: implement`, `revision: 5`; single issue "historical evidence changed" for `design.md` and `traceability.md` (accepted in §P1a, not blocking) |
| 1.1 | Entry: `mvnw.cmd -B clean test` | 0 | 190/0/0/0, BUILD SUCCESS (`baseline-entry-p1b.log`) |
| 1.2 | Engine: `mvnw.cmd -B clean test "-Dtest=!JspritConvergenceBenchmarkTest,!CircuityFactorMeasurementTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | 0 | 142/0/0/0, BUILD SUCCESS, 1:16 h (`baseline-engine-p1b.log`) |
| 2.1 | Entry: `mvnw.cmd -B -o compile` | 0 | BUILD SUCCESS |
| 2.2 | Entry: `mvnw.cmd -B -o test-compile` | 0 | BUILD SUCCESS |
| 2.3 | Entry: targeted run of the eight new classes | 0 | 53/0/0/0 |
| 2.4 | Engine: targeted run of the five new classes | 1 → 0 | first 20 run / 4 failures, all mine (case of jsprit skill names, one assertion still holding the pre-edit text); after the fix 20/0/0/0 |
| 2.5 | Engine: targeted `LegsEndpointTest,LegGeometryServiceTest` after the log fix of Deviation 13 | 0 | 10/0/0/0 |
| 3.1 | Entry: `mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` | 0 | **240 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (`step3-entry-p1b.log`) |
| 3.2 | Engine: same command as 1.2 on the final tree | 0 | **162 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 37:09 (`step3-engine-p1b.log`) |
| 3.3 | Java probe over the local GraphHopper graph (read-only), `GhProbe` + `GhPayloadProbe2` | 0 | locale map and payload measurement, see Verification |
| 3.4 | `node tools/feature-workflow/workflow.mjs checkpoint … checkpoint-p1b.json` | 0 | recorded, no transition |

One Engine verification run was started and then **stopped on purpose** (surefire JVM killed) after the
first minutes: reading the new `path()` had shown that it logged GraphHopper's error text, which names
the offending point (Deviation 13). The run was restarted on the fixed tree; its log replaced the
aborted one, so only the final run is on record.

### Verification Results

- **Engine full suite: VERIFIED (3.2)** — **162 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
  162 = the 142 baseline tests + the 20 new ones (4 `PolylineEncoderTest`, 3 `LegGeometryServiceTest`,
  7 `LegsEndpointTest`, 4 `LezSkillMappingTest`, 2 `LezSkillAssignmentTest`), and every one of them
  appears by name in `step3-engine-p1b.log`. No test failed, so the "same pre-existing failures only"
  requirement is met trivially (the baseline had none).
- **Engine wall time: MEASURED, cause not attributed.** This run took 37:09 against the baseline's
  1:16 h; the difference sits almost entirely in `JspritPerformanceBenchmarkTest`'s own 1001×1001
  matrix (829.9 s here vs 3 193.1 s in the baseline; 1 206 vs 313 route/s). Test counts and outcomes are
  identical, so nothing about correctness follows from the difference; the likely cause is machine load
  or OS file cache for the routing graph, and it is **not verified** which.
- **Engine new tests: VERIFIED (2.4, 2.5)** — 20 tests, all pass. They were also part of 3.2.
- **Entry new tests: VERIFIED (2.3, 3.1)** — 53 tests, all pass.
- **Existing tests: VERIFIED, none changed** — Entry 187 DB-free tests still pass (240 = 187 + 53);
  Engine 142 baseline tests still pass (3.2).
- **Entry full local build: PARTIALLY_VERIFIED.** `GvrpEntryApiApplicationTests` and `DepotMapperTest`
  were excluded because the default profile validates the schema (`ddl-auto=validate`) and the P1b
  migration is not applied yet — the same P1a lesson. They are expected to pass after the user applies
  `2026-10-28-lez-compliance.sql`; the full `mvnw.cmd -B clean test` (expected 243) has **not** been run.
- **GraphHopper locale: VERIFIED (3.3).** With the exact `graphhopper-core`/`graphhopper-web-api`
  11.0 jars, `TranslationMap.getWithFallBack` maps `vi` → `vi-VN`, `en` → `en-US`, an unknown tag →
  `en-US`, and `vi_VN` (underscore) → `en-US`. So `LegsService.resolveLocale` returns `vi-VN` for the
  `vi` that Entry sends, and the response echoes `vi`. On the local graph, all 19 legs of the measured
  route came back with Vietnamese instruction text, so the language is really applied, not just echoed.
- **Callback payload with geometry: MEASURED (3.3).** A 20-stop / 19-leg route of 18.14 km through
  Hanoi produced **1 365 UTF-8 bytes of encoded polyline** (5 characters need JSON escaping) and
  **4 818 UTF-8 bytes of Vietnamese instruction text in 176 instructions**. The completion callback
  carries only the polylines (+~1.4 kB per such route, ≈72 B/leg); the instructions are fetched at
  send time and stored, so they add their ~4.8 kB to the send response, not to the callback.
- **Log hygiene: VERIFIED by inspection.** No new log line carries coordinates, customer data, tokens
  or secrets; the one that did was fixed before the final run (Deviation 13).
- **Not exercised:** any database read/write of the new columns, any HTTP call against a running
  Entry/Engine, the real `sendRoute` transaction with `REQUIRES_NEW`, the Angular/legacy clients, and
  the migration itself.

### Modified Tests

**None.** `git diff --name-status -- GVRP_Engine_API/src/test GVRP_Entry_API/src/test` is empty and
`git status --short` lists only new test files (the P1a ones and those in the table above). No fixture,
helper, existing test or build/CI command was changed. The two D6 exclusions exist only on the command
line.

The test files I wrote and then corrected during 2.4 are mine, not pre-existing: the assertions in
`LegsEndpointTest` and `LezSkillMappingTest` were wrong (jsprit reports skill names lower-cased;
one assertion still held text I had already replaced in the fixture). Correcting a new test's own
expectation is not weakening an accepted specification.

### Security-Relevant Changes

- **New Entry endpoints** (authenticated, PLANNER through the existing `/api/v1/**` rule;
  `SecurityConfig` unchanged, no new `permitAll`):
  `GET /api/v1/solutions/{id}/compliance`, `POST /api/v1/solutions/{id}/compliance/recheck`,
  `GET /api/v1/routes/{id}/geometry`. RA-T2 proves DRIVER and CUSTOMER get 403 and anonymous 401.
- **Tenant isolation.** The branch comes only from `CurrentUserUtil.getCurrentBranchId()`. The
  compliance service loads the solution and refuses a row of another branch with
  `LezNotFoundException` (404, the same answer as a missing id) before any computation; geometry does
  the same through the route's solution. A null branch context is 403. TI-T1 covers
  compliance/recheck/geometry; SG-T4 keeps the existing send refusal.
- **Driver payload.** `DriverStopDTO` gains geometry, instructions and **zone names only** — no
  polygon and no rule data crosses to the driver; the existing ownership check is unchanged.
- **Engine `/legs`.** It is a new synchronous routing endpoint, limited to 2…1001 points, and it
  logs only the leg index and the exception class. The engine module has no request authentication
  today (no Spring Security, no key filter); `/legs` therefore inherits the existing posture rather
  than widening it. Not changed here (out of scope) and handed to the reviewer as a security
  observation.
- **Bounded work.** One routing query per leg, at most 1000 per request, and the send path makes at
  most one `/legs` call per route. Solver work is unchanged.
- **No secrets, no customer data.** Test geometry is synthetic (0–30° squares, unit-ish polylines);
  the measurement script ran locally and its output contains no customer data.
- **No dependency added**; `pom.xml` untouched.

### Deviations From Design

1. **D6 — two Engine test classes are excluded on the command line.** `JspritConvergenceBenchmarkTest`
   and `CircuityFactorMeasurementTest` are not run in Step 1/3 (user decision, 2026-10-27). Only the
   command line carries the exclusion; no file, surefire configuration or CI workflow was changed.
   Consequence: their state on this branch is **NOT_VERIFIED** for P1b.
2. **Ruleset hash scope.** The route's verdict uses the **job snapshot** (what the solver actually
   ran with), but the stored `lez_ruleset_hash` is the hash of a **solution-scoped** current compile
   (`currentFor(Solution)`), not the snapshot hash. The snapshot covers the job's full vehicle set and
   every order of the job, while the read side compares a route against the solution's own
   restrictions; storing the snapshot hash would make every route `STALE` after any unrelated order
   or vehicle change, and a snapshot hash could never match the read-side compile. Design §16.2-6
   says "compiled restrictions for (branch, date)" without fixing the scope.
3. **JSON names.** Engine and Entry map JSON to `SNAKE_CASE` globally, so `distanceM`/`timeMs`/
   `geometryToNext`/`streetName` travel as `distance_m`/`time_ms`/`geometry_to_next`/`street_name`.
   The DTO fields keep the codebase's camelCase style.
4. **Optional collaborators instead of constructor changes.** `RouteComplianceService` (callback,
   Pareto, customization, send) and `EngineApiClient` (send) are `@Autowired(required = false)` fields
   with a null guard; `OptimizationService` keeps its three-field constructor and `RouteEvaluationService`
   keeps its one-arg constructor beside the new `@Autowired` two-arg one. This is a §14 constraint, not
   a design choice: the historical unit tests build these classes with `@InjectMocks`/`new` and must not
   be edited. Consequence: a missing bean silently disables the LEZ hook (fail-open for certification,
   never for the send guard's legal check — see Remaining Uncertainty).
5. **`CompiledRestrictions` shape.** It is a JSON-friendly POJO rather than the design's abstract
   "compiled restrictions": per-zone `kind` as a string, forbidden minutes as `List<int[]>`,
   `missingAttributes`, `appliedPermitIds`, and the hash as a hex string. The snapshot is stored as
   JSON **text** in a `String` field, like the existing `input_data`.
6. **`ZoneRuleLoader`.** Rule loading moved into a package-private static helper taking the
   repositories as parameters, so `ZoneService`'s constructor and its eight `@InjectMocks` tests stay
   untouched.
7. **Violation persistence in its own transaction.** The send refusal throws
   `UnprocessablePlanException`, which rolls the surrounding transaction back — including the verdict
   that proves the refusal. `RouteComplianceService.persistViolation` therefore uses
   `Propagation.REQUIRES_NEW` with an `EntityManager` bulk update (no entity re-attachment).
   Superseded by §P1b fix (review-p1b F1).
8. **`ComplianceReadService` checks the tenant inline.** `SolutionService.belongsTo` is package-private
   and the read service lives in `service/lez`; the check is three lines over the loaded solution.
9. **`LezNotFoundException` stays in `service/lez`.** `exception/` is not in the allowed-change list and
   P1a already put the type there; the P1a handler mapping is reused unchanged.
10. **`/legs` answers 200 when a single leg is unroutable**, with `polyline: null` and
    `error: ROUTE_NOT_FOUND` on that leg. The task's "engine unavailable → send fails closed" is about
    the call failing; a refused leg is a routing fact, and the send guard then fails closed anyway
    because the checker reports `LEZ_GEOMETRY_MISSING`. A 400 is returned only for an invalid request.
11. **Locale echo.** The response echoes the **language tag** (`vi`) rather than the resolved
    `vi-VN`, matching the task's wording and keeping the response small; the resolved tag is what the
    instructions really use.
12. **Instructions are stored as JSON text** (`route_stops.instructions_to_next`, a `JSON` column) in
    the shape the engine returns (text, distance, time, sign, street). The migration comment says
    "turn-by-turn instructions", not a serialisation format.
13. **Coordinate-safe routing logs (hardening beyond the task).** The new `path()` originally logged
    `response.getErrors()`, whose text names the offending point; it now logs only the error count and
    throws a message without coordinates. The pre-existing `fetch()` in the same file had the same
    defect and was changed the same way — same file, same defect class, no behaviour change. This is
    the one change outside the task's strict list; it is recorded here rather than done silently.
14. **`OptimizationJobService` still stores the compiled snapshot even when the branch has no zones**
    (empty compilation). Design §16.2-7 only requires "behaviour unchanged"; storing the empty
    compilation is what lets the read side distinguish "certified with no zones" from "never checked".

### Remaining Uncertainty

- **Schema and persistence: Chưa xác minh.** The migration was not executed. Unverified: MySQL
  acceptance of the `JSON`/`ENUM` columns, `ddl-auto=validate` against the new entity mappings,
  Hibernate writing a `String` into a `JSON` column, and `MEDIUMTEXT` for polylines of very long legs.
- **`REQUIRES_NEW` violation persistence: Chưa xác minh** against a real transaction manager. The test
  observes the `EntityManager` calls, not a committed row.
- **The whole send path (N7) is NOT_VERIFIED end to end**: no DB, no engine, no `RouteSend` row was
  written. The mixed-date 422, the one-call leg fill, the 502 and the verdict persistence are verified
  only in isolation.
- **Engine over HTTP:** `/legs` was exercised through the controller object and through GraphHopper
  directly, never through Spring MVC + Jackson over the wire. `INDENT_OUTPUT` is enabled in the engine
  mapper, so real responses are pretty-printed (larger, still valid).
- **Callback size** is measured on one synthetic 18.14 km route on the local graph, not on a real
  branch's plan; the growth is linear in legs (~72 B per leg) plus escaping (`"` and `\` occur in the
  polyline alphabet, 5 characters here).
- **Time semantics.** Compliance minutes are minutes from 08:00 of the service date (the migration
  comment; the solver's trips start at 08:00). Whether that matches the planner's real shift start on
  every branch is **Chưa xác minh**; P2 will move avoidance into the solver and must revisit it.
- **Fail-open field injection (Deviation 4).** If Spring ever fails to wire `RouteComplianceService`,
  the callback, Pareto and customization hooks do nothing and the send guard silently skips the LEZ
  check. The `@Autowired(required = false)` keeps old tests intact but hides a wiring error at
  runtime; a startup assertion would be better and needs a design decision.
- **D4 is a behaviour change**: a job whose orders span two dates is now 400 for every branch, so any
  existing client that submits mixed dates breaks. The clients were not touched and were not run.
- **Tenant isolation** is verified at the service level (TI-T1) and by the role slice (RA-T2), not
  through a live request with a real JWT.
- **`UnassignedOrder.reason_code`** replaces the engine reason only when the order has zone keys and
  no vehicle in the request carried all its skills; the interactive `LEZ_UNREACHABLE` distinction of
  design §16.2-2 is not implemented in P1b.

### Lesson Candidates

1. **jsprit lower-cases skill names.** `Skills.values()` returns lower-cased values and matching is
   case-insensitive; a test that asserts the upper-case form fails while the behaviour is correct.
   Assert with `containsSkill` and a size, not with `values()` equality.
2. **`TranslationMap` needs the language tag, not the locale-with-underscore.** `vi` and `vi-VN`
   resolve to `vi-VN`; `vi_VN` silently falls back to English. A feature that sends a locale should
   pin this with a one-line probe against the exact jar version.
3. **GraphHopper error messages carry coordinates.** `response.getErrors()` names the offending point,
   so it must never reach a log or an exception message in a product that must not log coordinates.
4. **A clean Maven `test` in the Engine module costs 1:16 h** because `JspritPerformanceBenchmarkTest`
   builds its own 1001×1001 matrix (53 min) and runs three 1000-job optimisations. Run the new test
   classes first (`-Dtest=…`) and only then the full command; budget the wall time.

### Handoff to Reviewer

- Review the Engine side first: `LegsService`, `LegGeometryService`, `GraphHoperDistanceProvider.path`
  (locale, escaping, error handling), `PolylineEncoder` and the skill wiring in `OptimizationService`.
- Then the compiler and the checker (`RestrictionCompiler`, `ComplianceChecker`) against design §3–§5,
  and the four certification hooks, which are the places where a verdict can silently be skipped
  (Deviation 4).
- Decide on Deviations 2 (hash scope), 10 (200 with a per-leg error), 13 (log hardening in a file
  outside the strict list) and 14 (snapshot for zone-free branches).
- Ask the user to apply `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-28-lez-compliance.sql`
  (after `2026-10-27-lez-zones.sql`), then repeat `mvnw.cmd -B clean test` in `GVRP_Entry_API`
  (expected 243/0/0/0) and smoke-test one send and one compliance recheck.
- Security: open a checklist entry for the unauthenticated Engine `/legs` endpoint if the reviewer
  agrees it is a finding, not an accepted internal-network posture.
- The run stays in `implement` with a checkpoint; there is no transition to `verify` in P1b.

## P1b fix

Fix for review-p1b §3 F1 only (spec: `work-items/LEZ-001/task-opencode-p1b-fix.md`). Nothing from
F2–F8 was changed.

### Scope

- **Defect.** `RouteSendService.sendRoute` holds `SELECT … FOR UPDATE` on the route row
  (`routeRepository.findWithLockById`) and called `RouteComplianceService.persistViolation`, which opened
  `Propagation.REQUIRES_NEW` and issued a bulk `UPDATE routes` for the same row. The second transaction
  waited on the lock held by its own caller until `innodb_lock_wait_timeout` (~50 s), then failed: the
  client saw a 500 after the wait and the verdict it was meant to preserve was never committed.
- **Files.** The four allowed source/test files plus this record and `checkpoint-p1b-fix.json`:
  `service/RouteSendService.java`, `service/lez/RouteComplianceService.java`,
  `service/RouteSendServiceLezTest.java`, new `service/RouteSendTransactionTest.java`.

### Baseline

Command (repository `GVRP_Entry_API`, `JAVA_HOME` = JDK 21):

```text
mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"
```

Result: **240 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, 30.117 s**
(`baseline-entry-p1bfix.log`). The two exclusions are the same ones used for P1b: the migration
`2026-10-28-lez-compliance.sql` is still not applied to `gvrp_db` (checked: `routes` has no `lez_*`
columns, `route_stops` has no geometry column), so the two `@SpringBootTest` classes that validate the
new schema cannot pass yet. They are excluded on the command line only; no file, surefire configuration
or CI workflow was changed.

### Changes Made

1. **C1 — the refusal commits the verdict.** `sendRoute` is now
   `@Transactional(noRollbackFor = UnprocessablePlanException.class)`. `enforceLezCompliance` no longer
   calls `persistViolation`: `certify(route, violations, restrictions)` writes the verdict on the managed,
   locked `route`, and that state now commits together with the legs filled by `fillMissingLegs`. The
   supersede/insert block still runs only after the guard passes, so at the moment of the refusal nothing
   else has changed. The method javadoc and the guard's step list say this. The `LEZ_MIXED_DATES` 422 is
   unchanged and commits nothing (no entity changed).
2. **C2 — `persistViolation` deleted.** A search of `GVRP_Entry_API/src` found exactly one caller (the
   removed one), so the method was deleted together with the imports and field it alone used
   (`EntityManager`, `@PersistenceContext`, `Propagation`, `Transactional`). `RouteComplianceService` is
   now lock-free and transaction-free: it only reads and mutates the caller's managed entities.
3. **C3 — P1b send-guard test follows the new shape.** SG-T2 no longer mocks an `EntityManager` or
   verifies a bulk update; it asserts the state of the route returned by the locked-repository mock. See
   **Modified Tests** for the exact assertions.
4. **C4 — new DB-free shape test** `RouteSendTransactionTest` (3 tests, plain JUnit + reflection):
   `sendRoute`'s `@Transactional.noRollbackFor` contains `UnprocessablePlanException`; no public
   `RouteComplianceService` method carries `REQUIRES_NEW`; and the two exceptions that can still escape
   after the guard (`RevisionConflictException`, `DataInvalidException`) are **not** assignable to
   `UnprocessablePlanException`, because Spring applies `noRollbackFor` to subclasses too.
5. **Hierarchy check before choosing `noRollbackFor`.** No class in the repository extends
   `UnprocessablePlanException` (`extends UnprocessablePlanException` = 0 matches). After
   `enforceLezCompliance`, `sendRoute` can still throw `RevisionConflictException` (direct
   `RuntimeException`), a `DataAccessException` from `saveAndFlush`/the sibling lookup, or an NPE from a
   missing collaborator — none of them a subclass of the refusal, so the exemption cannot commit a
   half-done send. This is what `failuresAfterTheGuardStillRollBack` pins for the future.

### Commands Executed

From `GVRP_Entry_API`, `JAVA_HOME` = `C:\Program Files\Java\jdk-21`:

```text
mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"        # baseline
mvnw.cmd -B test "-Dtest=RouteSendServiceLezTest,RouteSendTransactionTest,RouteSendServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"        # final
```

Static checks from the repository root: modification times of `GVRP_Entry_API/src` against the baseline
log, and `REQUIRES_NEW` in `GVRP_Entry_API/src/main`. No migration was run and no SQL wrote to
`gvrp_db`; no Engine test was run.

### Verification Results

| Check | Result |
|---|---|
| Baseline (before the fix) | **240 run, 0/0/0, BUILD SUCCESS, 30.117 s** (`baseline-entry-p1bfix.log`) |
| Targeted (`RouteSendServiceLezTest`, `RouteSendTransactionTest`, `RouteSendServiceTest`) | **19 run (4 + 3 + 12), 0/0/0, BUILD SUCCESS, 15.546 s** (`targeted-entry-p1bfix.log`) |
| Full, same command as baseline | **243 run (240 + 3), 0/0/0, BUILD SUCCESS, 23.083 s** (`step2-entry-p1bfix.log`) |
| Files changed after the baseline | exactly the four allowed files (`RouteSendService.java`, `RouteComplianceService.java`, `RouteSendServiceLezTest.java`, new `RouteSendTransactionTest.java`) |
| `REQUIRES_NEW` in `src/main` | `EmailService` (2×) and `JobStatusUpdater` (1×); **none in `service/lez`** |
| `persistViolation` | 0 references left in `GVRP_Entry_API/src` |

The 243 is numerically the count P1b predicted, but the composition is different: 240 historical/other
tests + the 3 new tests, with the two schema-dependent `@SpringBootTest` classes still excluded. After
the user applies `2026-10-28-lez-compliance.sql` the full run is expected to add those 2 classes
(**Chưa xác minh**).

### Modified Tests

`RouteSendServiceLezTest` is a new P1b file (not an accepted historical test); only SG-T2's
`persistViolation` expectations were changed, reason **"reviewer rejected Deviation 7 (review-p1b F1)"**
for every line below. No assertion was weakened or removed for any other reason.

| Test | Before | After | Reason |
|---|---|---|---|
| `sgT2_permitExpiredSincePlanning_is422_keepsTheVerdictAndCreatesNoSend` | `verify(entityManager).createQuery(contains("update Route r"))` | removed; asserts `route.getLezStatus() == VIOLATION` | reviewer rejected Deviation 7 (review-p1b F1) |
| same | `verify(updateQuery).executeUpdate()` | removed; asserts `route.getLezCheckedAt() != null` | reviewer rejected Deviation 7 (review-p1b F1) |
| same | `assertThat(route.getLezRulesetHash()).isNotBlank()` | `isNotBlank()` **and** `isEqualTo(complianceService.currentFor(fixture.solution()).getHash())` (the compiled hash the task requires) | reviewer rejected Deviation 7 (review-p1b F1) |
| same | `assertThat(route.getLezViolations()).contains("ZONE_ACCESS")` | unchanged | — |

The rest of SG-T2 is untouched: the exception is still `UnprocessablePlanException` and still names
`ZONE_ACCESS`, `seq` and `zone=Z1`; the engine is still never called; no `RouteSend` is saved and no
sibling lookup/supersede happens. SG-T1, SG-T3 and SG-T4 are unchanged, as is every other test file.
`RouteSendTransactionTest` is **added**, not a modified test.

### Security-Relevant Changes

- No new endpoint, no `permitAll` rule, no query outside the existing branch-scoped repositories. The
  removed bulk `UPDATE routes` was keyed by `routeId` alone; the surviving `certify` path mutates the
  route already loaded by `findWithLockById`, which is branch-scoped in the caller
  (`SolutionService.belongsTo`-equivalent guard), so tenant isolation is unchanged.
- The `noRollbackFor` exemption is limited to `UnprocessablePlanException` and cannot hide a
  `RevisionConflictException`/`DataInvalidException`/`DataAccessException` failure (pinned by the new
  test).
- No secret, token, customer data or coordinate was added to code, tests or logs. No dependency added;
  `pom.xml` untouched. No migration executed.
- New external work on the error path: the refused send now commits the leg fill from `fillMissingLegs`
  (one `/legs` call maximum per route, already bounded by the P1b design) instead of rolling it back.
  This is the intended fix; it does not add a retry or a second call.

### Remaining Uncertainty

- **Real lock behaviour and the committed verdict: NOT_VERIFIED (Chưa xác minh).** No MySQL was
  available and no migration was applied, so the fix is verified only by unit tests and reflection.
  Nothing in this run demonstrates that the 422 now returns in a few seconds instead of waiting
  `innodb_lock_wait_timeout`, nor that the verdict row is actually committed. Only the user's manual run
  (review-p1b §5 step 4) can show that.
- **Transaction-manager semantics:** `noRollbackFor` is standard Spring behaviour, but this project has
  not exercised a real rollback/commit around `sendRoute` before. The same manual run covers it.
- **Leg fill now commits on refusal.** The geometry/instructions fetched just before the refusal stay
  in the database. That is desirable for the next attempt (no second engine call) but it is a behaviour
  change relative to P1b's rollback, and it is **NOT_VERIFIED** end to end.
- **`LEZ_MIXED_DATES`** still throws the same exception and will now also commit whatever the
  transaction changed before it — currently nothing, because it is thrown before `fillMissingLegs` and
  `certify`. A future change that mutates entities before that check would need to revisit this.
- **The manual step in the handoff is the acceptance criterion for F1**, not this record.

### Handoff

1. Apply `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-27-lez-zones.sql`, then
   `2026-10-28-lez-compliance.sql`, then run the full `mvnw.cmd -B clean test` in `GVRP_Entry_API`
   (expected 245 = 243 + the two `@SpringBootTest` classes; **Chưa xác minh**).
2. Review §5 step 4: enable a zone that bans the route's vehicle all day and covers one stop, then send
   the route. Expected: **422 within a few seconds**, `GET /api/v1/solutions/{id}/compliance` shows the
   route `VIOLATION`, and a second send does not call the engine again.
3. Still open from P1b and untouched here: F2–F8, the Deviation 2/10/13/14 decisions, and SEC-010
   (unauthenticated Engine `/legs`). This fix closes F1 only.

## P1c

Client increment of `work-items/LEZ-001/task-opencode-p1c.md` (design: `design-p1c.md`, facts:
`context-p1c.md`), in the order Step 0 → Step 4. P1b behaviour was not changed: the compiler, the
checker, the certification hooks and the send guard are untouched, and no review-p1b finding (F2–F8)
was addressed. P1c started with the user's decision of 2026-10-28 07:43 that the P1b manual test stays
**postponed**, not cancelled.

### Scope

- **Entry (read-only, DB-free tests).** B1: `GET /api/v1/lez/order-zones?orderIds=…`; B2: `zones` and
  `lez_unassigned` on `GET/POST …/compliance`.
- **Client.** C0 models/services/decoders, C1–C2 zones page and COPIED editor, C3 vehicle-type legal
  fields, C4 permits dialog, C5 order badge, C6 D4 refusal, C7 compliance panel, C8 send rule, C9
  planner map, C10 driver view.

### Baseline

From `GVRP_Entry_API` (`JAVA_HOME` = JDK 21) and `GVRP_Entry_client_v2`:

```text
mvnw.cmd -B clean test      # Entry, before any P1c change
npx ng test --watch=false --browsers=ChromeHeadless
npm run build
```

Result: Entry **246 run, 0 failures, BUILD SUCCESS** (`baseline-entry-p1c.log`); client **245 SUCCESS**
(`baseline-client-p1c.log`) and build **EXIT=0** (`baseline-client-build-p1c.log`). No pre-existing
failure had to be recorded.

### Changes Made

1. **B1 — order zone membership.** New `OrderZoneMembershipDTO`, `ZoneMembershipService`,
   `LezMembershipController` (`/api/v1/lez/order-zones`). Branch from the principal only (`LezSupport`
   → 403 when null); 1…200 ids, 400 on malformed/blank/oversize; ids of another branch or unknown ids
   are **omitted**; only **enabled** zones; geometry through `ZoneRuleLoader.effectiveGeometry`
   (LINKED → template, COPIED → copy) and membership with `GeoJsonGeometryParser.covers` (boundary =
   inside, like the compiler); `@Transactional(readOnly = true)`; no coordinates in logs.
2. **B2 — compliance read additions.** `SolutionComplianceDTO` gains `zones` and `lez_unassigned`.
   `ComplianceReadService.compliance()`/`recheck()` compile once and reuse the result for the hash and
   for `zones`; `complianceOf(solution, CompiledRestrictions)` derives the routes, the LEZ-only
   unassigned rows (`reason_code` starts with `LEZ_`) and the zone references. `STALE`/`UNCHECKED`
   logic and the constructor are unchanged.
3. **C0 — client foundation.** `core/models/lez.model.ts` (+ index export) with wire-exact keys,
   vehicle legal fields typed optional-with-null, `DriverStopDTO.geometry_to_next` /
   `instructions_to_next` / `zone_names`, `core/services/lez-api.service.ts` holding every LEZ call,
   `shared/utils/polyline.utils.ts` (local decoder, precision 1e5), `shared/utils/lez-format.utils.ts`
   (status labels, `HH:mm` intervals, `violationSummary`), `shared/utils/api-error.utils.ts`
   (`serverErrorMessage`).
4. **C1/C2 — zones page and COPIED editor.** `features/admin/zones/` with templates (read-only),
   *Enable* → LINKED, *Copy* → COPIED, enable toggle, update notice + acknowledge, delete, map preview
   with depots, `DEPOT_IN_ZONE` banner naming the depot, and the one-vehicle verdict check. The editor
   takes GeoJSON by paste or ≤ 2 MB file upload, previews it with `L.geoJSON` (no drawing plugin), and
   edits rules through the structured form of `zone-rule-form.utils.ts` (D7). Client validation mirrors
   `RuleValidator`; the server stays the authority. Admin child route + menu entry added.
5. **C3 — vehicle types.** The admin dialog shows the legal fields, the *Suggested: X* value with an
   explicit *Use suggestion* action (never applied silently), a missing-data hint, and always sends the
   four legal keys (`null` when empty) so an edit cannot clear them by omission. The list shows a
   "LEZ data missing" badge.
6. **C4 — permits.** `vehicle-permits-dialog` lists, creates, edits and deletes permits per vehicle;
   "Whole day" sends `schedule: null`, "Only during" sends `ALLOWED_ONLY` with intervals and optional
   days (P1a Deviation 2); `ALWAYS_FORBIDDEN` is not offered; delete only after confirmation.
7. **C5/C6 — orders.** `OrdersSectionComponent` asks for the zones of the visible page once per page
   and renders a `LEZ: <zone>` chip in the order cell; a failed request shows no chip and no toast. The
   plan action refuses a selection with more than one `delivery_date` before opening the dialog and
   names both dates; submit errors now show the server message (D4).
8. **C7 — compliance panel.** `features/main/orders/lez-compliance-panel/`, inside the Route tab and
   above the route view: one row per route with status chip, checked-at, violations (code, stop name
   from `orderStops` by `seq`, zone **name** from `zones`, server detail), *Recheck now*, LEZ-unassigned
   orders, and one line when no zone applies and every route is compliant. Clicking a violation calls
   `MapService.focusLeg(routeId, seq)`. It owns the request and emits the result; a failed request
   leaves the unknown state.
9. **C8 — send rule.** `RouteViewComponent` gains an optional `compliance` input (default `null` =
   unknown) and a `complianceRecheckNeeded` output. The send button is disabled with the reason while
   the route is known not to be `COMPLIANT` (`VIOLATION` → codes; `STALE`/`UNCHECKED` → "Recheck…"),
   and a 422 on send asks the panel for a fresh verdict. `null` keeps today's behaviour; the server
   guard stays the authority.
10. **C9 — planner map.** `MapService` draws a route from the stored server geometry when **every** leg
    has a polyline (no OSRM request), otherwise keeps today's OSRM/dashed path; enabled zones are
    drawn once per `displaySolution` in their own layer (light red fill, name tooltip) and cleared with
    the routes; `focusLeg` highlights the leg/stop of a violation and moves the view; failures fall
    back silently (`console.warn`). The decision itself is the pure function
    `shared/utils/route-geometry.utils.ts#completeRoutePolyline`.
    `focusLeg` corrected by §P1c review fix (review-p1c R3).
11. **C10 — driver view.** Stored leg polylines replace the OSRM path and stored instruction text
    replaces the OSRM maneuver list when the geometry is complete and at least one leg carries
    instructions (same `distance > 0` filter); a route without the new fields behaves exactly as
    today. Stops show `LEZ: <name>` chips from `zone_names`.

### Commands Executed

```text
mvnw.cmd -B clean test                                             # Entry, final (no exclusions)
npx ng test --watch=false --browsers=ChromeHeadless                # client, final
npm run build                                                      # client, final
```

Static checks from the repository root: `git status --short` scope review; `router.project-osrm.org`
appears only in `shared/services/routing.service.ts`; no new `console.log` (the pre-existing ones in
`orders-section`, `sidebar`, `edit-order-dialog`, `main` and `auth` are unchanged); no coordinate,
customer or token logging added.

### Verification Results

| Check | Result |
|---|---|
| Entry baseline | **246 run, 0 failures, BUILD SUCCESS** (`baseline-entry-p1c.log`) |
| Entry after Part B | **263 run, 0 failures, BUILD SUCCESS** (`p1c-partb-entry.log`) |
| Entry final, `mvnw.cmd -B clean test` **without exclusions** | **263 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, EXIT=0** (`p1c-entry-final3.log`) |
| Client baseline | **245 SUCCESS** (`baseline-client-p1c.log`), build EXIT=0 |
| Client final, `ng test` | **319 SUCCESS, 0 failed** (`p1c-client-c9c.log`) |
| Client final, `npm run build` | **EXIT=0**; only budget warnings (`p1c-client-build2.log`) |
| New Entry tests | `ZoneMembershipServiceTest` 9, `LezMembershipRoleAccessTest` 4, `ComplianceReadAdditionsTest` 4 (17 = 263 − 246) |
| New client tests | 74 = 319 − 245, in 12 new spec files |
| `router.project-osrm.org` | only `shared/services/routing.service.ts` |
| `package.json`, `package-lock.json`, `angular.json`, `tsconfig*`, `public/**` | unchanged |
| Engine API, migrations, `SecurityConfig`, `pom.xml`, `application*.properties`, `.github/`, `AGENTS.md`, `docs/` | no P1c edit (mtime check on `GVRP_Engine_API/src/main`) |
| New component SCSS | `vehicle-permits-dialog` 2.0 kB, `zones-admin` 1.9 kB, `zone-editor-dialog` 2.6 kB, `compliance-panel` 2.3 kB source; compiled `zones-admin` 4.97 kB (warning, under the 8 kB error) |
| `orders-table` compiled style | 7.93 kB (was 7.73 kB before P1c; over the 4 kB warning, under the 8 kB error) |

The Entry total did not change between Part B and the final run, but the final command ran **without**
the two exclusions used earlier: `GvrpEntryApiApplicationTests` (1) and `DepotMapperTest` (2) executed
and passed against the local schema in this run.

### Modified Tests

Both are the D8 allowance: mock methods / providers added in `beforeEach` only. No assertion, `it`,
fixture, expected value, tolerance or test list was changed, and no other existing spec was touched.

| File | Change | Reason |
|---|---|---|
| `orders-section.component.spec.ts` | one `LezApiService` spy (`orderZones` → `of([])`) and its provider, plus the import | D8 setup only |
| `orders-section-pareto.component.spec.ts` | same | D8 setup only |

`route-view.component.spec.ts`, `driver-view.component.spec.ts` and `send-route-dialog.component.spec.ts`
needed no change: their behaviour is preserved by optional inputs and optional `DriverStopDTO` fields.
`vehicle-type-dialog.component.spec.ts` is a **new** file (VT-T1); no existing vehicle-type spec existed.

### Security-Relevant Changes

- The new endpoint is under the existing `/api/v1/**` PLANNER rule; no `permitAll` rule and no
  `SecurityConfig` change. Branch comes from the principal, never from the request, and another
  branch's order id is indistinguishable from a missing one (omitted, no existence leak).
- All LEZ reads/writes keep server-side branch scoping: the client only passes ids, never a branch.
- No polygon or rule data is sent to drivers: the driver detail carries stored geometry, instructions
  and zone **names** only, as already produced by the server.
- No secret, token, customer data or coordinate added to code, tests, logs or evidence; no new
  dependency; no SQL written to `gvrp_db`; no migration run.

### Remaining Uncertainty

- **P1-seed (Hanoi templates) is still not installed**, so the zones page can only list templates that
  exist in the local database by hand. Nothing in P1c seeds data; the page, editor, permits and
  verdict screens were exercised against mocked `LezApiService` only. **NOT_VERIFIED** end to end.
- **No browser check was performed** in this run. The acceptance of the screens (layout, Leaflet
  overlay, focus behaviour, dialog flows) is the user's browser step after review.
- **MP-T1 is a pure-function test.** A real Leaflet map needs a laid-out container, so the source rule
  is verified as `completeRoutePolyline` / `completeDriverLegs` / `driverInstructionSteps` (no OSRM
  when they return a path) instead of asserting on `RoutingService` inside a live map. `MapService` and
  the driver view are the only callers; the rule branch is a few lines each.
- **Stored geometry is only as good as the server data.** Routes materialised before P1b have no
  polylines, so both maps keep their OSRM path; that fallback is the tested behaviour, not a defect.
- **P1b manual test still postponed**, as decided: F1's fix and the whole send guard remain
  **NOT_VERIFIED** end to end.
- `zones-admin.component.scss` (4.97 kB compiled) and `orders-table.component.scss` (7.93 kB compiled)
  exceed the 4 kB warning budget; both stay under the 8 kB error budget, and `angular.json` was not
  changed.

### Handoff

1. Review this record and the allowed-file list of `task-opencode-p1c.md`; the working tree is still
   uncommitted on `feature/lez-001` (no commit or push was made).
2. Browser check (user): zones page (enable/copy/edit geometry and rules/verdict), vehicle-type legal
   fields and suggestion, permits dialog, order badge, D4 refusal, compliance panel + Recheck, a
   disabled *Send to driver* with its reason, planner map overlay and violation focus, driver view
   instructions and zone chips.
3. Apply/seed P1-seed templates before judging the empty "Zone templates" state.
4. Still open from P1b and untouched here: F2–F8, the Deviation 2/10/13/14 decisions, SEC-010 and the
   postponed manual send test.

## P1c follow-up — live browser findings (2026-10-28)

Fixes applied after the user tested the P1c screens in a real browser against `Branch Huge 1 / hien` and
authorized the repair ("hợp lý, bạn sửa ngay đi"). Scope stays inside the P1c file allowance; no commit,
no push, no migration, no dependency and no rule file was touched.

### Scope

| ID | Defect found in the browser | Status after this change |
|---|---|---|
| A | `PUT /api/v1/vehicle-types/{id}` answered **500** for the payload both web clients send (`vehicle_features`) | **FIXED** |
| B | A new permit could be submitted without validity dates; the server answered `400 validFrom and validTo are required`, the page offered no dates and listed "Unlimited" | **FIXED** (client contract; server/DB `NOT NULL` unchanged) |
| C | The API reason never reached the screen: only "Failed to save the permit" / "Failed to save vehicle type" | **FIXED** |
| D | A malformed or unknown-field body answered **500** instead of **400** | **FIXED** |
| D2 | A supported URL called with the wrong method answered **500**; found while probing A (`GET` on a `PUT`-only route) | **FIXED** (405) — adjacent defect, same family as D |

### Baseline

P1c accepted state before this follow-up: Entry **263** tests / 0 failures (`p1c-entry-final3.log`),
client **319 SUCCESS** (`p1c-client-c9c.log`), `npm run build` EXIT=0 (`p1c-client-build2.log`).

### Changes Made

1. `dto/request/VehicleTypeInputDTO.java` — `@JsonAlias("vehicle_features")` on `features`. The canonical
   name stays `features`; the alias accepts the response field name that `GVRP_Entry_client` and
   `GVRP_Entry_client_v2` already send, so no client payload, spec or V1 consumer changes.
2. `exception/GlobalExceptionHandler.java` — new `HttpMessageNotReadableException` handler: **400**
   `VALIDATION_ERROR` with the most specific cause (`Unrecognized field "vehicle_features"`, JSON parse
   position). Only the cause is logged, never the payload.
3. `exception/GlobalExceptionHandler.java` + `util/ErrorCode.java` — new
   `HttpRequestMethodNotSupportedException` handler: **405** with the new code `0040501`. Web MVC raises
   this before the controller runs; it used to fall through the generic handler as 500.
4. `core/interceptors/error.interceptor.ts` — the thrown shape stays `{ message, status, originalError }`,
   but `message` now prefers `errors[0].message` then `message` from the API body.
5. `shared/utils/api-error.utils.ts` — `serverErrorMessage()` resolves the interceptor wrapper
   (`message`, `originalError.error`) and a raw `HttpErrorResponse` body, then falls back to the caller's
   text. This is the path the LEZ screens use for toasts and form errors.
6. `features/admin/vehicles/vehicle-permits-dialog/*` — `startCreate()` opens with a one-year window
   (today → same day next year, formatted locally so +07:00 cannot shift the day); `validate()` requires
   both bounds for a new permit only, because the edit path and the existing fixture have no dates; the
   form carries the hint "Both dates are required; a new permit opens with a one-year window."

### Commands Executed

```text
mvnw.cmd -B test                                     # Entry, full suite, no exclusions
mvnw.cmd -B test -Dtest=GlobalExceptionHandlerClientErrorsTest,VehicleTypeInputDeserializationTest
npx ng test --watch=false --browsers=ChromeHeadless  # client
npm run build                                        # client
```

Live probes against the running Entry API (same branch and principal as the browser test) and a browser
pass over `/admin/vehicle-types`, `/admin/vehicles` (permits dialog) in the in-app browser.

### Verification Results

| Check | Result |
|---|---|
| Entry, `mvnw.cmd -B test` **without exclusions** | **270 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, EXIT=0** (`p1c-fix-entry-final.log`); 270 = 263 + 7 new |
| Client, `ng test` | **323 SUCCESS, 0 failed, EXIT=0** (`p1c-fix-client2.log`); 323 = 319 + 4 new |
| Client, `npm run build` | **EXIT=0** (`p1c-fix-build.log`) |
| A — API | `PUT /api/v1/vehicle-types/9004` with the client payload → **200**, `legal_class=MOTORBIKE` persisted and read back through `GET /api/v1/vehicle-types` |
| A — UI | Edit dialog → *Save* → `PUT /api/v1/vehicle-types/9004` **200**, dialog closed, no error toast |
| B — UI | *+ Add permit* opens with `Valid from 2026-10-28`, `Valid to 2027-10-28`; *Save permit* → `POST …/permits` **201**, list shows `2026-10-28 – 2027-10-28` |
| B — client validation | Clearing `Valid from` shows `"Valid from" is required.` and issues **no** POST |
| C — UI | A 101-character permit number reaches the server and the dialog shows the API text `permitNo is longer than 100 characters` (previously only "Failed to save the permit") |
| D — API | Broken JSON → **400** `Malformed request body: Unexpected character ('}' …)`; unknown field → **400** `Malformed request body: Unrecognized field "nope" …` |
| D2 — API | `GET /api/v1/vehicle-types/9004` (PUT-only) → **405** `0040501 Method not allowed.` |
| Permit server contract | `POST …/permits` without dates → **400** `validFrom and validTo are required` (message now surfaced by the client) |

Data touched by the test and left as found, except one deliberate value:

- Two permits `TEST-P1C-FIX` on vehicle 109010 were created and deleted; `GET …/permits` answers `[]`.
- Vehicle type 9004 keeps `legal_class=MOTORBIKE` (was `null`) as the acceptance result of A;
  `gross_weight_kg`, `energy_type` and `emission_standard` stay `null`, so the "LEZ data missing"
  badge correctly stays visible.
- No zone, order, route, job or `lez_status` was written in this follow-up.

### Modified / New Tests

No existing test was modified, deleted, skipped, focused or excluded.

| File | Status | Covers |
|---|---|---|
| `dto/request/VehicleTypeInputDeserializationTest.java` | new (3) | `vehicle_features` alias, canonical `features`, unknown field still rejected |
| `exception/GlobalExceptionHandlerClientErrorsTest.java` | new (4) | 400 with cause, 400 without cause text, 405, unexpected failure stays 500 |
| `shared/utils/api-error.utils.spec.ts` | new (4) | interceptor wrapper, raw response body, `originalError`, fallback |

### Security-Relevant Changes

- The new handlers add no `permitAll`, no endpoint and no branch logic; 400/405 are returned before any
  service call, so tenant scoping is unchanged.
- Error messages echo only the Jackson cause (field name, character position). No payload, token,
  coordinate or customer data is logged or returned; the log line carries the cause only.
- `ErrorCode.METHOD_NOT_ALLOWED` is a new constant; no existing code value changed.

### Remaining Uncertainty

- **Legacy route geometry**: 0 of 215,251 `route_stops` rows carry `geometry_to_next`, so *Recheck* marks
  every pre-P1b route `VIOLATION / LEZ_GEOMETRY_MISSING` and blocks *Send to driver*. This is data, not
  a defect of these fixes, and it was **NOT** changed here.
- **D4 / LEZ badge and multi-date refusal** remain **NOT_VERIFIED** end to end: no order currently lies
  inside the "Test zone" polygon (0 of 25,054 orders).
- Cosmetic issues observed and deliberately left: the vehicle table capacity column renders only "kg";
  two dropdowns list "Test zone" twice and the permit list can show "Zones in force: Test zone, Test zone";
  the label "Weight bound reads"; a duplicated "Zone saved" toast; Leaflet marker icon 404s.
- One browser probe of a generic 400 exposed the DB-style field name (`grossWeightKg`) with the message
  "Invalid data validation."; the message is accurate but not user-oriented. Not changed here.
- P1b manual send test remains postponed (user decision), so the send guard is still **NOT_VERIFIED** end
  to end.

### Handoff

1. The running Entry API instance used by the browser test was started by the agent with
   `mvnw.cmd -B spring-boot:run` (log `p1c-fix-app.log`) because the earlier IDE-launched JVM held stale
   classes and answered 500 to every `/api/v1/vehicle-types/**` call; stop it and restart from the IDE
   when convenient.
2. Re-run the browser acceptance for the remaining cosmetic findings if they matter for the demo.
3. Everything else from P1c's Handoff still applies; the working tree stays uncommitted on
   `feature/lez-001`.

## P1c review fix

Fixes for findings **R1, R2, R3, R5 and R6** of `review-p1c.md`, executed on 2026-10-29 on the
engineer's side after the user's "có bạn viết đi". Client only: the Entry API, the Engine API and
P1b behaviour are untouched, and R4, R7, R8 and R9 stay open. No commit, no push, no migration and
no new dependency.

### Scope

| ID | Finding (review-p1c §4) | Status after this change |
|---|---|---|
| R1 (HIGH) | A `VIOLATION` made only of `LEZ_GEOMETRY_MISSING` disabled *Send to driver* although the send guard fetches the legs before checking compliance | **FIXED** (client; server untouched) |
| R2 | The planner never saw the server's 422/409 explanation: the handlers read only `error.error.errors[0].message`, which the interceptor wrapper hides | **FIXED** |
| R3 | Clicking a violation recoloured the whole route and used an arrival-time index, not the plan index | **FIXED** |
| R5 | A permit could be saved from the edit form with a cleared validity date, which the server rejects | **FIXED** (client validation) |
| R6 | The rule form accepted a blank "Effective from", which `RuleValidator` rejects | **FIXED** (client validation) |

### Baseline

Before any edit, from `GVRP_Entry_client_v2`:

| Check | Result |
|---|---|
| `npx ng test --watch=false --browsers=ChromeHeadless` | **323 SUCCESS, 0 failed, EXIT=0** (`p1c-review-fix-baseline-test.log`) |
| `npm run build` | **EXIT=0, 0 ERROR, 12 warnings** (`p1c-review-fix-baseline-build.log`); initial total 665.96 kB, `orders-table.component.scss` 7.93 kB, `route-view.component.scss` 4.68 kB |
| Port 4200 | `ng serve` (PID 23376) stopped before the run and the port verified free (L-2026-011) |

### Changes Made

1. `shared/utils/map-focus.utils.ts` — **new pure decision** for R3: `violationFocus(code, seq,
   detail)` returns `{ stopSeq, leg: { fromSeq, toSeq } }`. `ZONE_TRANSIT` and
   `LEZ_GEOMETRY_MISSING` point at the leg `seq → seq+1`; `LEZ_DATA_MISSING` does too **only** when
   its detail contains "transit" (the leg variant is otherwise indistinguishable); every other code
   (`ZONE_ACCESS`, the stop variant of `LEZ_DATA_MISSING`) points at stop `seq` plus the leg
   `seq−1 → seq` when `seq > 0`. Keeping it pure keeps the rule testable without Leaflet, as P1c did
   for the geometry source rule.
2. `shared/utils/route-geometry.utils.ts` — new `storedLegPaths(geometry)`: one decoded path per leg
   in `from_seq` order, or null when any leg lacks a polyline (the same completeness rule as
   `completeRoutePolyline`). `completeRoutePolyline` itself is unchanged.
3. `shared/services/map.service.ts` —
   - the focus registry is now `FocusableRoute { planMarkers, planStops, legPaths }` per route id.
     `planStops` is `orderStops(route.stops)` (the order the compliance panel uses) and
     `planMarkers` maps that same order to the markers through the stop object, so the arrival-time
     order in which the markers are created stays exactly as before.
   - new `highlightLayer` (`L.FeatureGroup`), created in `initializeMap` and cleared in
     `clearRoutes`; `focusable.clear()` now also runs on `initializeMap`.
   - `focusLeg(routeId, seq, code, detail?)` clears the previous highlight, draws the offending leg
     in `#b42318` weight 8 (the stored leg polyline when the route was drawn from server geometry,
     otherwise a straight segment between the two plan stops), then centres on the stop (zoom ≥ 15,
     popup open) or fits the leg bounds. The route polyline keeps its own colour; the previous
     whole-route recolour is gone.
   - `storedPath(route)` returns `{ polyline, legs }`; `drawStraightLine` no longer takes the
     registry entry, because the registry no longer stores drawn lines.
4. `lez-compliance-panel/compliance-panel.component.ts` — `ComplianceViolationRow` carries `code`
   and `detail`, and `focusViolation` passes both to `focusLeg`. The template is unchanged.
5. `route-view/route-view.component.ts` / `.html` / `.scss` —
   - R1: `sendBlockedReason` returns null for a `VIOLATION` whose violations are **all**
     `LEZ_GEOMETRY_MISSING` (`isGeometryOnly`); any other code, and an empty violation list, keeps
     today's text. New `sendNote(route)` renders "Road geometry is fetched and checked when you
     send." next to the enabled button (`.send-note`, no toast). The compliance panel is unchanged,
     so the route still shows its reported state.
   - R2: the send `error` handler, `describeEvaluateError` and `describeSaveError` use
     `serverErrorMessage(error, <current fallback>)`. Every status branch and its order is kept:
     0/502 answer "unreachable" before any lookup, 409 still sets `saveConflict` and appends
     " Load the latest revision to continue.", 422 on send still emits `complianceRecheckNeeded`.
6. `vehicle-permits-dialog/*` — `validate()` requires both `valid_from` and `valid_to` on create
   **and** on edit (R5); the one-year default for a new permit and the existing date-order check are
   unchanged. The hint now reads "Both dates are required for a new or edited permit; a new permit
   opens with a one-year window."
7. `zones/zone-rule-form.utils.ts` — `validateRuleForm` adds `"Effective from" is required.` when
   `effectiveFrom` is blank (R6), mirroring `RuleValidator`. No other rule is changed.

### Commands Executed

```text
node tools/session-brief.mjs
git status --short
node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json
npx ng test --watch=false --browsers=ChromeHeadless   # baseline, then final
npm run build                                         # baseline, then final
```

All from the repository root, except the two npm/ng commands, run from `GVRP_Entry_client_v2`. The
Entry and Engine modules were not touched, so their suites were not run.

### Verification Results

| Check | Result |
|---|---|
| Client, `ng test` final | **339 SUCCESS, 0 failed, EXIT=0** (`p1c-review-fix-step2-test.log`); 339 = 323 + 16 new |
| Client, `npm run build` final | **EXIT=0, 0 ERROR** (`p1c-review-fix-step2-build.log`) |
| Build warnings vs baseline | same 12 warnings, same files; initial total **665.96 kB** unchanged; `orders-table.component.scss` **7.93 kB** unchanged; `route-view.component.scss` 4.68 → **4.74 kB** (the `.send-note` rule) |
| Changed files | 16, all inside the task's allowance (checked by modification time against the baseline logs): `route-view.component.{ts,html,scss}`, `route-view-lez.component.spec.ts`, `map.service.ts`, `map-focus.utils.ts` + spec, `route-geometry.utils.ts` + spec, `compliance-panel.component.ts` + spec, `vehicle-permits-dialog.component.{ts,html}` + spec, `zone-rule-form.utils.ts` + spec |
| Forbidden files | untouched: `error.interceptor.ts`, `api-error.utils.ts`, the five D8 specs, `zone-editor-dialog.component.spec.ts`, `package*.json`, `angular.json`, `public/**`, Entry/Engine |
| `router.project-osrm.org` | still only `shared/services/routing.service.ts` |

Not verified here: anything that needs a browser or a running server (see Remaining Uncertainty).

### Modified Tests

No test was deleted, renamed, skipped, focused or excluded, and no tolerance was widened. Three
existing P1c expectations changed because the fix makes them wrong by design; each is tagged.

| File | Status | Covers |
|---|---|---|
| `route-view-lez.component.spec.ts` | +5 `it` | FR1-T1, FR1-T2, FR2-T1, FR2-T2 (422 and 409 via one `failingSave` helper) |
| `shared/utils/map-focus.utils.spec.ts` | new (6) | FR3-T2: the six index decisions of `violationFocus` |
| `shared/utils/route-geometry.utils.spec.ts` | +2 `it` | FR3-T1: three paths in `from_seq` order; null when one leg is unrouted |
| `lez-compliance-panel/compliance-panel.component.spec.ts` | +1 `it`, 1 assertion changed | FR3-T3: `focusLeg(11, 1, 'ZONE_FORBIDDEN', 'Rule 1')` — the change is the two new arguments, **review-p1c R3**; the new case proves a leg violation is passed through unchanged |
| `vehicle-permits-dialog/vehicle-permits-dialog.component.spec.ts` | +1 `it`, 1 fixture changed | FR5-T1: clearing `valid_to` on an edit blocks the PUT. The stored `permit` fixture gained `valid_from`/`valid_to`, which a real permit always has — **review-p1c R5** |
| `zones/zone-rule-form.utils.spec.ts` | +1 `it`, 1 fixture changed | FR6-T1: blank `effectiveFrom` is rejected. The "accepts a valid interval rule" fixture gained `effectiveFrom: '2027-01-01'` so it stays valid — **review-p1c R6** |

### Remaining Uncertainty

- **NOT_VERIFIED in a browser**: the R3 highlight (leg vs. stop, layer, fit, popup) and the R1 send
  note. No server was started and no live data was used in this fix; both need the user's P1c
  browser check.
- The R1 exception relies on `RouteSendService.enforceLezCompliance` calling `fillMissingLegs`
  **before** the compliance check. That was read in the Entry source by the reviewer, not executed
  here. If the fetch still leaves a leg unrouted, the server refuses the send and R2 now shows its
  text.
- The highlight of a route drawn from OSRM or straight lines is a straight segment between the two
  plan stops, not the drawn path.
- The focus registry assumes the compliance `seq` is an index into `orderStops(route.stops)` (true
  because `orderStops` only moves the closing depot last) and that the server emits legs
  `0…n−1`. A change to either convention would mis-target the highlight; `seq` beyond the stop list
  simply highlights nothing.
- R5 and R6 are client-side mirrors of server validation. They were not exercised in the browser
  this time, and the server remains the authority.
- P1b manual send test remains postponed (user decision), so the send guard is still
  **NOT_VERIFIED** end to end.
- R4, R7, R8 and R9 of `review-p1c.md` remain open; they are outside this task.

### Handoff

1. Claude re-reviews this fix against `review-p1c.md` §4.
2. The user runs the P1c browser check: click a violation in the compliance panel (the offending leg
   is highlighted and the view moves there) and open a plan whose only violation is
   `LEZ_GEOMETRY_MISSING` (the button stays enabled with the note, and the send either succeeds or
   shows the server's own text).
3. The user runs the P1b review §5 step 4 manual send test when convenient; it is still postponed.
4. Nothing is committed or pushed. The working tree stays uncommitted on `feature/lez-001`, and every
   earlier handoff item still applies.

