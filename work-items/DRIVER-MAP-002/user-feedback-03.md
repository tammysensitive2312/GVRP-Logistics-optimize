# User feedback 3 — DRIVER-MAP-002 (2026-10-09, with screenshot)

Source: user testing rev-8 handoff: stop markers effectively invisible on the map.

- Root cause: `.driver-pin` rules live in component SCSS (emulated encapsulation), but Leaflet creates marker DOM imperatively without Angular content attributes → rules never match. The planner map avoids this with inline styles in marker html (`marker.service.ts` precedent).
- Fix: inline-styled numbered pins (34px circle, white border, shadow) + name pill, same visual language as planner markers. Keep `driver-pin` class names for test/DOM hooks.
- No requirement change (DG-001: visible numbered markers).
