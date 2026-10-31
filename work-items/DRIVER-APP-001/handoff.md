# Handoff — DRIVER-APP-001 Flutter driver app (MVP + UI revision 2)

Date: 2026-10-31. Run: `work-items/DRIVER-APP-001/run.json`. The handoff fingerprint is the `handoff` hash that `workflow.mjs` writes into `run.json` on `pass`.

## Exact version

- **Worktree:** `E:\OR-tools java example\GVRP-driver-app`, branch `feature/driver-app-001`, base commit `b057218`.
- **Uncommitted:** `GVRP_Driver_app/` as it is on disk at the `pass` transition, with the debug network config at its placeholder.
- **Packages:** `pubspec.lock` (flutter_riverpod 3.4.3, http 1.6.0, flutter_secure_storage 11.2.0, flutter_map 8.3.2, latlong2 0.10.1, geolocator 14.1.2, url_launcher 6.3.3; dev: mocktail 1.0.5).
- **Specs:**
  - `design.md` (accepted 2026-10-27);
  - `ui-design.md` (accepted 2026-10-28), plus §8 revision 2 (requested 2026-10-31, `findings-ui-rev2.md`).

## How to run

Prerequisite: the Entry API is running from the main worktree on port 8080.

Emulator, from `GVRP_Driver_app/`:

```
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Physical phone: follow `GVRP_Driver_app/README.md` § "Physical Android phone":
1. USB debugging on the phone.
2. Put the LAN IP in the debug network config, locally only and never committed.
3. Firewall rule for port 8080.
4. Run `flutter run -d <device-id> --dart-define=API_BASE_URL=http://<LAN-IP>:8080`.

## Test steps and expected results

| # | Step | Expected |
|---|---|---|
| 1 | Open the app with no session | Blue login header "GVRP Tài xế"; button disabled until branch, username and password are filled |
| 2 | Log in with a PLANNER account | "Tài khoản này không phải tài xế."; still on login |
| 3 | Log in with a DRIVER account | "Tuyến của tôi" with route cards in server order: plate, "Gửi lúc dd/MM/yyyy HH:mm", "Người gửi" |
| 4 | If an older version exists | Blue info banner about the newer version; red "Phiên bản cũ" badge on the old row |
| 5 | Open a route sent after LEZ-001 P1b | Google map; solid blue legs with arrows; numbered blue pins; green depot; bottom sheet with "Hướng dẫn", the "TIẾP THEO" box ("Bước 1/n") and "Điểm tiếp theo · 1/n" |
| 6 | Open a route sent before P1b | Dashed lines with the legend "Đường đi chưa có từ máy chủ (chỉ minh hoạ)"; "Chưa có hướng dẫn từ máy chủ cho chặng này." |
| 7 | Map controls | "+" / "−" change the zoom by one level; pinch, double tap and drag work; "Xem cả tuyến" shows all stops above the sheet at any sheet height |
| 8 | "Đã đến điểm này", then "Quay lại" | Next stop and guidance move forward one leg, then back; "Quay lại" is disabled on the first leg |
| 9 | Tap the phone number | The phone dialer opens with that number. Nothing else is launched |
| 10 | "Theo dõi vị trí" | Explanation dialog first; after "Tiếp tục" the OS permission prompt; once granted, a blue dot that the map follows. A manual pan turns following off |
| 11 | Open a SUPERSEDED route | Red banner "Đây là phiên bản cũ — không đi theo tuyến này"; no guidance; no leg buttons |
| 12 | "Đăng xuất", or an expired token | Back to login; the expired case shows "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại." |

## Known limitations

- Map tiles come from the undocumented Google endpoint, like the web client. This is **for development and testing only** and a release blocker (`ui-design.md` §8.5).
- Server path, instructions and zone names appear only for routes sent after LEZ-001 P1b.
- Wrong password probably shows "Không kết nối được máy chủ": the backend returns 500 (NOT_VERIFIED).
- Leg progress is local and resets when the route is reopened.
- No offline mode, dark mode or tablet layout (`ui-design.md` §7).
- Not committed yet. Commit, PR and closure need the user's permission.

User test acceptance does not authorize deployment or publication.

## Revision note (2026-10-31)

This handoff replaces the one accepted at run revision 9. The only difference: the test files now generate their token, password and account values at run time (`findings-test-values.md`). App behaviour and the test steps above are unchanged.
