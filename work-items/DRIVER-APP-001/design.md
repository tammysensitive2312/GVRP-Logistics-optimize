# Design — DRIVER-APP-001 (Flutter driver app, MVP)

Status: **ACCEPTED** by the user on 2026-10-27 20:40 +07:00 ("1. Duyệt thiết kế 2. Duyệt application id 3. Cho phép ghi vào AGENTS.md"). This confirms the MVP reading in `request.md` §1 (view + in-app guidance, no backend change). Application id `org.truong.gvrp_driver` is approved. The `AGENTS.md` §3 component entry is approved and was added by Claude on this branch. (An earlier status line recorded at 20:37 by a parallel session said the entry was not approved; the user's 20:40 message supersedes it.) Implementation needs an explicit instruction (AGENTS.md §6).
Inputs: `request.md` (DA-R01…R11) and `context.md`. Diagrams in `diagrams/` (§10).

Labels:
- **[api]** = existing backend behaviour (`context.md` §1);
- **[p1b]** = fields that will exist after LEZ-001 P1b;
- **[proposal]** = new design.

## 1. Principles
1. **Read-only client of the existing driver API.** No backend change in this work item.
2. **One path.** Draw only server geometry. Never call a router. No external navigation.
3. **Fail visible, not silent.** Missing geometry, superseded routes, expired tokens and network errors are always shown in Vietnamese.
4. **Privacy by default.** Customer data lives only in memory. The token is the only thing persisted, and only in secure storage. Nothing personal goes to logs.

## 2. Project layout — [proposal]
`GVRP_Driver_app/` at the repository root, next to `GVRP_Entry_API`, created with `flutter create --org org.truong --project-name gvrp_driver --platforms android`.

```
GVRP_Driver_app/
  lib/
    main.dart                 # bootstrap, ProviderScope
    app.dart                  # MaterialApp, theme, Vietnamese strings, auth gate
    config/env.dart           # API_BASE_URL from --dart-define (no default in release)
    l10n/strings_vi.dart      # all UI strings (DA-R11)
    core/
      api/api_client.dart     # HTTP, bearer header, snake_case JSON, error mapping, 401 → logout
      api/api_error.dart      # typed errors: Unauthorized, Forbidden, NotFound, Network, Server
      auth/token_store.dart   # flutter_secure_storage wrapper
      geo/polyline.dart       # Google encoded polyline decode (1e5, [lat, lon]) — local code
    features/
      auth/                   # login screen + controller (role check)
      routes/                 # models, repository, list screen, detail screen, controllers
      map/route_map.dart      # flutter_map: markers, server polylines, straight-line fallback, my location
      guidance/guidance_panel.dart  # instructions per leg, progressive disclosure, local leg index
      location/location_service.dart  # permission explanation + geolocator stream
  android/app/src/debug/res/xml/network_security_config.xml   # cleartext only for dev hosts (DA-R10)
  test/                      # unit + widget tests (§8)
  README.md                  # how to run against the local API
```

State management: **Riverpod** (`flutter_riverpod`). Controllers stay testable without widgets.

## 3. Dependencies — [proposal; versions to be checked on pub.dev at implementation, AGENTS.md §15]
| Package | Purpose |
|---|---|
| `flutter_riverpod` | State and DI |
| `http` | REST client |
| `flutter_secure_storage` | Token storage (Android Keystore) |
| `flutter_map` + `latlong2` | OSM map, polylines, markers (no Google SDK and no API key) |
| `geolocator` | Location stream and permission |
| `url_launcher` | `tel:` links only (DA-R03). **Never** used for map or navigation URLs (DA-R08) |
| dev: `flutter_test`, `mocktail` | Tests |

The encoded polyline decoder is local code (§2), not a package: it is tiny, and matching the server's encoder exactly matters.

## 4. Data model (client) — mirrors the API, snake_case on the wire
- `RouteSend {id, solutionId, routeId, vehicleLicensePlate, driverName, senderName, sentAt, status(CURRENT|SUPERSEDED)}` [api]
- `DriverRoute {sendId, solutionId, routeId, vehicleLicensePlate, status, stops[]}` [api]
- `DriverStop {sequenceNumber?, type(ORDER|DEPOT), orderId?, locationName, address?, lat, lon, arrivalTime?, departureTime?, customerName?, customerPhone?, deliveryNotes?, demand?, geometryToNext? [p1b], instructionsToNext? [p1b], zoneNames? [p1b]}`
- `Instruction {text, distanceM, timeMs, sign, streetName?}` [p1b]

Unknown fields are ignored. Missing [p1b] fields are allowed. The decoder never throws on absent data.

## 5. Screens and behaviour

### 5.1 Login (DA-R01)
- Fields: branch name, username, password. "Đăng nhập" calls `POST /api/v1/auth/login`.
- If `role != "DRIVER"`: show "Tài khoản này không phải tài xế." and **store nothing**.
- If DRIVER: save the token, then go to My routes.
- 400 (missing branch, wrong credentials, as returned by the API): show "Sai chi nhánh, tên đăng nhập hoặc mật khẩu."

### 5.2 My routes (DA-R02)
- List from `GET /api/v1/driver/routes`, in server order (CURRENT first).
- Each row: license plate, "Gửi lúc dd/MM/yyyy HH:mm", and a "Phiên bản cũ" tag for SUPERSEDED.
- Banner "Có phiên bản tuyến mới hơn — tuyến hiện hành nằm ở đầu danh sách" when any SUPERSEDED exists.
- Pull to refresh. Empty state: "Chưa có tuyến nào được giao."

### 5.3 Route detail (DA-R03…R07)
Layout: the map in the top half; a bottom sheet with
1. a zone banner (DA-R06);
2. a guidance card (DA-R05);
3. a next-stop card;
4. "Tất cả điểm dừng (n)" expandable.

- **SUPERSEDED:** a red banner "Đây là phiên bản cũ — không đi theo tuyến này". Guidance is hidden.
- **Next-stop card:** the stop after the current leg index. Shows name, arrival, address, customer, phone (tap → `tel:`), notes.
- **Leg progression:** the leg index starts at 0 (start depot → first stop). "Đã đến điểm này" advances it. "Quay lại" goes back. It is local and not persisted; reopening a route starts at 0.
- **Guidance card:** the instructions of the current leg. Shows the first step, "Bước 1/n" and "Xem tất cả bước". If the leg has no instructions: "Chưa có hướng dẫn từ máy chủ cho chặng này."
- **Map:**
  - stop markers numbered by plan order; depots with a distinct icon; stops with zone names marked with a distinct border;
  - per leg, the decoded `geometryToNext` as a solid polyline. Otherwise a **straight dashed line** with the legend "Đường đi chưa có từ máy chủ (chỉ minh hoạ)";
  - the current leg is highlighted;
  - my position appears after permission; a "Theo dõi vị trí" toggle.
- **Location (DA-R07):** an in-app explanation first ("Vị trí chỉ hiển thị trên bản đồ của bạn, không gửi về máy chủ"), then the OS prompt. If denied, show a help text and keep the map working.
- **No external navigation:** there is no button or link to Google Maps, `geo:` intents or other apps anywhere (DA-R08). `url_launcher` is called only with `tel:`.

### 5.4 Session (DA-R01, R09)
Any 401 from any call clears the token and returns to Login with "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại." Logout is in the app bar.

## 6. API client and errors — [proposal]
- Base URL from `--dart-define=API_BASE_URL`. Debug default `http://10.0.2.2:8080` (emulator). A release build without it fails at startup.
- Header `Authorization: Bearer <token>`. Timeouts 15 s.

Error mapping:

| Response | Typed error | UI |
|---|---|---|
| 401 | Unauthorized | Logout, then Login |
| 403 | Forbidden | "Bạn không có quyền xem tuyến này." |
| 400 with `RESOURCE_NOT_FOUND` | NotFound | "Không tìm thấy tuyến." |
| other 4xx | Client | Generic Vietnamese message with retry |
| 5xx, timeout, socket | Server / Network | "Không kết nối được máy chủ" + "Thử lại" |

Logs: method, path template and status only. Never bodies, tokens, names, phones or coordinates.

## 7. Android specifics — [proposal]
- `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` permissions.
- A debug-only `network_security_config.xml` permitting cleartext for `10.0.2.2` and one documented LAN host. Release keeps the platform default (no cleartext).
- Application id `org.truong.gvrp_driver` (open question in `request.md` §7). Min SDK as the Flutter default.

## 8. Tests (Flutter, no device needed)
| ID | Case | Expected |
|---|---|---|
| DT-01 | Polyline decode of the published Google example `_p~iF~ps|U_ulLnnqC_mqNvxq`@` | Exact points |
| DT-02 | Models parse the sample JSON of §4 without [p1b] fields; with them | Both parse; optional fields null or populated |
| DT-03 | Login: role PLANNER | Refusal shown; `token_store` never written |
| DT-04 | Login: role DRIVER | Token stored; navigates to My routes |
| DT-05 | Any call returns 401 | Token cleared; Login shown with the expiry message |
| DT-06 | My routes with CURRENT and SUPERSEDED | Order kept; banner and tag shown |
| DT-07 | Detail of a SUPERSEDED send | Red banner; guidance hidden |
| DT-08 | Detail with geometry on leg 1 and none on leg 2 | One solid polyline, one dashed with the legend |
| DT-09 | Guidance: instructions for leg 0; advance to leg 1 without instructions | Step text shown; then the no-guidance message |
| DT-10 | Stop with `zone_names` | Zone banner and marked stop |
| DT-11 | Tap phone | `url_launcher` called with `tel:` only |
| DT-12 | Static check: no `geo:`, `google.navigation`, `maps.google`, `comgooglemaps` strings in `lib/` | None found (DA-R08) |
| DT-13 | Logs during an API error | No body, token or customer field |

Manual acceptance follows `request.md` §5, on the Pixel 8 emulator against the local Entry API.

## 9. Assumptions and limitations
- The JWT lifetime is whatever `spring.gvrp.jwt.expiration-ms` holds. There is no refresh, so drivers re-login.
- Before LEZ-001 P1b is merged and running, routes have no server path or instructions. The app shows labelled straight lines and "no guidance" messages. That is expected, not a defect.
- OSM public tiles are for development only (tile usage policy). Production needs a provider (out of scope).
- Leg progress is local. If the planner re-sends a newer version, the driver sees it on refresh (superseded banner) and must reopen it.
- CI for the app (a new job in `.github/workflows/ci.yml`) is a rule/config change. It is proposed for a later step with user approval.

## 10. Diagrams (`diagrams/`, PlantUML + SVG/PNG)
| File | Shows |
|---|---|
| `drv-component` | App layers and the backend endpoints they call |
| `drv-seq-login` | Login, role check, token storage, 401 handling |
| `drv-seq-route-detail` | List → detail → map and guidance rendering with server geometry or fallback |
| `drv-state-session` | LoggedOut / LoggedIn / Expired |
| `drv-activity-driver-flow` | The driver's day in the app: open route, follow legs, call, advance |
