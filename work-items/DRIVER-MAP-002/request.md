# Request — DRIVER-MAP-002 (driver map v2: guidance in-app)

Source: user message 2026-10-08 (build mode) — full proposal + GPS dot approved.
Parent: DRIVER-MAP-001 (await_user_test; untouched by this run except shared driver-view files).

## Explicit requirements

- DG-001: Stop markers show the location name (labeled pins), depots visually distinct.
- DG-002: Route polyline carries direction arrows (same pattern as the planner map).
- DG-003: Ordered maneuver list per route from OSRM steps ("Turn right onto X — 300 m"); street names pass through as returned.
- DG-004: Live GPS dot (+accuracy circle) while a route detail is open; denial/absence fails silent, never blocks the view.
- DG-005: No new backend, no billing, no voice, no auto-rerouting.

## Exclusions

- Voice guidance, rerouting, offline maps, GPS tracking history.

## Acceptance

- Markers labeled, arrows visible, maneuver list matches OSRM steps order with distances; GPS dot appears when permission granted and is absent (without errors) when denied; suites green.
