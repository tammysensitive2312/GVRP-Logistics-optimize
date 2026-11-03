# Design — SEC-XSS-001

## D1 — Helper (XS-R3)
New `shared/utils/html.utils.ts`:
```ts
/** Escapes text for safe interpolation into HTML strings (Leaflet popups, tooltips, divIcon). */
export function escapeHtml(value: unknown): string
```
`null`/`undefined` → `''`; numbers → `String`; replaces `&` first, then `<`, `>`, `"`, `'`.

## D2 — Apply at every sink (XS-R1, XS-R2)
- Plain-text tooltips (`bindTooltip(depot.name)`, `bindTooltip(stop.location_name)`, `bindTooltip(zone.name)`): pass `escapeHtml(...)`.
- Template builders (`createDepotPopup`, `createOrderPopup`, `createRouteStopPopup`, route polyline tooltips, driver `divIcon` html, location-picker popup): wrap **every** interpolated API value with `escapeHtml(...)`; leave constant markup and numbers formatted by the code as is. Attribute values (`title="..."`, `data-*`) also use `escapeHtml`.
- Do not switch to `DomSanitizer.bypassSecurityTrust*` or Angular `innerHTML` bindings.

## D3 — Tests (new spec files only)
| ID | Case | Expected |
|---|---|---|
| XS-T1 | `escapeHtml` on `<img src=x onerror=1>`, `a & b`, `"q" 'q'`, `null`, `42`, Vietnamese text | escaped / `''` / `'42'` / unchanged letters |
| XS-T2 | `MarkerService.createDepotMarker` with a depot named `<img src=x onerror="window.__x=1">`: open the popup on a test map, read its DOM | no `img` element; text content contains the literal name; `window.__x` undefined |
| XS-T3 | Same for order popup and route-stop popup | same |
| XS-T4 | `ZoneMapComponent` (real component, pattern of `zone-map-draw.component.spec.ts`) with that depot; open the marker tooltip | no `img` in `.leaflet-tooltip` |
| XS-T5 | `MapService` zone tooltip with a hostile zone name | no injected element |
Private builders may be tested through the public method or by bracket access in the spec only.

## Risks
Double escaping if a value is already escaped somewhere — check each builder; display must not show `&amp;`.
