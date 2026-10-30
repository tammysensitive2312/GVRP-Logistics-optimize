# DRIVER-MAP-002 — in-app guidance and the driver position dot

**Problem.** Handing each leg to Google Maps broke the driver's flow and did not show where the
driver was relative to the route.

**Decision.**

- The driver screen keeps an in-app guidance panel for the current leg: manoeuvre list, remaining
  distance and the next stop.
- The driver's own position is drawn as a dot on the map, from the browser geolocation API. A denied
  or unavailable position degrades silently: the dot is omitted and the route still renders.
- The map is rendered once per route load. Re-rendering on every change previously reset pan and
  zoom; a render-once flag guards it.
- Marker styles are applied inline so they survive the render-once path.
- The layout is responsive: mobile first, and the desktop grid is code-reviewed but was not exercised
  on a desktop browser during the run.

**Date.** Requested 2026-10-08 (approved with the full proposal plus the GPS dot); state `done` rev 13.

**Source.** `work-items/DRIVER-MAP-002/` (deleted): `design.md`, `correction-01.md`, `verification.md`,
`user-feedback-02.md`, `user-feedback-03.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D06-dispatch/README.md`, `TC-D06-017`.

**Limitations recorded at closure.** Real-device GPS was left to a user device test (unchanged).
