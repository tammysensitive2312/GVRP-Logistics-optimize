# Verification — CI-CLIENT-001

Node: `verify`. Date: 2026-10-19. Run: `work-items/CI-CLIENT-001/run.json` (revision 4).
Branch `fix/ci-client-001` @ `21d2805` **plus uncommitted working-tree changes** (see "Exact version").

## 1. Environment

| Item | Value |
|---|---|
| OS / shell | Windows 10, PowerShell 5.1 |
| Node / npm | v22.23.2 / 10.9.8 (lockfileVersion 3, npm 10 — matches design D1's "Node 20 or 22 with npm 10") |
| Browser | Chromium 155.0.0.0 via `bsk` (bundled), page zoom 0.9 → dpr 0.9000000134110451 |
| Dev server | `npx ng serve --port 4300` (this session's temporary instance, started and stopped twice: one build of the pre-change tree, one of the post-change tree). The user's own server on 4200 was **not** used for captures. |
| Test runner | Karma + Chrome Headless (Angular CLI defaults) |
| Sass | dart-sass 1.105.2 (`npx sass`), the same tool for both sizes |
| Logs / artifacts | `%TEMP%\opencode\cci001-*.log`, `cci001-d3-before\`, `cci001-d3-after\`, `cci001-d3-deter\`, `cci001-spec-audit*.json/txt`, `cci001-lockdiff.js`, `cci001-decl-audit.js`, `cci001-testnames.js`, `cci001-compare.js` |

## 2. Commands, outcomes and exit codes

Full listing with rationale: `implementation.md` → "Commands Executed". Summary of the checks that
gate this node:

| Check | Command | Exit | Outcome |
|---|---|---|---|
| Clean install on the new lock (CCI-001) | `rmdir /s /q node_modules` then `npm ci` | **0** | 1079 packages; no sync error |
| Build (CCI-002) | `npm run build` | **0** | no `login.component.scss` ERROR; login warning reduced to 2.09 kB over 4.10 kB (total 6.19 kB) |
| Build, baseline for comparison | `npm run build` (pre-change tree) | 1 | login ERROR: 1.21 kB over the 8.19 kB budget, total 9.40 kB |
| Tests (CCI-004) | `npm test -- --watch=false --browsers=ChromeHeadless` | 1 (unchanged) | 245 executed, `5 FAILED, 240 SUCCESS`; identical five names before and after |
| UI equivalence (CCI-003) | `node cci001-compare.js cci001-d3-before cci001-d3-after` | 0 | `captures=15 style_diffs=0 png_nonidentical=0/15` |
| Guard: specs untouched | `git hash-object` per spec/`src/testing` file vs `HEAD:` | 0 | 0 files differ |
| Guard: config untouched | `git status --short`; `git diff --stat HEAD -- package.json` | 0 | only the two allowed files modified; no `package.json`/`angular.json`/`.github` change |

## 3. UI verification with `bsk` (real browser, not code inspection)

`bsk` was available and used. Sessions (one writer, no user tabs borrowed): `ugds` (before + determinism
repeats), `wugs` (specificity audit), `kunn` (after captures + after specificity audit). All sessions were
stopped when done (`bsk session list` → none active). No user tab was borrowed, no credential, cookie or
token was read, and the only page touched was the local dev server's login route.

Recorded session steps, per capture (15 before, 15 after, plus repeats):

1. `bsk session start`; `bsk emulate --width/--height/--dpr`
   (widths requested as `target × 0.9` — 1152/810/360 and height 720 — because the host page zoom is 0.9;
   the page then reports the exact target viewport 1280/900/400 × 800 at dpr 0.9).
2. `bsk navigate http://localhost:4300/` → `bsk wait-for-element --selector ".login-form"` (visible within 300 ms).
3. Set the state with real input events: `focus` / `fill` (username, password, branch) and, for the error
   state, blur-with-`touched` so the component's own `error-border` + `.error-text` appear. No credentials
   were entered — the `filled` state uses obvious test strings only.
4. Settle: wait `document.fonts.ready`, await every finite animation, pause infinite animations at
   `currentTime = 0`.
5. Dump `getComputedStyle` (~90 properties) + `getBoundingClientRect` for 20 elements and 5 pseudo-elements
   into JSON via `bsk evaluate`.
6. Inject `*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}`,
   then `bsk screenshot --out`.

Observations that make the comparison meaningful (recorded before it was used):

- **Determinism** — capturing the same state twice produced byte-identical JSON *and* PNG (SHA-256), in
  `cci001-d3-deter\`. Without this, a 0-diff result would be uninterpretable.
- **Sensitivity (positive controls)** — `default` vs `error` = 62 computed-style differences (error text
  appears, border `rgb(208,2,27)`, background `rgb(255,245,245)`); `default` vs `password-focus` = 7
  differences (border `rgb(74,144,226)` + `0 0 0 4px` ring). The harness detects real visual changes.
- **State coverage** — 5 states per width: `default` (button **disabled** `rgb(153,153,153)` /
  `cursor:not-allowed`), `username-focus`, `password-focus`, `error` (border + error text present),
  `filled` (**button enabled**). This covers design D3's three required states (button disabled, field with
  error border+text, password focused) plus the focused username input, at all three widths.
- **Equality** — all 15 pre-change captures equal all 15 post-change captures: 0 style differences and
  15/15 identical PNG bytes (see the D3 table in `implementation.md`).
- **Rendering path** — the served component CSS was confirmed to be the new file before capturing:
  `bsk`-audited scoped selectors are `.login-left[_ngcontent-…]`, `.form-icon…`, i.e. the flattened shape,
  with specificity ≥ (0,2,0). This rules out "the screenshots are identical because the new styles were
  never served".

## 4. Requirement coverage

| Req | Covered by | Status |
|---|---|---|
| CCI-001 | section 2 (npm ci exit 0) + `implementation.md` D1 lock audit | VERIFIED (local) |
| CCI-002 | section 2 (build exit 0, ERROR gone, 6.19 kB vs 8.19 kB) | VERIFIED (local) |
| CCI-003 | section 3 + the D3 comparison table | VERIFIED (Chromium 155) |
| CCI-004 | section 2 (Karma totals/names; spec byte-compare; config untouched) | VERIFIED |
| CCI-005 | not executed — no commit/push authorised, so no GitHub Actions run exists | NOT_VERIFIED |

`traceability.md` carries the same mapping with evidence pointers.

## 5. Independent inspection (document ↔ behaviour alignment)

- The declaration-level audit (`cci001-decl-audit.js`) compares the pre- and post-change stylesheets as
  multiset of property/value pairs: 218 declarations, 137 distinct, **0 count differences**; both media
  queries and both keyframes present; the only intentional loss is the unused `@import`.
- The specificity audit (`cci001-spec-audit.js`) enumerated all 6 stylesheets of the running application and
  every rule that matches each login element. The only global matches are `*` (0-0-0) and the Material
  `.mat-typography h1/h2/p` rules (0-1-1); the flattened component selectors are all ≥ (0,2,0). **No case
  required keeping an extra context class**, which is why the file could be reduced this far (table in
  `implementation.md` → "Specificity Cases").
- One pre-existing quirk was re-confirmed, not introduced: `.password-field input { padding-right: 44px }`
  is overridden by `.form-group input[type="password"]` in both versions, so the computed
  `padding-right` is 16px before and after. Kept verbatim because design D2 §4 forbids value changes.

## 6. Limitations and unresolved items

1. **The 4.10 kB warning for this component persists.** Only the 8.19 kB ERROR is cleared. This is
   structural: the bytes *inside declaration blocks* (all selector text excluded) already total 4,815 B.
   Design §43 treats < 4 kB as optional. Reported as a deviation in `implementation.md`.
2. **The design's "aim ≤ 6 kB" is missed by 216 B** (6,216 B = 6.19 kB). Closing that gap would need
   declaration changes (forbidden by D2 §4) or specificity-risky flattening (against D2 §2/§5).
3. **CCI-005 is not verifiable here.** CI must be observed after an authorised push; the local equivalent
   of the job's steps passes.
4. **Single browser engine.** Only Chromium 155 was exercised; the equivalence claim is scoped to Blink at
   1280/900/400 px with page zoom 0.9. Firefox/WebKit were not run (out of scope).
5. **Animation timing curves were not compared frame-by-frame.** Coverage is the pixel-identical still
   frames in 15 states plus the paused infinite `bounce` animation and the finite transitions enumerated at
   settle time.
6. **Design diagrams (`.puml` + rendered SVG) were not produced** for this item. The `design` node asks for
   them when a design is accepted; for a stylesheet-flattening change with no interface change, none was
   created, and inventing an architecture diagram would add no verifiable information. Recorded here as a
   known documentation gap so the reviewer can decide.
7. **Uncommitted evidence.** All results were produced from the working tree, not from a commit. The
   scoped-file fingerprint recorded by the ledger at this transition is the authoritative identity; if the
   files change, this verification no longer applies (the ledger enforces this for acceptance, and
   re-verification is required after any further edit).
8. **`bsk` usage note.** The browser skill was loaded after the capture harness had already been run with
   the CLI. The rules that matter were followed (own session, no user tab borrowed, no credential access,
   sessions stopped, page content treated as data); `bsk session stop` was invoked with the ID after a first
   call using the wrong flag form, and `bsk session list` confirmed no session was left open.
