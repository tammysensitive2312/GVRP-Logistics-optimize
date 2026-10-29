# Review — MAP-ORDERS-001 and BROWSER-FIX-001 (2026-10-29)

Reviewer: Claude. Subject: `MAP-ORDERS-001/implementation.md` (branch `fix/map-orders-001` @ `d6e4015`) and `BROWSER-FIX-001/implementation.md` (branch `fix/browser-fix-001`, uncommitted). Static reading of the changed code and the new tests; nothing re-run by the reviewer.

## Verdict

**Both accepted for the user's browser test**, with one MEDIUM finding to confirm in that test (F1). Reported results: Entry 298/0/0/0 (baseline 270 + 28), client 385 SUCCESS (363 + 22), build EXIT 0 with the baseline's 12 warnings. No existing test modified. Both runs are at `verify`.

## Checked

| Item | Result |
|---|---|
| MAP D1–D4 (`main.component`, `orders-section.ordersLoaded`, `date.utils`, `map.component`, `map.service.clearSolution/loadOrders`) | Correct. Deviation 1 (order markers not redrawn while a solution is shown; latest page drawn on clear) **accepted**: it keeps the task constraint. |
| E1 `OrderInputDTO` | Initializer removed; `@Null(OnCreate)` kept; mapper ignores `status` on create; PUT without `status` now keeps the stored value (recorded). |
| E2 `GlobalExceptionHandler.handleValidationFailure` | 400, one detail per error, WARN with count and object name only. |
| E3–E5 | Capacity mapped from the vehicle type; refusal message with ≤10 codes; duplicate `addAll` removed. |
| E6 | Five key-bearing detail strings now quote `label(zone)`; Deviation 1 (the sixth string names no zone) **accepted**. |
| E7 `ZoneService` | `findZone` first (404 before any check); validation before any write; uniqueness read only through `findByBranchIdOrderByIdAsc(branchId)`; `uniqueName` keeps the template name when free, `(copy)`, `(copy 2)`…, ≤200 chars. Tenant scoping intact. |
| C1–C7 | As designed; rename sent before geometry; a retry after a failed geometry save re-sends the same name, which the server accepts (own name excluded). |
| Test integrity | New files only; text-assertion check recorded. |

## Findings

| ID | Severity | Finding | Proposed action |
|---|---|---|---|
| F1 | MEDIUM | The Add Order dialog sends `customer_phone: ""` when the phone is left empty. `@Pattern("^[0-9+\\-\\s()]+$")` rejects an empty string (null would pass). Before this fix the 500 hid it; now an order without a phone is expected to fail with 400 "Invalid phone number format". BF-T1/BF-T2 payloads omit the phone, so the tests do not catch it. | Confirm in test B2. If confirmed: client sends `null` for blank optional fields (`customer_phone`, `delivery_notes`, time windows), or the pattern accepts empty. Small follow-up task. |
| F2 | LOW | With DevTools, Spring's `ExceptionHandlerExceptionResolver` still logs `Resolved [MethodArgumentNotValidException … rejected value [...]]` at WARN (seen in the 07:18 trace), so rejected values (phone) still reach the dev log. | User decision: `spring.mvc.log-resolved-exception=false` in the dev properties (not allowed in this task). |
| F3 | LOW | Select All: if the filter or page changes while the all-orders request is running, the late response still sets `selectedOrders` and the notice for the old filter. | Ignore a response whose filters/page differ from those at request time. |
| F4 | INFO | Validation errors name the Java field (`orderCode`), not the wire name (`order_code`). The client shows only the message. | None now. |
| F5 | INFO | Select All loads every matching order in one request (25 054 rows on 2026-08-21); the table's checkboxes do not mirror it. Accepted by the user's choice. | None. |
| F6 | INFO | `MAP-ORDERS-001/implementation.md` was edited after its `04` step (session brief: "historical evidence changed"); both `traceability.md` files still say NOT_VERIFIED. | Update after the browser test. |

## Browser test

Scenarios: `test-scenarios.md` (this folder). Results go into §Results below.

## Results

(pending)
