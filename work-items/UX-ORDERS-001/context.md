# Context — UX-ORDERS-001

Inspected 2026-10-29 22:05–22:25 by Claude on the `fix/browser-fix-001` working tree. Entry paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`, client paths to `GVRP_Entry_client_v2/src/app/`.

## Entry API

- `service/GeocodingService.geocode(address)` → `GeocodeResult(lat, lng)` or `null` (disabled, blank, not found, any exception). Uses Goong (`geocoding.goong.*`, `geocoding.enabled` default true). Logs `log.debug("Calling Goong API for address: {}", …)`, `log.info("Goong success: '{}' -> ({}, {})", address, lat, lng)`, `log.warn("Goong could not find address: {}", …)` — address and coordinates in the log.
- `service/orders/OrderService`: `createOrder(input, branchId)` → `validateBusinessLogic(input)` → `orderMapper.toEntity(input, branch, input.getDeliveryDate())` → save. `updateOrdersById` same pattern. `geocodingService` is injected and unused. `Order.location` is `nullable = false`; `OrderMapper` builds it with `geometryMapper.createPoint(lat, lng)`.
- `dto/request/OrderInputDTO`: `latitude`/`longitude` are nullable `Double` with range checks; `address` has `@Size(max=255)` only.
- `service/orders/OrderImportService.processParsedData`: sets `serviceTime` default, geocodes when lat/lng null and address present (error row if null), then `validator.validate(dto, OnCreate.class)` — **`deliveryDate` is still null** here (`@NotNull`), so every row fails "Delivery date is required"; the date is applied later in `orderMapper.toEntity(dto, branch, request.getDeliveryDate())`. `validateBusinessLogic` requires coordinates or address and both or neither time-window bounds.
- `dto/request/OrderImportRequest` (`@ModelAttribute`): `file`, `textData`, `deliveryDate` (`LocalDate`, no `@DateTimeFormat`), `serviceTime`, `skipValidationErrors`, `overwriteExisting`. `controller/OrderController.importOrders`: `POST /api/v1/orders/import`, multipart.
- `service/integration/file/CsvFileParser`: header row, case-insensitive, camel/snake variants; columns `orderCode*, customerName*, customerPhone, address*, demand*, serviceTime, timeWindowStart, timeWindowEnd (HH:mm), priority, deliveryNotes, requiredSkills (;-separated)`; **no latitude/longitude**. `JsonFileParser` exists (not inspected). `TextDataParser`: `|`-separated, ≥10 fields: `code|name|phone|address|demand|tws|twe|priority|notes|serviceTime`.
- `dto/response/ImportResultDTO`: `success, totalRecords, importedCount, skippedCount, errors[lineNumber, orderCode, field, errorMessage, rawData], requiresConfirmation` (snake_case on the wire).

## Client

- `features/main/orders/components/edit-orders-dialog/edit-order-dialog.component.{ts,html}`: form defaults `latitude: 0, longitude: 0, customer_phone: '', time_window_*: '', delivery_notes: ''`; `validate()` requires `latitude && longitude` ("Coordinates are required"); `canSubmit()` requires address; "Pick Location on Map" opens `MapPickerDialogComponent`. After BROWSER-FIX-001: `delivery_date: localDateIso()`, errors via `serverErrorMessage`.
- `core/models/order.model.ts`: `OrderInputDTO.latitude/longitude: number` (required); `OrderDTO extends OrderInputDTO` (used by the map, table, etc.).
- `core/services/api.service.ts`: `addOrder(OrderInputDTO)`, `updateOrder(id, OrderInputDTO)`; **no import method**.
- `features/main/orders/components/import-orders-dialog/import-orders-dialog.component.{ts,html}`: returns `ImportDialogResult {method, deliveryDate, serviceTime, overwrite, skipValidationErrors, file?, textData?}`; accepts `.csv,.xlsx`; `deliveryDate` = UTC `toISOString()` date; `downloadTemplate()` CSV with `latitude,longitude` columns; text hint "OrderCode|Name|Phone|Address|Demand|TWStart|TWEnd|Priority|Notes" (9 fields).
- `features/main/orders/orders-section-view/orders-section.component.ts#onImportClick`: builds `FormData` (`file`|`textData`, `deliveryDate`, `serviceTime`, `overwriteExisting`; **no** `skipValidationErrors`) then `// TODO … console.log`. Existing `bulk-report` banner pattern (signal + grouped details) can show a result.

## Not verified

- `JsonFileParser` field names; whether Spring binds `deliveryDate=2026-10-29` to `LocalDate` without `@DateTimeFormat` (engineer verifies with a test, adds the annotation if not).
- Whether the configured Goong key is valid on the user's machine (live check).
