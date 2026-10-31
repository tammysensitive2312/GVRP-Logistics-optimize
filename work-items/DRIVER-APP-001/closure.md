# Closure — DRIVER-APP-001 (Flutter driver app, MVP + UI revision 2)

Closed: 2026-10-31, at the user's request: "xác nhận nghiệm thu bản mới, thêm lessons rồi đóng run đi".

## Outcome

- Delivered: `GVRP_Driver_app/`, an Android Flutter driver app.
  - It is a read-only client of the Entry API driver endpoints, with no backend change.
  - Specs: `design.md` §2–§7 and `ui-design.md`, including revision 2 (§8).
- Accepted by the user for the handoff `207d7b5b32d48547f6e27fb7f478c473e5b9137f21da3c2f60272111fbf9a4a2` (`user-acceptance-rev12.json`, run rev 13).
  - The earlier acceptance of handoff `0a0c43a2…` (rev 9) was superseded by a test-only change (`findings-test-values.md`).
- Evidence:
  - `implementation.md`, `verification.md`, `handoff.md`;
  - `findings-ui-rev2.md`, `findings-test-values.md`;
  - `traceability.md`;
  - the run ledger `run.json` (proofs `01`–`14`).
- Code: branch `feature/driver-app-001`, commit `1d784f0` (app and evidence up to rev 12). A follow-up commit holds the closure files and the lessons.
- PR into `master`: to be opened by the user on GitHub, because the GitHub CLI is not installed on the machine. The PR text was prepared in the session.

## Verification summary

- `flutter analyze`: clean.
- `flutter test`: 54/54 passed (DT-01…DT-13 plus supporting tests).
- Debug APK builds.
- Emulator session by the agent; physical phone (Samsung SM-A165F) by the user.
- Not verified on a device: pinch and double-tap zoom, the location permission flow, a placed call, superseded and zone screens. See `verification.md`.

## Knowledge updates (AGENTS.md §16)

| File | Change |
|---|---|
| `docs/engineering/LESSONS.md` | Added L-2026-015 (Flutter Android toolchain on Windows), L-2026-016 (Riverpod 3 retry and overrides), L-2026-017 (widget tests with sheets and flutter_map), L-2026-018 (local-only edits in a run's scope change the handoff). The user approved adding lessons on 2026-10-31. These IDs may collide with lessons added later on the LEZ-001 branch: renumber on merge if so |
| `AGENTS.md` §3 | `GVRP_Driver_app` entry, added 2026-10-27 with user approval |
| `CLAUDE.md` "Build and test" table | **Not updated.** It does not list `GVRP_Driver_app` (`flutter analyze`, `flutter test`). This is a rule file and needs the user's explicit approval. Proposed row: `GVRP_Driver_app` → `flutter analyze`, `flutter test`, `flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080` |
| `docs/security/SECURITY_CHECKLIST.md` | **Not updated by this run.** The user reported during this session a cross-branch exposure in the web client: a solution of Branch A stays visible after logging into Branch B. It is outside this run's scope and is proposed as its own work item (session card "Web client: stale solution leaks across branch logins"), which must record the finding |
| `docs/research/KNOWN_ISSUES.md` | Not updated. The wrong-password 500 in the Entry API is still NOT_VERIFIED (static reading only) |
| `docs/research/PROJECT_STATE.md` | Not affected: no research claim |

## Open follow-ups (not part of this run)

1. **Release blocker:** replace the undocumented Google tile endpoint with a licensed source or the official SDK before any release (`ui-design.md` §8.5).
2. Re-check the app against real LEZ-001 P1b responses once P1b is merged: `zone_names`, the `sign` codes, routes with partial geometry.
3. Entry API: does a wrong password return 500 (`BadCredentialsException`)? If confirmed, it needs a backend work item.
4. CI job for `GVRP_Driver_app` in `.github/workflows/ci.yml` (`flutter analyze`, `flutter test`). This is a rule/config change that needs user approval.
   - **Done 2026-10-31**, approved by the user ("tôi nghĩ bạn có thể làm"): job `driver-app` with `subosito/flutter-action@v2`, Flutter 3.47.8 stable, `flutter pub get --enforce-lockfile`, `flutter analyze`, `flutter test`. The same commands passed locally (analyze clean, 54/54). Not yet verified on GitHub Actions.
5. The web client cross-branch exposure: see the session card above.

User acceptance covers user testing only. It does not authorize deployment or publication.
