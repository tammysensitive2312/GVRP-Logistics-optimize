# Request — UX-LEZ-001 (zone drawing, rule help, expandable route sections)

Source: Claude session of 2026-10-29 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 22:03 | "ở màn hình tuỳ chỉnh zone … user sẽ rất khó khăn khi không biết phải chỉnh sửa cái payload này sao cho đúng … thà rằng chế ra bản mẫu hoặc format mẫu cho user" | Editing raw GeoJSON is too hard; offer a guided way and samples. |
| 22:03 | "ruleset form này những thành phần là gì, để làm gì, điền thông tin như thế nào" | The rule form needs explanations (Claude answered in chat 22:10). |
| 22:03 | "ở tab route khi ấn recheck nếu có quá nhiều lỗi lại không thể cuộn chuột xuống … muốn thêm tính năng phóng to ở các sections components này … nút ấn … dialog to … nút thu nhỏ" | Route tab cannot scroll; add an expand button that opens a large dialog with a collapse button. |
| 22:18 | "đồng ý, bạn viết đi" | Accepts Claude's plan (22:10) for UX-LEZ-001 items 1–4, including drawing the zone by clicking on the map (no new library). |

## Requirements

| ID | Requirement |
|---|---|
| UL-R1 | A planner can draw a COPIED zone's polygon by clicking points on the editor map (draw, undo point, finish, clear); the GeoJSON text follows the drawing. Pasting/uploading GeoJSON stays. A "Load example" and a short format hint help users who paste. |
| UL-R2 | Every rule-form field has a one-line explanation; a rule can start from a preset; the form warns (without blocking) about rules that can never or always match by mistake. |
| UL-R3 | The Route tab scrolls when its content is taller than the panel. |
| UL-R4 | The Route tab (compliance panel + route list) and the Timeline tab have an "Expand" button opening a large dialog with the same content and a "Collapse" button. |
| UL-R5 | Repeated `LEZ_GEOMETRY_MISSING` findings of one route are shown as one line. |

## Exclusions

- Drawing plugins or any new npm dependency; editing vertices of an existing polygon (draw anew instead); MultiPolygon drawing (one polygon per drawing).
- LINKED zones (read-only); server changes; the send guard and compliance logic.
