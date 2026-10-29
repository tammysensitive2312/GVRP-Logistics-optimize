# Review — LEZ-001 P1c and P1c follow-up (independent)

Reviewer: Claude (claude.ai session), 2026-10-29 +07:00. Static reading through the file bridge; nothing was executed, no database touched.
Subject: `implementation.md` §P1c and §"P1c follow-up — live browser findings (2026-10-28)", `checkpoint-p1c.json`, `checkpoint-p1c-fix.json`, the uncommitted tree on `feature/lez-001`.
Status words follow AGENTS.md §5.

## 1. Verdict

**CHANGES REQUIRED (small).** The increment matches `design-p1c.md` and the task's file allowance, the tenant scoping of the new endpoint is correct, and test integrity holds. One HIGH finding makes every pre-P1b route unsendable from the UI in a branch with an enabled zone although the server would accept it (R1), and the planner never sees the server's 422 explanation (R2). Both are a few lines of client code.

## 2. What was inspected

| Area | Files (read in full unless noted) |
|---|---|
| Record | `implementation.md` §P1c + follow-up, `checkpoint-p1c.json`, `checkpoint-p1c-fix.json`, `run.json` (state `implement`, rev 8 after two checkpoints) |
| Entry B1 | `service/lez/ZoneMembershipService`, `controller/LezMembershipController`, `dto/response/OrderZoneMembershipDTO`, `GeoJsonGeometryParser.covers` |
| Entry B2 | `service/lez/ComplianceReadService`, `dto/response/SolutionComplianceDTO` |
| Entry follow-up | `exception/GlobalExceptionHandler`, `util/ErrorCode`, `dto/request/VehicleTypeInputDTO` |
| Entry tests | `ZoneMembershipServiceTest`, `LezMembershipRoleAccessTest`, `ComplianceReadAdditionsTest`, `GlobalExceptionHandlerClientErrorsTest`, `VehicleTypeInputDeserializationTest` |
| Client core | `core/models/lez.model.ts`, `core/services/lez-api.service.ts`, `core/interceptors/error.interceptor.ts`, `app.config.ts` (interceptor order), `app.routes.ts` |
| Client utils | `polyline.utils`, `route-geometry.utils`, `lez-format.utils`, `api-error.utils`, `features/admin/zones/zone-rule-form.utils` |
| Client screens | `shared/services/map.service`, `route-view.component.ts`, `lez-compliance-panel/*`, `orders-section.component.{ts,html}`, `driver-view.component.ts` (map/instruction part); `zones-admin`, `zone-editor-dialog`, `vehicle-type-dialog`, `vehicle-permits-dialog` (key logic, by search) |
| Test integrity | the five D8 specs against the snapshots Claude took on 2026-10-28 before P1c; mtimes of every historical Java test |

## 3. Test integrity

- `orders-section.component.spec.ts`, `orders-section-pareto.component.spec.ts`: line-ending-normalised diff (L-2026-009) shows only one import, one `LezApiService` spy returning `of([])`, and its provider, all in setup. No `it`, assertion, fixture or expected value changed. **Within D8.**
- `route-view.component.spec.ts`, `driver-view.component.spec.ts`: identical to the snapshot. `send-route-dialog.component.spec.ts`: mtime unchanged since 2026-10-06.
- Entry: every historical test file keeps its pre-P1c mtime; P1c/follow-up tests are new files only. `ComplianceReadServiceTest` (P1b) unchanged; the additions went to `ComplianceReadAdditionsTest` as instructed.
- Reported results (not re-run by the reviewer): Entry 270/0/0/0 without exclusions; client 323 SUCCESS; build EXIT=0.

## 4. Findings

| ID | Severity | Finding | Required action |
|---|---|---|---|
| R1 | **HIGH** | **UI blocks routes the server would send.** `ComplianceChecker` reports `LEZ_GEOMETRY_MISSING` for every leg without a polyline once a zone is enabled. *Recheck* stores that as `VIOLATION`, and `RouteViewComponent.sendBlockedReason` then disables *Send to driver*. The server's send guard, however, first calls `fillMissingLegs` (engine `/legs`) and only then checks, so the same route can pass. Result recorded by the follow-up itself: 0 of 215,251 `route_stops` have geometry, so every existing route in a branch with an enabled zone is unsendable from the UI. Design-p1c §3.6 did not foresee this interaction (reviewer's own spec gap). | In `sendBlockedReason`, treat a `VIOLATION` whose violations are **all** `LEZ_GEOMETRY_MISSING` as sendable, with a visible note "Road geometry is fetched and checked when you send". Any other code still blocks. One `route-view-lez` test for each case. No server change. |
| R2 | MEDIUM | **The server's refusal text never reaches the planner.** `errorInterceptor` re-throws `{message, status, originalError}`, but `RouteViewComponent` reads `error.error.errors[0].message` on send, evaluate and save, which is always undefined behind the interceptor. A 422 from the send guard (`ZONE_ACCESS seq=… zone=…`) and the D5 LEZ refusal on save therefore show only "Could not send the route." / "The plan violates hard constraints…". Pre-existing pattern, but P1c relies on it (design-p1c §3.6). | Use `serverErrorMessage(error, <existing fallback>)` in the three handlers of `route-view.component.ts`; keep the status checks (`status` is on the wrapper). New tests in `route-view-lez.component.spec.ts` with the interceptor-shaped error. |
| R3 | MEDIUM | **`focusLeg` highlights the whole route, permanently.** Server and OSRM paths are drawn as one polyline per route, so `entry.lines[seq]` is undefined for `seq > 0` and the fallback `lines[0]` turns the entire route red; the style is never reset. The record says it "highlights the leg/stop". Design §8 asks for the offending leg. | Draw a temporary highlight layer for the leg ending at `seq` (stored geometry: decode that leg only; fallback: straight segment between the two markers), remove the previous highlight on the next focus or redraw, and correct the record. |
| R4 | LOW | Zone overlay race: two `displaySolution` calls in flight both add their `listZones` result after `clearLayers`, so zones can be drawn twice. | Ignore a response whose solution is no longer the displayed one (token/counter). |
| R5 | LOW | Permit edit allows clearing `valid_from`/`valid_to` (create-only check, justified by "the edit fixture has no dates"); the server always answers 400. Product code shaped by a test fixture. | Require both dates on edit too; give the new spec's edit fixture dates. |
| R6 | LOW | Rule form does not require `effective_from` although `RuleValidator` does (server 400 is shown, so not blocking). | Add the check in `validateRuleForm` + one RF case. |
| R7 | LOW | `handleUnreadableBody` logs and returns the Jackson cause; for type mismatches Jackson includes the offending input value, which may be a customer field (AGENTS.md §15 logging). | Log the exception class and the JSON path only; keep the cause in the response. |
| R8 | INFO | `orders-table.component.scss` compiles to 7.93 kB, 0.07 kB under the 8 kB **error** budget: the next style line there breaks the build. | Note for later work; no action now. |
| R9 | INFO | "Test zone" twice in dropdowns is expected when the branch holds a LINKED and a COPIED zone of the same template (both carry the template name). | Optional: show the mode next to the name. |

## 5. Scope check

- P1c stayed inside the task's allowed files.
- The follow-up touched four files outside that list (`GlobalExceptionHandler`, `ErrorCode`, `VehicleTypeInputDTO`, `error.interceptor.ts`). The user authorised the repair in the browser session ("hợp lý, bạn sửa ngay đi"); all four are inside the run scope (`*/src`). **Accepted** as an authorised scope addition; the record lists them openly.
- A1 (`@JsonAlias("vehicle_features")`) fixes a pre-existing 500 on every vehicle-type edit from both clients — confirmed by reading `VehicleTypeInputDTO` (canonical name `features`) against the client payload. Correct and minimal.
- D/D2 handlers: 400 for unreadable bodies and 405 for wrong methods replace a generic 500; no tenant logic, no `permitAll`. Accepted (see R7).
- No dependency, `angular.json`, `package*.json`, `public/**`, migration, `SecurityConfig` or P1b behaviour change found.

## 6. Security

- `GET /api/v1/lez/order-zones`: branch only from the principal, null branch → 403 before any read, other-branch and unknown ids omitted (no existence leak), 1…200 ids, malformed list → 400, PLANNER-only by the existing rule (OZ-T3). **No tenant finding.**
- Compliance additions read only the already branch-checked solution.
- Driver payload unchanged in shape (names only, no polygons).
- SEC-010 (unauthenticated Engine API) remains OPEN and untouched.

## 7. Still open (not P1c defects)

- P1b manual test (review-p1b §5 step 4) postponed by user decision → send guard NOT_VERIFIED.
- P1-seed (Hanoi templates) not done.
- review-p1b F2–F8.
- The agent-started `spring-boot:run` instance on 8080 (follow-up handoff) should be stopped and the IDE instance restarted.

## 8. Re-review of the P1c review fix (2026-10-29)

Subject: `implementation.md` §P1c review fix, `checkpoint-p1c-review-fix.json`, task `task-opencode-p1c-review-fix.md`. Static reading; nothing executed.

**Verdict: R1, R2, R3, R5, R6 FIXED in code — accepted, pending the user's browser check.**

| Finding | Check | Result |
|---|---|---|
| R1 | `RouteViewComponent.sendBlockedReason` / `isGeometryOnly` / `sendNote`; tests FR1-T1/T2 | Only an all-`LEZ_GEOMETRY_MISSING` list unblocks; any other code or an empty list still blocks; note rendered. Correct. |
| R2 | send handler, `describeEvaluateError`, `describeSaveError` | `serverErrorMessage` with the old fallbacks; 0/502 still first, 409 still sets `saveConflict` and appends the instruction, 422 on send still emits. Tests use the interceptor shape. Correct. |
| R3 | `map-focus.utils.violationFocus`, `MapService.focusLeg`, `storedLegPaths`, panel passes `code`/`detail` | Leg vs stop matches `ComplianceChecker` (seq = stop, or departing stop of the leg); stops indexed by `orderStops` and mapped to markers by identity; highlight in its own layer, cleared on next focus and in `clearRoutes`; route colour untouched. Correct. Stated assumption (legs `0…n−1`, `orderStops` = plan order) is recorded. |
| R5 | `vehicle-permits-dialog.validate()` | Both dates required on create and edit; edit fixture given dates. Correct. |
| R6 | `validateRuleForm` | `"Effective from" is required.` added; one fixture given a date. Correct. |

Test integrity: the five D8 specs keep their pre-fix mtimes; the three changed P1c expectations are each caused by the fix and listed (R3 call arguments, R5 and R6 fixtures). Reported 339 SUCCESS, build EXIT=0 (not re-run by the reviewer). Scope: 16 files, all in the allowance.

Observations (INFO, no action required):
- Several P1c fixtures use codes the server never emits (`ZONE_FORBIDDEN`, `NO_LEGAL_VEHICLE`). The rules under test do not depend on the code value, so the tests are still meaningful; prefer real codes in new tests.
- For an error without a body, `serverErrorMessage` returns the interceptor's generic status text (e.g. 422 "Validation error. Please check your input.") instead of the local fallback. Acceptable.

Still open: R4, R7, R8, R9; the P1c browser check; review-p1b §5 step 4; P1-seed; review-p1b F2–F8.

## 9. Browser check (2026-10-29, reviewer, built-in browser, planner Branch Huge 1)

Services: Entry 8080, Engine 8081, client 4200 (started by the user). Data: all 25 054 orders of the branch are on 2026-08-21, SCHEDULED.

Test setup changed in the DB through the UI (not reverted):
- COPIED zone (branch_zone_id 2, key Z2): geometry was the French Quarter box `105.845,21.02 – 105.86,21.035`, now `105.825,20.99 – 105.83,20.995` (5 orders inside). Rule was `TRUCK, GROSS > 3500 kg, ALWAYS_FORBIDDEN, from 2027-01-01`; now empty selector (every vehicle), `ALWAYS_FORBIDDEN`, from 2026-01-01. Enabled.
- Permit `TEST-R5` on 30H-109010.LG created and deleted again.
- Jobs #88, #89, #90 (solutions 69, 70, 71) planned from orders `ORD-00378*`, `ORD-00349*`, `ORD-00032*`.
- Route 40853 (solution 66, 30H-108799.LG) sent to driver "hoang to cuong" (send id 3).

| # | Check | Result |
|---|---|---|
| 1 | Zone editor: blank "Effective from" → `Rule 1: 'Effective from' is required.` (R6); empty selector → "Applies to every vehicle." | PASS |
| 2 | Verdict check zone 2 × 30H-109010.LG → "Forbidden all day 00:00–24:00" | PASS |
| 3 | Permit: add with blank "Valid to" → error; edit and clear "Valid to" → `'Valid to' is required.`, no PUT sent (R5) | PASS |
| 4 | Order table chip `LEZ: Test zone` on ORD-00378594; one `order-zones` request per page | PASS |
| 5 | D4 two-date toast | NOT TESTED — only one delivery date exists and Add Order returns 500 (see B1) |
| 6 | Plan 29 orders × 4 vehicles: panel lists ORD-00378594 as left out, 4 routes Compliant, both zone polygons drawn, 4 polylines from `/routes/{id}/geometry` (91–627 points), no OSRM request | PASS (see R10) |
| 7 | Disable Z2 → plan (job #90, ORD-00032904 on 30H-108841.LG seq 4) → enable Z2: routes show "Needs recheck", send disabled "Recheck…"; Recheck → 30H-108841.LG Violation (ZONE_ACCESS + 2× ZONE_TRANSIT), its send disabled with the codes, other 3 sendable. Clicking a ZONE_TRANSIT row adds one 32-point highlight (one leg, not the 118–476-point route) (R3) | PASS |
| 8 | Old route (job #87): Recheck → only `LEZ_GEOMETRY_MISSING` ×5, send enabled with "Road geometry is fetched and checked when you send." (R1); Send → 200, toast "Route sent to …"; Recheck → Compliant | PASS. R2 failure text not exercised (send succeeded) |
| 9 | Driver view | NOT TESTED — needs the driver's login |

New findings:
- **R10 (LOW)**: `lez_unassigned[].reason` and violation `detail` show the internal zone key: "No compliant vehicle for zone Z2", "Stop is in zone Z2 …". The planner never sees "Z2" anywhere else. Server text should use the zone name (or the client should map `zone_key` → name from `zones`).
- **R9 confirmed**: "Zones in force: Test zone, Test zone" and the permit zone dropdown cannot be told apart.
- **B1 (HIGH, outside LEZ)**: `POST /api/v1/orders` from the Add Order dialog → 500 "Internal server error." (payload: valid code, name, address, lat/lon, demand, service time, priority, date; empty phone and time windows). **Cause (stack trace 2026-10-29 07:18):** `OrderInputDTO.status` has the field initializer `= OrderStatus.SCHEDULED` and `@Null(groups = OnCreate.class)`; Jackson builds the DTO with the no-args constructor, so `status` is SCHEDULED even though the client omits it, and every create fails validation. Second cause: `GlobalExceptionHandler` has no handler for `MethodArgumentNotValidException`, so this (and every bean-validation failure on any endpoint) answers 500 "Internal server error." instead of 400 with the field message.
- INFO: planning orders already in another plan → 400 `0040901` toast "Resource conflict." without saying which orders/plan; job monitor's unassigned hint does not mention LEZ; plan dialog shows "2839.2000000000003 kg"; vehicles admin "Capacity" column shows only "kg"; permits table scrolls horizontally and hides the delete icon.
- MAP-ORDERS-001 confirmed live: date filter opens on 10/28 on 10/29 (+07:00), map shows no order markers after a date change; the Add Order dialog date also defaults to 10/28 (same `toISOString()` cause, not in MAP-ORDERS-001 scope).
