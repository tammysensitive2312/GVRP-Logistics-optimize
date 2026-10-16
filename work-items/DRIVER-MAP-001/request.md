# Request — DRIVER-MAP-001 (driver map + Google Maps navigation)

Source: user message 2026-10-08 (build mode) — "làm đi" for: overview map + per-leg Google Maps buttons + sent_at formatting. No in-app voice navigation (deferred as agreed).

## Explicit requirements

- DM-001: Driver route detail shows a mini-map with the full route: road-following polyline, numbered stop markers in plan order, auto-fit bounds.
- DM-002: Each ORDER stop with coordinates gets a "Navigate" link opening Google Maps directions to that stop (new tab, `rel="noopener"`).
- DM-003: UI note stating Google routing is for guidance only; stop order follows the plan.
- DM-004: `sent_at` renders as readable local datetime, not raw ISO.
- DM-005: Geometry reuses the existing `RoutingService` (OSRM, straight-line fallback) and the same Google tile layer as the planner map. No new routing backend, no API keys, no billing.

## Exclusions

- In-app turn-by-turn/voice navigation, live rerouting, driver GPS tracking, offline maps.

## Acceptance

- Detail of a multi-stop route shows polyline + correctly ordered markers; Navigate links encode the stop's own lat/lon; sent_at formatted; OSRM-down still shows dashed fallback; suites green.
