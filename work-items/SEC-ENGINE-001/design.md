# Design — SEC-ENGINE-001

Inputs: `request.md` (EN-R1…R8), `context.md`. Source: `master` @ `bdbaf29`.

## D1 — Engine inbound service-key filter (EN-R1, EN-R2, EN-R3)

New `GVRP_Engine_API/.../security/ServiceKeyFilter extends OncePerRequestFilter` (spring-web, no
new dependency), registered by `config/SecurityFilterConfig` as a `FilterRegistrationBean` on `/*`
with the highest precedence.

- Header `X-Engine-Key`. Property `gvrp.security.inbound-key=${GVRP_ENGINE_INBOUND_KEY:}`.
- Default deny. The only exemption is `GET` with `request.getRequestURI()` **exactly equal** to
  `/api/v1/optimization/health`. Any other spelling of that path (encoded, trailing slash, double
  slash, path parameter) is not exempt and needs the key — the comparison fails closed, which avoids
  the raw-URI prefix trap of SEC-012.
- Comparison: `MessageDigest.isEqual` on UTF-8 bytes of the configured and presented keys.
- Blank configured key: every protected request gets 401, and the filter logs one ERROR at startup
  ("inbound key not configured; all protected requests will be refused"). The response is the same
  401 as for a wrong key, so an unauthenticated caller cannot tell the two apart.
- 401 body `{"status":"UNAUTHORIZED"}`; the presented key is never logged.
- Startup WARN if the configured key is shorter than 32 characters.

## D2 — Entry sends the key on Engine calls only (EN-R4)

`EngineApiClientImpl` gets `@Value("${engine.inbound-key:}") String engineInboundKey` and one
`engineHeaders(boolean json)` helper used by all five calls; `progress` moves from `getForEntity` to
`exchange(GET)` and `cancel` from `HttpEntity.EMPTY` to an entity with the headers. The shared
`RestTemplate` bean is not touched, so Goong and Slack calls never carry the key.
Property in Entry: `engine.inbound-key=${GVRP_ENGINE_INBOUND_KEY:}`. Both `*.example` files get the
placeholder; `tools/check-secrets.mjs` already covers names containing `key`.

One key per direction: `GVRP_ENGINE_CALLBACK_KEY` (Engine → Entry, header `X-API-Key`) stays as is;
`GVRP_ENGINE_INBOUND_KEY` (Entry → Engine, header `X-Engine-Key`) is new.

## D3 — Bound on solver time (EN-R7, EN-R8) — decided: option B

Decision: user, 2026-11-10, answer "B: trần cứng phía server (Recommended)".

The request's `timeoutSeconds` has never been enforced, so every past result (UI jobs and the
benchmark rows, some with 900 s timeout and 3769 s solve) was produced by iterations alone.

| Option | Behaviour | Effect on results |
|---|---|---|
| **A** | Enforce `min(timeoutSeconds, server cap)` | FAST/NORMAL/THOROUGH stop at 480/900/3600 s. Small jobs unchanged; large benchmark runs (n1000, n5000) stop earlier → different, comparable-only-to-new results. |
| **B (recommended)** | Enforce a server-side cap only (`gvrp.solver.max-runtime-seconds`, default 7200 s per job, measured from `JobHandle.startedAt`); `timeoutSeconds` stays advisory and is logged as such | No change for any recorded run (largest observed solve 3769 s). Bounds abuse and runaway jobs. Enforcing the request timeout becomes part of the benchmark protocol decision (`GVRP_Benchmark` checklist step 5), not of this security fix. |

Mechanism (both options): replace the single premature-termination lambda with a small
`SolverDeadline` (cancel flag OR `now > deadline`, clock injectable for tests). Jsprit checks it each
iteration and returns the best solution found so far; the Engine logs a WARN with the iteration
count when the deadline, not the iteration limit, ended the solve. The matrix-build phase is not
covered by this bound (recorded as a limitation).

## D4 — Ceilings on configured work and request size (EN-R5, EN-R6, EN-R8)

New `config/EngineLimits` (`@Value` with defaults), checked in `OptimizationController.validateRequest`
before `optimizeAsync`, so nothing is queued when a request is refused.

| Property | Default | Reason |
|---|---|---|
| `gvrp.limits.max-iterations` | 20000 | 4× the largest preset (5000) |
| `gvrp.limits.max-threads` | 16 | presets use 4; benchmark used 1 and 4 |
| `gvrp.limits.max-orders` | 10000 | 2× the largest benchmark instance (5000) |
| `gvrp.limits.max-vehicles` | 5000 | largest instance uses 3000 |
| `gvrp.limits.max-depots` / `max-vehicle-types` | 500 / 500 | far above any instance |

`maxIterations` or `numThreads` < 1 or above the ceiling → 400 (`REJECTED`, message names the
field and the ceiling). A list above its ceiling → 413. Missing values keep today's defaults
(2000 iterations, 1 thread). `EngineLimits` is field-injected with a default instance so the
existing five-argument controller constructor used by tests keeps working.

## D5 — Executor concurrency (exclusion, observed only)

With `spring.threads.virtual.enabled=true` the `@Async` executor may be unbounded. Observed at
runtime during verification (thread names / executor class in the log). A limit is a separate
decision.

## Tests

| ID | Test | Req |
|---|---|---|
| EN-T1 | Filter: no key, wrong key, empty key, key with different case → 401, chain not called | R1, R3 |
| EN-T2 | Filter: correct key → chain called | R1 |
| EN-T3 | Filter: `GET /api/v1/optimization/health` without key → passes; `/health/`, `%68ealth`, `//health`, `;x` variants and `POST /health` → 401 | R1 |
| EN-T4 | Filter: blank configured key → 401 even when the header is blank too | R2 |
| EN-T5 | MockMvc (standalone controller + filter): encoded `progress` path without key → 401; with key → 200 | R1 |
| EN-T6 | Controller: iterations/threads 0 or above ceiling → 400, `optimizeAsync` never called; presets 800/2000/5000 × 4 threads accepted | R5, R8 |
| EN-T7 | Controller: orders/vehicles/depots/types above ceiling → 413, not queued | R6 |
| EN-T8 | `SolverDeadline`: cancel → stop; past deadline → stop; otherwise continue (fake clock) | R7 |
| EN-T9 | Entry `EngineApiClientImpl` with `MockRestServiceServer`: all five calls carry `X-Engine-Key`; blank key → header absent | R4 |
| EN-T10 | Existing Engine and Entry suites unchanged | R8 |

Runtime (verify): Engine + Entry with keys from the git-ignored local files; direct Engine calls
without key → 401 on every endpoint except health; a normal job from the UI completes.
