# Task for the engineer — DRIVER-APP-001 (Flutter driver app, MVP)

You are `gvrp-engineer` (or a Claude Code session acting as the engineer). **Work only inside the worktree `E:\OR-tools java example\GVRP-driver-app`** (branch `feature/driver-app-001`), **never** in `E:\OR-tools java example\GVRP-Logistics_Optimize`: LEZ-001 is running there. Implementation must be authorized by the user before you start; the user gives you this task only after authorizing it. Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the worktree root unless a step says otherwise.

## Read first
1. `work-items/DRIVER-APP-001/request.md`
2. `work-items/DRIVER-APP-001/context.md`
3. `work-items/DRIVER-APP-001/design.md`: the specification, with the test table in §8
4. `docs/engineering/LESSONS.md`: L-2026-003, -004, -010 (lock file tracked), -013, -014

If the design is wrong or impossible, stop and report. Do not redesign.

## Step 0 — preconditions
1. Run `git -C . rev-parse --abbrev-ref HEAD`. It must print `feature/driver-app-001`; otherwise stop.
2. Run `node tools/session-brief.mjs` and include the output. An open `LEZ-001` entry there is expected; it belongs to the other worktree. Do not touch it.
3. Run `git status --short`. Only `work-items/DRIVER-APP-001/` and `GVRP_Driver_app/README.md` may be untracked. Otherwise stop and report.
4. Run `flutter --version` and `flutter doctor -v`. Record the Flutter and Dart versions and the Android toolchain status. If the Android toolchain is not OK, stop and report.

## Step 1 — workflow ledger (before creating the project)
```
node tools/feature-workflow/workflow.mjs init work-items/DRIVER-APP-001/run.json . DRIVER-APP-001 GVRP_Driver_app
node tools/feature-workflow/workflow.mjs step work-items/DRIVER-APP-001/run.json ready work-items/DRIVER-APP-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DRIVER-APP-001/run.json ready work-items/DRIVER-APP-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DRIVER-APP-001/run.json ready work-items/DRIVER-APP-001/03-proof.json
```
Each command must print a new state: `discover`, `design`, `implement`. Check that `run.json` `root` is the worktree's Windows path (L-2026-014). On failure, stop and report.

## Step 2 — create the project and add dependencies
From `GVRP_Driver_app/`:
```
flutter create --org org.truong --project-name gvrp_driver --platforms android .
flutter pub add flutter_riverpod http flutter_secure_storage flutter_map latlong2 geolocator url_launcher
flutter pub add --dev mocktail
```
- Keep the existing `README.md`, and extend it with run instructions (§README below).
- For **every** added package, record in `implementation.md` the exact resolved version from `pubspec.lock`, the pub.dev publisher, and the latest-release date you checked (AGENTS.md §15: exists, maintained, no look-alike name).
- `pubspec.lock` stays tracked (application, L-2026-010 spirit).
- Run `flutter test` once on the generated project and record the result as the baseline.

## Step 3 — implement (design §2–§7)
Implement exactly the layout of design §2, the client model of §4, the screens and behaviour of §5, the API client and errors of §6, and the Android specifics of §7:
- `config/env.dart`: `const String.fromEnvironment('API_BASE_URL')`. Debug default `http://10.0.2.2:8080`. A release build without it shows a configuration error screen and makes no network call.
- `android/app/src/debug/res/xml/network_security_config.xml`: cleartext for `10.0.2.2` only, plus a commented placeholder for one LAN IP. Reference it **from the debug manifest only** (`android/app/src/debug/AndroidManifest.xml`). The main and release manifests keep the platform default.
- Permissions: `INTERNET` (all builds), `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
- JSON: snake_case keys exactly as in `context.md` §1 (`access_token`, `send_id`, `vehicle_license_plate`, `geometry_to_next`, `instructions_to_next`, `zone_names`, `distance_m`, `time_ms`, `street_name`, …).
- `core/geo/polyline.dart`: Google encoded polyline **decode**, precision 1e5, `[lat, lon]`. Write it yourself, with no package.
- UI strings: Vietnamese, all in `l10n/strings_vi.dart`, wording as in design §5 where given.
- `url_launcher` is called **only** with `Uri(scheme: 'tel', path: phone)`.

## README
Add to `GVRP_Driver_app/README.md`:
- prerequisites (the Flutter version you used);
- `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080` for the emulator, and the LAN-IP variant for a phone (with the debug network-config step);
- that the backend runs from the main worktree;
- that server paths and instructions appear only for routes sent after LEZ-001 P1b.

**No credentials** in the README.

## Tests (design §8: DT-01 … DT-13, new files under `GVRP_Driver_app/test/`)
- No device, emulator or network: mock HTTP (`http` `MockClient`) and secure storage (a fake `TokenStore`).
- DT-12 is a test that scans `lib/` for `geo:`, `google.navigation`, `maps.google`, `comgooglemaps`, `maps.app.goo.gl` and fails if any is found.
- DT-13 captures log output (the app's own logger) during an API error and asserts that no body, token, name, phone or coordinate appears.

## Allowed changes
- Everything inside `GVRP_Driver_app/` (generated project, your code, tests, README).
- `work-items/DRIVER-APP-001/implementation.md` (new) and `work-items/DRIVER-APP-001/checkpoint-*.json`.

## Forbidden
- Anything outside `GVRP_Driver_app/` and `work-items/DRIVER-APP-001/`. In particular: `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `AGENTS.md` (Claude already added the §3 entry with the user's approval, 2026-10-27 20:40), `.github/`, `docs/`.
- Any router or navigation call: OSRM, Google Directions, `geo:`/Maps intents. No Google Maps SDK, no API keys.
- Analytics, crash-reporting or any SDK that sends data off the device.
- Persisting customer data (no database, no shared preferences for route data). Only the token, in secure storage.
- Test accounts, passwords or tokens in any file.
- Committing, pushing, rewriting history. Touching the other worktree.

## Step 4 — verification
From `GVRP_Driver_app/`:
1. `flutter analyze`: 0 errors. List any warnings.
2. `flutter test`: all green. Report the counts and the test names per DT id.
3. `flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080`: it must succeed. Report the APK path. Do not commit build output; check that `.gitignore` covers `build/`.
4. Optional, if an emulator is running and the user has started the Entry API from the main worktree: open the app and do the login screen only, without using real credentials in your report. Otherwise write "not exercised".

## Step 5 — record and transition
Write `work-items/DRIVER-APP-001/implementation.md` with:
- Scope
- Environment (Flutter, Dart, Android SDK)
- Baseline
- Dependencies (table: package, version, publisher, last release)
- Changes Made
- Commands Executed (with exit codes, including Steps 0–2)
- Verification Results
- Modified Tests ("none": all tests are new)
- Security-Relevant Changes (cleartext scope, permissions, storage, logging)
- Deviations From Design
- Remaining Uncertainty
- Lesson Candidates
- Handoff to Reviewer

Then:
```
node tools/feature-workflow/workflow.mjs step work-items/DRIVER-APP-001/run.json ready work-items/DRIVER-APP-001/04-proof.json
```
It must print state `verify`. Do not go further. Tell the user you are done.
