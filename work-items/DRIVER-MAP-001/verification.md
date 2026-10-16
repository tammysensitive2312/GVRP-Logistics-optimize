# Verification — DRIVER-MAP-001

Environment: Node v22 + Chrome Headless 154.

## Checks

- `tsc --noEmit -p tsconfig.spec.json` → clean.
- Driver-view spec 8/8 (4 existing + 4 new: URL encoding, null coords, container/note/link, sent formatting).
- Full suite → 147 SUCCESS + same 5 pre-existing failures (verified identical: FleetSetup, Navbar, 3× CollectionCache).
- `ng build` → no new ERROR/WARNING files (only pre-existing login ERROR + known warnings); driver styles under budget.
- Leaflet map init is guarded (no container/coords → no map, no throw); OSRM failure → dashed fallback via existing service semantics.

## Repair verification + live browser test (2026-10-08, driver cuong)

- Driver-view spec 9/9 (incl. new single-render spec); full suite 148 + same 5 pre-existing; tsc 0; budgets unchanged.
- Live (ng serve hot-reload, driver cuong, assigned route 30H-108480.LG): `#driver-mini-map` shows 8 loaded tiles, 9 numbered pins, 1 road-following polyline; 9 Navigate links with per-stop coords; note + `08/10/2026 06:25` formatting confirmed in DOM.
- Screenshot evidence: `C:\Users\ACER\AppData\Local\Temp\driver-map.png` (Hanoi map, blue route, numbered markers, note, Navigate links) — kept outside the repo by policy.

## Limitations

- OSRM demo rate limits apply as on the planner map; live GPS dot not in scope.

No defects in scope → route `pass` to `await_user_test`.
