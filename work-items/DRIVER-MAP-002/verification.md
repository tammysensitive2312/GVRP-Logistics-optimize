# Verification — DRIVER-MAP-002

Environment: Node v22 + Chrome Headless 154; live Edge 154 via browser-skill (driver cuong, route 30H-108480.LG).

## Checks

- `tsc` clean; routing spec 5/5; driver-view spec 11/11 (incl. GPS dot + denial + maneuver list/fallback).
- Full suite → 157 SUCCESS + same 5 pre-existing failures (verified identical).
- `ng build` → no new ERROR/WARNING files (only pre-existing login ERROR).
- Live: labeled pins (names visible), arrows on polyline, 88-step maneuver list with real street names ("Turn left onto Ngõ 52 Phố Gia Quất (445 m)"), zero-distance noise filtered, Navigate links, note, formatted sent time. Screenshot: `C:\Users\ACER\AppData\Local\Temp\driver-map-v4.png` (outside repo).

## Repair 2 verification + live browser test (2026-10-09, driver cuong)

- Driver-view spec 13/13; routing spec 5/5; full suite 157 + same 5 pre-existing; tsc 0; budgets unchanged.
- Live (ng serve hot-reload): labeled pins readable ("1 ORD-1163", depot green), polished maneuvers ("At the end of the road, turn left onto Phố Gia Quất (277 m)"), zero-noise guidance list, mobile full-bleed map.
- Screenshot: `C:\Users\ACER\AppData\Local\Temp\driver-map-v5.png` (outside repo).
- Repair-2 live re-check: v6 showed pins but overlapping name pills in dense clusters → dropped to number-only pins (v7 screenshot: clean blue numbered circles, green depot, names in list + tap tooltips).
- PC 2/3 grid verified by code review only (automation viewport is 805px); honest limitation recorded.

- Real-device GPS dot unverified (headless/automation has no position; code path unit-covered with mocks). OSRM demo rate limits as before. Voice/reroute out of scope.

No defects in scope → route `pass` to `await_user_test`.
