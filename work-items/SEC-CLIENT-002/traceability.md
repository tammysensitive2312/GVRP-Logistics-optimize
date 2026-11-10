# Feature traceability — SEC-CLIENT-002

Inspected source revision: `0b6b759` (2026-11-10). Request: `request.md`. Context: `context.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| MC-R1 | Lead #8 confirmed with failing specs before any code change | — | — | MC-T1..T4, MC-T6 failed at `0b6b759` (`context.md`) | VERIFIED |
| MC-R2 | Late zone list never drawn after reset / new map | D1, D2 | `shared/services/map.service.ts` (`drawZones`, `initializeMap`, `reset`, constructor hook); `core/services/session-reset.service.ts` (`registerResetHook`) | MC-T1, MC-T4 | VERIFIED (unit specs); browser check pending |
| MC-R3 | Late route geometry, later routes and `fitBounds` dropped after reset / new map / clear | D1 | `shared/services/map.service.ts` (`displaySolution`, `displayRoute`, `clearRoutes`) | MC-T2, MC-T3, MC-T6 | VERIFIED (unit specs); browser check pending |
| MC-R4 | Other per-branch layers checked | D3 | none | static inspection (`context.md`) | PARTIALLY_VERIFIED |
| MC-R5 | Same-session drawing unchanged | D1 | — | MC-T5; existing `map.service.spec.ts`, `map.service.xss.spec.ts`, `map.component.spec.ts` | VERIFIED (unit specs); browser check pending |

Decision (side session, 2026-11-10): generation counter instead of `takeUntil`, matching the stores
of SEC-011; `clearRoutes` also bumps the generation (fixes the same late-write within one session,
MC-T6).
