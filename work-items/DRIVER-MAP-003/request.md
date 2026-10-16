# Request — DRIVER-MAP-003 (follow-mode)

Source: user message 2026-10-09 (build mode) — follow-mode approved at DRIVER-MAP-002 acceptance.
Parent: DRIVER-MAP-002 (done); GPS watch + dot already exist there.

## Explicit requirements

- FM-001: "Theo dõi vị trí" toggle on the driver route detail; when ON and positions arrive, the map pans to keep the GPS dot in view.
- FM-002: Any manual map drag/zoom (user gesture) pauses follow until re-toggled (no fighting the driver).
- FM-003: Toggle OFF (default) behaves exactly as today (static overview + dot).
- FM-004: No backend, no billing, no tracking history.

## Exclusions

- Route history recording, ETA re-computation, background tracking (Flutter chặng 2 scope).

## Acceptance

- Toggle ON pans map to dot on position updates; manual drag pauses; toggle OFF stops panning; suites green.
