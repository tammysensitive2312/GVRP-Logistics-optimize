# Review — UX-ORDERS-001

Reviewer: Claude (cloud session), 2026-10-30. Read against `master` @ `081640a` (the branch `feat/ux-orders-001` @ `64073c0` is already merged).
Scope read: `OrderService`, `OrderImportService`, `CsvFileParser`, `GeocodingService`, `OrderInputDTO`, `OrderMapper`, `BaseMapperConfig`, `GeometryMapper`, `Order` entity; client `order-payload.utils.ts`, `edit-order-dialog.component.ts`, `import-orders-dialog.component.ts`, `orders-section.component.{ts,html}`; `implementation.md`.
Not re-run: Maven and Karma (the engineer's figures 314/0/0/0 and 405 SUCCESS are taken as recorded).

## Verdict

**Accepted with follow-ups.** S1–S3 and C1–C3 are implemented as designed; the two deviations (geocode after the branch-scoped lookup on update; `resolveCoordinates` before `validateBusinessLogic` on create) are improvements. Test integrity holds (no existing test changed). The S3 logging change is correct and asserted. F1 must be fixed before the import is called "working end to end".

## Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| F1 | MEDIUM | VERIFIED by reading; consequence PLAUSIBLE (not run) | `OrderImportService.processParsedData`: when geocoding returns null, the error is added but the loop does **not** `continue`. The row has an address, so bean validation and `validateBusinessLogic` pass and the row is also added to `validOrdersToSave`. With `skipValidationErrors=false` nothing changes (the method returns `success:false`). With `skipValidationErrors=true` the row is saved with `location = null` (`createPoint(null, null)`), but `orders.location` is `NOT NULL`: the insert fails inside the outer `@Transactional`, Spring marks the transaction rollback-only, and the commit is expected to end in `UnexpectedRollbackException` → `500`, with **every** row of the file rolled back. Before S2 this path was unreachable (every row failed on `deliveryDate`). Fix: `continue;` after the geocoding error, plus a test "skip=true, one unresolvable address → other rows imported, that row in errors once". |
| F2 | LOW | VERIFIED by reading | Edit cannot clear an optional field. C1 sends blank phone / notes / time windows as `null`; `OrderMapper.updateEntityFromDTO` uses `NullValuePropertyMappingStrategy.IGNORE` (`BaseMapperConfig`), so the stored value is kept while the dialog reports "Order updated successfully". For `delivery_notes` this is a regression: the old `""` cleared it. Fix (server): `@BeanMapping(nullValuePropertyMappingStrategy = SET_TO_NULL)` on `updateEntityFromDTO` for the optional scalar fields (PUT semantics), with a test. |
| F3 | LOW | VERIFIED by reading | Edit with a changed address keeps the old coordinates (both present → no geocoding). The order silently keeps its old map position. Suggest: in the dialog, when the address changes and the coordinates were not picked/typed in this session, clear them (server then geocodes), or show "Address changed — clear coordinates to relocate". |
| F4 | LOW | VERIFIED by reading (pre-existing) | Import line numbers are mixed: CSV parse errors carry the file line (header = 1), geocoding/validation errors carry `i + 1` of the parsed list. After a parse-error row, later numbers shift; for CSV they are off by one. The banner "Line N" can point at the wrong row. |
| F5 | INFO | carried from `implementation.md` | `processParsedData` still DEBUG-logs all parsed rows and the request as JSON (names, phones, addresses) — AGENTS.md §15. The DB-failure text `"Lỗi lưu DB: " + e.getMessage()` can carry SQL values into `errors[]`. Proposed one follow-up item for both. |
| F6 | INFO | pre-existing | `OrderService.validateBusinessLogic` builds the "both start and end, or neither" detail but never throws it (also recorded in `GVRP_Document/03-basic-design/D03-orders/README.md` §3). |
| F7 | INFO | observation outside scope | `GeometryMapper.validateCoordinates` ends with an unconditional `throw new DataInvalidException(errorsInfo)`; `DepotMapper` uses `createPoint(DepotInputDTO)` for `POST /depots`. By reading, every depot create with coordinates answers `400` with an empty detail list. Not verified live; recommend a separate fix item (see the DOC-SYSTEM-001 review, F2). |

## Security

- S3 logs: no address, coordinates, URL or key — VERIFIED by reading `GeocodingService` and the UO-T8 names.
- Client banner never renders `raw_data` — VERIFIED (`importErrorLine`).
- Tenant isolation on update: lookup by `findByIdAndBranchId` before geocoding — VERIFIED.

## Manual test scenarios (browser + API)

| # | Steps | Expected |
|---|---|---|
| UO-M1 | Add Order, real Hà Nội address, leave lat/lon empty | Saved; marker at the address; no address or coordinates in the Entry log at INFO |
| UO-M2 | Add Order, address "zzzz qqqq", lat/lon empty | Toast "Could not find coordinates for this address…"; nothing saved |
| UO-M3 | Add Order, only latitude filled | Client error "Enter both latitude and longitude, or leave both empty." |
| UO-M4 | Add Order, phone empty | Saved (BROWSER-FIX-001 F1 closed) |
| UO-M5 | Edit an order, clear Notes, Save | **Expected to fail today (F2):** notes still shown after reload |
| UO-M6 | Edit an order, change the address only | **F3:** marker stays at the old place |
| UO-M7 | Import → Download template → import it unchanged for today | 2 imported; ORD002 geocoded |
| UO-M8 | Import the template again, overwrite off | 0 imported, 2 skipped |
| UO-M9 | CSV with one row having only `latitude` | Banner: "Line 2 (…): latitude — latitude and longitude must be given together"; nothing imported |
| UO-M10 | CSV: one good row + one row with an unresolvable address; skip errors **off** | "Import refused: 1 rows have errors"; nothing imported |
| UO-M11 | Same file, skip errors **on** | **F1:** expected today: Import failed (500) and nothing imported; after the fix: 1 imported, 1 error |
| UO-M12 | Paste text mode, two 10-field lines with addresses | 2 imported |
| UO-M13 | Choose an `.xlsx` file | "Only CSV and JSON files are allowed" |

Record every order code created; they are test rows in the shared database.
