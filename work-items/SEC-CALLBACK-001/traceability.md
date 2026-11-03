# Feature traceability — SEC-CALLBACK-001

Inspected source revision: `c0cdfd5` (2026-11-04). Request: `request.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| CB-R1 | No callback without key, any encoding | D1 | `SecurityConfig`, `ApiKeyAuthenticationFilter` | CB-T1, CB-T2, CB-T5; manual re-probe | NOT_VERIFIED |
| CB-R2 | Spring Security decides, fail closed | D1 | same | CB-T1…T5 | NOT_VERIFIED |
| CB-R3 | Constant-time key compare | D2 | `ApiKeyAuthenticationFilter` | CB-T3, CB-T4 | NOT_VERIFIED |
| CB-R4 | Completion only with own-branch orders/vehicles | D3 | `OptimizationCallbackService.saveSolution`, `CallbackRejectedException`, `GlobalExceptionHandler` | CB-T6…T8; manual re-probe | NOT_VERIFIED |
| CB-R5 | Valid callbacks unchanged | D1, D3 | — | CB-T3, CB-T9, existing callback tests | NOT_VERIFIED |

Decisions: fix SEC-004 here (same filter); refuse the whole callback on any foreign id (no partial save) — Claude, 2026-11-04.
