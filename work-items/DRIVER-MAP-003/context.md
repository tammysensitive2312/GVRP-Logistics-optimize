# Discovery — DRIVER-MAP-003 (read-only)

- GPS watch + dot + ring already live in `driver-view.component.ts` (`startGeoWatch`/`stopGeoWatch`, cleared on back/destroy/re-open) — VERIFIED by reading.
- Leaflet map object held in `this.map`; position updates flow only into marker/ring today, never into view (VERIFIED).
- Leaflet exposes user-gesture events `dragstart`/`zoomstart` on the map — the standard hook to detect manual moves (framework knowledge; verified at implement time against installed leaflet typings).
- `panTo` keeps zoom (unlike `setView`); follow must not fight gestures → pause-on-gesture is the design (FM-002).
