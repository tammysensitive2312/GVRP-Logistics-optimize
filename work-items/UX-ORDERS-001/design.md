# Design — UX-ORDERS-001

Inputs: `request.md` (UO-R1…R6), `context.md`. Technical design by Claude, 2026-10-29. Entry API + client v2; no schema change, no new dependency. Stacked on BROWSER-FIX-001.

## 1. Entry API

**S1 — Geocode on create/update (UO-R1).** `OrderService`, before mapping in `createOrder` and `updateOrdersById`, a private `resolveCoordinates(OrderInputDTO input)`:
- both `latitude` and `longitude` present → unchanged;
- exactly one present → `DataInvalidException` (`VALIDATION_ERROR`, field `latitude`, "Latitude and longitude must be given together.");
- both null → address blank → `DataInvalidException` (`VALIDATION_ERROR`, field `address`, "Address is required when no coordinates are given."); else `geocodingService.geocode(address)`; null → `DataInvalidException` (`VALIDATION_ERROR`, field `address`, "Could not find coordinates for this address. Pick the location on the map or enter latitude and longitude."); else set both on the DTO.
- Update: the same rule on the incoming DTO (a client that clears both coordinates gets the address geocoded again).
- No log line with address or coordinates.

**S2 — Import fixes (UO-R4).**
- `OrderImportService.processParsedData`: before geocoding/validation of each row, `if (dto.getDeliveryDate() == null) dto.setDeliveryDate(request.getDeliveryDate())`. Nothing else in the method changes.
- `OrderImportRequest.deliveryDate`: add `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` (harmless if binding already worked).
- `CsvFileParser.parseRecord`: optional `latitude`, `longitude` columns (same lookup as other columns): both blank → null; one blank → `ParseException` ("latitude and longitude must be given together"); not a number → `ParseException` naming the column. Range checks stay with Bean Validation.

**S3 — Geocoding logs (UO-R6).** `GeocodingService`: keep behaviour; log lines become `debug("Geocoding request sent")`, `debug("Geocoding succeeded")`, `warn("Geocoding found no result")`, `error("Geocoding failed: {}", e.getClass().getSimpleName())` — no address, no coordinates, no URL (the URL carries the API key).

## 2. Client

**C1 — Order dialog (UO-R1, UO-R2).** `edit-order-dialog.component.{ts,html}`:
- form `latitude: null, longitude: null` for a new order; inputs without the required marker; hint under them: "Leave empty to locate the address automatically, or pick the location on the map."
- `validate()`: address required; coordinates both empty or both filled ("Enter both latitude and longitude, or leave both empty."); latitude in [-90, 90], longitude in [-180, 180] when filled; the old "Coordinates are required" check removed.
- Payload built by a pure exported function `toOrderPayload(form)` in a new `features/main/orders/components/edit-orders-dialog/order-payload.utils.ts`: trims strings; `''`/whitespace → `null` for `customer_phone`, `delivery_notes`, `time_window_start`, `time_window_end`; `null`/`undefined`/`''` coordinates → `null`; numeric strings → numbers. Create still strips `status`.
- `core/models/order.model.ts`: new `OrderWritePayload` (keys of `OrderInputDTO` with `latitude`, `longitude`, `customer_phone`, `delivery_notes`, `time_window_start`, `time_window_end` allowing `null`, plus optional `delivery_date`, `status`). `ApiService.addOrder` / `updateOrder` accept `OrderInputDTO | OrderWritePayload` (existing callers unchanged). `OrderInputDTO`/`OrderDTO` unchanged.

**C2 — Import call (UO-R3).**
- `core/models/order.model.ts`: `ImportErrorDTO {line_number, order_code, field, error_message, raw_data}`, `ImportResultDTO {success, total_records, imported_count, skipped_count, errors, requires_confirmation}`.
- `ApiService.importOrders(formData: FormData): Observable<ImportResultDTO>` → `POST {apiUrl}/orders/import` (no explicit Content-Type).
- `OrdersSectionComponent.onImportClick`: FormData `file` or `textData`, `deliveryDate`, `serviceTime`, `overwriteExisting`, **`skipValidationErrors`**; call `importOrders`; on success store `importResult` (signal) and: `success` → toast "Imported X, skipped Y" (+ ", Z errors" when any) and `loadOrders()`; `!success` → toast error "Import refused: Z rows have errors. Nothing was imported." On HTTP error → toast `serverErrorMessage(err, 'Import failed')`.
- `orders-section.component.html`: when `importResult()` has errors, a banner reusing the `bulk-report` classes: "Import: X imported · Y skipped · Z errors", "View details" lists up to 100 errors as "Line N (code): field — message" (no `raw_data`, which may hold customer data), "Dismiss".

**C3 — Import dialog (UO-R5).** `import-orders-dialog.component.{ts,html}`:
- accept `.csv,.json` (message "Only CSV and JSON files are allowed"); hint text updated accordingly.
- `deliveryDate = localDateIso()`.
- `downloadTemplate()`: header `orderCode,customerName,customerPhone,address,latitude,longitude,demand,serviceTime,timeWindowStart,timeWindowEnd,priority,deliveryNotes,requiredSkills` and two example rows: one with coordinates, one without (address only, geocoded), Vietnamese-address example quoted, `requiredSkills` with `;`.
- Text hint: "Format (10 fields): OrderCode|Name|Phone|Address|Demand|TWStart|TWEnd|Priority|Notes|ServiceTime" and placeholder `ORD001|Nguyen Van A|0901234567|1 Trang Tien, Ha Noi|25.5|08:00|11:00|1|Call first|10`; one hint line "Coordinates are found from the address automatically."

## 3. Tests (new files only)

| ID | Case | Expected |
|---|---|---|
| UO-T1 | `OrderService.createOrder` (Mockito): no coordinates, geocoder returns a point | coordinates set before mapping; saved |
| UO-T2 | same, geocoder returns null; address blank; only latitude | `DataInvalidException` with fields `address`, `address`, `latitude`; nothing saved; geocoder not called for blank/one-sided |
| UO-T3 | `createOrder` with both coordinates | geocoder never called |
| UO-T4 | `updateOrdersById` with both coordinates null | geocoded |
| UO-T5 | `OrderImportService` text import of 2 valid rows with addresses, geocoder stubbed | 2 imported (delivery date applied before validation) |
| UO-T6 | `CsvFileParser`: row with lat/lon; row without; row with only lat; lat `abc` | parsed; null coords; error row; error row naming `latitude` |
| UO-T7 | `OrderImportRequest` binding through `standaloneSetup(OrderController)` multipart with `deliveryDate=2026-10-29` | service receives `LocalDate.of(2026,10,29)` |
| UO-T8 | `GeocodingService` log lines (ListAppender or equivalent) on success and on failure | no address, no coordinate, no key in any message |
| UO-C1 | `toOrderPayload`: blanks → null; coords empty → null; numbers parsed | as stated |
| UO-C2 | Order dialog: no coords + address → `addOrder` called with null coords; one coord → error, no call; lat 95 → error | as stated |
| UO-C3 | `ApiService.importOrders` (`HttpTestingController`) | POST `…/orders/import` with the FormData body |
| UO-C4 | `onImportClick` with a dialog stub returning text input | FormData has `textData`, `deliveryDate`, `serviceTime`, `overwriteExisting`, `skipValidationErrors`; success → toast + `loadOrders`; `success=false` → error toast, no reload; errors banner lists "Line 2 (ORD-2): demand — …" and no raw data |
| UO-C5 | Import dialog: `.xlsx` rejected, `.json` accepted; default date = `localDateIso()`; template text has the 13 header columns | as stated |

## 4. Risks

- Live geocoding depends on the Goong key in the user's properties (not in scope). Unit tests stub the geocoder.
- Importing 25k rows geocodes row by row; out of scope (import of coordinates via CSV avoids it).
