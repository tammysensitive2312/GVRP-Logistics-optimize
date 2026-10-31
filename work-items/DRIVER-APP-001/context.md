# Context — DRIVER-APP-001 (discover)

Inspected: 2026-10-27, reading the main working tree (branch `feature/lez-001` = master `b057218` + uncommitted LEZ-001 P1a). The driver-facing files listed below are not touched by P1a, so they equal `b057218`, the base of this work item's branch `feature/driver-app-001` (worktree `E:\OR-tools java example\GVRP-driver-app`). Static reading through the file bridge; nothing executed. Status words follow AGENTS.md §5.

## 1. Driver-facing API today

| Fact | Source | Status |
|---|---|---|
| `POST /api/v1/auth/login` (public). Body `{branch_name, username, password}` (all required). Response `{access_token, user_id, username, branch_id, branch_name, role}`. The JWT carries user id, branch id and role; there is **no refresh token**. Lifetime comes from `spring.gvrp.jwt.expiration-ms`. | `controller/AuthController.java`, `dto/request/LoginRequest.java`, `dto/response/LoginResponse.java`, `security/jwt/JwtTokenProvider.java` | VERIFIED (static) |
| JSON is snake_case globally (`WebConfig.objectMapper`). | `config/WebConfig.java` | VERIFIED (static) |
| CORS allows only `localhost`, `127.0.0.1` and `192.168.*.*` origins. Irrelevant for a native app (no Origin enforcement on Android HTTP clients). | `config/WebConfig.java` | VERIFIED (static) |
| `/api/v1/driver/**` requires role DRIVER (SEC-ROLE-001). | `config/SecurityConfig.java` | VERIFIED (static) |
| `GET /api/v1/driver/routes` returns the driver's own sends: CURRENT first, then SUPERSEDED. Each item: `id, solution_id, route_id, vehicle_license_plate, driver_id, driver_name, sender_name, sent_at, status`. **No delivery date.** | `RouteSendService.mySends`, `dto/response/RouteSendDTO.java` | VERIFIED (static) |
| `GET /api/v1/driver/routes/{sendId}` returns `{send_id, solution_id, route_id, vehicle_license_plate, status, stops[]}`. Stops are in plan order (start depot first, end depot last, `planRank`). Each stop: `sequence_number, type (ORDER/DEPOT), order_id, location_name, address, latitude, longitude, arrival_time, departure_time, customer_name, customer_phone, delivery_notes, demand`. | `RouteSendService.sendDetail`, `dto/response/DriverStopDTO.java` | VERIFIED (static) |
| Another driver's send → **403** (`ForbiddenException`). A missing send → **400** `RESOURCE_NOT_FOUND` (`DataInvalidException`). Missing or expired token → 401 (`JwtAuthenticationEntryPoint`). | `RouteSendService.sendDetail`, `GlobalExceptionHandler` | VERIFIED (static), entry point not re-read |

## 2. Contract changes coming from LEZ-001 P1b (`work-items/LEZ-001/task-opencode-p1b.md` N8)
- `DriverStopDTO` gains `geometry_to_next` (encoded polyline, precision 1e5, `[lat, lon]`), `instructions_to_next` (list of `{text, distance_m, time_ms, sign, street_name}`) and `zone_names`.
- Instructions and geometry are filled **at send time**, so every route sent after P1b carries them. Routes sent earlier do not.
- Status: planned, **NOT_VERIFIED**. The app must tolerate their absence (DA-R04 fallback).

## 3. The web driver view today (behaviour to match, one thing to avoid)
- Same list and detail as above: superseded banner, next-stop card, full stop list, tap-to-call, follow-my-location with a permission explanation, "Route guidance" with current step plus the full list. Source: `GVRP_Entry_client_v2/src/app/features/driver/driver-view/driver-view.component.html`.
- Path and steps come from **public OSRM** (`shared/services/routing.service.ts`). The app must not copy this (DA-R04).
- **Each stop has a "Navigate" link to Google Maps** (`googleMapsUrl(stop)`). With low-emission zones, this can lead a driver through a banned zone. The app excludes it (DA-R08). **The web view must drop or guard it in LEZ-001 P1c.** Reported to LEZ-001 as a P1c requirement.

## 4. Environment notes
- The user develops Flutter in Android Studio on Windows with a Pixel 8 emulator.
- The emulator reaches the host's `localhost:8080` as `10.0.2.2:8080`.
- Android 9+ blocks cleartext HTTP by default. A debug-only network security config is needed for the dev hosts (DA-R10).
- OpenStreetMap's public tile servers have a usage policy that forbids heavy or production use. Fine for development; production needs a provider (excluded, DA §4).

## 5. Parallel work with LEZ-001 (process)
- LEZ-001's run scope includes `GVRP_Entry_API/src`, `GVRP_Engine_API/src`, `GVRP_Entry_client_v2/src` and `GVRP_Document`. The app must not touch them while LEZ-001 is open (L-2026-005).
- LEZ-001 P1b's Step 0 stops on untracked files outside an allow-list, and both efforts share one working tree. The app is therefore developed in a **separate git worktree** on its own branch (`feature/driver-app-001` from `master`), with its own run (`work-items/DRIVER-APP-001/run.json`, scope `GVRP_Driver_app`).
- The backend for manual testing runs from the main tree (branch `feature/lez-001`).
- `AGENTS.md` §3 does not list a driver app component yet. Adding it is a rule-file change and needs user approval.

## 6. Unanswered decisions carried to design
See `request.md` §7. None blocks design.
