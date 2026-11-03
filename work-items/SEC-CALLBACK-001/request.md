# Request — SEC-CALLBACK-001 (Engine callback boundary)

Source: Claude Code session 2026-11-04 (+07:00). User message: "Yes, do both" — add SEC-012…015 to the checklist and create the fix work items, after the runtime validation in `work-items/SEC-AUDIT-001/runtime-validation.md`.

## Findings addressed

| Finding | Severity | Runtime evidence |
|---|---|---|
| SEC-012 | Critical | `POST /api/v1/solutions/%63allbacks/progress` without `X-API-Key` → 200, handler executed; plain path → 401 |
| SEC-013 | Critical (with SEC-012) / High | unauthenticated forged `/complete` for job 70 (branch 1000) re-statused order #1 of branch 1 and stored it in solution 77 |
| SEC-004 | Low | callback key compared with `String.equals` (fixed here because it is the same filter) |

## Requirements

| ID | Requirement |
|---|---|
| CB-R1 | No request reaches a callback handler without a valid Engine API key, whatever the URI encoding, path parameters, double slashes or trailing slash. |
| CB-R2 | Callback authorization is decided by Spring Security on the same matched path Spring MVC uses (no raw-URI string checks). Fail closed. |
| CB-R3 | The API key comparison is constant-time. |
| CB-R4 | A completion callback may only reference orders and vehicles of the job's own branch. A callback that references any foreign or unknown order/vehicle is refused as a whole: nothing is saved, no order status changes, and the job is not marked COMPLETED. |
| CB-R5 | Valid callbacks (same branch, valid key) behave exactly as today. |

## Exclusions

- Engine API authentication / network isolation (SEC-010), key rotation (SEC-001): separate decisions.
- Per-job callback credentials or signing: possible later hardening.
- Client changes, documentation (`GVRP_Document`).

## Acceptance

Automated tests for CB-R1…R5 (MockMvc for the filter chain, unit tests for the service), all existing tests green, then a manual re-run of the two runtime probes of `SEC-AUDIT-001/runtime-validation.md` (encoded path → 401; forged foreign-branch completion refused).
