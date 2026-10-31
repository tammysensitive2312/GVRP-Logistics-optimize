# Verification — DRIVER-APP-001 (verify rev 7)

Date: 2026-10-31. Worktree `E:\OR-tools java example\GVRP-driver-app`,
branch `feature/driver-app-001` @ `b057218`. Changes since `b057218` are uncommitted:
- `GVRP_Driver_app/` and `work-items/DRIVER-APP-001/` (both untracked);
- the approved `AGENTS.md` §3 entry.

Environment: Flutter 3.47.8, Dart 3.13.7, Android SDK platform 37 / build-tools 36, Windows 11.

Status words follow AGENTS.md §5.

## Automated checks (run from `GVRP_Driver_app/`)

| Check | Result |
|---|---|
| `flutter analyze` | 0 errors, 0 warnings, 0 infos ("No issues found!") |
| `flutter test` | **54 passed**, 0 failed, 0 skipped (`+54: All tests passed!`) |
| `flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080` | Success; APK under `build/` (gitignored) |
| Merged debug manifest | Permissions: INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, plus AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; `networkSecurityConfig` present in debug only |

The last `flutter analyze` and `flutter test` ran after the debug network config was put back to its placeholder (see below).

## Requirement coverage

Per-ID test names are in `implementation.md` § Verification Results, and the revision 2 additions in § Revision 2.

| Requirement | Automated evidence | On-device evidence | Status |
|---|---|---|---|
| DA-R01 login, DRIVER only, token, 401 → login | DT-03, DT-04, DT-05 (3) | Session kept across reinstall on the emulator; user logged in on the emulator and on a phone | PARTIALLY_VERIFIED (login screen not driven by the agent) |
| DA-R02 My routes | DT-06 (2), empty/error tests | Emulator: 3 sends, server order, new card style | VERIFIED for CURRENT; SUPERSEDED rendering by test only |
| DA-R03 detail, next stop, tap-to-call | DT-07, DT-11 | Emulator: next-stop card with phone link; call not placed | PARTIALLY_VERIFIED |
| DA-R04 server geometry only, labelled fallback | DT-01 (4), DT-08 (4) | Emulator: P1b geometry drawn for all legs on route 30H-… (so no fallback on device) | VERIFIED (geometry), fallback by test only |
| DA-R05 guidance from server instructions, local leg index | DT-09, leg-bound test | Emulator: "TIẾP THEO" with server instruction, "Bước 1/18"; "Quay lại" disabled at leg 0 | VERIFIED |
| DA-R06 zone awareness | DT-10 (2) | No zone data on the test account | PARTIALLY_VERIFIED |
| DA-R07 location explained, never sent | Widget test (explanation, then denial) | Not exercised on a device | PARTIALLY_VERIFIED |
| DA-R08 no external navigation | DT-11, DT-12 (3) | No such control on screen | VERIFIED |
| DA-R09 in-memory data, safe logs, Vietnamese errors | DT-13, error-mapping tests | — | VERIFIED (by test) |
| DA-R10 base URL per build, cleartext debug-only | Env tests, merged-manifest check | Emulator via `10.0.2.2`; phone via a LAN IP (user) | VERIFIED |
| DA-R11 Vietnamese UI | All widget tests use `S.*` | Emulator screens all Vietnamese | VERIFIED |
| UI rev 2 (`ui-design.md` §8): web style, Google tiles, zoom controls | 2 zoom tests | See the emulator session list below | VERIFIED except pinch and double tap |

## On-device sessions

**Emulator** `emulator-5554` (`Medium_Phone_API_37.0`, 1080 × 2400), Entry API on the host. The agent used `adb input` and `screencap`, with the user's own logged-in session. Checked:
- My routes list;
- route detail with Google tiles, P1b geometry and arrows;
- "+" twice, which zoomed in 2 levels;
- one-finger pan;
- "Xem cả tuyến";
- sheet dragged up and down;
- the expanded stop list;
- back navigation.

Two defects found there were fixed and re-checked on the emulator:
1. The fit and attribution inset did not follow the sheet.
2. The navigation bar overlapped the end of the sheet list.

**Physical phone** Samsung SM-A165F (USB, debug build against the host's LAN IP), tested by the user on 2026-10-31. The user reported "Tôi test app trên điện thoại oke rồi". The agent did not observe this session.

## Change made during verification

`GVRP_Driver_app/android/app/src/debug/res/xml/network_security_config.xml`:
- Before: the user had uncommented the LAN `<domain>` line with their machine's IP, for the phone build.
- The user decided on 2026-10-31 to put back the commented `192.168.X.Y` placeholder ("Trả về placeholder"), so the handed-off and committed version holds no real address.
- After: placeholder restored. Re-run results: `flutter analyze` clean, `flutter test` 54/54. Debug builds again allow cleartext to `10.0.2.2` only, until the user re-adds a LAN IP locally.

## Not verified / limitations

- Pinch and double-tap zoom on a device: `adb input` has no multi-touch.
- Login screen driven on a device by the agent.
- Location permission flow, the `tel:` launch, superseded and zone screens on a device.
- Real P1b responses beyond this one account's routes. The `sign` mapping assumes GraphHopper codes.
- Wrong password: the backend probably returns 500, so the app shows the connection message (NOT_VERIFIED, backend behaviour).
- Release build: not built. The configuration-error path is covered by tests only.
- **Google tiles are for development and testing only:** release blocker, `ui-design.md` §8.5.

## Open decisions for the user (AGENTS.md §14)

1. The generated template test `test/widget_test.dart` was removed (see `implementation.md` § Modified Tests).
2. The dummy values in tests (`fake-token-for-tests`, `not-a-real-password`, invented phones and coordinates).

Neither blocks user testing. Both must be decided before closure.

---

## Re-verification after the test-value change (2026-10-31)

Change: test files only (`findings-test-values.md`, `implementation.md` § Test values). No change to `lib/`, Android or dependencies, so the build and on-device results above still apply to the app code.

| Check | Result |
|---|---|
| `grep` for the former literals (`fake-token-for-tests`, `not-a-real-password`, `taixe_thu`, `Chi nhánh Thử`) in `test/` and `lib/` | No match |
| `flutter analyze` | No issues found |
| `flutter test` | 54 passed, 0 failed, 0 skipped |

Open decisions §14: both resolved by the user on 2026-10-31. The template test removal is approved, and the credential literals were replaced by values generated at run time.
