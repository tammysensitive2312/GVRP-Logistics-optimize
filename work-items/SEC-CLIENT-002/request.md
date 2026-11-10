# Request — SEC-CLIENT-002 (stale map responses across a session reset)

Source: Claude Code session 2026-11-10 (+07:00), delegated to a side session in worktree
`E:\gvrp-wt\sec-client-002` (branch `fix/sec-client-002` from `master` `0b6b759`).

## Finding addressed

| Lead | Fingerprint | Claim |
|---|---|---|
| Audit lead #8 (`work-items/SEC-AUDIT-001/audit/NEEDS-VALIDATION.md` §8) | `GVRP_Entry_client_v2.session-reset.map-service-stale-zone-callback` | `MapService` is a root singleton. A branch-zone (`GET /zones`) or route-geometry response requested under branch A's session and arriving after logout/401/login and after branch B's `<app-map>` is created is drawn on B's map, because the callbacks write into layer fields that `initializeMap` re-binds to B's map. `SessionResetService.resetBranchState()` (SEC-011) does not cover `MapService`. |

Related: SEC-011 (FIXED 2026-11-01) — central `SessionResetService.resetBranchState()` on logout, 401 and login; stores ignore responses of requests started before a reset.

## Requirements

| ID | Requirement |
|---|---|
| MC-R1 | First confirm or refute the lead from source and with a new spec that fails on the unfixed code. If refuted, record the evidence and change no code. |
| MC-R2 | A branch-zone list requested while drawing a solution is never drawn after a session reset or after the map was re-initialized for a new `<app-map>`. |
| MC-R3 | The same holds for the per-route asynchronous steps of `displaySolution` (stored route geometry, OSRM geometry, remaining routes of the loop and the final `fitBounds`). |
| MC-R4 | Other map layers loaded per branch (depots, orders, LEZ zones, routes) are checked for the same gap; any gap proven by a failing spec is fixed. |
| MC-R5 | Behavior within one session is unchanged: a solution drawn on a live map still shows its routes, arrows, tooltips and zones. |

## Exclusions

- Server-side changes (the server scopes `/zones` and `/routes/{id}/geometry` to the JWT branch already).
- Component-scoped Leaflet maps (`zone-map`, `driver-view`, pickers): checked, changed only if a gap is proven.
- Cancelling the HTTP request on the wire (only its effect on client state matters here).

## Acceptance

New spec(s) that fail before the fix and pass after; all existing specs green and unmodified; build exit 0 with the baseline warning set; manual two-planner browser check by the parent session.
