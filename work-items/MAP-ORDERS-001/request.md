# Request — MAP-ORDERS-001 (order markers missing on the planner map)

Source: user messages in the Claude session of 2026-10-29 (+07:00, Vietnamese). Communication is Vietnamese; this artifact is English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 00:12 | "còn một lỗi nữa là không hiện các orders trên map bạn ạ, ở dạng bình thường ấy, bạn check xem giúp tôi" | In normal mode (no solution shown), the planner map on `/main` shows no order markers. |
| 00:15 | "bảng có order nhưng bản đồ không có marker nào, nhất là sau khi đổi ngày hoặc đổi trang trong bảng. Đúng là tôi gặp lỗi này, bạn viết task luôn đi" | Confirms the symptom: the order table lists orders while the map has none, especially after changing the date or the page. Authorizes a task. |

## Requirements

| ID | Requirement |
|---|---|
| MO-R1 | The planner map shows a marker for every order the order table currently lists (same filter, same page), and follows every reload of the table (date, status, search, page, page size, bulk update, order edit). |
| MO-R2 | "Today" (default date filter of the dashboard and of the table, and the date picker limit) is the planner's **local** calendar date, not the UTC date. |
| MO-R3 | When the shown solution is cleared, depots and the table's order markers are drawn again. |
| MO-R4 | An order without usable coordinates is skipped on the map; it does not prevent the other markers from being drawn. |

## Exclusions

- Sidebar statistics (they keep today's source and semantics).
- The date picker's "no future date" limit (`maxDate`): kept, only computed locally.
- Hiding order markers while a solution is displayed (existing, intended behaviour).
- Any LEZ-001 behaviour, the Entry/Engine APIs, the driver view.

## Acceptance

- With orders on the selected date and page, the map shows exactly those orders; changing date or page updates the map within the same reload.
- Between 00:00 and 07:00 +07:00, the default filter is the local date.
- Clear solution → depots and the current page's orders reappear.
- A page containing an order without coordinates still shows the other markers.
