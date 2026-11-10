# Request — SEC-ENGINE-001 (Engine inbound authentication and work limits)

Source: Claude Code session 2026-11-10 (+07:00). User messages: "quay lại làm mấy task về security
lần trước chúng ta còn dang dở" and, choosing between the open groups, "Engine auth trước".

## Findings addressed

| Finding | Severity | Evidence |
|---|---|---|
| SEC-010 | Medium | Every Engine endpoint answers without credentials; runtime-confirmed 2026-11-04 (`work-items/SEC-AUDIT-001/runtime-validation.md`) |
| Audit lead #6 `EngineApiController.no-auth-boundary` | — (part of SEC-010) | Any caller can cancel, read the result of, or resend any job id; Engine then calls Entry with its callback key (credential-bearing proxy). Source trace in `work-items/SEC-AUDIT-001/audit/NEEDS-VALIDATION.md` §6 |
| Audit lead #7 `EngineOptimization.unbounded-configured-work` | — (needs_validation) | `maxIterations` and `numThreads` from the request go to Jsprit without a ceiling; `timeoutSeconds` is ignored (termination commented out); list sizes unbounded. Source trace §7 |

## Requirements

| ID | Requirement |
|---|---|
| EN-R1 | Every Engine request is refused with 401 unless it carries a valid service key, whatever the path encoding, method or trailing slash. The only exception is the exact health path `GET /api/v1/optimization/health`. |
| EN-R2 | If no key is configured, the Engine refuses every protected request (fail closed) and says so in its log at startup. |
| EN-R3 | The key comparison is constant-time. The key comes from the environment / git-ignored local file, never a tracked file, and differs from the Entry callback key (one key per direction). |
| EN-R4 | Entry sends the key on every Engine call (submit, progress, cancel, evaluate, legs) and on no other outbound call (the shared `RestTemplate` also serves Goong and Slack). |
| EN-R5 | `maxIterations` and `numThreads` above a server-side ceiling, or below 1, are refused with 400 before any work starts. |
| EN-R6 | Orders, vehicles, depots and vehicle types above a server-side ceiling are refused with 413 before any work starts. |
| EN-R7 | A job cannot run without a bound on solver time (see decision D3 in `design.md`). |
| EN-R8 | Requests from Entry with today's presets behave exactly as before (same iterations, threads, results). |

## Exclusions

- Rotating existing secrets (SEC-001), mTLS, rate limiting per caller: separate decisions.
- Concurrency limit of the async executor: observed at runtime in this item; a limit is added only if
  the observation shows the executor is unbounded and the user agrees.
- Job ownership per branch inside the Engine: with authentication only Entry can call the Engine,
  and Entry already scopes jobs by branch.
