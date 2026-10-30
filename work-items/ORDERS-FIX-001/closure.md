# Closure — ORDERS-FIX-001

Closed 2026-10-31 by Claude Code on the user's instruction ("close items tự commit…", `user-acceptance.json`).

## Requirement coverage

| Req | Status | Evidence |
|---|---|---|
| OF-R1 ungeocodable import row not saved | Implemented, unit-tested | `OrderImportRowsTest.ofT1_*`, `ofT2_*` |
| OF-R2 optional fields clearable on edit/overwrite | Implemented, unit-tested | `OrderMapperSetToNullTest` |
| OF-R3 address-only edit relocates | Implemented, spec-tested | OF-C1, OF-C2 specs |
| OF-R4 source line on every import error | Implemented, unit-tested | `ofT4_*`, `ofT5_*`, `ofT6_*`, `ofT7_databaseFailure*` |
| OF-R5 no personal data in import logs / error text | Implemented, unit-tested | `ofT7_*` |
| OF-R6 single time-window bound refused | Implemented, unit- and spec-tested | `OrderServiceTimeWindowTest`, OF-C3 spec |

Final counts: Entry 324/0/0/0, client 446 SUCCESS, build EXIT 0 (12 warnings, unchanged).

## Linked documents

`request.md`, `context.md`, `design.md`, `implementation.md` (rev 4), `review.md` (R1–R6), `implementation-rework.md`, `verification.md`, `handoff.md`, `user-acceptance.json`.

## Limitations carried forward

- Accepted without running the manual scenarios OF-M1…OF-M6 (live geocoding, real import, browser hint). They remain the recommended checks.
- One database failure during an import still rolls back the whole import (design §4, excluded).
- `GVRP_Document/03-basic-design/D03-orders/README.md` still describes the old PUT semantics and import behaviour (left for the documentation pass by the user's decision of 2026-10-30 23:27).
- Lesson candidate (PowerShell 5.1 encoding) proposed in `implementation-rework.md`, not yet added to `docs/engineering/LESSONS.md`.
