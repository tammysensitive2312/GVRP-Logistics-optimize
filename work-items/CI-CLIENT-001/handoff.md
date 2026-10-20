# Handoff — CI-CLIENT-001

## Exact version under test

| Item | Value |
|---|---|
| Run | `CI-CLIENT-001` (ledger revision 4, node `verify`) |
| Repository / root | `GVRP-Logistics_Optimize` |
| Branch | `fix/ci-client-001` |
| Base commit | `21d280565d7375a8e12b8f0b4285d9269554479e` (`master`, short `21d2805`) |
| Working tree | **dirty by design** — the two authorised files below are uncommitted. Nothing has been committed, pushed or merged. |

Scoped files (the ledger records a fingerprint of these at this transition; the hash of this handoff
document is printed by the transition command and quoted in the message presenting this handoff):

| File | Size | SHA-256 |
|---|---|---|
| `GVRP_Entry_client_v2/package.json` | 1,702 B | `078CD001E080669D40CC0BFE74885A181600504809717527915600D8EA0F6C43` (**unchanged** — same as `HEAD`) |
| `GVRP_Entry_client_v2/package-lock.json` | 553,371 B | `07301D3BDA64A113C5B2167CA5DDB32D1AEE4F4B152948D8B45665915B71A80D` (regenerated) |
| `GVRP_Entry_client_v2/src/app/features/auth/login/login.component.scss` | 7,151 B | `B302E47DB2BE44984A412C5B9B83C65E20A3165363D08EFE50697757764A235A` (rewritten) |

Also produced (not product code): `work-items/CI-CLIENT-001/{implementation.md,verification.md,traceability.md}`.

## What the user must test

Environment: Node 20 or 22 with npm 10 (Node v22.23.2 / npm 10.9.8 was used here). Run from
`GVRP_Entry_client_v2`.

1. **Lock file (CCI-001).**
   `Remove-Item -Recurse -Force node_modules` then `npm ci`
   → **Expected: exit 0**, no "package.json and package-lock.json are not in sync" error.
   (Warning output and an audit summary are normal.)
2. **Build (CCI-002).**
   `npm run build`
   → **Expected: exit 0**, and **no** `login.component.scss` line in the ERROR set.
   The login style line will still appear as a **warning**: *Budget 4.10 kB was not met by 2.09 kB with a
   total of 6.19 kB* — this is expected and accepted (see Limitations).
3. **Unit tests (CCI-004).**
   `npm test -- --watch=false --browsers=ChromeHeadless`
   → **Expected: exit 1** with `TOTAL: 5 FAILED, 240 SUCCESS` and the five known names
   (`3× CollectionCache`, `FleetSetupComponent keeps the vehicle when the confirmation is dismissed`,
   `NavbarComponent navigates when a tab is clicked`). Exit 1 is pre-existing; the CI job `client-test` is
   `continue-on-error`.
4. **Login screen (CCI-003) — the visual test.**
   `npx ng serve` and open the login page (no backend needed to render it).
   Compare against the pre-change version for: default state (disabled login button, grey `#999`),
   blur the branch field empty so the **error** border + message appear, focus the **password** field
   (blue border + soft ring, eye toggle still positioned over the field),
   and a filled state (login button turns blue/enabled).
   Check the three layouts by resizing: wide (>968 px, two columns), ≤968 px, and ≤480 px (phone).
   → **Expected: pixel-for-pixel identical to the old stylesheet.** Evidence: 15 before/after captures
   (1280/900/400 px × default / username-focus / password-focus / error / filled) with **0 computed-style
   differences and 15/15 byte-identical PNGs**; screenshots are in
   `%TEMP%\opencode\cci001-d3-before\` and `%TEMP%\opencode\cci001-d3-after\` (not committed).
5. **Optional, and the only thing this machine cannot check (CCI-005).**
   After an authorised commit + push, the GitHub Actions run of `Client v2 (build)` should be green.
   **This was not executed here** because committing and pushing are explicitly forbidden by the work item.

## Expected results — summary

| Requirement | Expected |
|---|---|
| CCI-001 | `npm ci` → exit 0 |
| CCI-002 | `npm run build` → exit 0, no login ERROR (6.19 kB vs the 8.19 kB error budget) |
| CCI-003 | Login screen visually unchanged (byte-identical rendering in 15 states) |
| CCI-004 | Same 5 known failures, nothing new; `package.json`, `angular.json`, specs untouched |
| CCI-005 | CI green after an authorised push — **unverifiable locally** |

## Known limitations (please read before accepting)

1. **The 4.10 kB warning for `login.component.scss` remains** (6.19 kB total, 2.09 kB over the warning
   budget). Only the error threshold is cleared. Removing the warning is impossible without changing or
   deleting declarations — the bytes inside the declaration blocks alone are 4,815 B — and design D2 §4
   forbids touching values. Accepted on the user's explicit instruction (2026-10-19); design §43 lists
   < 4 kB as optional.
2. **The design's "aim ≤ 6 kB" was missed by 216 B** (6,216 B compiled). Further reduction would require
   either declaration changes or a flattening that risks losing to the Material global rules
   (`.mat-typography h1/h2/p`).
3. **Uncommitted working tree.** Results apply to the exact file hashes above. Any later edit invalidates
   this handoff and needs re-verification (the ledger blocks acceptance if scoped files changed).
4. **Single browser engine.** Chromium 155 only, at page zoom 0.9; the three widths were emulated as
   1280/900/400 × 800. No Firefox/WebKit run.
5. **Animation timing curves** were not compared frame-by-frame (still frames + paused/awaited animations
   only).
6. **Design diagrams (`.puml`)** were not produced for this item; recorded as a documentation gap in
   `verification.md` §6.
7. **Operational note.** During implementation, `npm ci` required deleting `node_modules`, which was locked
   by the user's running dev server on port 4200. That server was stopped and restarted identically
   (`npm run start`; log `%TEMP%\opencode\cci001-restart-user-server.log`); it is running now (HTTP 200).

## What has explicitly NOT been done

- No commit, no push, no merge, no history rewrite (forbidden by the work item).
- No change to `angular.json` budgets, `.github/`, other stylesheets, templates, TypeScript or i18n files.
- No spec/test file was edited, disabled, focused or skipped; no dependency version changed.
- No deployment or publication of any kind. Acceptance of this handoff does not authorise deployment.
