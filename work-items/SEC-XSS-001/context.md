# Context — SEC-XSS-001

Inspected 2026-11-04 by Claude Code (`c0cdfd5`). Paths relative to `GVRP_Entry_client_v2/src/app/`. Leaflet 1.9.4: string content of tooltips, popups and `divIcon.html` is set with `innerHTML`.

| File | Sink | API-supplied text |
|---|---|---|
| `features/admin/zones/zone-map/zone-map.component.ts` (~325) | `bindTooltip(depot.name)` | depot name — **runtime-confirmed** |
| `features/driver/driver-view/driver-view.component.ts` (~236) | `bindTooltip(stop.location_name)`; `divIcon` html (~229) | stop location name; check what the icon html interpolates |
| `shared/services/map.service.ts` (~193, 252, 275, 395) | `layer.bindTooltip(zone.name)`, `polyline.bindTooltip(...)` | zone name; route tooltip strings (vehicle plate, type…) |
| `shared/services/marker.service.ts` (27, 80, 186; html 33, 115) | `bindPopup(createDepotPopup / createOrderPopup / createRouteStopPopup)` | depot/order/stop fields: names, address, phone, notes, order code, plate |
| `shared/components/location-picker/location-picker.component.ts` (~144) | `bindPopup(createPopupHtml(latlng))` | coordinates (numbers) — check whether any address text is included |

No escaping helper exists in `shared/utils/`. Specs already exist for several services/components; do not edit them. Lessons: client entries in `docs/engineering/LESSONS.md` (L-2026-010 lockfile, L-2026-011 stop dev server before `npm ci`).
