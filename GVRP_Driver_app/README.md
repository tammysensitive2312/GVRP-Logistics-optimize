# GVRP_Driver_app

Flutter driver app (Android first) for GVRP Logistics Optimize. Work item:
`work-items/DRIVER-APP-001/` (`design.md`, `ui-design.md`).

The project was created with
`flutter create --org org.truong --project-name gvrp_driver --platforms android .`.

What it does: a driver logs in, sees the routes sent to them, and follows one
route on a map (Google tiles, as in the web client) with the server's path and turn instructions.
It is a read-only client of the Entry API driver endpoints. It never calls a
router and never hands navigation to another app. Only the access token is
stored on the device (Android Keystore through `flutter_secure_storage`).

## Prerequisites

- Flutter **3.47.8** (stable, Dart 3.13.7). Check with `flutter --version`.
- Android SDK with platform tools and NDK 28.2.13676358 (installed from
  Android Studio's SDK Manager). `flutter doctor` must show the Android
  toolchain as OK.
- An Android emulator (developed with a Pixel 8 image) or an Android phone.
- The Entry API running on port 8080. It runs from the **main worktree**
  (`E:\OR-tools java example\GVRP-Logistics_Optimize`), not from this one.
  See that worktree's `CLAUDE.md` for how to start it. Use a DRIVER account
  of your local test data; no credentials are kept in this repository.

## Run

From `GVRP_Driver_app/`:

```
flutter pub get
```

### Emulator

The emulator reaches the host's `localhost:8080` as `10.0.2.2:8080`. Debug
builds may talk plain HTTP to that address only.

```
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

### Physical Android phone (same Wi-Fi as the machine running the API)

1. **Phone:** Settings → About phone → tap "Build number" 7 times to enable
   Developer options. Then Settings → System → Developer options → turn on
   **USB debugging** (and, for Android 11+, optionally **Wireless debugging**).
2. **Connect** the phone by USB and accept the "Allow USB debugging?" prompt
   on the phone. Check that it is listed:

   ```
   flutter devices
   ```

3. **LAN IP:** find the IPv4 address of the Windows machine on the Wi-Fi
   (`ipconfig`, adapter "Wireless LAN adapter Wi-Fi", for example
   `192.168.1.23`).
4. **Allow cleartext to that IP (debug only):** in
   `android/app/src/debug/res/xml/network_security_config.xml`, uncomment the
   placeholder `<domain>` line and replace `192.168.X.Y` with the LAN IP. Do
   not commit a real address.
5. **Firewall:** allow inbound TCP 8080 on Windows (once, in an
   administrator PowerShell):

   ```
   New-NetFirewallRule -DisplayName "GVRP Entry API 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```

   The Wi-Fi network profile must be "Private". Check from the phone's
   browser that `http://<LAN-IP>:8080/api/v1/driver/routes` answers (a 401
   page is fine: it means the API is reachable).
6. **Run** (installs a debug build and keeps hot reload):

   ```
   flutter run -d <device-id> --dart-define=API_BASE_URL=http://<LAN-IP>:8080
   ```

   Or build an APK and install it without keeping the cable connected:

   ```
   flutter build apk --debug --dart-define=API_BASE_URL=http://<LAN-IP>:8080
   adb install -r build/app/outputs/flutter-apk/app-debug.apk
   ```

   The APK can also be copied to the phone and opened there (allow "Install
   unknown apps" for the file manager).

A release build cannot talk to `http://` (no cleartext), so use a debug
build against a LAN backend.

### Release builds

A release build needs `--dart-define=API_BASE_URL=https://…`. Without it the
app shows a configuration error screen and makes no network call. Release
builds allow no cleartext HTTP.

## Server path and instructions

The map draws the path and the guidance card shows turn instructions only for
routes **sent after LEZ-001 P1b** is running on the backend (fields
`geometry_to_next`, `instructions_to_next`, `zone_names`). Older routes show
a dashed straight line labelled "Đường đi chưa có từ máy chủ (chỉ minh hoạ)"
and "Chưa có hướng dẫn từ máy chủ cho chặng này." That is expected.

Map tiles come from the same Google tile endpoint as the web client
(`mt1.google.com/vt`). This endpoint is undocumented, and using it outside
Google's SDKs or APIs is against the Google Maps Platform terms. It is for
development and testing only and must be replaced before any release
(`work-items/DRIVER-APP-001/ui-design.md` §8.5).

## Checks

```
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Tests need no device, emulator or network. The test IDs (DT-01 … DT-13) are
listed in `work-items/DRIVER-APP-001/design.md` §8.

## Layout

```
lib/
  main.dart, app.dart          bootstrap, theme, AuthGate, configuration error screen
  config/env.dart              API_BASE_URL
  l10n/strings_vi.dart         all UI strings (Vietnamese)
  theme.dart, widgets/         visual tokens and shared widgets
  core/api/                    HTTP client and typed errors
  core/auth/token_store.dart   secure token storage
  core/geo/polyline.dart       encoded polyline decoder (local code)
  core/log/app_logger.dart     logger: method, path template, status only
  features/auth/               login and session
  features/routes/             models, repository, list and detail screens, tel: caller
  features/map/route_map.dart  flutter_map rendering
  features/guidance/           instruction card and steps sheet
  features/location/           location permission and stream
```
