# Context — ORDERS-FIX-001

Inspected 2026-10-30 23:30–23:50 +07:00 by Claude (cloud session), reading `master` @ `081640a` through the device bridge. Nothing was run. Entry paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`, client paths to `GVRP_Entry_client_v2/src/app/`.

## Import (`service/orders/OrderImportService`)

- `createOrdersByImportRequest` is `@Transactional`; it parses (CSV / JSON / text) into `ParseResult<OrderInputDTO>` (`validItems`, `errors`) and calls `processParsedData(rawOrders, parseErrors, request, branch)`.
- `processParsedData`:
  - starts with three `log.debug` lines that serialize **all parsed rows**, the parse errors and the request to pretty JSON (customer names, phones, addresses, coordinates) — F5;
  - per row `i`: default `serviceTime`, default `deliveryDate`; if a coordinate is missing and the address is present → `geocodingService.geocode(address)`; on `null` it adds `buildError(i + 1, code, "address", "The coordinates cannot be obtained from the address: " + address, address)` and **does not `continue`** — F1; then Bean Validation (`OnCreate`) → error + `continue`; then `validateBusinessLogic` (local copy, returns a message; it does reject a single time-window bound) → error, else `validOrdersToSave.add(dto)`.
  - So an ungeocodable row with an address passes both checks and is also queued for saving with `latitude/longitude = null`.
  - errors present and `skipValidationErrors != TRUE` → `success:false`, nothing saved. Otherwise `saveOrdersToDatabase`.
- `saveOrdersToDatabase`: per row, `existsByBranchIdAndOrderCode`; overwrite → `orderMapper.updateEntityFromDTO(dto, existing)`; new → `orderMapper.toEntity(dto, branch, deliveryDate)`; `save`. `catch (Exception e)` → `log.error("Failed to save order {}: {}", code, e.getMessage())` and `buildError(null, code, "Database", "Lỗi lưu DB: " + e.getMessage(), null)` — F5 (the message can carry SQL values). Line number `null`.
- `Order.location` is `nullable = false`; `OrderMapper` builds it with `geometryMapper.createPoint(lat, lng)` → `null` when a coordinate is null. A saved F1 row therefore fails at insert/flush inside the transaction (expected `UnexpectedRollbackException` → 500, everything rolled back — PLAUSIBLE, not run).

## Line numbers (F4)

- `CsvFileParser.parse`: counter starts at 1 (header) and is incremented per record → first data row = 2; parse errors carry it. Valid rows lose it.
- `TextDataParser.parse`: `lineNumber = i + 1` over raw lines (blank lines counted, skipped); parse errors carry it. Valid rows lose it.
- `JsonFileParser.parse`: `currentLine = parser.getCurrentLocation().getLineNr()` before each object; errors carry it. Valid rows lose it.
- `ParseResult<T>`: `@RequiredArgsConstructor` with `validItems`, `errors` only.
- `processParsedData` numbers its own errors `i + 1` over the valid list → for CSV, off by one, and shifted after every parse-error row.

## Order update (F2, F3, F6)

- `service/orders/OrderService.updateOrdersById`: `validateBusinessLogic` → `findByIdAndBranchId` (tenant scope) → `resolveCoordinates` (both given → kept; one → 400; none → geocode address) → `orderMapper.updateEntityFromDTO` → save.
- `mapper/BaseMapperConfig`: `nullValuePropertyMappingStrategy = IGNORE`. `OrderMapper.updateEntityFromDTO` maps every DTO property except `id`, `branch`, `createdAt`, `updatedAt`, `routeSegments`; `location` by expression. A `null` from the client therefore keeps the stored value. `status` is also mapped from the DTO (null on import → kept), so a mapper-wide `SET_TO_NULL` would null `status` (`nullable = false`).
- `Order` columns `customer_phone`, `time_window_start`, `time_window_end`, `delivery_notes` are nullable.
- `OrderService.validateBusinessLogic`: the "both bounds or neither" branch builds `List.of(ErrorDetail…)` and discards it — no throw (F6). The import's own copy does reject it.
- Client `features/main/orders/components/edit-orders-dialog/`:
  - `order-payload.utils.ts#toOrderPayload` sends blank phone / notes / time windows as `null`, empty coordinates as `null` (UX-ORDERS-001 C1).
  - `edit-order-dialog.component.ts`: `fillForm(order)` loads the stored address and coordinates and stores `originalFormSnapshot` (JSON string); `validate()` checks start < end only when both times are set; `onPickLocation()` sets `form.latitude/longitude` from the map picker. On edit, an address change with untouched coordinates sends the old coordinates → server keeps them (F3).

## Tests that exist (by name)

`OrderServiceTest`, `OrderServiceGeocodingTest`, `OrderImportDeliveryDateTest` (Mockito, real `TextDataParser` and validator — a good pattern), `CsvFileParserCoordinatesTest`, `controller/OrderImportBindingTest`; client `order-payload.utils.spec.ts` and specs of the dialogs. Their assertions on line numbers, error texts and half time windows are **not** inspected — the baseline assertion check does it.

## Not verified

- The live 500 of F1 with "skip errors" on (UO-M11).
- Whether `OrderMapperImpl` (generated) can be instantiated in a plain unit test with `GeometryMapper` injected by reflection.
