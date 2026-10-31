# Engineering Lessons

How to work in this repository without repeating past mistakes: build, test,
CI, tooling, workflow and delegation traps.

Scope boundary:

* Defects and scientific hazards of the GVRP system itself go to
  `docs/research/KNOWN_ISSUES.md`.
* Security findings go to `docs/security/SECURITY_CHECKLIST.md`.
* Facts true only on one machine (paths, JDK location, local ports in use) go
  to the user-level agent file (`~/.config/opencode/AGENTS.md`), not here.

Rules (AGENTS.md section 16):

* Add an entry when a work item hit a non-obvious trap that the next agent
  would hit again. Do not add what the code or a command already shows.
* Every entry has a date and a source (work item, file or user message).
* Never delete an entry. When it no longer applies, set
  `Status: SUPERSEDED by L-YYYY-NNN` or `Status: OBSOLETE — <reason, date>`.
* Next free ID: take the highest existing number plus one.

Entry format:

```text
## L-YYYY-NNN — <short title>
Date: YYYY-MM-DD
Source: <work item / file / user message>
Applies to: <modules, commands or situations>
Status: ACTIVE
Lesson: <what to do or avoid, and why>
```

---

## L-2026-001 — CI has no MySQL and no GraphHopper graph
Date: 2026-10-16
Source: `.github/workflows/ci.yml`; work-items/SEC-TENANT-001, SEC-ROLE-001
Applies to: every new Java test
Status: ACTIVE
Lesson: New tests must run without a database or the `.data/*.osm.pbf` graph
(Mockito, `@WebMvcTest`, plain unit tests). `@SpringBootTest` classes are
excluded by name in `ci.yml`; adding a class to that list is a test-exclusion
change and needs user approval (AGENTS.md section 14). If a DB-free test is
impossible, stop and report instead of excluding it.

## L-2026-002 — Spring Data turns a null parameter into IS NULL
Date: 2026-10-16
Source: work-items/SEC-TENANT-001/closure.md
Applies to: derived queries such as `findByIdAndBranchId` used for tenant scoping
Status: ACTIVE
Lesson: A null branch argument matches rows whose branch column is NULL. Such a
query is fail-closed only when the column is NOT NULL (true for
`depots.branch_id`). For a nullable column, reject a null branch explicitly
before querying.

## L-2026-003 — Report a clean build, not an incremental one
Date: 2026-10-16
Source: work-items/SEC-TENANT-001/closure.md ("no clean build" gap)
Applies to: Maven verification in Entry API and Engine API
Status: ACTIVE
Lesson: Classes compiled earlier by the IDE in `target/` can hide compile or
test failures. Verification evidence must come from `mvnw.cmd -B clean test`.

## L-2026-004 — Check test libraries before specifying a test
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md
Applies to: task specs for security or web-layer tests
Status: ACTIVE
Lesson: `spring-security-test` was missing from `GVRP_Entry_API/pom.xml`; the
spec had to allow exactly that dependency. Read the pom before writing the test
table. On Spring Boot 3.4+ use `@MockitoBean`; `@MockBean` is deprecated
(follow-up open in `RoleAccessTest`).

## L-2026-005 — Freeze run scope between `pass` and `finish`
Date: 2026-10-16
Source: Claude session 2026-10-16 (an edit to hashed `traceability.md` had to be reverted); `tools/feature-workflow/workflow.mjs`
Applies to: business-development workflow runs
Status: ACTIVE
Lesson: `accepted` and `finish` re-check the handoff hash, and evidence files
are hashed when a transition records them. Editing a scoped file or recorded
evidence after `pass` breaks the run. Put later facts in `closure.md` and make
post-closure edits after `finish`.

## L-2026-006 — Workflow commands are run by the engineer or one closing script
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md (friction), SEC-SESSION-001/closure.md
Applies to: Claude → OpenCode delegation (`/delegate`)
Status: ACTIVE
Lesson: Claude may have file tools but no shell on the user's machine. Relaying
individual commands through the user was the main friction in the first trial.
The engineer runs Steps 0–4 including workflow commands; closing is one
`close-run.ps1`.

## L-2026-007 — Check JAVA_HOME before running Maven
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md (engineer reported a `JAVA_HOME` issue)
Applies to: any agent running `mvnw.cmd`
Status: ACTIVE
Lesson: Both APIs need JDK 21. When Maven fails before compiling, check that
`JAVA_HOME` points to a JDK 21 install first. The actual path is machine-specific
and belongs in the user-level agent file.

## L-2026-008 — Name long-running test exclusions up front
Date: 2026-10-18
Source: work-items/PARETO-001
Applies to: GVRP_Engine_API full test runs
Status: ACTIVE
Lesson: The full Engine suite takes more than an hour because benchmark tests
use real matrices. A task spec must name any test to be skipped in local
verification and get user approval for each, instead of letting the engineer
decide mid-run.

## L-2026-009 — Normalize line endings before byte comparison
Date: 2026-10-18
Source: work-items/PARETO-001
Applies to: reviews that prove existing tests were not modified
Status: ACTIVE
Lesson: With `core.autocrlf`, files can differ only in CRLF/LF. Normalize line
endings before comparing, and report a line-ending-only diff as such, not as a
test modification.

## L-2026-010 — Keep `package-lock.json` tracked and in sync
Date: 2026-10-19
Source: work-items/SEC-TENANT-001/closure.md, PARETO-002, CI-CLIENT-001
Applies to: GVRP_Entry_client_v2 dependency changes
Status: ACTIVE
Lesson: CI runs `npm ci`, which fails when the lock file is missing or out of
sync with `package.json` (a missing transloco entry broke it once). Commit the
regenerated lock file in the same change as the dependency edit.

## L-2026-011 — Stop the dev server before `npm ci`
Date: 2026-10-20
Source: work-items/CI-CLIENT-001
Applies to: GVRP_Entry_client_v2 on Windows
Status: ACTIVE
Lesson: `npm ci` failed while the dev server (`ng serve`, port 4200) was
running; stop it first (on Windows, open files in `node_modules` are the likely
cause, not verified). Five pre-existing Karma failures are tracked as
TEST-CLIENT-001; do not report them as new.
Update 2026-10-23: the full client suite ran 245/245 SUCCESS (fixed by `5cd1ae8`;
source: `work-items/DRIVER-MAP-004/verification-rereview.md`). The five-failure
note no longer holds; the `npm ci` part still applies. TEST-CLIENT-001 closed
2026-10-23 by user decision; `client-test` in `.github/workflows/ci.yml` is now
blocking (no `continue-on-error`).

## L-2026-012 — Verification runs mutate the local database
Date: 2026-10-19
Source: work-items/PARETO-002
Applies to: manual and runtime verification against `gvrp_db`
Status: ACTIVE
Lesson: Running optimization jobs for verification writes solutions and
revisions into the local database. Record which rows a verification created,
and do not compare database totals across runs without accounting for them.

## L-2026-013 — `recover` shows no scope changes without a checkpoint
Date: 2026-10-23
Source: Claude session 2026-10-23 (closing DRIVER-DELIVERY-001, DRIVER-MAP-001, DRIVER-MAP-004); `recover()` in `tools/feature-workflow/workflow.mjs`
Applies to: business-development workflow runs waiting in `await_user_test` or `accepted`
Status: ACTIVE
Lesson: `recover` compares the scope only with the last checkpoint, so a run
without one always reports `changes: []`, even when later commits changed the
scope. Before promising `accepted` or `finish`, compare `snapshot(root, scope)`
with `run.handoff`. If they differ, route `changed` → implement → verify to get
a new handoff, then take acceptance for that handoff.

## L-2026-014 — Workflow and git from the Cowork Linux VM shell
Date: 2026-10-26
Source: Claude session 2026-10-25/26 (LEZ-001 init); `createRun()` in `tools/feature-workflow/workflow.mjs`
Applies to: agents running commands through the Claude desktop VM shell (`$HOME/mnt/GVRP-Logistics_Optimize`)
Status: ACTIVE
Lesson: `init` stores `fs.realpathSync(root)` in `run.json`, so a run created
from the VM records `/sessions/.../mnt/...` and the Windows CLI cannot resolve
it (LEZ-001 has this). Create runs on Windows, or keep using the VM shell for
that run. In the VM, plain `git status` shows every file modified because of
CRLF; use `git -c core.autocrlf=true status`. The CLI deletes `.lock` files,
so the VM shell needs delete permission for the folder first.

## L-2026-015 — Flutter Android toolchain on Windows
Date: 2026-10-31
Source: DRIVER-APP-001 setup, 2026-10-27/28 (`work-items/DRIVER-APP-001/implementation.md` § Setup issues)
Applies to: `GVRP_Driver_app` builds on the Windows dev machine; Android Studio; emulator
Status: ACTIVE
Lesson:
- **cmdline-tools:** `flutter doctor` needs the SDK's cmdline-tools. Install them from Android Studio's SDK Manager.
- **NDK:** install it from the SDK Manager with "Show Package Details". A CLI wrapper split the package name and the build reported "NDK … not found".
- **Run configurations:** run the app with the Flutter plugin's run configuration and the Flutter SDK path set. A Dart command-line configuration fails with `dart:ui` "isn't a type" errors.
- **Cache drives:** when the pub cache (`C:`) and the project (`E:`) are on different drives, Kotlin fails with "Could not close incremental caches". `kotlin.incremental=false` in `android/gradle.properties` fixes it (as the repo has it), and so would a `PUB_CACHE` on the project drive.
- **Emulator under load:** `adb install` during a Gradle build once coincided with a system_server restart. The package was then left without its activity ("Activity class … does not exist"); re-running `adb install -r` fixed it.
- **adb on PATH:** `adb` is not on PATH by default; use `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. With an emulator and a phone attached, pass `-s <serial>`.

## L-2026-016 — Riverpod 3 retries failing providers by default
Date: 2026-10-31
Source: DRIVER-APP-001 (`GVRP_Driver_app/lib/main.dart`, `test/support/test_app.dart`)
Applies to: Flutter code and tests using `flutter_riverpod` 3.x
Status: ACTIVE
Lesson:
- **Retry:** Riverpod 3 retries a provider that throws. Error screens then appear only after the retries, and widget tests wait or see loading states. Set `ProviderScope(retry: (_, _) => null)` in the app and in every test scope when errors must surface at once.
- **Wrapped errors:** errors read through dependent providers arrive wrapped in `ProviderException` (`package:flutter_riverpod/misc.dart`). Unwrap them before mapping to typed errors.
- **Duplicate overrides:** overriding the same provider twice in one `ProviderScope` fails with an assertion. Give the test harness a parameter instead of appending a second override.

## L-2026-017 — Widget tests with bottom sheets and flutter_map
Date: 2026-10-31
Source: DRIVER-APP-001 (`GVRP_Driver_app/test/support/test_app.dart`, `test/features/routes/route_detail_test.dart`, `test/features/map/route_map_test.dart`)
Applies to: Flutter widget tests of screens with a `DraggableScrollableSheet` or `FlutterMap`
Status: ACTIVE
Lesson:
- **Lazy sheet content:** content of a `DraggableScrollableSheet` is built lazily.
  - A button below the visible part is not built, or a tap misses it. Use `ensureVisible` before tapping.
  - After scrolling down, cards at the top are disposed. Jump the sheet's `Scrollable` back to 0 before asserting on them.
  - A smaller initial sheet size can break taps that worked before.
- **Tiles:** keep network tiles out of tests through a provider switch (`mapTilesEnabledProvider`).
- **Reading the camera:** use `MapCamera.of(element)` on an always-built layer such as `PolylineLayer`. Markers outside the viewport are not built, so they make a poor anchor.

## L-2026-018 — Local-only edits inside a run's scope change the handoff
Date: 2026-10-31
Source: DRIVER-APP-001 rev 7–13 (`work-items/DRIVER-APP-001/run.json`, `verification.md`, `findings-test-values.md`)
Applies to: business-development runs whose scope holds machine-specific config (`GVRP_Driver_app` debug network config)
Status: ACTIVE
Lesson:
- **Machine-specific edits:** a developer's machine-specific edit to a scoped file becomes part of the fingerprinted handoff and of the commit. Example: putting their LAN IP into `network_security_config.xml` for a phone build. Restore the placeholder, with the user's decision, before `pass`.
- **Edits after acceptance:** any edit after acceptance, even to tests only, needs `changed` → implement → verify → `pass` and a new acceptance for the new handoff hash.
- **`recover` during builds:** `workflow.mjs recover` reported `Scope cannot be verified: EBUSY` while Gradle held files under `build/`. Run it when no build is running.
