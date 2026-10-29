# Implementation — UX-ORDERS-001

Engineer: Claude Code (acting as `gvrp-engineer` for `task-opencode.md`; the OpenCode run itself failed with "Upstream request failed: Invalid credential" and the user asked Claude to do the task), 2026-10-29, Windows host shell.
Branch: `feat/ux-orders-001`, created from `fix/browser-fix-001` @ `150c561`.

## Scope

Design S1–S3 (Entry API) and C1–C3 (client v2): Add/Edit Order without coordinates (server geocodes the address), blank optional fields sent as `null` (BROWSER-FIX-001 review F1), working Import Orders end to end, import delivery date applied before validation, optional CSV coordinates, correct template and text format, geocoding logs without address/coordinates/URL. No schema change, no dependency, no Excel parsing, no `application*.properties` change.

## Baseline

Taken on `feat/ux-orders-001` before any code edit (tree = `150c561`).

| Module | Command | Result |
|---|---|---|
| GVRP_Entry_API | PowerShell, `JAVA_HOME` = JDK 21, `.\mvnw.cmd -B clean test` | EXIT=0, **298 / 0 / 0 / 0**, BUILD SUCCESS |
| GVRP_Entry_client_v2 | `npx ng test --watch=false --browsers=ChromeHeadless` | EXIT=0, **385 SUCCESS** |
| GVRP_Entry_client_v2 | `rm -rf dist; npm run build` | EXIT=0, 12 warnings (same list as BROWSER-FIX-001). PowerShell counted 13 lines because it wraps npm's stderr in one extra `NativeCommandError` line; the sorted text diff shows the same 12 warnings |

Timing check: the baseline ran in the background while the code was read; the Entry classes were compiled at 22:27:55, before the first source edit (`OrderService.java` at 22:28:17), so the baseline reflects the unmodified tree. No client file was edited before the client baseline finished.

**Assertion check (Step 2)** over `GVRP_Entry_API/src/test` and `GVRP_Entry_client_v2/src/**/*.spec.ts` for `Coordinates are required`, `latitude: 0`, `order_template.csv`, `.xlsx`, `OrderImportService`, `CsvFileParser`, `Goong`, `onImportClick`, `importOrders`:
- `latitude: 0` appears only in fixtures (`timeline-view`, `solution-view.utils`, `route-view` specs) and in `coordinate.utils.spec` (`hasMapCoordinates({latitude: 0, …})`) — not related to the order dialog.
- `OrderImportService` appears only as a `@Mock` in `OrderCreateValidationTest`, `OrderBulkControllerTest`, `RoleAccessTest`.
- No other match. No existing assertion is broken by the design.

## Changes Made

### Entry API (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`)

| Item | File | Change |
|---|---|---|
| S1 | `service/orders/OrderService.java` | new private `resolveCoordinates(input)` + `invalidField(field, message)`: both coordinates → unchanged; one → `VALIDATION_ERROR` on `latitude`; none + blank address → `VALIDATION_ERROR` on `address`; else `geocodingService.geocode(address)`, null → `VALIDATION_ERROR` on `address` with the design's text, else both set. `createOrder`: called first (before `validateBusinessLogic`, so the field errors win). `updateOrdersById`: called **after** the branch-scoped `findByIdAndBranchId` (see Deviation 1). No log line added |
| S2 | `service/orders/OrderImportService.java` | the one line `if (dto.getDeliveryDate() == null) dto.setDeliveryDate(request.getDeliveryDate());` (plus a one-line comment) before geocoding/validation |
| S2 | `dto/request/OrderImportRequest.java` | `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` on `deliveryDate` (+ import) |
| S2 | `service/integration/file/CsvFileParser.java` | optional `latitude`/`longitude` columns via the existing column lookup: both blank → null; one blank → `ParseException` "latitude and longitude must be given together" (field = the missing one); not a number → `ParseException` "Invalid number format for '<column>'" (field = the column). New private `parseOptionalDouble` |
| S3 | `service/GeocodingService.java` | log lines only: `debug("Geocoding request sent")`, `debug("Geocoding succeeded")` (was INFO with address and coordinates), `warn("Geocoding found no result")`, `error("Geocoding failed: {}", e.getClass().getSimpleName())` (+ one comment line). Behaviour unchanged |

### Client v2 (`GVRP_Entry_client_v2/src/app/`)

| Item | File | Change |
|---|---|---|
| C1 | `core/models/order.model.ts` | new types `OrderWritePayload`, `ImportErrorDTO`, `ImportResultDTO`; `OrderInputDTO` / `OrderDTO` unchanged |
| C1/C2 | `core/services/api.service.ts` | `addOrder` / `updateOrder` accept `OrderInputDTO \| OrderWritePayload`; new `importOrders(formData)` → `POST {apiUrl}/orders/import`, no explicit Content-Type |
| C1 | `features/main/orders/components/edit-orders-dialog/order-payload.utils.ts` (new) | `OrderFormModel` type; pure `toOrderPayload(form)` (trims, blanks → `null` for phone/notes/time windows, empty coordinates → `null`, numeric strings → numbers); helpers `isBlankCoordinate`, `coordinateValue` |
| C1 | `edit-order-dialog.component.ts` | form typed `OrderFormModel`, `latitude/longitude: null` for a new order; `validate()`: "Coordinates are required" removed, both-or-neither ("Enter both latitude and longitude, or leave both empty."), range checks when filled; create/update send `toOrderPayload(this.form)` (create still strips `status`) |
| C1 | `edit-order-dialog.component.html` / `.scss` | required markers removed from Latitude/Longitude; hint "Leave empty to locate the address automatically, or pick the location on the map."; `.coordinates-hint` rule (6 lines) |
| C2 | `features/main/orders/orders-section-view/orders-section.component.ts` | `onImportClick` appends `skipValidationErrors` and calls `importOrders`: success → toast "Imported X, skipped Y[, Z errors]" + `loadOrders()`; `success=false` → error toast "Import refused: Z rows have errors. Nothing was imported."; HTTP error → `serverErrorMessage(err, 'Import failed')`. Signals `importResult`, `importDetailsOpen`, computed `importErrorLines` (≤ 100 lines); `clearImportResult`, `toggleImportDetails`; exported pure `importErrorLine(error)` → "Line N (code): field — message" (never `raw_data`). The `console.log` of the dialog result is removed |
| C2 | `orders-section.component.html` | import banner (only when errors exist) reusing `bulk-report` / `bulk-icon` / `bulk-summary` / `bulk-toggle` / `bulk-dismiss` / `bulk-details`; **no SCSS change** |
| C3 | `features/main/orders/components/import-orders-dialog/import-orders-dialog.component.{ts,html}` | accept `.csv,.json` ("Only CSV and JSON files are allowed"; radio label and drop hint updated); `deliveryDate = localDateIso()`; exported `ORDER_IMPORT_TEMPLATE_CSV` (13 columns, one row with coordinates, one address-only row, quoted Vietnamese address, `;` skills) used by `downloadTemplate()`; text hint "Format (10 fields): …|ServiceTime", placeholder per design, line "Coordinates are found from the address automatically." |

### New tests

Entry (16): `service/orders/OrderServiceGeocodingTest` — `uoT1_noCoordinates_areGeocodedBeforeMapping_andSaved`, `uoT2_addressNotFound_isRefusedOnAddress_andNothingIsSaved`, `uoT2_blankAddressWithoutCoordinates_isRefusedOnAddress_withoutGeocoding`, `uoT2_onlyLatitude_isRefusedOnLatitude_withoutGeocoding`, `uoT3_bothCoordinates_areKept_andTheGeocoderIsNeverCalled`, `uoT4_updateWithBothCoordinatesNull_isGeocoded`, `uoT4_updateOfAnotherBranchOrder_isNotFound_beforeAnyGeocoding`; `service/orders/OrderImportDeliveryDateTest` — `uoT5_twoValidTextRowsWithAddresses_areImported`; `service/integration/file/CsvFileParserCoordinatesTest` — `uoT6_coordinatesAreParsed_andBlankCoordinatesAreNull`, `uoT6_onlyLatitude_isAnErrorRow`, `uoT6_nonNumericLatitude_isAnErrorRowNamingTheColumn`, `uoT6_fileWithoutCoordinateColumns_stillParses`; `controller/OrderImportBindingTest` — `uoT7_deliveryDateFormField_bindsToLocalDate`; `service/GeocodingServiceLoggingTest` — `uoT8_success_logsNoAddressNoCoordinates`, `uoT8_noResult_logsNoAddress`, `uoT8_failure_logsNeitherTheUrlNorTheKey`.

Client (20): `edit-orders-dialog/order-payload.utils.spec.ts` (UO-C1, 4), `edit-orders-dialog/edit-order-dialog-geocode.component.spec.ts` (UO-C2, 5), `core/services/api-import.service.spec.ts` (UO-C3, 1), `orders-section-view/orders-section-import.component.spec.ts` (UO-C4, 5), `import-orders-dialog/import-orders-dialog-formats.component.spec.ts` (UO-C5, 5).

## Commands Executed

| Step | Command | Exit / result |
|---|---|---|
| (user) | `opencode run --agent gvrp-engineer …` | exit 0 but no work: "agent gvrp-engineer is a subagent … Falling back to default agent", "Error: Upstream request failed: Invalid credential" |
| (user request) | `git commit` of BROWSER-FIX-001 on `fix/browser-fix-001` | `150c561` |
| 0.1 | `workflow.mjs status work-items/BROWSER-FIX-001/run.json` | `verify` rev 4 |
| 0.2 | `git log -1 --oneline`; `git status --short` | `150c561`; only `work-items/UX-LEZ-001/`, `work-items/UX-ORDERS-001/` (allowed). `BROWSER-FIX-001/review.md` and `test-scenarios.md` were already committed in `150c561` |
| 0.3 | `node tools/session-brief.mjs` | 0 (summary below) |
| 0.4 | `git switch -c feat/ux-orders-001` | 0 |
| 0.5 | servers | not touched; `ng serve` (PID 15636) still running, as recorded in BROWSER-FIX-001 |
| 1 | `workflow.mjs init …` + `step … ready 01/02/03-proof.json` | 0 each: `intake` 0 → `discover` 1 → `design` 2 → `implement` 3 |
| 2 | Entry / client tests / build | see Baseline |
| 3 | Entry `.\mvnw.cmd -B clean test` (JDK 21) | 0, **314 / 0 / 0 / 0** |
| 3 | client `npx ng test --watch=false --browsers=ChromeHeadless` | 0, **405 SUCCESS** |
| 3 | client `rm -rf dist; npm run build` | 0, 0 ERROR, 12 warnings, same set as baseline |
| 3 | `git status --short`, `git diff --stat` | 14 tracked files (248+, 31−), 11 new files, all allowed |
| 4 | `workflow.mjs step … ready 04-proof.json` | run after this file; result in the handoff message |

Session brief (2026-10-29T15:27Z): branch `fix/browser-fix-001` @ `150c561`; open: BROWSER-FIX-001 `verify` rev 4 (no checkpoint), LEZ-001 `implement` rev 10 (scoped files changed; historical evidence changed), MAP-ORDERS-001 `verify` rev 4 (no checkpoint; historical evidence changed); security not FIXED: SEC-001 (Critical), SEC-010 (Medium), SEC-004/005/007/008 (Low); 14 of 14 lessons active.

## Verification Results

- Entry: 314 = 298 + 16 new; 0 failures/errors/skipped. VERIFIED.
- Client: 405 = 385 + 20 new, all SUCCESS. VERIFIED.
- Build: EXIT=0, no ERROR, no new warning. The existing `edit-order-dialog.component.scss` budget warning grows from 861 to 946 bytes over the 4.10 kB warning budget (the `.coordinates-hint` rule); far below the error budget. VERIFIED.
- Only allowed files changed. VERIFIED.
- Live check (real geocoding, Add Order without coordinates, Import of the template): not run. NOT_VERIFIED.

## Modified Tests

None.

## Security-Relevant Changes

- **S3 logging:** geocoding logs carry no address, no coordinates, no URL and no key; the failure line logs the exception class name only (the `RestClientException` message contains the request URL with `api_key`). Asserted by UO-T8 with a logback `ListAppender`.
- **Import errors without raw data:** the client banner shows "Line N (code): field — message" only; `raw_data` is never rendered (UO-C4 asserts a customer name and phone in `raw_data` do not appear). Note: the server's own geocoding-failure message for an import row still embeds the address ("The coordinates cannot be obtained from the address: …"); that is the planner's own input shown back to them, and the method was outside the allowed one-line change.
- **Tenant isolation:** update geocodes only after the branch-scoped lookup; another branch's id answers not found before any outbound geocoding call (UO-T4 second test). Create uses the principal's branch as before. No new endpoint, no `permitAll`.
- Observation, not changed (outside the allowed edit): `OrderImportService.processParsedData` still DEBUG-logs the parsed orders and the request as JSON (customer names, phones, addresses). Proposed follow-up: remove those debug lines.

## Deviations From Design

1. **S1 on update — order of operations:** `resolveCoordinates` runs after `findByIdAndBranchId` (design: "before mapping"; still before mapping). This prevents a geocoding call for another branch's order id. `validateBusinessLogic` keeps running first, as before.
2. **S1 on create:** `resolveCoordinates` runs before `validateBusinessLogic`, so a missing address without coordinates answers the S1 field error (`address`) instead of the older generic message.
3. **C1 helpers:** besides `toOrderPayload`, `order-payload.utils.ts` exports `OrderFormModel`, `isBlankCoordinate` and `coordinateValue` (used by `validate()`), and the dialog's form type changed from `OrderInputDTO & {…}` to `OrderFormModel` so `null` coordinates type-check.
4. **C3:** the template text is an exported constant `ORDER_IMPORT_TEMPLATE_CSV` so UO-C5 can check it without a browser download.

## Remaining Uncertainty

- Live geocoding with the user's Goong key: NOT_VERIFIED (all tests stub the geocoder).
- Import of a large file still geocodes row by row (design risk, out of scope).
- `JsonFileParser` field names were not inspected; a JSON import file format is not documented in the dialog.
- The map picker opens at its default centre when the dialog has no coordinates (it treats `null` like `0`, as before for `0`).
- Browser behaviour of the new dialog hint, the import banner and the toasts: NOT_VERIFIED.

## Lesson Candidates

- Counting warnings from a PowerShell-captured `npm run build` log over-counts by one (stderr is wrapped in a `NativeCommandError` record); compare with a sorted text diff, or capture from Git Bash.

## Handoff to Reviewer

- Read first: `OrderService.resolveCoordinates` and its two call sites (Deviations 1–2), `GeocodingService` log lines, `orders-section.component.ts#onImportClick`.
- Re-run: `cd GVRP_Entry_API; .\mvnw.cmd -B clean test` with JDK 21 (314/0/0/0); `cd GVRP_Entry_client_v2 && npx ng test --watch=false --browsers=ChromeHeadless` (405) and `npm run build` (12 warnings).
- Suggested live check: Add Order with a real Hà Nội address and empty coordinates; Add Order with an empty phone (F1); download the template and import it on today's date; import a row with only latitude. Record every row created.
- `traceability.md` is not in this task's allowed files.
