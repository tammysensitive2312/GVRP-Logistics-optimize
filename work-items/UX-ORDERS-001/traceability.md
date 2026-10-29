# Feature traceability

Feature ID: UX-ORDERS-001
Inspected source revision: `fix/browser-fix-001` working tree, 2026-10-29 22:05–22:25 +07:00
Request source: `work-items/UX-ORDERS-001/request.md` (user messages 2026-10-29 22:03, 22:18)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| UO-R1 | Order without coordinates is geocoded (22:03) | S1, C1 | Planned: `OrderService.resolveCoordinates`, edit-order dialog | UO-T1…T4, UO-C2; browser | None | NOT_VERIFIED |
| UO-R2 | No 0,0 default; blanks sent as null (review F1) | C1 | Planned: `order-payload.utils#toOrderPayload`, `OrderWritePayload` | UO-C1, UO-C2 | None | NOT_VERIFIED |
| UO-R3 | Import works end to end | C2 | Planned: `ApiService.importOrders`, `OrdersSectionComponent.onImportClick`, import banner | UO-C3, UO-C4; browser | None | NOT_VERIFIED |
| UO-R4 | Delivery date before validation; CSV lat/lon | S2 | Planned: `OrderImportService.processParsedData`, `OrderImportRequest`, `CsvFileParser` | UO-T5…T7 | None | NOT_VERIFIED |
| UO-R5 | Correct template, text format, file types (22:03) | C3 | Planned: import dialog | UO-C5 | None | NOT_VERIFIED |
| UO-R6 | Geocoding logs without address/coordinates | S3 | Planned: `GeocodingService` | UO-T8 | None | NOT_VERIFIED |

## Decision log

| Decision | Source | Affected |
|---|---|---|
| Split into UX-ORDERS-001 and UX-LEZ-001 | Claude 22:10, user 22:18 | all |
| Geocode on the server, not the client | Claude 22:10, user 22:18 | UO-R1 |
| Optional CSV lat/lon columns | Claude 22:10 (optional item 5), user 22:18 | UO-R4 |
| Import errors banner without raw data | Claude (AGENTS.md §15) | UO-R3 |
