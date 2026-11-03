# Request — SEC-XSS-001 (stored XSS in map popups and tooltips)

Source: Claude Code session 2026-11-04 (+07:00), user "Yes, do both" after `work-items/SEC-AUDIT-001/runtime-validation.md`.

Finding: **SEC-015 (High)** — depot 9005 named `AUDIT<img src=x onerror=…>` executed its handler when its marker tooltip opened on `/admin/zones`. Names and other text stored by the API reach Leaflet `bindTooltip` / `bindPopup` / `divIcon({html})` as HTML strings, and Leaflet assigns them with `innerHTML`.

## Requirements

| ID | Requirement |
|---|---|
| XS-R1 | Every API-supplied text placed into a Leaflet popup, tooltip or `divIcon` HTML in `GVRP_Entry_client_v2` is HTML-escaped (`& < > " '`), or passed as a text node. |
| XS-R2 | Display stays the same for normal names (Vietnamese text, `&`, quotes render as text). |
| XS-R3 | One shared, tested escaping helper; no ad-hoc escaping per file. |

## Exclusions

- Content-Security-Policy header/meta: hardening, separate decision (Google tiles, Angular dev server); record as follow-up.
- Server-side input validation of names (output encoding is the fix).
- Legacy client `GVRP_Entry_client`.

## Acceptance

New specs for the helper and for at least one sink per file; all specs green; build without new warnings; manual re-check: depot 9005 (or a new probe depot) shows its name as text and `window.__gvrpXss` stays undefined.
