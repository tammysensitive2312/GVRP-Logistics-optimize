# Feature traceability — SEC-XSS-001

Inspected source revision: `c0cdfd5` (2026-11-04). Request: `request.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| XS-R1 | All API text escaped at Leaflet sinks | D2 | the five files in `context.md` | XS-T2…T5; manual re-check | NOT_VERIFIED |
| XS-R2 | Normal names unchanged | D1, D2 | — | XS-T1 | NOT_VERIFIED |
| XS-R3 | One shared helper | D1 | `shared/utils/html.utils.ts` | XS-T1 | NOT_VERIFIED |

Decision: CSP deferred as hardening (Claude, 2026-11-04; user may add it).
