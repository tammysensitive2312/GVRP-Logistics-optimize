# Traceability — CI-CLIENT-001

Updated at the `verify` node (2026-10-19). Branch `fix/ci-client-001` @ `21d2805` + working-tree changes.
Evidence paths are inside this work item or the temp folder `%TEMP%\opencode\` (captures are deliberately
not committed).

| Req | Requirement | Design | Verification | Status |
|---|---|---|---|---|
| CCI-001 | `npm ci` succeeds on the committed lock | D1 | `npm ci` after `rmdir /s /q node_modules` → **exit 0**; lock diff audited: only the `@jsverse/transloco` 7.5.1 subtree added, 0 removed, 0 version changes on pre-existing entries (`implementation.md` → D1) | **VERIFIED** (local) |
| CCI-002 | `npm run build` succeeds; `login.component.scss` compiled below 8 kB | D2 | `npm run build` → **exit 0**; login ERROR line gone; compiled 9,429 B → **6,216 B** (`sass --style=compressed`), build reports 6.19 kB vs the 8.19 kB error budget. The 4.10 kB **warning** line persists (see Deviations 1) | **VERIFIED** (local) |
| CCI-003 | Login screen visually unchanged | D2 step 5, D3 | 15 before/after captures (3 widths × 5 states) in a real browser: **0 computed-style/geometry differences, 15/15 PNG byte-identical**; specificity audit: no global rule can win (report has no context class) | **VERIFIED** (Chromium 155) |
| CCI-004 | No test, dependency-version or config regression | — | Karma before = after: 245 executed, `5 FAILED, 240 SUCCESS`, the **same five names**; all `*.spec.ts` and `src/testing/*` byte-identical to `HEAD` (0 differ); `package.json` and `angular.json` unchanged | **VERIFIED** |
| CCI-005 | CI `Client v2 (build)` green | D1+D2 | Not executed: no commit/push/PR was authorised, so no GitHub Actions run exists for this branch. The job's steps pass locally (`npm ci` → 0, `npm run build` → 0) | **NOT_VERIFIED** (pending a pushed branch) |

Notes on honesty of the record:

- CCI-005 is the only requirement that cannot be closed from this machine; it is deliberately left
  `NOT_VERIFIED` and is listed in `handoff.md` as a known limitation.
- CCI-004's "no config regression" covers `angular.json` (budgets untouched) and `.github/` (untouched);
  `git status --short` shows only `package-lock.json` and `login.component.scss` modified.
