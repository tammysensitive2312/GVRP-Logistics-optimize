# Design — DRIVER-MAP-003

All in `driver-view.component.{ts,html}` (+ specs). No service/backend change.

## Behavior

- Toggle button "📍 Theo dõi vị trí" above the map (off by default, FM-003). Enabled only while a detail with a live map is open.
- ON: each accepted `watchPosition` fix calls `map.panTo(dot, {animate: true})`.
- Any `dragstart`/`zoomstart` on the map flips follow OFF (one-way; FM-002) — re-toggle to resume.
- Toggle OFF or back/list/destroy: panning stops (watch itself keeps running for the dot per DRIVER-MAP-002 lifecycle; no new watch is created).

## Failure cases

- No position yet (denied/unavailable): toggle is a no-op visually except its pressed state; first fix starts panning. No errors, no toasts.
- Map destroyed (back/list): follow resets to OFF.

## Verification

- Specs: follow ON pans on fix; gesture pauses; OFF stops panning; watch lifecycle unchanged.
- Full suite + tsc; live check limited to toggle presence (real GPS movement unverifiable in automation — honest limitation, user device step).
