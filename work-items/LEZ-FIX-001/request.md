# Request — LEZ-FIX-001 (follow-ups of the UX-LEZ-001 review)

Source: Claude session of 2026-10-30 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 23:25 | Pasted the summary of the 2026-10-30 reviews: UX-LEZ-001 accepted with three small points — double-click zooms while drawing, self-intersecting ("bow tie") polygon not blocked, preset select not reset after applying. | The review findings are the defect list. |
| 23:27 | "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm. Phần document tạm thời remaining chưa động tới" | Authorizes fix work items, implemented by Claude Code. Documentation is left untouched. |

Origin: `work-items/UX-LEZ-001/review.md` F1–F4. Correction found while writing this item (Claude, 23:40): the server **already refuses** a self-intersecting polygon — `GeoJsonGeometryParser.parse` ends with `if (!result.isValid()) throw invalid("Geometry is not valid (for example self-intersecting or overlapping)")`. So F2 is a usability defect (the planner learns it only at Save, after the rules), not a data-integrity one.

## Requirements

| ID | Requirement | Origin |
|---|---|---|
| LF-R1 | While drawing a zone, a double-click on the map does not zoom; outside drawing mode double-click zoom works as before. | F1 |
| LF-R2 | While drawing, an outline whose edges cross is shown as not finishable: Finish is disabled and the control says why; Undo point or Cancel recovers. A non-crossing outline finishes as today. | F2 |
| LF-R3 | After a preset is applied, the preset select shows "Choose a preset…" again, and the same preset can be applied again. | F3 |
| LF-R4 | The "Only electric vehicles" preset tells the planner that vehicles with no energy type recorded are also banned. | F4 |

## Exclusions

- `GVRP_Document` (D07 description of the editor): left for the documentation pass (user 23:27).
- Server changes (the server check stays the authority); MultiPolygon drawing; holes; editing an existing outline vertex by vertex.
- Review F5 (two compliance panels while the expanded Route dialog is open): design-accepted, unchanged.
