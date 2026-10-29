# Request — BROWSER-FIX-001 (defects found in the 2026-10-29 browser check)

Source: Claude session of 2026-10-29 (+07:00, Vietnamese). Communication is Vietnamese; this artifact is English (AGENTS.md §2). The defects were found by Claude during the P1c browser check (`work-items/LEZ-001/review-p1c.md` §9) and listed to the user at 07:21.

| When | User message | Recorded meaning |
|---|---|---|
| 07:20 | Attached the Entry API stack trace of a failed Add Order: "đây là lỗi của add orders này. Các lỗi khác bạn đã điều tra được nguyên nhân chưa ? nếu chưa thì liệt kê các lỗi đó ra cho tôi" | Provides the B1 cause evidence; asks for the causes of the other defects. |
| 07:23 | "lập task để fix các bug bạn nêu ra đi" | Authorizes a task that fixes every defect Claude listed (except MAP-ORDERS-001, which already has its own task). |
| 07:25 | AskUserQuestion: "Select All Orders" → **"Mọi order khớp bộ lọc (Recommended)"** | The toolbar button selects every order matching the current filters across all pages. |
| 07:25 | AskUserQuestion: zone names (R9) → **"Bản copy có tên riêng (Recommended)"** | A COPIED zone gets its own name on copy and can be renamed in its editor; existing duplicates are renamed by the user. |

## Requirements

| ID | Requirement | Origin |
|---|---|---|
| BF-R1 | Creating an order from the Add Order dialog succeeds for a valid payload that omits `status`. | B1 (stack trace 07:18) |
| BF-R2 | A bean-validation failure on any endpoint answers **400** with one error per field (field name + message), not 500; the log carries no rejected value. | B1 second cause |
| BF-R3 | The Add Order / Edit Order dialog shows the server's error text, and its default delivery date is the planner's local date. | browser check |
| BF-R4 | "Select All Orders" selects every order matching the current date, status and search filters (all pages); changing filter or page, or toggling a row, ends that selection. | user 07:25 |
| BF-R5 | The planning dialog shows the total demand with at most two decimals. | browser check |
| BF-R6 | The vehicles admin list shows each vehicle's capacity (from its vehicle type). | browser check |
| BF-R7 | Planning orders that are not `SCHEDULED` is refused with a message that says why and names the orders (at most 10, then "+N more"). | browser check |
| BF-R8 | When a job leaves orders unserved, the job monitor also points to the low-emission panel. | browser check |
| BF-R9 | In the permits dialog the table fits the dialog and the delete button is visible without horizontal scrolling. | browser check |
| BF-R10 | LEZ violation details and the LEZ-unassigned reason name the zone by its name, not by the internal key (`Z2`). | review-p1c R10 |
| BF-R11 | Zones of a branch can be told apart: a new COPIED zone gets a name not used by another zone of the branch, and a COPIED zone can be renamed (unique within the branch). | review-p1c R9, user 07:25 |
| BF-R12 | The completion callback stores each unassigned order once in the solution's list. | found by Claude while investigating BF-R7 |

## Exclusions

- MAP-ORDERS-001 (map markers, local date of the dashboard filters, clear-solution redraw): separate task, run first.
- review-p1c R4, R7, R8; review-p1b F2–F8; P1-seed; driver view; the D4 two-date browser test.
- The order status workflow itself (orders become `ON_ROUTE` / `UNASSIGNED` after a job and are then refused by planning): unchanged; only the refusal message changes.
- Renaming LINKED zones (their name comes from the template).
- Stored texts of past solutions (old violation details are rewritten by the next Recheck; old LEZ-unassigned reasons stay as stored).

## Acceptance

- Add Order with valid data → order created; with a missing order code → 400 and the field message shown in the dialog.
- "Select All Orders" on a date with N matching orders → toolbar and Plan show N; page change clears it.
- Planning dialog shows e.g. `2,839.2 kg`; vehicles list shows `<capacity> kg`.
- Planning `ON_ROUTE` orders → toast explains they are not `SCHEDULED` and lists codes.
- A plan with an LEZ-excluded order → reason "No compliant vehicle for zone "<name>""; after Recheck, violation details use the zone name.
- Copying a template whose LINKED zone exists → copy named "<template name> (copy)"; renaming a COPIED zone works, a duplicate name is refused.
