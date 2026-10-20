# Review — CI-CLIENT-001

Reviewer: Claude (orchestrator), 2026-10-19 08:05 (+07:00). Independent of the OpenCode session
that implemented and verified this item.

Reviewed tree: `fix/ci-client-001` @ `21d2805` plus uncommitted changes. Ledger state
`await_user_test`, handoff `e60c55f4…`.

Method:
- Re-ran the checks below in Claude's own environment, on file contents read from the user's disk.
- Did **not** re-run `npm ci`, `npm run build`, Karma or the browser captures. The results for
  those come from the engineer and are labelled as such.

## Claim

The lock file is regenerated with only the transloco subtree added. `login.component.scss`
compiles below 8 kB with identical rendering. No test, dependency-version or config change.

## Independent checks (Claude)

| Check | Result |
|---|---|
| Lock content | Applied Claude's own earlier `npm install --package-lock-only` diff (sandbox, 2026-10-18) to the `master` lock. The result is **content-identical** to the lock on disk once CRLF is normalised. The disk copy has CRLF line endings from `core.autocrlf`; Git normalises them on commit. |
| Lock structure | 15 packages added, all from the `@jsverse/transloco` 7.5.1 subtree. 0 removed, 0 version changes. All other field changes are `dev`/`devOptional` flags plus the root `dependencies` entry. |
| Scss file identity | Disk file: 7,151 B, SHA-256 `b302e47d…`, which matches `handoff.md`. |
| Compiled size | Dart Sass, compressed: old 9,429 B, new **6,216 B**. Same numbers as the engineer. |
| Declaration equivalence | Old and new were both compiled, and every declaration was keyed by (media/keyframes context, rightmost compound selector, property, value). Result: 240 = 240 declarations, **0 differences**. The only selector that is still ambiguous after flattening is `p`; `.form-header p` and `.feature-text p` are kept distinct, as the design requires. |
| Cascade inside the component | Media rules: `.login-container .login-right` (0,2,0) still beats the base `.login-right` (0,1,0), so the outcome is unchanged. `.form-group input[type=password]` still beats `.password-field input`; this was a no-op before and still is, as the engineer recorded. No other selector pair targets the same element with conflicting properties. |
| Protected specs | The 21 specs and `src/testing/transloco-testing.ts` are content-identical to the 2026-10-18 snapshot. |

## Engineer evidence (not re-run by Claude)

- `npm ci` exit 0.
- `npm run build` exit 0, with no login error. A warning remains: 6.19 kB against the 4.10 kB budget.
- Karma: the same 5 known failures.
- 15 before/after browser captures: 0 computed-style differences and byte-identical PNGs. The
  comparison was shown to be deterministic and to have sensitivity controls.

## Findings (non-blocking)

| # | Finding | Disposition |
|---|---|---|
| C1 | The login style budget **warning** remains (6.19 kB vs 4.10 kB), and the result misses the 6 kB aim by 216 B. The declarations alone are about 4.8 kB, so removing the warning would require changing the styles themselves. | Accepted. The design marks this as optional. The checkpoint records the user accepting it during the OpenCode session; confirm it in your acceptance message. |
| C2 | Hover states (`:hover` on inputs, the button, links and the toggle) were not captured. Risk is low: the declarations are identical per target and pseudo-class, and no cascade conflict was found. | Accept. |
| C3 | The engineer stopped and restarted the user's dev server on port 4200 so that `npm ci` could delete `node_modules`. This was reported openly and touched no repository files. | Process note. Future tasks should state up front that `npm ci` needs the dev server stopped. |
| C4 | CCI-005 (CI green) cannot be verified until the branch is pushed. Locally the build used Node 22, while CI uses Node 20 (both ship npm 10). | Verify after the push. |
| C5 | Implementation and verification were done by the same OpenCode orchestrator session. This review provides the independent check. | Recorded. |

No security finding: no secret, no endpoint, no dependency version change.

## Verdict

**PASS.** The claim is supported. The remaining gates are the user's own test of `handoff.md`
steps 1–4, then the CI run after the push.
