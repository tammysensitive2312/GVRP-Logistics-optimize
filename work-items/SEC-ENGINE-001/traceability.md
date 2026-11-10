# Traceability — SEC-ENGINE-001

Inspected source revision: `bdbaf29` (2026-11-10). Request: `request.md`. Design: `design.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| EN-R1 | Key required on every Engine endpoint except exact health GET | D1 | `security/ServiceKeyFilter`, `config/SecurityFilterConfig` | EN-T1, T2, T3, T5; runtime | NOT_VERIFIED |
| EN-R2 | Fail closed without configured key | D1 | `ServiceKeyFilter` | EN-T4 | NOT_VERIFIED |
| EN-R3 | Constant-time, env-sourced, separate key | D1, D2 | `ServiceKeyFilter`, both `application.properties`, `*.example` | EN-T1; `check-secrets` | NOT_VERIFIED |
| EN-R4 | Entry sends key on Engine calls only | D2 | `EngineApiClientImpl` | EN-T9 | NOT_VERIFIED |
| EN-R5 | Iteration/thread ceilings → 400 | D4 | `config/EngineLimits`, `OptimizationController` | EN-T6 | NOT_VERIFIED |
| EN-R6 | List-size ceilings → 413 | D4 | same | EN-T7 | NOT_VERIFIED |
| EN-R7 | Solver time bounded | D3 | `service/SolverDeadline`, `OptimizationService.createAlgorithm` | EN-T8 | NOT_VERIFIED |
| EN-R8 | Entry presets unchanged | D3, D4 | — | EN-T6, EN-T10 | NOT_VERIFIED |

Decision: D3 option B (server-side cap 7200 s, request timeout advisory) — user, 2026-11-10.
