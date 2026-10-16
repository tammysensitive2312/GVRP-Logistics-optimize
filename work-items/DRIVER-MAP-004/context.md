# Context — DRIVER-MAP-004

Parent run: DRIVER-MAP-003 (done, follow-mode). Inspected revision: files read
2026-10-12 (driver-view.component.{ts,html,scss,spec}, routing.service
ManeuverStep, driver.model DriverStopDTO).

## Current behavior (VERIFIED live via `bsk` as cuong, route 30H-108480.LG)

- Right column renders all maneuvers at once (23 steps on screen; 82 total
  once legs fully resolve) then the full 9-stop list below — the complaint.
- Maneuvers come from `routing.getRouteLegs` (OSRM, no key/billing);
  zero-length steps already filtered in code.
- Stops carry arrival/address/customer/phone/notes + per-stop Google Maps
  `Navigate` link; all must stay reachable (DM4-003).

## Decision

Signals `guidanceOpen`/`stopsOpen` (default false, reset on open/back) +
`nextStop` computed (first non-depot, else first). Shared `ng-template`
stop card for preview and list (single markup source). No service change.
