# Request — DEPOT-FIX-001 (creating a depot always fails with 400)

Source: Claude session of 2026-10-30 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 23:25 | Pasted the summary of the 2026-10-30 reviews, including "DOC-SYSTEM-001 F2 … `GeometryMapper.validateCoordinates` luôn ném lỗi … `POST /depots` có lẽ luôn trả về 400 … Nên mở một task sửa riêng." | The depot defect gets its own fix item. |
| 23:27 | "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm. Phần document tạm thời remaining chưa động tới" | Authorizes fix work items, implemented by Claude Code. Documentation (`GVRP_Document`, DOC-SYSTEM-001 corrections) is left untouched for now. |

Origin of the defect: `work-items/DOC-SYSTEM-001/review.md` F2 and `work-items/UX-ORDERS-001/review.md` F7 (both VERIFIED by reading, not run).

## Requirements

| ID | Requirement |
|---|---|
| DF-R1 | `POST /api/v1/depots` with a valid body (name, address, latitude in [-90, 90], longitude in [-180, 180]) creates the depot in the caller's branch and answers `201` with the depot. |
| DF-R2 | A coordinate outside its range is still refused with `400` and a detail naming the field (`latitude` and/or `longitude`). |

## Exclusions

- `GVRP_Document` (D02 row for this defect): left for the DOC-SYSTEM-001 corrections, per the user (23:27).
- Depot update/delete endpoints (none exist today); the setup wizard UI; any other mapper.
