# Feature traceability

Feature ID: DRIVER-APP-001
Inspected source revision: `b057218` (driver API files; see context.md)
Request source: `work-items/DRIVER-APP-001/request.md` (user messages 2026-10-27 20:05–20:25 +07:00)

Code and test links updated 2026-10-31 (design revision 2). Code exists since implement rev 3–4 (`implementation.md`). Status `PARTIALLY_VERIFIED` = unit/widget tests green, not yet checked on a device.

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DA-R01 | Login, DRIVER only, secure token, 401 → login | §5.1, §5.4, §6 | `features/auth/auth_controller.dart`, `features/auth/login_screen.dart`, `core/api/api_client.dart`, `core/auth/token_store.dart`, `app.dart` `AuthGate` | DT-03, DT-04, DT-05 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R02 | My routes, CURRENT first, superseded banner | §5.2; ui-design §4.3, §8.2 | `features/routes/routes_screen.dart`, `routes_controller.dart` | DT-06 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R03 | Route detail, next stop, tap-to-call | §5.3; ui-design §4.4, §8.2 | `features/routes/route_detail_screen.dart`, `phone_caller.dart` | DT-07, DT-11 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R04 | Server geometry only; labelled fallback | §5.3, §2; ui-design §8.3 | `features/map/route_map.dart` `buildLegLines`, `core/geo/polyline.dart` | DT-01, DT-08 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R05 | Guidance from server instructions, local leg index | §5.3; ui-design §8.2 | `features/guidance/guidance_panel.dart`, `routes_controller.dart` `LegController` | DT-09 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R06 | Zone awareness | §5.3 | `route_detail_screen.dart` banners, `route_map.dart` `StopMarker` | DT-10 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R07 | Location explained, never sent | §5.3; ui-design §4.5 | `features/location/location_service.dart`, `route_detail_screen.dart` | widget test (denial) + manual | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R08 | No external navigation | §5.3, §3 | whole `lib/`; `phone_caller.dart` is the only launcher | DT-11, DT-12 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R09 | In-memory customer data, safe logs, Vietnamese errors | §1, §6 | `api_client.dart`, `core/log/app_logger.dart` | DT-13 | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R10 | Base URL per build; cleartext debug-only | §6, §7 | `config/env.dart`, `android/app/src/debug/res/xml/network_security_config.xml` | env tests + merged-manifest check | 2026-10-31 | PARTIALLY_VERIFIED |
| DA-R11 | Vietnamese UI | §2 | `l10n/strings_vi.dart` | widget tests | `flutter test` 2026-10-31 | PARTIALLY_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Flutter, Android first | 2026-10-27 20:10 "tôi đồng ý sử dụng flutter" | All | — |
| MVP = view + in-app guidance, no backend change | 2026-10-27 20:10 "theo đề xuất của bạn" (Claude's interpretation, to confirm) | All | Confirm at design review |
| No external navigation button | 2026-10-27 20:10 "không kèm nút mở google maps" | DA-R08 | Same issue reported to LEZ-001 P1c (web driver view) |
| ~~Design accepted; `AGENTS.md` §3 entry not approved~~ (recorded at 20:37 by a parallel session; **superseded** by the 20:40 row below) | 2026-10-27 20:37 "Duyệt" | All | — |
| Design accepted; MVP reading confirmed; app id `org.truong.gvrp_driver`; `AGENTS.md` §3 entry allowed | 2026-10-27 20:40 "1. Duyệt thiết kế 2. Duyệt application id 3. Cho phép ghi vào AGENTS.md" | All | Implementation still needs an explicit instruction |
| Separate git worktree and branch `feature/driver-app-001` from `master` | 2026-10-27 20:25 "oke sử dụng worktree"; created by the user at 20:26 | process | Run root = the worktree path |
| UI design (`ui-design.md`) accepted, with 5 decisions | 2026-10-28 "duyệt bắt đầu từ bước 2" | DA-R01…R07 | — |
| Claude writes the whole app (the user had first planned to code it) | 2026-10-31 "bạn code luôn app driver cho tôi đi" | All | implement rev 3 → verify rev 4 |
| UI revision 2: web-client style, map zoom controls, Google tiles like the web client (not the SDK, no API key) | 2026-10-31 "cải thiện UI, maps có thể dùng google maps giống bản web …"; answer "Tile Google như web" | DA-R02…R06 | `findings-ui-rev2.md`; `ui-design.md` §8; release blocker §8.5 (tile terms) |

## Synchronization review
- 2026-10-31: design revision 2 (`ui-design.md` §8) changes visuals and the tile source only. Behaviour, API contract and DT-01…DT-13 are unchanged.
