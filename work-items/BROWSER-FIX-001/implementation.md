# Implementation — BROWSER-FIX-001

Engineer: Claude Code (acting as `gvrp-engineer` for `task-opencode.md`), 2026-10-29, Windows host shell.
Branch: `fix/browser-fix-001`, created from `fix/map-orders-001` @ `d6e4015`. Nothing in this task is committed.

## Scope

Design E1–E7 (Entry API) and C1–C7 (client v2) of `design.md`, covering BF-R1…BF-R12. No schema change, no migration, no dependency, no Engine change, no `pom.xml` / `package*.json` / `angular.json` / `tsconfig*` / `SecurityConfig` / `application*.properties` change.

## Baseline

Taken on `fix/browser-fix-001` before any code edit (tree = `d6e4015`).

| Module | Command | Result |
|---|---|---|
| GVRP_Entry_API | `mvnw.cmd -B clean test` (JDK 21) | EXIT=0, BUILD SUCCESS, **Tests run: 270, Failures: 0, Errors: 0, Skipped: 0**. The two `@SpringBootTest` classes passed, so the full command is the baseline (the exclusion fallback was not needed) |
| GVRP_Entry_client_v2 | `npx ng test --watch=false --browsers=ChromeHeadless` | EXIT=0, **363 SUCCESS**, 0 failing |
| GVRP_Entry_client_v2 | `rm -rf dist && npm run build` | EXIT=0, 0 ERROR, 12 warnings (initial bundle budget; 10 component-style budgets; `leaflet` not ESM) — same list as the MAP-ORDERS-001 record |

**Text-assertion check (Step 2):**

- `GVRP_Entry_API/src/test` for `Stop is in zone`, `Leg crosses zone`, `decides transit through`, `No compliant vehicle for zone`, `Resource conflict.`, `RESOURCE_CONFLICT`: **no match**.
- Wider search for `detail()` / `getReason()` / `getMessage()` assertions: only `contains`-style or unrelated checks — `OptimizationCallbackServiceLezTest` `getReason()).contains("Z1")` (still passes: fixture zone name is `"Zone Z1"`), `ComplianceCheckerTest` `detail()).contains(ATTR_GROSS_WEIGHT)` (text untouched), `ComplianceReadAdditionsTest` `isEqualTo("No compliant vehicle")` (a stored row read back, not produced by the changed code), `ZoneServiceTest` `getName()).isEqualTo("Test zone")` (copy name kept when free).
- `GVRP_Entry_client_v2/src` specs for `Check capacity, time windows` and `Total Demand`: **no match in any spec** (only in the two templates).
- Verdict: no existing assertion is broken by the design; implementation proceeded.

## Changes Made

### Entry API (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`)

| Item | File | Change |
|---|---|---|
| E1 | `dto/request/OrderInputDTO.java` | `status` field initializer removed; type and `@Null(groups = OnCreate.class)` kept |
| E2 | `exception/GlobalExceptionHandler.java` | new `@ExceptionHandler(MethodArgumentNotValidException.class)` → 400, one `ErrorDetail` per error (`VALIDATION_ERROR` code, default message, `FieldError#getField` or null), Spring's order; one WARN line `Request validation failed: {count} error(s) on {objectName}` |
| E3 | `dto/response/VehicleDTO.java`, `mapper/VehicleMapper.java` | `Integer capacity`; `@Mapping(source = "vehicleType.capacity", target = "capacity")` on `toDTO` |
| E4 | `service/OptimizationJobService.java` | invalid-status branch only: message `Only SCHEDULED orders can be planned. These orders are already planned, delivered or left unassigned: <≤10 codes>[ (+N more)].`; `code`, `resource`, `field` unchanged. The limit 10 is a literal with a comment (no new class constant, to stay inside "the message only") |
| E5 | `service/OptimizationCallbackService.java` | the second `solution.getUnassignedOrders().addAll(unassignedList);` deleted |
| E6 | `service/lez/ComplianceChecker.java` | new `public static String label(CompiledZone)` (trimmed name, else key); the zone key in the detail strings replaced by `"\"" + label(zone) + "\""` (see Deviation 1) |
| E6 | `service/lez/RouteComplianceService.java` | `noCompliantVehicleReason`: each key → quoted `ComplianceChecker.label` of the compiled zone with that key (key itself when not compiled), joined by `", "`; `java.util.stream.Collectors` import added for it |
| E7 | `dto/request/BranchZoneUpdateRequest.java` | fields `Boolean enabled`, `String name`; Lombok annotations kept; explicit `BranchZoneUpdateRequest(Boolean enabled)` added |
| E7 | `service/lez/ZoneService.java` | `updateZone`: `findZone` first (unchanged), then validation per design (both null → invalid; name on LINKED → `LezConflictException`; blank; > 200; duplicate effective name of another zone of the branch, trimmed and case-insensitive → invalid), then apply and save once. `createZone` COPIED: `name = uniqueName(template.getName(), branchId)`. Private helpers `uniqueName`, `effectiveName`, `nameKey`, constant `NAME_MAX_LENGTH = 200`. LINKED creation unchanged |

### Client v2 (`GVRP_Entry_client_v2/src/app/`)

| Item | File | Change |
|---|---|---|
| C1 | `features/main/orders/components/edit-orders-dialog/edit-order-dialog.component.ts` | default `delivery_date: localDateIso()`; create/update error toasts `serverErrorMessage(err, 'Failed to create order' / 'Failed to update order')`; `status` strip unchanged |
| C2 | `features/main/orders/orders-section-view/orders-section.component.{ts,html}` | `(selectAllClick)="onSelectAllClick()"`; `allMatchingSelected` + `onSelectAllClick()` per design (info toast at 0, one `getOrders(currentFilters, 0, totalElements)`, success/error toasts, second click ignored while loading); reset in `onFilterChange`, `onPageChange`, `onClearSelection`, `onOrderSelectionChange`, bulk-status success. Notice line reuses the existing `.bulk-report` / `.bulk-icon` / `.bulk-summary` / `.bulk-dismiss` classes; **no SCSS change** |
| C3 | `features/main/jobs/route-planning-dialog/route-planning-dialog.component.html` | `{{ data.totalDemand \| number: '1.0-2' }} kg`. `.ts` unchanged: `CommonModule` (already imported) provides `DecimalPipe` |
| C4 | `features/admin/vehicles/vehicles-admin.component.html` | `{{ vehicle.capacity != null ? vehicle.capacity + ' kg' : '—' }}` |
| C5 | `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.html` | second sentence in the unserved alert; existing text unchanged; no SCSS change |
| C6 | `features/admin/vehicles/vehicle-permits-dialog/vehicle-permits-dialog.component.{html,scss}` | `<td class="actions-cell"><div class="row-actions">…</div></td>`; `.actions-cell { white-space: nowrap; width: 1%; }` |
| C7 | `core/services/lez-api.service.ts` | `renameZone(zoneId, name)` → `PATCH /zones/{id}` with `{ name }` |
| C7 | `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.{ts,html,scss}` | COPIED only: "Name" input above Geometry (signal prefilled with `data.zone.name`); save validates blank / > 200 before any request; changed trimmed name → `renameZone` first, then geometry, then rules (extracted to private `saveGeometryAndRules`, same order and messages as before); rename error → `formErrors` with `serverErrorMessage(error, 'Failed to rename the zone')`. SCSS: `.name-input` shares the `.geometry-text` rule |

### New tests

Entry (28 new tests, all new files):

| File | Tests |
|---|---|
| `dto/request/OrderInputCreateStatusTest` | `bfT1_createWithoutStatus_bindsNullAndHasNoViolation`, `bfT1_createWithStatus_hasOneViolationOnStatus` |
| `controller/OrderCreateValidationTest` | `bfT2_blankOrderCode_is400WithTheFieldMessage_andNoRejectedValue`, `bfT2_twoInvalidFields_giveOneErrorEach` |
| `mapper/VehicleMapperCapacityTest` | `bfT3_capacityComesFromTheVehicleType`, `bfT3_noVehicleType_givesNullCapacityWithoutException` |
| `service/OptimizationJobStatusMessageTest` | `bfT4_twelveOnRouteOrders_messageNamesTenAndCountsTheRest`, `bfT4_fewOrders_areAllNamedWithoutACount` |
| `service/OptimizationCallbackUnassignedOnceTest` | `bfT5_oneUnassignedOrder_isStoredOnce` |
| `service/lez/LezZoneNameTextTest` | `bfT6_stopInsideAnAllDayZone_detailNamesTheZone`, `bfT6_legCrossingAnAllDayZone_detailNamesTheZone`, `bfT6_blankZoneName_fallsBackToTheKey`, `bfT6_label_trimsTheName`, `bfT7_reasonNamesEveryCoveringZone`, `bfT7_keyWithoutACompiledZone_isPrintedAsTheKey` |
| `service/lez/ZoneNameTest` | `bfT8_copyWhileTheLinkedZoneExists_isNamedCopy`, `bfT8_copyWhenBaseAndCopyAreTaken_isNamedCopy2_caseInsensitive`, `bfT8_longTemplateName_staysWithin200Characters`, `bfT8_nameIsReadThroughTheBranchScopedQuery`, `bfT9_renameCopied_trimsAndSaves`, `bfT9_renameToItsOwnNameInAnotherCase_isAllowed`, `bfT9_blankName_isInvalid_andNothingIsSaved`, `bfT9_nameOf201Characters_isInvalid_andNothingIsSaved`, `bfT9_renamingALinkedZone_isAConflict_andNothingIsSaved`, `bfT9_bothFieldsNull_isInvalid_andNothingIsSaved`, `bfT9_nameOfAnotherZoneInOtherCase_isInvalid_andNothingIsSaved`, `bfT9_enabledOnly_savesEnabledAndKeepsTheName`, `bfT9_otherBranchZone_isNotFound_beforeAnyNameCheck` |

Client (22 new tests, all new files): `features/main/orders/components/edit-orders-dialog/edit-order-dialog-errors.component.spec.ts` (BF-C1, 4), `features/main/orders/orders-section-view/orders-section-select-all.component.spec.ts` (BF-C2, 8), `features/main/jobs/route-planning-dialog/route-planning-dialog-demand.component.spec.ts` (BF-C3, 1), `features/main/jobs/job-monitoring-dialog/job-monitoring-lez-hint.component.spec.ts` (BF-C4, 1), `features/admin/vehicles/vehicle-permits-dialog/vehicle-permits-layout.component.spec.ts` (BF-C5, 1), `features/admin/zones/zone-editor-dialog/zone-editor-rename.component.spec.ts` (BF-C6, 6), `core/services/lez-api-rename.service.spec.ts` (BF-C7, 1).

## Commands Executed

From the repository root unless a module is named.

| Step | Command | Exit / result |
|---|---|---|
| (user request) | `git commit` of MAP-ORDERS-001 on `fix/map-orders-001` | `d6e4015` |
| 0.1 | `workflow.mjs status work-items/MAP-ORDERS-001/run.json`; `implementation.md` exists | 0, state `verify` rev 4 |
| 0.2 | `git log -1 --oneline`, `git status --short` on `fix/map-orders-001` | `d6e4015`, clean (BROWSER-FIX-001 docs were already committed in `8682ceb`) |
| 0.3 | `node tools/session-brief.mjs` | 0 (summary below) |
| 0.4 | `git switch -c fix/browser-fix-001` | 0 |
| 0.5 | stop `ng serve` | not done: PID 15636 still listens on :4200; stopping it was refused by the agent permission classifier earlier in this session (MAP-ORDERS-001 Deviation 2) and was not retried. Entry/Engine servers not touched |
| 1 | `workflow.mjs init work-items/BROWSER-FIX-001/run.json . BROWSER-FIX-001 GVRP_Entry_API/src GVRP_Entry_client_v2/src` | 0, `intake` rev 0 |
| 1 | `workflow.mjs step … ready 01/02/03-proof.json` | 0 each: `discover` rev 1, `design` rev 2, `implement` rev 3 |
| 2 | first Entry attempt via Git Bash `cmd //c "mvnw.cmd -B clean test"` | 1: `'mvnw.cmd' is not recognized` (shell quoting of the path with spaces); not a test result |
| 2 | PowerShell, `JAVA_HOME=C:\Program Files\Java\jdk-21` for the command only, `.\mvnw.cmd -B clean test` | 0, 270 / 0 / 0 / 0 |
| 2 | client `npx ng test …`; `rm -rf dist && npm run build` | 0, 363 SUCCESS; 0, 12 warnings |
| 2 | text-assertion searches (Grep) | see Baseline |
| 3 | Entry `.\mvnw.cmd -B clean test` (JDK 21) | 0, **298 / 0 / 0 / 0**, BUILD SUCCESS |
| 3 | client `npx ng test --watch=false --browsers=ChromeHeadless` | 0, **385 SUCCESS** |
| 3 | client `rm -rf dist && npm run build` | 0, 0 ERROR, 12 warnings, identical to baseline (sorted text diff) |
| 3 | `git status --short`, `git diff --stat` | 22 tracked files changed (264 insertions, 25 deletions) + 14 new test files + `work-items/BROWSER-FIX-001/run.json` |
| 4 | `workflow.mjs step … ready 04-proof.json` | run after this file is written; result in the handoff message |

Session brief (Step 0.3, 2026-10-29T00:42Z): branch `fix/map-orders-001` @ `d6e4015`, clean. Open work items: LEZ-001 `implement` rev 10 (issues: "Scoped files changed; inspect and reverify affected work", "Some historical evidence is changed or unavailable"); MAP-ORDERS-001 `verify` rev 4 (issues: "No checkpoint: reconstruct progress from transition evidence", "Some historical evidence is changed or unavailable" — the latter because one line of `MAP-ORDERS-001/implementation.md` was edited after its `04` step was recorded). Security not FIXED: SEC-001 (Critical), SEC-010 (Medium), SEC-004/005/007/008 (Low). 14 of 14 lessons active.

Environment note: the shell's `JAVA_HOME` points to `C:\Program Files\Java\jdk-19\bin\` (L-2026-007); every Maven run here set `JAVA_HOME` to the JDK 21 install for that command only.

## Verification Results

- Entry: 298 = baseline 270 + 28 new; 0 failures, 0 errors, 0 skipped. VERIFIED.
- Client tests: 385 = baseline 363 + 22 new, all SUCCESS. VERIFIED. (`ERROR:` console lines in the log, e.g. "Failed to create depot", come from existing specs and are present in the baseline log too.)
- Client build: EXIT=0, no ERROR, no new budget warning. VERIFIED.
- Static: every changed/new path is in the task's allowed list. VERIFIED.
- Live browser / UI check: not run. NOT_VERIFIED.

## Modified Tests

None. No existing test, fixture or helper (Java or client) was edited, renamed, disabled, skipped, focused or excluded.

## Security-Relevant Changes

- **E2 logging:** the new handler logs one WARN line with the error count and the bound object's name only; no rejected value, no field value, no stack trace. The response carries the constraint's default message and the field name only (BF-T2 asserts the phone value is absent from the body). Before, these requests fell to `handleGenericException`, which logged the full exception including rejected values.
- **E7 tenant scoping:** `updateZone` still starts with `findZone(id, branchId)` (`findByIdAndBranchId`): another branch's id answers 404 before any name check or write (BF-T9 `otherBranchZone`). Name uniqueness reads only `findByBranchIdOrderByIdAsc(branchId)` of the caller's branch, in both `updateZone` and `createZone` (BF-T8 `nameIsReadThroughTheBranchScopedQuery`). The branch still comes from the authenticated principal through the existing controller; no new endpoint, no `permitAll`, no `SecurityConfig` change. The rename uses the existing `PATCH /zones/{id}`.
- The duplicate-name error echoes the zone name the planner typed (branch configuration, not customer data).
- No secret, token or customer data in code, tests, logs or this file.

## Deviations From Design

1. **E6 "six detail strings":** `ComplianceChecker` has **five** detail strings that print the zone key (two stop texts, one transit missing-data text, two transit texts); all five now use `label(zone)`. The sixth candidate, `"Missing vehicle data decides zone access: …"`, names no zone and was left unchanged. Reviewer: confirm, or decide whether it should name the zone.
2. **C3:** the `.ts` was not touched; `CommonModule` already provides the `number` pipe.
3. **E4:** the 10-code limit is an inline literal with a comment instead of a constant, to keep the edit inside the invalid-status branch.
4. **Step 0.5:** `ng serve` not stopped (see Commands). No `npm ci` or dependency change ran.
5. No client spec for C4 (design has none; the server side is covered by BF-T3).

## Remaining Uncertainty

- Browser behaviour of every change (Add Order, Select All, planning dialog value, vehicles list, job monitor text, permits layout, zone rename, Recheck texts): NOT_VERIFIED.
- Select All: the table's own checkboxes do not reflect the all-pages selection (the table keeps its page and its `SelectionModel`); the toolbar count, Plan and the notice use `selectedOrders`. One request loads every matching order (25 054 rows on the test date; several MB), accepted by the user's choice; no server page cap.
- Stored texts of past solutions are unchanged: old violation details are rewritten only by the next Recheck; old LEZ-unassigned reasons stay as stored (request exclusion).
- E1 changes PUT semantics: an update without `status` now keeps the stored status instead of resetting it to `SCHEDULED` (mapper ignores nulls). The client always sends `status` on update, so the UI is unaffected. NOT_VERIFIED against other API clients.
- Existing duplicate zone names in the database are not renamed (user decision: the user renames them).

## Lesson Candidates

- Running `mvnw.cmd` from Git Bash with `cmd //c` in a path with spaces fails with "not recognized"; run Maven through PowerShell (`& .\mvnw.cmd …`) with `JAVA_HOME` set for the command. Candidate addition to L-2026-007 / L-2026-014.

## Handoff to Reviewer

- Read first: `ZoneService.updateZone` / `uniqueName` (tenant scoping, validation order), `GlobalExceptionHandler.handleValidationFailure` (logging), then Deviation 1.
- Re-run: `cd GVRP_Entry_API && mvnw.cmd -B clean test` with JDK 21 (expect 298 / 0 / 0 / 0); `cd GVRP_Entry_client_v2 && npx ng test --watch=false --browsers=ChromeHeadless` (expect 385 SUCCESS) and `npm run build` (EXIT=0, 12 warnings).
- Live check suggested (planner account the user names): Add Order valid / with blank code; Select All on a date with N orders, then page change; planning dialog demand; vehicles list capacity; plan with an LEZ-excluded order and Recheck; copy a template whose LINKED zone exists, rename the copy, try a duplicate name. Record every row created.
- `traceability.md` is not in this task's allowed files and is left for the reviewer.
