# D03 — Orders

The demand side of the problem: what has to be delivered, where, when, and what state it is in. This is
also the only domain with a bulk import and a bulk status change.

Screens: `/main` (orders table, filters, toolbars, edit/search dialogs) — there is no dedicated orders route.
Diagrams: [state-order-status.svg](diagrams/state-order-status.svg)
([source](diagrams/state-order-status.puml)), [sequence-order-import.svg](diagrams/sequence-order-import.svg)
([source](diagrams/sequence-order-import.puml)).

Use cases: UC-D03-01 … UC-D03-06. Rules: BR-020 … BR-034.

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/main` | `MainComponent` → `OrdersSectionComponent` | the orders table, filters, selection and toolbars |
| — | `OrdersFiltersComponent` | `date`, `status`, free-text `search` |
| — | `OrdersTableComponent` | rows, selection, paging |
| — | `EditOrderDialogComponent` | create and edit, including `MapPickerDialogComponent` for coordinates |
| — | `ImportOrdersDialogComponent` | file or pasted text, delivery date, overwrite/skip switches |
| — | `BulkStatusDialogComponent` | `PATCH /orders/status` with a target status |
| — | `RoutePlanningDialogComponent` | hands the selected orders to D04 |

The table and the dialogs live inside the main layout rather than on their own route, so an order cannot be
linked to directly. The only deep links in the client are `/jobs/:id` (D04) and `/?solution=<id>` (D09).

---

## 2. API

Six endpoints, `ROLE_PLANNER`, all branch-scoped.

| Method | Path | Request | Success | Notes |
|---|---|---|---|---|
| POST | `/api/v1/orders` | `OrderInputDTO` (group `OnCreate`) | `200 OrderDTO` | geocodes when coordinates are absent |
| GET | `/api/v1/orders` | `page`=0, `size`=10, `date`, `status`, `search` | `200 PageResponse<OrderDTO>` | sorted by `created_at` **descending** (the JPQL `ORDER BY`), with `id` descending appended by the `Pageable` |
| GET | `/api/v1/orders/{orderId}` | — | `200 OrderDTO` | |
| PUT | `/api/v1/orders/{orderId}` | `OrderInputDTO` (group `OnUpdate`) | `200 OrderDTO` | full replacement of the editable fields |
| POST | `/api/v1/orders/import` | `multipart/form-data`, `@ModelAttribute OrderImportRequest` | `200 ImportResultDTO` | see §4 |
| PATCH | `/api/v1/orders/status` | `BulkUpdateOrderStatusRequest` | `200 BulkUpdateOrderStatusResponse` | lenient batch, see §5 |

### `OrderInputDTO`

| Field | Constraint |
|---|---|
| `id` | must be **null** on create (`@Null(groups = OnCreate)`) |
| `order_code` | `@NotBlank`, ≤ 50 |
| `customer_name` | `@NotBlank`, ≤ 100 |
| `customer_phone` | ≤ 20, `^[0-9+\-\s()]+$`, optional |
| `address` | ≤ 255; required when coordinates are absent |
| `latitude` / `longitude` | −90…90 / −180…180, both or neither |
| `demand` | `@NotNull`, ≥ 0.01 |
| `service_time` | ≥ 0, optional |
| `time_window_start` / `time_window_end` | `LocalTime`, optional |
| `priority` | ≥ 1, optional |
| `delivery_notes` | ≤ 1000 |
| `required_skills` | set of `VehicleSkill` |
| `delivery_date` | `@NotNull` |
| `status` | must be **null** on create (`@Null(groups = OnCreate)`); ignored on update |

Coordinates and geocoding, in the implemented order:

1. both coordinates present → used as given, no geocoding;
2. exactly one present → `400` + `0040001`, `field: latitude`;
3. neither present → `address` is required and is geocoded; a miss is `400` + `0040001` with
   `field: address` and the message *Could not find coordinates for this address. Pick the location on the
   map or enter latitude and longitude.*

On **update**, the branch-scoped lookup happens before `resolveCoordinates`, so a foreign id never triggers a
geocoding call (BR-024). On **create** the order of the two calls is reversed relative to the address check:
`resolveCoordinates` runs first, so a payload with neither coordinates nor address is answered with the
"address required" detail rather than the generic business-rule message.

### Listing

`search` matches `order_code`, `customer_name` and `address` case-insensitively; the query is a repository
method, not a `LIKE` built by the controller. Filters are applied in the query, so paging is consistent with
the filter set. An unparseable `date` (`yyyy-MM-dd`) or `status` token is `400` + `0040001` with
`field: date` / `field: status`. There is no maximum `size`, no sort parameter, and no total-count cap.

---

## 3. Errors

| Situation | Status | Body |
|---|---|---|
| invalid field, or `status`/`id` sent on create | 400 | `0040001`, one detail per field |
| one coordinate without the other | 400 | `0040001`, `field: latitude` |
| no coordinates and no address | 400 | `0040001`, `resource: orders` |
| geocoding miss | 400 | `0040001`, `field: address` |
| time window start after end | 400 | `0040001`, `resource: orders` |
| unknown or foreign order id | **400** | `0040402`, `resource: orders` |
| invalid `status` value in `PATCH` | 400 | `0040001` |
| duplicate `order_code` in the branch | **500** | `0050001` — the unique index rejects the insert and `DataIntegrityViolationException` is unhandled |
| illegal transition inside `PATCH` | `200` | the row appears in `failed[]`, not as an error response |

"Unknown id is `400`, not `404`" is not a mistake in this table: `OrderService` throws
`DataInvalidException` with the `0040402` code, and the handler maps that class to `400`
(`03-basic-design/common/README.md` §5). BR-024/BR-033 state `404`; the implemented status is `400`.

### Two defects worth naming

1. **A half-specified time window is accepted.** `OrderService.validateBusinessLogic` builds the detail
   *"Time Window must have both start and end times, or neither."* and then never throws it — the
   `List.of(...)` expression's value is discarded. A payload with only `time_window_start` passes validation
   and is stored. The import path computes the same rule as a string and does return it, so a row can be
   rejected on import and accepted through `POST /orders`. Status `VERIFIED` by reading both methods.
2. **A duplicate order code is a `500`.** Nothing pre-checks `(branch_id, order_code)` on create or update;
   the insert fails on the unique index. The create dialog only reports a server error.

---

## 4. Import

`POST /api/v1/orders/import`, `multipart/form-data`, bound with `@ModelAttribute` (not a JSON body).

| Form field | Meaning |
|---|---|
| `delivery_date` | required; applied to every row that has none |
| `service_time` | applied to every row that has none |
| `file` | a CSV **or JSON** file |
| `text_data` | pasted rows, mutually exclusive with `file` |
| `overwrite_existing` | when true, a matching `order_code` is updated instead of skipped |
| `skip_validation_errors` | when true, valid rows are saved and bad rows are reported |

Sending both inputs, or neither, is rejected with `400` + `0040002` and `field: file`. The request DTO also
builds a `choice` code, and `case 4` of the service's switch throws a simulated `RuntimeException`; that branch
is unreachable because `getChoice()` only returns 0…3. It is leftover debug code, not a feature.

**XLSX is not supported.** The parser selects on the file extension and accepts only `.csv` and `.json`;
anything else answers `400` + `0040002` with the message *Định dạng file không hỗ trợ (chỉ CSV/JSON).* — in
Vietnamese, unlike every other error in the API. BR-025 says CSV **or XLSX**, which is wrong: correct it when
next touching that file.

Row processing, once parsed:

1. fill `service_time` and `delivery_date` from the request when the row has neither;
2. geocode the address when coordinates are missing — one HTTP call per row, sequential, inside the request
   thread;
3. bean-validate with the `OnCreate` group; **only the first violation of a row** is reported;
4. apply the business rules (address-or-coordinates, time window);
5. if any error row exists and `skip_validation_errors` is not `true`, return `success:false`,
   `imported_count:0` and the error table — nothing is written, because the method returns before the save
   loop;
6. otherwise save: insert new codes, update existing codes when `overwrite_existing`, skip them otherwise;
   a row that fails at the database is appended to `errors[]` and the loop continues.

`ImportResultDTO` = `{success, imported_count, skipped_count, total_records, requires_confirmation, errors[]}`,
each error being `{line_number, order_code, field, error_message, raw_data}`. `requires_confirmation` is
always `false`: the two-phase "review then confirm" flow that the field implies is not implemented — the
client re-submits the same file with `skip_validation_errors=true`.

Because geocoding is per row and sequential, an import with no coordinates is bounded by the Goong API, not
by the database. There is no request-size or row-count limit beyond the servlet default.

---

## 5. Bulk status change

`PATCH /api/v1/orders/status` with `{ids: [...], status: "ON_ROUTE"}`.

| Outcome | Shape |
|---|---|
| transition legal | element of `updated[]` as `{id, status}` |
| transition illegal | element of `failed[]` as `{id, order_code, reason}` |
| unknown or foreign id | element of `failed[]`, reason `Order not found in this branch` |
| request itself invalid (empty list, unknown status) | `400` |

Every input id appears in exactly one list, the call is `200` whenever the request is well formed, and rows
are flipped with a single `UPDATE … WHERE id IN (…)` per partition. Foreign ids are not distinguished from
missing ones in the reason string, which is the intended non-disclosure.

---

## 6. Data touched

`orders` (all six endpoints), `branches` (read, for the create path), `route_stops.order_id` (read-only here;
the FK is `SET NULL` on delete, so deleting an order leaves its stop behind — `02-system-design/05-data-architecture.md`
§3.5). Status values are the six-token enum in `state-order-status.puml`.

The order row carries `location POINT` and `required_skills JSON`; neither is exposed as a column in the
table. `delivery_date` is `DATE`, not a timestamp, which is why one planning job covers exactly one date
(BR-062).

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/OrderController.java`,
`.../service/orders/{OrderService,OrderStatusTransitionService,OrderImportService}.java`,
`.../service/integration/file/{CsvFileParser,JsonFileParser,TextDataParser}.java`,
`.../service/GeocodingService.java`, `.../mapper/OrderMapper.java`,
`.../dto/request/{OrderInputDTO,OrderImportRequest,BulkUpdateOrderStatusRequest}.java`,
`.../dto/response/{OrderDTO,ImportResultDTO,ImportError,BulkUpdateOrderStatusResponse}.java`,
`.../entity/enums/OrderStatus.java`, `.../repository/OrderRepository.java`,
`.../exception/GlobalExceptionHandler.java`,
`GVRP_Entry_client_v2/src/app/features/main/**` (orders section, filters, table, dialogs),
`GVRP_Entry_client_v2/src/app/core/services/api.service.ts`,
`01-requirements/03-business-rules.md` (BR-020…BR-034).
