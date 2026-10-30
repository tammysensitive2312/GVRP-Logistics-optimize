# Request — ORDERS-FIX-001 (follow-ups of the UX-ORDERS-001 review)

Source: Claude session of 2026-10-30 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 23:25 | Pasted the summary of the 2026-10-30 reviews: UX-ORDERS-001 "chấp nhận, kèm việc phải sửa" with F1 (MEDIUM) … F4 (LOW). | The review findings are the defect list. |
| 23:27 | "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm. Phần document tạm thời remaining chưa động tới" | Authorizes fix work items, implemented by Claude Code. Documentation is left untouched. |

Origin: `work-items/UX-ORDERS-001/review.md` F1–F6 (F5 and F6 were in the review file but not in the pasted summary; they are included because they are defects in the same two classes — F5 is an AGENTS.md §15 logging finding, F6 a validation that never fires). Re-read by Claude on `master` @ `081640a` at 23:35; findings still hold.

## Requirements

| ID | Requirement | Origin |
|---|---|---|
| OF-R1 | In an import, a row whose address cannot be geocoded is reported once in `errors[]` and is **not** saved. With "skip errors" on, the other valid rows are imported; with it off, nothing is imported. | F1 |
| OF-R2 | Editing an order and clearing phone, notes, time-window start or time-window end stores the field as empty (`NULL`). An import with "overwrite existing" replaces those four fields the same way. Other fields keep their current behaviour. | F2 |
| OF-R3 | Editing an order and changing only its address (coordinates untouched in this edit) relocates the order: the server geocodes the new address. The dialog tells the planner this will happen. Coordinates picked or typed in this edit are kept. | F3 |
| OF-R4 | Every import error carries the line of the source: CSV file line (header = line 1), text line (first line = 1), JSON line of the object. Parse, validation, geocoding and database errors use the same numbering. | F4 |
| OF-R5 | The import writes no customer name, phone, address or coordinates to the log at any level, and the database-failure text in `errors[]` carries no exception message. | F5, AGENTS.md §15 |
| OF-R6 | Creating or updating an order with only one time-window bound is refused (`400`, "Time Window must have both start and end times, or neither."), and the order dialog says so before sending. | F6 |

## Exclusions

- `GVRP_Document` (D03 PUT semantics, import rollback wording): left for the DOC-SYSTEM-001 corrections (user 23:27).
- Making a database failure of one row leave the other rows imported (the import runs in one transaction; see design §4).
- Excel import; geocoding provider; batch geocoding performance.
- Orders already stored with a single time-window bound (not migrated; the planner fixes them on the next edit).
