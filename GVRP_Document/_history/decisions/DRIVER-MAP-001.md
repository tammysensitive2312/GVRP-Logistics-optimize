# DRIVER-MAP-001 — driver map and per-leg navigation links

**Problem.** The driver view listed stops as text. A driver could not see the route on a map and had
no way to hand a leg to a navigation app.

**Decision.**

- The driver route screen shows an overview map of the sent route with the stop markers in delivery
  order.
- Each leg gets an explicit "open in Google Maps" button built from the leg's origin and destination
  coordinates; the app never starts navigation on its own.
- In-app voice navigation was deferred by user decision and is still absent.
- `sent_at` is rendered in the user's locale and time zone rather than as a raw timestamp.
- The map uses the same tile and routing sources as the planner map; the OSRM demo server rate limits
  apply unchanged.

**Date.** Requested 2026-10-08; accepted 2026-10-23 on the current code; state `done` rev 13.

**Source.** `work-items/DRIVER-MAP-001/` (deleted): `request.md`, `design.md`, `verification.md`,
`verification-rereview.md`, `findings-scope-drift.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D06-dispatch/README.md` (screens and screen
flow), `TC-D06-016`.

**Limitations recorded at closure.** Leaflet is a CommonJS package and produces a build warning only.
