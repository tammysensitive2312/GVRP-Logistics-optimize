# Review — LEZ-001 increment P1a

Reviewer: Claude (Claude session, 2026-10-27 ~00:15 +07:00). This is an independent review. It does not rely on `implementation.md`.
Method: direct reading of the files on branch `feature/lez-001` (uncommitted working tree) through the file bridge. **No shell was available**, so nothing was compiled or executed by the reviewer.

## 1. Files inspected

| Area | Files read in full |
|---|---|
| Evaluator | `service/lez/RuleEvaluator`, `ScheduleNormalizer`, `RuleSelector`, `RuleSchedule`, `RuleView`, `PermitView`, `VehicleFacts` |
| Services | `service/lez/ZoneService`, `ZoneTemplateService`, `VehiclePermitService`, `LezSupport`, `GeoJsonGeometryParser` |
| Controllers | `controller/BranchZoneController`, `VehiclePermitController`, `ZoneTemplateController` |
| Persistence | `repository/BranchZoneRepository`, `BranchZoneRuleRepository`, `VehiclePermitRepository`, `ZoneRuleTemplateRepository`, `VehicleRepository`; `entity/BranchZone`, `ZoneTemplate`, `VehicleType`; migration `2026-10-27-lez-zones.sql` |
| Edited existing code | `service/VehicleTypeService`, `exception/GlobalExceptionHandler`, `config/SecurityConfig` (unchanged, read for role rules) |
| Tests | `service/lez/ZoneServiceTest` in full; the test directory listing |
| Records | `implementation.md` §P1a (read last, for comparison only) |

## 2. Test integrity (AGENTS.md §14)

- Every pre-existing test file under `GVRP_Entry_API/src/test` has a modification time from before this session. The latest is `ParetoServiceTest`, 2026-10-20. The new files are the seven test classes listed in `implementation.md`.
- No shell, so **no byte comparison against `617b720`** (L-2026-009). The mtime evidence and the implementer's `git diff --name-status 617b720 -- GVRP_Entry_API/src/test` are consistent, but the byte comparison is **not verified by the reviewer**.
- `ZoneServiceTest` checks behaviour, not only calls. Examples: ZS-T4 asserts 7 × 404 **and** that no save, delete or rule write happened, and that `findById` (unscoped) is never used. ZS-T2 asserts the branch on every copied rule. Nothing is weakened or skipped.

## 3. Findings

| # | Severity | Finding | Verdict |
|---|---|---|---|
| F1 | — | **Tenant isolation holds.** The branch comes only from `CurrentUserUtil.getCurrentBranchId()`, and a null branch gets 403 before any query. Every branch-side lookup is scoped: `findByIdAndBranchId`, `findByIdAndFleetBranchId`, `findByIdAndVehicleIdAndBranchId`, bulk delete `WHERE zone AND branch`. A permit needs vehicle **and** zone of the caller's branch. New rows take the branch from the principal (or from the already scoped zone). Templates expose no branch data. | OK |
| F2 | — | **Roles.** `SecurityConfig` (unchanged) has `/api/v1/driver/** → DRIVER`, then `/api/v1/** → PLANNER`. All new paths fall under PLANNER. No new `permitAll`. | OK |
| F3 | — | **Evaluator matches design §3.3.** Rules are filtered by date and weekday. Every present selector field must match. A null fact counts as a match and is reported, but only when the rule matches overall. Forbidden intervals are unioned and merged. Permits are subtracted. ALLOWED / ALL_DAY / PARTIAL are decided correctly. `ScheduleNormalizer` is correct: half-open intervals, merging of touching intervals, midnight split, `24:00` accepted only as an end, complement and subtract. | OK |
| F4 | — | **GeoJSON parsing** has a vertex bound checked before coordinates are allocated, a lon/lat range check, ring closure, JTS `isValid`, SRID 4326 with x = lon, and `covers` for the boundary. | OK |
| F5 | Medium | **Persistence of polygons is unexercised.** The migration has not been run. The MySQL `SRID` column attribute, the generated column and unique key, `ddl-auto=validate` against `ENUM`/`MULTIPOLYGON`/`JSON`, Hibernate's JSON mapping of the records, and the polygon round trip through hibernate-spatial (axis order) have never run. | **Blocking for P1b**, not for this review: needs the user's migration run, a full clean test and the manual smoke test (§5) |
| F6 | Low | **Full-replacement PUT on vehicle types clears the legal attributes** when today's client (which does not know them) saves a type (Deviation 9). The effect fails closed: the type becomes restricted, never more permissive. Still, data typed through the API is lost silently. | Accept for P1a. **P1c requirement:** the vehicle-type dialog must always send the four fields |
| F7 | Low | **Permit schedule vocabulary is inverted** (Deviation 2). A permit covers the minutes its schedule does *not* forbid, so `FORBIDDEN_DURING 06–09` on a permit means "all day except 06–09". This is correct as specified, but easy to misuse. | Accept. **P1c requirement:** the UI offers only "whole day" (null) or "allowed hours" (`ALLOWED_ONLY`) for permits |
| F8 | Low | **Mixed casing on the wire** (Deviation 3). Outer fields are snake_case (`template_id`, `valid_from`), while the selector and schedule JSON are camelCase (`legalClasses`, `daysOfWeek`). This is deliberate, so stored rules and seed SQL are mapper-independent. | Accept. Record for P1c and P1-seed |
| F9 | Low | **Unreadable request bodies become 500** (pre-existing catch-all in `GlobalExceptionHandler`). For example `mode: "FOO"` on `POST /zones` returns 500 instead of 400. It is not introduced by P1a, but the new endpoints are affected. | Follow-up outside LEZ-001: map `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` to 400 |
| F10 | Info | **404 vs 400 inconsistency.** The new endpoints return 404 for missing or foreign IDs (as specified); the older ones return 400 `RESOURCE_NOT_FOUND`. Both are safe for tenant isolation. | Accept |
| F11 | Info | **Local `@SpringBootTest` classes fail until the migration is applied** (`ddl-auto=validate`). CI excludes them. Lesson candidate 1 is valid. | Add to LESSONS at closure |
| F12 | Info | The after-midnight part of an interval belongs to the same service date (Deviation 6). This is consistent with one-date jobs (design §4). | Accept |

No secret, token or customer data was found in the code, the logs or the tests. The one new log line contains branch, zone, mode and template IDs only. No dependency was added.

## 4. Verdict

**P1a PASSES review with conditions.** The code matches the design and is safe for tenant isolation. Before P1b starts:

1. The user applies `2026-10-27-lez-zones.sql` to the local database.
2. `mvnw.cmd -B clean test` in `GVRP_Entry_API` is all green (expected 190 run, 0 failures, 0 errors).
3. The manual smoke test in §5 passes. Above all, the polygon round trip keeps [longitude, latitude].

F6–F8 become requirements of P1c. F9 becomes a separate follow-up.

## 5. Manual test given to the user

Recorded in the chat message of 2026-10-27; the results will be appended here.
