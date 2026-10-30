# Feature traceability

Feature ID: ORDERS-FIX-001
Inspected source revision: `master` @ `081640a`, 2026-10-30 23:30–23:50 +07:00 (read only, not run)
Request source: `work-items/ORDERS-FIX-001/request.md` (user messages 2026-10-30 23:25, 23:27)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| OF-R1 | Ungeocodable import row reported once, not saved (review F1) | S1 | Planned: `OrderImportService.processParsedData` | OF-T1, OF-T2; UO-M10, UO-M11 | None | NOT_VERIFIED |
| OF-R2 | Clearing phone / notes / time window stores NULL (review F2) | S2 | Planned: `OrderMapper.updateEntityFromDTO` | OF-T3; UO-M5 | None | NOT_VERIFIED |
| OF-R3 | Address-only edit relocates the order (review F3) | C1 | Planned: `order-payload.utils#clearStaleCoordinates`, `EditOrderDialogComponent.onSubmit` | OF-C1, OF-C2; UO-M6 | None | NOT_VERIFIED |
| OF-R4 | Import errors carry the source line (review F4) | S3 | Planned: `ParseResult`, `CsvFileParser`, `TextDataParser`, `JsonFileParser`, `OrderImportService` | OF-T4, OF-T5, OF-T6 | None | NOT_VERIFIED |
| OF-R5 | No personal data in import logs or DB error text (review F5, AGENTS.md §15) | S1, S4 | Planned: `OrderImportService` | OF-T7 | None | NOT_VERIFIED |
| OF-R6 | Single time-window bound refused (review F6) | S5, C2 | Planned: `OrderService.validateBusinessLogic`, `EditOrderDialogComponent.validate` | OF-T8, OF-C3 | None | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Fix items written by Claude, implemented by Claude Code | User 23:27 | all | — |
| Include review F5 and F6 although not in the pasted summary | Claude (same classes; AGENTS.md §15 for F5) | OF-R5, OF-R6 | User may drop them before the run starts |
| Per-field `SET_TO_NULL`, not mapper-wide | Claude (import rows carry `status = null`; `orders.status` NOT NULL) | OF-R2 | — |
| Per-row DB failure isolation excluded | Claude (needs a transaction redesign) | OF-R1 | Candidate follow-up item |
| No documentation change | User 23:27 | — | DOC-SYSTEM-001 F3/F4 wording stays for the documentation pass |

## Synchronization review

- Requirements versus implementation: to fill at verify.
- Design versus implementation: to fill at verify.
- Test and documentation coverage: D03 (PUT semantics, import DB-failure wording) intentionally not updated here.
- Stale links or changed assumptions: none known.
- Remaining limitations: one row's DB failure still rolls back the whole import (design §4).
