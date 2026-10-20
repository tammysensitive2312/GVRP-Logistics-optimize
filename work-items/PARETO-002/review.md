# Review — PARETO-002 (client v2 Pareto tab)

Reviewer: Claude (orchestrator session), 2026-10-19. Independent of the OpenCode implementer and
of the OpenCode verifier. Method: static reading of the changed files on disk, content comparison
of the pre-existing client specs against a snapshot taken before PARETO-002 started, and reading
of both verification records. The reviewer did **not** re-run Karma or `ng build` on the dirty
tree (the cloud clone does not contain the uncommitted changes); test and build numbers below are
the verifier's, labelled as such.

Reviewed tree: `feature/pareto-002` at `4e2f928` plus uncommitted PARETO-002 and PARETO-003
changes, scope fingerprint `f1bb0602…` (the PARETO-003 verified tree). The original PARETO-002
handoff `a67a2637…` no longer exists on disk because PARETO-003 edited the same scope.

## Claim

PARETO-002 implements PUI-001..PUI-009 as designed: a Pareto tab with chart, list and apply;
scenario progress in job monitoring; the Pareto hint in the planning dialog; the
`PARETO_ALTERNATIVE` label in the send-route dialog; no change to existing tests, dependencies or
build configuration.

## Supporting evidence

| Area | Finding |
|---|---|
| API client | `getPareto` / `applyParetoCandidate` match the PARETO-001 endpoints (`GET /solutions/{id}/pareto`, `POST /solutions/{id}/pareto/{candidateId}/apply`, empty body). No branch id is sent; tenant scoping stays server-side (AGENTS.md §15). |
| Models | `pareto.model.ts` mirrors the snake_case Entry DTOs, including `not_applicable_reason` values. |
| Orders section | Frontier loaded only when `solution()` is non-null (design constraint); stale responses for a solution no longer active are dropped; a failed load hides the tab and logs a warning without a toast; the tab appears only with candidates. |
| Pareto view | Apply confirms first, then handles 409 (banner + load latest), 400/`0040402` ("Plan not found" + reload), 422/400 (server message). `canApply` uses the server's `applicable` flag. |
| Job monitoring | Scenario label and dots only when `scenarioCount > 0`; overall percent still comes from the engine (no fabricated progress); scenario index clamped to `[1, count]`. |
| Planning dialog | Hint text and `× 5 scenarios (Pareto)` appear only when Pareto is enabled. |
| Send-route dialog | `typeLabel` translates only `PARETO_ALTERNATIVE`; other types keep raw text, so existing option text is unchanged. |
| Test integrity (§14) | All 21 pre-existing client specs and `src/testing/transloco-testing.ts` are **content-identical** (line endings and trailing newline normalised) to the snapshot taken on 2026-10-18 before PARETO-002 started. This is content-level evidence, stronger than the `git diff` check in `verification.md`. |
| Dependencies / config | `package.json`, `package-lock.json`, `angular.json` and `proxy.conf.json` unchanged per verifier check 4 (PARETO-003 `verification.md` §2). No new dependency. |
| Tests / build (verifier) | At `f1bb…`: 245 executed / 240 success / 5 failures, exactly the five pre-existing names. Build: the only error is the pre-existing `login.component.scss` budget error; no new budget entry. |

## Findings (non-blocking)

| # | Severity | Finding | Disposition |
|---|---|---|---|
| R1 | Low (efficiency) | After a successful apply, `store.setSolution()` triggers the frontier effect **and** `reload` calls `onParetoReload()`, which clears `paretoLoadedForId`. If the first GET has not returned when the effect's microtask runs, `GET /solutions/{new}/pareto` is sent twice. The result is correct, only redundant. | Follow-up. |
| R2 | Low (efficiency) | Job monitoring calls `getPareto` for every completed job, including non-Pareto jobs, which return an empty list. One extra request per completed job. | Accept. |
| R3 | Info | The chart has no `clip-path`, so a point just outside the zoom window can be drawn inside the axis margin (also noted by the PARETO-003 verifier §4.8). | Follow-up, cosmetic. |
| R4 | Residual risk | Paths not seen live, unit evidence only: `SERVICE_DOMINATED` marker, 422 / 400-`0040402` responses, clustered note, send-route label. | Recorded in closure. |
| R5 | Residual risk | `pareto-view.component.spec.ts` and `pareto-chart.utils.spec.ts` were created in PARETO-002 and are untracked. The only evidence that PARETO-003 did not edit them is their file times. | Accept. They are new tests of an unaccepted item, not existing tests under §14. |

No security finding: no new endpoint, no secret, no logging of personal data (`console.warn`
logs only the HTTP error object of the frontier call).

## Verdict

**PASS.** The claim is supported, with the limits listed in R4 and R5. The run must go through
`changed` → re-verify → `pass` at the current fingerprint before acceptance, because the original
handoff `a67a…` is stale (see `findings-r2.md`).
