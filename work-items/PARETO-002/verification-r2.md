# Re-verification at revision 7 — PARETO-002

Date: 2026-10-19. Tree: `feature/pareto-002` @ `4e2f928` + uncommitted changes.
Scope fingerprint `f1bb06021795dcd01caa86b6a96346852412e67680e8c0c193a7d53f89425dc1`.
`close-run.ps1` aborts after `pass` if the ledger computes a different fingerprint, so this
record cannot be attached to a tree it does not describe.

## Evidence reused, all produced against this exact fingerprint

| Check | Result | Source |
|---|---|---|
| Karma (`npm test -- --watch=false --browsers=ChromeHeadless`) | 245 executed, 240 success, 5 failures = the five pre-existing names | PARETO-003 `verification.md` §2 (independent re-run by the OpenCode verifier) |
| `npm run build` | only error is the pre-existing `login.component.scss` budget; budget set identical to PARETO-002 gate; bundle 665.55 kB | same, §2 |
| Spec / helper / config / dependency diff | empty | same, §2 checks 3–4 |
| Pre-existing spec content | 21 specs + `src/testing/transloco-testing.ts` identical to the pre-PARETO-002 snapshot | `review.md` (Claude) |
| PARETO-002 features live at this tree | chart geometry identical to the PARETO-002 capture; frontier path, active ring, engine star and dominated marker present; selection works; Apply disabled for a DOMINATED point | PARETO-003 `verification.md` §4.1, §4.7, §4.8 |
| Static review of PARETO-002 code at this tree | PASS, non-blocking findings R1–R5 | `review.md` |
| User screen test | accepted, 2026-10-19 00:35 (+07:00) | user message in the Claude session |

## Coverage carried over from revision 5 (`verification.md`)

Apply 200 / 409 / cancel and the DB check of revision #67 were verified live at `a67a…`.
PARETO-003 does not change `apply()`, `handleApplyError()`, `canApply()` or the API client
(static trace), so those results are carried over, not re-executed.

## Not verified

The same items as `verification.md` "Not executed": `SERVICE_DOMINATED` live, the 422 and
400-`0040402` paths live, a clustered Pareto job live, and the send-route picker live.
`npm run build` exits 1 until CI-CLIENT-001 fixes the login style budget.

Verdict: **PASS** for revision 7, with the limits above.
