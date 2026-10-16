# User feedback — DRIVER-MAP-001 (failure report, 2026-10-08)

Source: agent proxy browser test on the handoff (driver cuong, real assigned route).

- Symptom: `#driver-mini-map` renders empty (0 tiles/markers/paths); Navigate links + note + sent_at all fine.
- Root cause (author's bug): `destroyMap()` clears `mappedSendId`, and `renderMiniMap()` calls `destroyMap()` first without restoring the flag → every change-detection cycle re-renders; re-creating `L.map` on an initialized container throws ("Map container is already initialized"), swallowed by try/catch → permanently empty map.
- Fix: flag owns render-once semantics (set AFTER render, cleared only on list/back/open change); `destroyMap()` touches only the Leaflet object. No requirement/design change.
