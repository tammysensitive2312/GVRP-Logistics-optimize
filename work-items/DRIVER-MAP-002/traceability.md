# Feature traceability

Feature ID: DRIVER-MAP-002 (follow-up to DRIVER-MAP-001, still await_user_test)
Request source: work-items/DRIVER-MAP-002/request.md (user authorized full scope + GPS 2026-10-08)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DG-001 | Labeled markers, distinct depots (request.md) | design.md Driver view | driver-view divIcon + CSS | DOM specs (names visible) + live screenshot | verification.md | VERIFIED |
| DG-002 | Direction arrows (request.md) | design.md Driver view | polylinedecorator import | live polyline+arrows screenshot | verification.md | VERIFIED |
| DG-003 | Ordered maneuver list from OSRM (request.md) | design.md RoutingService | getRouteLegs + instruction builder | matrix + mapping specs + live 88-step list | verification.md | VERIFIED |
| DG-004 | Live GPS dot, silent denial (request.md) | design.md Driver view | watchPosition lifecycle | mocked-geolocation specs (dot + denial) | verification.md | PARTIALLY_VERIFIED (real-GPS live pending user device) |
| DG-005 | No backend/billing/voice/reroute (request.md) | design.md | none | suites + build green | verification.md | VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Full scope + GPS | User 2026-10-08 | All | None |
| New additive RoutingService method (old untouched) | Planner map depends on current shape | DG-003 | None |
| English instruction templates, street names verbatim | Existing English UI | DG-003 | i18n later if wanted |

## Synchronization review

- Requirements versus implementation: all DG implemented (incl. zero-distance step filter polish).
- Design versus implementation: matches.
- Test and documentation coverage: routing 5 + driver GPS/maneuver specs green; full suite 157 + 5 pre-existing; tsc 0; budgets unchanged.
- Stale links or changed assumptions: none.
- Remaining limitations: OSRM demo limits; real-device GPS + voice/reroute out of scope.
