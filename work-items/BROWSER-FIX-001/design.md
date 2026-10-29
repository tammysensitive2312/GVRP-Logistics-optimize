# Design — BROWSER-FIX-001

Inputs: `request.md` (BF-R1…R12), `context.md`. Technical design by Claude, 2026-10-29. Entry API + client v2; no schema change, no new dependency, no Engine change. Runs **after MAP-ORDERS-001** (stacked branch; reuses its `localDateIso`).

## 1. Entry API

**E1 — Order create (BF-R1).** `dto/request/OrderInputDTO.java`: remove the field initializer of `status` (keep the field, its type and its `@Null(groups = OnCreate.class)`). Nothing else in the class changes. Effect on update: a PUT without `status` now keeps the stored status (mapper ignores nulls) instead of resetting it to `SCHEDULED`; record this in the implementation notes.

**E2 — 400 for validation failures (BF-R2).** `exception/GlobalExceptionHandler.java`: new `@ExceptionHandler(MethodArgumentNotValidException.class)` → `400` with one `ErrorDetail` per error: `code = ErrorCode.VALIDATION_ERROR.getCode()`, `message` = the constraint's default message, `field` = the field name (`FieldError#getField`; global `ObjectError` → `field` null), in the order Spring reports them. Log one `WARN` line with the error count and the object name only — never the rejected value. No other handler changes.

**E3 — Vehicle capacity (BF-R6).** `dto/response/VehicleDTO.java`: add `private Integer capacity;`. `mapper/VehicleMapper.toDTO`: `@Mapping(source = "vehicleType.capacity", target = "capacity")`. JSON key `capacity` (global snake_case already matches).

**E4 — Planning refusal text (BF-R7).** `OptimizationJobService.validateAndLoadOrders`, invalid-status branch only: keep `code`, `resource` and `field` exactly as today; replace the message with
`"Only SCHEDULED orders can be planned. These orders are already planned, delivered or left unassigned: " + <first 10 order codes joined by ", "> + (more ? " (+N more)" : "") + "."`
(order of codes = order of the loaded list, as today).

**E5 — One row per unassigned order (BF-R12).** `OptimizationCallbackService.saveSolution`: delete the second, identical `solution.getUnassignedOrders().addAll(unassignedList);`. Nothing else in the class changes.

**E6 — Zone names in LEZ texts (BF-R10).**
- `ComplianceChecker`: new `static String label(CompiledRestrictions.CompiledZone zone)` → trimmed `name` when not blank, else `key`. In the six detail strings, replace the key by `"\"" + label(zone) + "\""`, e.g. `Stop is in zone "Test zone" and the vehicle is banned all day there`. Codes, `seq`, `orderId`, `zoneKey` and the `LEZ_GEOMETRY_MISSING` texts are unchanged.
- `RouteComplianceService.noCompliantVehicleReason`: map each key of `orderZoneKeys(orderId)` to `ComplianceChecker.label` of the zone with that key in `restrictions.getZones()` (key itself when not found), quoted, joined by `", "`: `No compliant vehicle for zone "Test zone"`.
- Stored texts are not migrated (request exclusions).

**E7 — Distinct zone names (BF-R11).**
- `BranchZoneUpdateRequest`: fields `Boolean enabled`, `String name`; keep `@Data @NoArgsConstructor @AllArgsConstructor` and **add** an explicit constructor `BranchZoneUpdateRequest(Boolean enabled)` (used by existing tests).
- `ZoneService.updateZone(id, request, branchId)`: `findZone` first (unchanged order); then validate before writing: `request == null` or both fields null → `LezSupport.invalid("enabled or name is required")`; when `name != null`: LINKED → `LezConflictException("Zone <id> is LINKED to its template and cannot be renamed")`; trimmed name empty → invalid `"name must not be blank"`; longer than 200 → invalid `"name must be at most 200 characters"`; equal (case-insensitive, trimmed) to the effective name of **another** zone of the branch → invalid `"Another zone of this branch is already named <name>"`. Then apply `enabled` (if given) and the trimmed `name` (if given), save once, return as today (`warnings` rule unchanged).
- `ZoneService.createZone` COPIED: `name = uniqueName(template.getName(), branchId)`: the effective names of the branch's zones (`zoneRepository.findByBranchIdOrderByIdAsc(branchId)`; LINKED → `sourceTemplate.getName()`, COPIED → `getName()`, nulls skipped; compared trimmed, case-insensitive). If the base name is free → base; else the first free of `"<base> (copy)"`, `"<base> (copy 2)"`, `"<base> (copy 3)"`, …; the base is shortened so the result fits 200 characters. LINKED creation unchanged.
- Effective-name helper shared by both paths (private method in `ZoneService`).

## 2. Client v2

**C1 — Order dialog (BF-R3).** `edit-order-dialog.component.ts`: default `delivery_date: localDateIso()` (`@shared/utils/date.utils`, from MAP-ORDERS-001); create and update error toasts use `serverErrorMessage(err, 'Failed to create order')` / `serverErrorMessage(err, 'Failed to update order')`. Nothing else changes (the `status` strip stays).

**C2 — Select all matching orders (BF-R4).** `orders-section.component.{ts,html}`:
- html: bind `(selectAllClick)="onSelectAllClick()"` on `app-orders-action-toolbar`; below the toolbar, when `allMatchingSelected` is true, a one-line notice "All {{ selectedOrders.length }} orders matching the filters are selected." with a "Clear selection" button (`onClearSelection()`). Reuse existing classes/styles where possible; any new SCSS stays small and never pushes the file over the 8 kB error budget.
- ts: field `allMatchingSelected = false`; `onSelectAllClick()`: if `totalElements === 0` → `toastService.info('No order matches the filters.')` and return; else `apiService.getOrders(this.currentFilters, 0, this.totalElements)` (same filters object as the table) → `selectedOrders = response.content`, `allMatchingSelected = true`, `toastService.success(\`Selected ${n} orders matching the filters.\`)`; on error → `toastService.error(serverErrorMessage(err, 'Failed to select all orders'))`, selection unchanged. A second click while a request runs is ignored.
- `onFilterChange`, `onPageChange`, `onClearSelection`, `onOrderSelectionChange` (a row toggle in the table) and `onBulkEditStatus` success set `allMatchingSelected = false` (the last two keep their current `selectedOrders` logic). `loadOrders` is **not** called by select-all (the table keeps its page).
- Plan, D4 check and bulk status use `selectedOrders` as today.

**C3 — Demand format (BF-R5).** `route-planning-dialog.component.html`: `{{ data.totalDemand | number: '1.0-2' }} kg` (import `DecimalPipe` or `CommonModule` in the component if missing).

**C4 — Capacity cell (BF-R6).** `vehicles-admin.component.html`: show `{{ vehicle.capacity }} kg` when `capacity` is not null/undefined, else `—`.

**C5 — LEZ hint in the job monitor (BF-R8).** `job-monitoring-dialog.component.html`: keep the existing unserved alert text unchanged and add, inside the same alert, a second sentence: "Orders left out for low-emission zones are listed in the compliance panel of the Route tab."

**C6 — Permit actions layout (BF-R9).** `vehicle-permits-dialog.component.html`: `<td class="actions-cell"><div class="row-actions">…same two buttons…</div></td>`; `.scss`: `.actions-cell { white-space: nowrap; width: 1%; }` (`.row-actions` keeps `display: flex` on the div).

**C7 — Rename a COPIED zone (BF-R11).**
- `core/services/lez-api.service.ts`: `renameZone(zoneId: number, name: string): Observable<BranchZoneDTO>` → `PATCH /zones/{id}` with `{ name }`.
- `zone-editor-dialog.component.{ts,html}`: COPIED only, a "Name" text input above Geometry, prefilled with `data.zone.name`. On save: trimmed empty → form error "Name is required."; longer than 200 → "Name must be at most 200 characters." (no request). If the trimmed name differs from the original → `renameZone` first, then geometry, then rules (today's order); otherwise no rename call. Rename error → `formErrors` with `serverErrorMessage(error, 'Failed to rename the zone')`, nothing else sent.

## 3. Tests (new files only)

Entry (JUnit 5, Mockito, no DB):

| ID | Case | Expected |
|---|---|---|
| BF-T1 | `ObjectMapper` (project config or `findAndRegisterModules`) reads a valid create JSON without `status` into `OrderInputDTO`; Bean Validation (`Validation.buildDefaultValidatorFactory()`) with group `OnCreate` | `getStatus()` null; no violation. With `"status":"SCHEDULED"` → one violation on `status` |
| BF-T2 | `MockMvcBuilders.standaloneSetup(new OrderController(mocks)).setControllerAdvice(new GlobalExceptionHandler())`, POST `/api/v1/orders` with blank `order_code` and a phone value | 400; body has an error with `field` = the order-code field and its message; the phone value does not appear in the body; service not called |
| BF-T3 | `Mappers.getMapper(VehicleMapper.class).toDTO` (or the generated impl) of a vehicle whose type capacity is 1000; type null | `capacity` 1000; null without exception |
| BF-T4 | `OptimizationJobService.submitJob` with 12 orders, all `ON_ROUTE` | `DataInvalidException`; code `RESOURCE_CONFLICT`; `field` lists all 12 codes (as today); message starts "Only SCHEDULED orders can be planned", contains 10 codes and "(+2 more)" |
| BF-T5 | Completion callback with one unassigned order (same Mockito setup style as `OptimizationCallbackServiceLezTest`, new class) | saved solution has exactly one unassigned row |
| BF-T6 | `ComplianceChecker.check` with `zone("Z1", square, "Test zone")` all-day ban: stop inside; leg crossing; zone with blank name | details contain `"Test zone"` and not `zone Z1`; blank name → detail contains `"Z1"`; `zoneKey` still `Z1` |
| BF-T7 | `RouteComplianceService.noCompliantVehicleReason` with two zones named "A" and "B" covering the order; key without a compiled zone | `No compliant vehicle for zone "A", "B"`; unknown key printed as the key |
| BF-T8 | `ZoneService.createZone` COPIED when the branch already has a LINKED zone of the template; then with "Test zone" and "Test zone (copy)" taken | names `Test zone (copy)` and `Test zone (copy 2)`; a 199-character template name still yields ≤ 200 characters |
| BF-T9 | `ZoneService.updateZone`: rename COPIED (trimmed, saved); blank; 201 chars; LINKED + name; both fields null; name equal (other case) to another zone; enabled only | saved name; invalid; invalid; `LezConflictException`; invalid; invalid; enabled saved, name untouched. Invalid cases: nothing saved |

Client (Jasmine/Karma, spies, no real HTTP):

| ID | Case | Expected |
|---|---|---|
| BF-C1 | Edit-order dialog: default date; `addOrder` errors with the interceptor shape `{message, status, originalError}` carrying a server body | `delivery_date === localDateIso()`; toast shows the server message |
| BF-C2 | Orders section: `totalElements` 37, click select-all | `getOrders(currentFilters, 0, 37)`; `selectedOrders` = response; `allMatchingSelected` true; then `onPageChange` / `onFilterChange` / a table selection event / clear → false; `totalElements` 0 → no request, info toast; error → selection unchanged, error toast |
| BF-C3 | Planning dialog with `totalDemand` 2839.2000000000003 | rendered value has no more than two decimals (no `0000000`) |
| BF-C4 | Job monitor with unserved > 0 | the alert contains the existing sentence and "low-emission" |
| BF-C5 | Permits dialog with one permit | the actions `td` contains a `.row-actions` div holding Edit and Delete |
| BF-C6 | Zone editor (COPIED): unchanged name → no `renameZone`; changed → `renameZone(id, trimmed)` called before `replaceZoneGeometry`; blank / 201 chars → error, no call; rename error → message, geometry not sent | as stated |
| BF-C7 | `LezApiService.renameZone` (`HttpTestingController`) | `PATCH …/zones/5` with body `{ name: 'X' }` |

If any row cannot be tested without a database or a live browser, extract the logic into a pure function and test that; if even that is impossible, stop and report.

## 4. Risks

- E6 changes user-facing texts; an existing test asserting an exact old text would fail → stop and report (do not edit it).
- C2 loads every matching order in one request (25 054 rows ≈ several MB on the test data). Accepted by the user's choice; no page cap exists on the server today.
- E1 changes PUT semantics for a missing `status` (kept instead of reset); the client always sends it, so the UI is unaffected.
