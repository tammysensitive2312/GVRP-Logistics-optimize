# Feature traceability

Feature ID: LEZ-FIX-001
Inspected source revision: `master` @ `081640a`, 2026-10-30 23:35–23:50 +07:00 (read only, not run)
Request source: `work-items/LEZ-FIX-001/request.md` (user messages 2026-10-30 23:25, 23:27)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| LF-R1 | No double-click zoom while drawing (review F1) | D1 | Planned: `ZoneMapComponent.startDraw` / `stopDrawing` | LF-C2; UL-M4 | None | NOT_VERIFIED |
| LF-R2 | Crossing outline cannot be finished, reason shown (review F2) | D2 | Planned: `zone-draw.utils#ringSelfIntersects`, `ZoneMapComponent.updateFinishButton` / `finishDraw` | LF-C1, LF-C3; UL-M2 with a bow tie | None | NOT_VERIFIED |
| LF-R3 | Preset select resets after applying (review F3) | D3 | Planned: `ZoneEditorDialogComponent.onPresetSelected`, editor template | LF-C4; UL-M7 | None | NOT_VERIFIED |
| LF-R4 | Electric preset explains the unknown-energy case (review F4) | D4 | Planned: `RULE_PRESETS` hint, editor template | LF-C5 | None | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Fix items written by Claude, implemented by Claude Code | User 23:27 | all | — |
| Client-only check for crossing edges; server `isValid()` stays the authority | Claude (server already refuses, `GeoJsonGeometryParser`) | LF-R2 | — |
| No new input/output on `app-zone-map` | Claude (UX-LEZ-001 NG0303 lesson candidate) | LF-R2 | — |
| Include review F4 (hint) | Claude (one line, same file) | LF-R4 | User may drop it |
| No documentation change | User 23:27 | — | D07 editor description stays for the documentation pass |

## Synchronization review

- Requirements versus implementation: to fill at verify.
- Design versus implementation: to fill at verify.
- Test and documentation coverage: D07 intentionally not updated here.
- Stale links or changed assumptions: review F2 overstated the risk (server already refuses invalid geometry) — corrected in `request.md`.
- Remaining limitations: browser behaviour NOT_VERIFIED until UL-M2/M4/M7 are run.
