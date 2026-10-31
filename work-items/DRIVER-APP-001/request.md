# Request — DRIVER-APP-001 (Flutter driver app, Android first; MVP)

Source: user messages in the Claude session of 2026-10-27 (Vietnamese).
Communication is in Vietnamese; this artifact is in English (AGENTS.md §2).

## 1. Source messages and recorded decisions

| When (+07:00) | User message | Recorded meaning |
|---|---|---|
| 2026-10-27 20:05 | "bây giờ muốn tạo một app android cho driver dùng, song song với việc implement cho LEZ-001 có được không bạn ? mình tạo một app mới luôn mà ?" | A new, separate driver app, built in parallel with LEZ-001. This is the "chặng 2" foreseen in DRIVER-DELIVERY-001 (`work-items/DRIVER-DELIVERY-001/request.md`: "Flutter app in chặng 2 reusing the same driver-scoped APIs"). |
| 2026-10-27 20:10 | "1. công nghệ: tôi đồng ý sử dụng flutter" | **Flutter**, Android first. |
| 2026-10-27 20:10 | "2. phạm vi MVP: theo đề xuất của bạn" | **MVP = view assigned routes + in-app guidance, no backend change.** Claude's interpretation of the option it had recommended as parallel-safe; to be confirmed at intake review. |
| 2026-10-27 20:10 | "3. dẫn đường: không kèm nút mở google maps" | **No button that opens Google Maps (or any external navigation app).** |

Constraints Claude stated and the user did not object to:
- The app does not change `GVRP_Entry_API/src` while LEZ-001 is open (that path is in LEZ-001's frozen scope, L-2026-005).
- The app is written against the driver API contract as it will be **after LEZ-001 P1b** (`geometry_to_next`, `instructions_to_next`, `zone_names`).

## 2. Goal

A driver logs in on an Android phone, sees the routes sent to him, opens one, and follows it: stop order, customer contact, the server-certified path on a map, and turn-by-turn steps. He sees nothing that would lead him onto a different path than the one the planner sent (LEZ-001 R12, BR-1/BR-2).

## 3. Requirements

| ID | Requirement |
|---|---|
| DA-R01 | **Login** with branch name, username and password (`POST /api/v1/auth/login`, snake_case JSON). Only role `DRIVER` may continue; any other role sees a clear refusal and nothing is stored. The token is kept in platform secure storage. Logout clears it. Any 401 returns to the login screen. |
| DA-R02 | **My routes** list (`GET /api/v1/driver/routes`): current sends first, superseded ones marked; a banner when superseded sends exist (same meaning as the web driver view); pull to refresh. |
| DA-R03 | **Route detail** (`GET /api/v1/driver/routes/{send_id}`): stops in plan order; a "next stop" card; per stop: name, planned arrival, address, customer name, phone (**tap to call**), delivery notes; superseded routes clearly marked as not to be followed. |
| DA-R04 | **Map**: numbered stop markers, depot markers, the driver's own position. The path is drawn **only** from server geometry (`geometry_to_next`, encoded polyline precision 1e5). Until the backend provides it, legs are drawn as straight dashed lines labelled "đường đi chưa có từ máy chủ" (approximate). **No OSRM, no Google Directions, no other router.** |
| DA-R05 | **Guidance**: turn instructions from `instructions_to_next`, shown per leg with progressive disclosure (current step, then the full list), as in the web driver view. The driver advances to the next leg in the app (local state only, nothing is sent). |
| DA-R06 | **Zone awareness**: stops with `zone_names` are marked, and the route shows a banner "Tuyến có vùng hạn chế — đi đúng đường trên bản đồ" when any stop has zone names. |
| DA-R07 | **Location**: an explanation before the OS permission prompt; a follow-me mode. The position is shown on the map only and is **never sent** to the server in the MVP. |
| DA-R08 | **No external navigation**: no button or link that opens Google Maps or another navigation app (decision 3). |
| DA-R09 | **Privacy and errors**: customer data is held in memory only (no persistent cache); network and server errors are shown in Vietnamese with a retry; no customer data, token or coordinates in logs. |
| DA-R10 | **Configuration**: the API base URL is set per build (emulator `http://10.0.2.2:8080`, LAN IP for a physical phone). Cleartext HTTP is allowed **only in debug builds** and only for those development hosts. |
| DA-R11 | **Vietnamese UI** throughout. |

## 4. Exclusions (MVP)
- Any backend change: confirming delivery or failure, driver status, pushing location, notifications. These are later work items, after LEZ-001 closes.
- iOS build and release, Play Store release, a production map-tile provider, offline maps, persistent offline cache.
- Editing routes or anything a planner does.

## 5. Acceptance expectations
On the Pixel 8 emulator, against the local Entry API with a test DRIVER account:
1. Login works and other roles are refused.
2. The list matches the web driver view for the same account.
3. The detail matches the plan order and data.
4. Tap-to-call opens the dialer.
5. The map shows server paths when present, or labelled straight lines before LEZ-001 P1b.
6. Guidance steps match the server instructions.
7. There is no external-navigation entry anywhere.
8. The app returns to login after the token expires (401).

## 6. Authorization boundary
- Authorized by the 20:05/20:10 messages: intake, discover and design for DRIVER-APP-001.
- **Implementation needs an explicit instruction** after the design is reviewed (AGENTS.md §6), as for LEZ-001.

## 7. Open questions
- The send list has no delivery date (`RouteSendDTO`), only `sent_at`. Is that enough for drivers? Adding a date is a backend change, so it comes after LEZ-001.
- Token lifetime: `spring.gvrp.jwt.expiration-ms` (value not read; it is in `application.properties`). There is no refresh token, so drivers re-login when it expires.
- ~~Android application id~~: **approved `org.truong.gvrp_driver`** (2026-10-27 20:40). The installed Flutter/Dart versions are recorded by the engineer.
- ~~`AGENTS.md` §3 entry~~: **approved** (2026-10-27 20:40). Claude added the line on branch `feature/driver-app-001`.
