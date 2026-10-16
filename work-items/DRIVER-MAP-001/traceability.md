# Feature traceability

Feature ID: DRIVER-MAP-001
Request source: work-items/DRIVER-MAP-001/request.md (user authorized 2026-10-08)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DM-001 | Mini-map with ordered markers + fit (request.md) | design.md Map | driver-view renderMiniMap | container + fallback specs; live check pending | verification.md | VERIFIED (component scope) |
| DM-002 | Per-stop Navigate links with own coords (request.md) | design.md UI | googleMapsUrl helper | URL specs; live target pending | verification.md | VERIFIED (component scope) |
| DM-003 | Guidance-only note (request.md) | design.md UI | note text | DOM spec | verification.md | VERIFIED (component scope) |
| DM-004 | Readable sent_at (request.md) | design.md UI | DatePipe | DOM spec | verification.md | VERIFIED (component scope) |
| DM-005 | Reuse RoutingService/tiles, no billing (request.md) | design.md UI/Map | no service change | suites + build green | verification.md | VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| OSRM + straight fallback (same as planner) | Existing RoutingService behavior | DM-001, DM-005 | None |
| No in-app voice navigation | User scope decision | All | Future run if justified |

## Synchronization review

- Requirements versus implementation: all DM implemented as designed.
- Design versus implementation: matches (helper + guarded map init + note + DatePipe).
- Test and documentation coverage: 4 new specs green; full suite 147 + 5 pre-existing; tsc 0; budgets unchanged.
- Stale links or changed assumptions: none.
- Remaining limitations: OSRM demo rate limits (same as planner map); live GPS dot not in scope.
- Repair 1 (`user-feedback-01.md`): render-once flag fix + single-fetch spec; verified live (tiles/pins/polyline/screenshot).
