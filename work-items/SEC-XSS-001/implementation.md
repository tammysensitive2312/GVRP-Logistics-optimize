# Implementation — SEC-XSS-001

Date: 2026-11-04. Implementer: Claude Code (side session) in worktree
`E:\gvrp-wt\sec-xss-001`, branch `fix/sec-xss-001` from `master` `f095ef4`.
Nothing committed. Paths below are relative to `GVRP_Entry_client_v2/src/app/`.

## Scope
Fix SEC-015 (stored XSS through Leaflet `innerHTML` sinks) per `design.md`
D1–D3: one shared `escapeHtml` helper applied to every API-supplied value
interpolated into a Leaflet tooltip, popup or `divIcon` html string.

## Baseline (before any edit)
| Command | Exit | Result |
|---|---|---|
| `npm ci` (worktree had no `node_modules`) | 0 | 982 packages; `package*.json` unchanged |
| `npx ng test --watch=false --browsers=ChromeHeadless` | 0 | Executed 471 of 471 SUCCESS |
| `rm -rf dist && npm run build` | 0 | 12 warnings (10 component-style budgets, 1 initial bundle budget, 1 CommonJS `leaflet` module) |

## Changes per sink
| File | Sink | Change |
|---|---|---|
| `shared/utils/html.utils.ts` (new) | — | `escapeHtml(value: unknown): string`; `null`/`undefined` → `''`; `&` first, then `<`, `>`, `"`, `'` |
| `features/admin/zones/zone-map/zone-map.component.ts` | `bindTooltip(depot.name)` | `bindTooltip(escapeHtml(depot.name))` (runtime-confirmed sink). `title: depot.name` left as is: Leaflet assigns it as a DOM property, not HTML |
| `features/driver/driver-view/driver-view.component.ts` | `divIcon` html `${label}`; `bindTooltip(stop.location_name)` | `escapeHtml(label)` (label comes from API `sequence_number`); `bindTooltip(escapeHtml(stop.location_name))`. `color` is a code constant |
| `shared/services/map.service.ts` | zone `bindTooltip(zone.name)`; 2 route polyline tooltips; straight-line tooltip | `escapeHtml(zone.name)`; `escapeHtml(route.vehicle_license_plate)` (3×), `escapeHtml(route.order_count)` (2×). `distance.toFixed(1)` is code-formatted, left as is |
| `shared/services/marker.service.ts` | `createDepotPopup`, `createOrderPopup`, `createRouteStopIcon`, `createRouteStopPopup` | Every API value escaped: depot `name`, `address`; order `order_code`, `customer_name`, `customer_phone`, `address`, `demand`, `time_window_start/end`, `status`; stop `sequence_number` (icon and popup), `location_name` (2×), `arrival_time`, `departure_time`, `arrival_time \|\| departure_time`, `demand`, `wait_time`, and `vehiclePlate` (2×). Code-formatted numbers (`toFixed`), `markerColor` (from `STATUS_COLORS`) and constant markup unchanged |
| `shared/components/location-picker/location-picker.component.ts` | `bindPopup(createPopupHtml(latlng))` | **No change**: the popup contains only i18n strings (code-owned) and `toFixed(6)` coordinates; no API text (see Deviations) |

No value was escaped before reaching these builders (searched for `&amp;`/`&lt;` in non-spec `.ts`), so there is no double escaping; XS-T3 checks that `Kho A & B` displays without `&amp;`.

## New tests (new spec files only)
| ID | File | Specs |
|---|---|---|
| XS-T1 | `shared/utils/html.utils.spec.ts` | 5 |
| XS-T2, XS-T3 | `shared/services/marker.service.xss.spec.ts` | 5 (depot, order with edit button, route stop, depot route stop, no visible entities) |
| XS-T4 | `features/admin/zones/zone-map/zone-map-xss.component.spec.ts` | 1 (real component, depot id 9005, `.leaflet-tooltip` has no `img`) |
| XS-T5 | `shared/services/map.service.xss.spec.ts` | 2 (zone tooltip via `drawZones`, approximate-route tooltip via `drawStraightLine`; bracket access in spec only) |

Each sink spec opens the popup/tooltip on a real Leaflet map and asserts: no `img`
element, text content contains the literal payload, `window.__x` undefined.

## Commands with exit codes (after)
| Command | Exit | Result |
|---|---|---|
| `npx ng test --watch=false --browsers=ChromeHeadless` | 0 | Executed 484 of 484 SUCCESS (471 baseline + 13 new) |
| same, with `escapeHtml` temporarily made a no-op (mutation check, file restored and byte-compared) | 1 | 10 FAILED: all 7 sink specs (XS-T2..T5) and 3 XS-T1 specs; proves the specs detect the vulnerability |
| `rm -rf dist && npm run build` | 0 | 12 warnings, same set as baseline (diffed after removing sizes) |

The `'Polling error:', Error: network` console lines appear 12 times in both baseline and after logs (pre-existing, not failures).

## Verification
- VERIFIED: unit/component specs above (Karma, ChromeHeadless 156), build.
- NOT_VERIFIED: manual browser re-check on `/admin/zones` with real depot 9005 (left to the user, see Handoff). Driver-view sinks are covered by static inspection only (no new spec; design D3 has none for driver view).

## Modified Tests
None. No existing spec, stub, fixture or helper was edited.

## Security-Relevant Changes
- Closes the SEC-015 sinks listed in `context.md` on the client side. No `DomSanitizer.bypassSecurityTrust*`, no Angular `innerHTML` binding, no new `permitAll`, no new dependency.
- `docs/security/SECURITY_CHECKLIST.md` not edited (docs forbidden by task.md); SEC-015 status update to FIXED should follow the user's manual re-check and review.

## Deviations
- `location-picker.component.ts` not changed: no API text reaches its popup. Escaping i18n strings was not done because they are code-owned and may intentionally contain markup.
- Driver-view has no new spec (not in D3).

## Remaining Uncertainty
- No CSP in `index.html` (out of scope); a future sink missed by review would still execute. Defense in depth is a separate item.
- Server still stores raw HTML in names (stored text is not rejected or sanitized); any other client that renders it as HTML (e.g. legacy `GVRP_Entry_client`) is not covered.
- Other `innerHTML`-style sinks outside the five files were not part of this item.

## Lesson Candidates
- Leaflet `L.geoJSON(...).bindTooltip` binds the tooltip on the GeoJSON group, not on its child polygons; a spec must look for the tooltip on the group (cost one failing run here).

## Handoff — manual re-check for the user
1. Run the client from this branch (or merge it) against the local stack.
2. Log in to the branch owning depot **9005** (name containing `<img src=x onerror=...>`), open `/admin/zones`, hover the depot marker.
3. Expected: the tooltip shows the name as literal text including `<img ...>`; no broken-image icon; in DevTools console `window.__gvrpXss` is `undefined`.
4. Optional: open the solution map / driver view with a stop or plate holding the same payload; the text must appear literally.
