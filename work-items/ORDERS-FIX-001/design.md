# Design — ORDERS-FIX-001

Inputs: `request.md` (OF-R1…R6), `context.md`. Technical design by Claude, 2026-10-30. Entry API + client v2; no schema change, no new dependency, no API shape change.

## 1. Entry API

**S1 — Ungeocodable import row is not saved (OF-R1).** `OrderImportService.processParsedData`: after adding the geocoding error, `continue;`. The error text no longer echoes the address (OF-R5): `"Could not find coordinates for this address."`, `rawData` `null`.

**S2 — Clearable optional fields (OF-R2).** `OrderMapper.updateEntityFromDTO`: per-property strategy on exactly four targets, mapper-wide `IGNORE` unchanged:

```java
@Mapping(target = "customerPhone",   nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
@Mapping(target = "deliveryNotes",   nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
@Mapping(target = "timeWindowStart", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
@Mapping(target = "timeWindowEnd",   nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
```

(keep the existing `@Mapping`s; add `source` if MapStruct requires it). Do **not** switch the whole mapper or method to `SET_TO_NULL`: `status`, `priority`, `serviceTime`, `requiredSkills`, `deliveryDate` must keep `IGNORE` (an import row has `status = null`; `orders.status` is `NOT NULL`). This also applies to the import "overwrite existing" path, which uses the same method: an overwriting row without phone/notes/time window clears them — intended (overwrite = replace).

**S3 — Source line numbers (OF-R4).**
- `ParseResult<T>`: add `List<Integer> sourceLines` (one per valid item, same order). Keep the existing 2-argument constructor (sets `sourceLines` to `null`) so other users compile unchanged; add a 3-argument constructor.
- `CsvFileParser`: record `lineNumber` for each valid record (header = 1). `TextDataParser`: `lineNumber` (`i + 1`). `JsonFileParser`: `currentLine`. Each returns the 3-argument `ParseResult`.
- `OrderImportService`: collect the lines next to `allRawOrders`; in `processParsedData`, the line of row `i` is `sourceLines.get(i)` when present, else `i + 1`. Keep the line next to each queued row so a database error in `saveOrdersToDatabase` also carries it (today `null`).

**S4 — No personal data in import logs or DB error text (OF-R5).**
- Remove the three JSON-serializing `log.debug` lines of `processParsedData` (and the `try/catch` around them); replace with one `log.debug("Import rows: {} parsed, {} parse errors", rawOrders.size(), parseErrors.size())`.
- `saveOrdersToDatabase` catch: `log.error("Failed to save imported order {}: {}", dto.getOrderCode(), e.getClass().getSimpleName())`; error entry message `"Could not save this order."` (no `e.getMessage()`), field `"Database"`, line from S3.
- Do not add any other log line with name, phone, address or coordinates. The order code is a business key and may stay.

**S5 — Single time-window bound refused (OF-R6).** `OrderService.validateBusinessLogic`: wrap the existing `List.of(...)` of the "both or neither" branch in `throw new DataInvalidException(...)` (same code and message as the import's rule). No other change to the method.

## 2. Client

**C1 — Address change relocates (OF-R3).** `edit-order-dialog.component.ts` + new pure function in `order-payload.utils.ts`:

```ts
/** On edit: address changed but coordinates are still the loaded ones → send them empty so the server geocodes. */
export function clearStaleCoordinates(form: OrderFormModel, original: { address: string; latitude: number | null; longitude: number | null }): OrderFormModel
```

- Returns `form` unchanged when the trimmed address equals the trimmed original, or when either coordinate differs from the original (picked on the map or typed in this edit); otherwise a copy with `latitude: null, longitude: null`.
- The dialog keeps `original` from `fillForm` and applies the function in `onSubmit` (edit mode only) before `toOrderPayload`.
- HTML: under the coordinates, when `isEditMode` and the function would clear them, one hint line: "Address changed — the location will be found again from the new address. Pick it on the map to set it yourself."
- If the server cannot geocode, its existing field message is shown (UX-ORDERS-001 S1); nothing else changes.

**C2 — Time-window bounds (OF-R6).** `validate()`: exactly one of `time_window_start` / `time_window_end` filled → error "Enter both start and end time, or leave both empty."

## 3. Tests (new files only)

| ID | Case | Expected |
|---|---|---|
| OF-T1 | Import (pattern of `OrderImportDeliveryDateTest`): text, 2 rows; geocoder returns a point for row 1, `null` for row 2; `skipValidationErrors = true` | `success = true`, `importedCount = 1`; exactly one error for row 2 (line 2, field `address`); mapper `toEntity` called once; message does not contain the address |
| OF-T2 | same file, `skipValidationErrors = false` | `success = false`, `importedCount = 0`, one error, `save` never called |
| OF-T3 | `OrderMapper.updateEntityFromDTO` on the generated `OrderMapperImpl` (with `GeometryMapper` set by reflection): entity with phone, notes, both time bounds, status `SCHEDULED`, priority 2; DTO with those four `null`, `status` `null`, `priority` `null`, coordinates set | the four fields become `null`; `status` stays `SCHEDULED`, `priority` stays 2 |
| OF-T4 | CSV via real `CsvFileParser` + `MockMultipartFile`: header, row A (bad `demand`), row B (ungeocodable), row C (valid); `skip = true` | errors at lines 2 and 3; C imported |
| OF-T5 | text with a blank first line, then an ungeocodable row | error at line 2 |
| OF-T6 | JSON array of two objects on separate lines, second ungeocodable | error carries the JSON line of the second object (not `2` by index) |
| OF-T7 | logging: `ListAppender` on `OrderImportService` at `DEBUG` during OF-T1 and during a `save` that throws `RuntimeException("Duplicate entry 'Nguyen Van A' …")` | no log message and no `errors[]` message contains the customer name, phone, address, a coordinate or the exception text; the DB error carries the row's line |
| OF-T8 | `OrderService.createOrder` and `updateOrdersById` (Mockito), both coordinates given, only `timeWindowStart` set | `DataInvalidException` with the "both … or neither" message; nothing saved |
| OF-C1 | `clearStaleCoordinates`: same address; changed address + same coords; changed address + new coords; whitespace-only change | unchanged; cleared; unchanged; unchanged |
| OF-C2 | Edit dialog (spec with ApiService stub): load an order, change only the address, save | `updateOrder` called with `latitude: null, longitude: null`; hint visible before save |
| OF-C3 | Edit dialog: only start time → error, `updateOrder`/`addOrder` not called | as stated |

## 4. Risks and remaining behaviour

- A database failure of one import row still marks the whole transaction rollback-only (expected 500, nothing saved). S1 removes the known cause; making rows independent is excluded (request). Record this as remaining uncertainty.
- S2 changes PUT semantics for four fields only; a client that omits them now clears them. The only client (edit dialog) always sends the whole form. DOC-SYSTEM-001 F3 wording stays for the documentation pass.
- S5 can refuse edits of existing orders that already hold a single bound; the dialog's C2 message tells the planner what to fix.
