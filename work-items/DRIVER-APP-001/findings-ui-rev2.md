# Findings — DRIVER-APP-001, UI revision 2 (design change)

Date: 2026-10-31. Source: user message in the Claude Code session, after the
run reached `verify` rev 4:

> "tôi muốn bạn cải thiện UI, maps có thể dùng google maps giống bản web mà,
> map chưa zoom được. Các màn hình của app có thể lấy theo phong cách bản web
> chẳng hạn. Sau đó hướng dẫn tôi nạp code vào điện thoại để test thử"

Decision asked and answered in the same session (2026-10-31):
- Question: how to show Google Maps.
- Answer: **"Tile Google như web"**. Keep `flutter_map` and switch the tile source to
  `https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}`, the same source the
  web client uses (`driver-view.component.ts`, `map.component.ts`).
- Rejected: the official Google Maps SDK (needs an API key and billing) and keeping OSM.

## Findings

| # | Finding | Kind | Route |
|---|---|---|---|
| F1 | The user wants the map to look like the web client (Google tiles) | Design change: `design.md` §3 and `ui-design.md` §4.4 name OSM tiles | design |
| F2 | "map chưa zoom được": the app has only gesture zoom (pinch, double tap) and no visible zoom control. The web client has Leaflet's +/- control | Design gap: `ui-design.md` has no zoom control | design |
| F3 | The user wants the screens styled like the web client (blue palette, light-blue info cards, outlined buttons, numbered blue pins, green depot pins) | Design change: `ui-design.md` §3 tokens (teal seed) | design |

## Constraints that stay

- No Google Maps SDK and no API key. The tiles are fetched as images, like the web client.
- No router call, no external navigation intent (DA-R08, DT-12). The web client's "Navigate" link to Google Maps is **not** copied.
- Every `[§5]`/`[§6]` string stays verbatim.

## Known risk accepted by the user

`mt1.google.com/vt` is an undocumented endpoint. Using it outside Google's SDKs or APIs is against the Google Maps Platform terms, as it already is in the web client. It is acceptable for development and testing only. Production needs a licensed tile source or the official SDK. This is recorded as a release blocker in `ui-design.md` rev 2.
