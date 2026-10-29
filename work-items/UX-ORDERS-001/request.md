# Request — UX-ORDERS-001 (Add Order without coordinates, working Import)

Source: Claude session of 2026-10-29 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 22:03 | "ở dialog add order tôi thấy không cần ép người dùng phải cung cấp lat lon, bởi vì đã nhập địa chỉ và lấy địa chỉ tương đối bằng geocoding service rồi mà đúng không" | Latitude/longitude must not be mandatory in Add Order; the address is geocoded. |
| 22:03 | "…như đối với import orders ấy. Với bạn có bản mẫu file import không." | Asks for an import template. |
| 22:18 | "đồng ý, bạn viết đi" | Accepts Claude's plan (22:10): task UX-ORDERS-001 items 1–5. |

Claude's findings (22:10, re-checked 22:20): Add Order never geocodes (`OrderService.createOrder` holds `GeocodingService` but does not call it); the client forces coordinates and defaults them to `0, 0`; the Import dialog never calls the API (`onImportClick` ends in `// TODO`); server-side import validates every row with `@NotNull deliveryDate` before the date is applied, so every row would fail; the CSV parser ignores `latitude`/`longitude`; the dialog accepts `.xlsx` (server: CSV/JSON only) and its text hint lists 9 fields (server: 10); BROWSER-FIX-001 review F1 (empty phone sent as `""` fails `@Pattern`).

## Requirements

| ID | Requirement |
|---|---|
| UO-R1 | Add/Edit Order accepts an order without latitude and longitude; the server geocodes the address. If the address cannot be geocoded the request is refused with a field message on `address`. Giving only one of latitude/longitude is refused. |
| UO-R2 | The order dialog no longer defaults coordinates to 0; empty optional fields (phone, notes, time windows, coordinates) are sent as `null`. |
| UO-R3 | Import Orders works end to end from the dialog (file CSV/JSON or pasted text): the request reaches the server, the result (imported, skipped, per-line errors) is shown, the table reloads. |
| UO-R4 | Import rows get the dialog's delivery date before validation; CSV rows may carry optional `latitude`/`longitude` (both or neither), otherwise the address is geocoded. |
| UO-R5 | The dialog offers a correct CSV template and a correct text-format example (10 fields) and accepts only the formats the server reads. |
| UO-R6 | Geocoding logs carry no address and no coordinates (AGENTS.md §15). |

## Exclusions

- Excel (`.xlsx`) import; reverse geocoding; geocoding on the map picker.
- Changing the geocoding provider or its configuration (`application*.properties`).
- Order status workflow, planning, LEZ.
