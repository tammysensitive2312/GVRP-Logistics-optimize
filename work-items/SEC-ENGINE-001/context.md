# Context — SEC-ENGINE-001

Inspected source: `master` @ `bdbaf29` (2026-11-10), worktree `E:\gvrp-wt\sec-engine-001`.

## Engine inbound surface

| Endpoint | Handler | Today |
|---|---|---|
| `POST /api/v1/optimization` | `OptimizationController.optimize` | starts an async solve for a caller-chosen `jobId`; only shape checks |
| `GET /{jobId}/progress` | `progress` | reads `JobRegistry` |
| `POST /{jobId}/cancel` | `cancel` | sets the cancel flag of any running job |
| `GET /{jobId}/result` | `result` | returns the spooled payload |
| `POST /{jobId}/resend` | `resend` | re-sends the payload to Entry with the callback key |
| `GET /health` | `health` | status only |
| `POST /evaluate` | `evaluate` | synchronous routing, capped at 200 stops (`gvrp.evaluate.max-stops`) |
| `POST /legs` | `legs` | synchronous routing, capped at 1001 points (`LegsService.MAX_POINTS`) |

- No Spring Security on the classpath (`GVRP_Engine_API/pom.xml`); the only `WebMvcConfigurer`
  (`config/WebConfig`) registers CORS. No servlet filter or interceptor exists.
- `server.port=8081`, no `server.address`; bind interface not restricted by source.

## Entry outbound calls to the Engine

`service/integration/external_api/EngineApiClientImpl` uses the application-wide `RestTemplate`
bean (`config/RestTemplateConfig`), which `GeocodingService` (Goong) and `SlackNotificationChannel`
also use. Five calls: submit (`postForEntity` with JSON headers), progress (`getForEntity`, no
headers), cancel (`postForEntity` with `HttpEntity.EMPTY`), evaluate and legs (JSON headers).
A global interceptor would therefore leak an Engine key to third parties: the key must be added
per call in this class.

Existing keys: `engine.api.key` (Entry) / `entry.api-key` (Engine) = `GVRP_ENGINE_CALLBACK_KEY`,
used only for Engine → Entry callbacks with header `X-API-Key`.

## Solver work

`OptimizationService.createAlgorithm`:

- `maxIterations = config.maxIterations ?: 2000`, `numThreads = config.numThreads ?: 1`, passed to
  Jsprit `ITERATIONS` / `THREADS` with no ceiling.
- Premature termination is set once, to the cancel flag of the `JobHandle`.
- The `timeoutSeconds` branch is empty; the `TimeTermination` lines are commented out. Note in
  `GVRP_Benchmark/RUNBOOK.md`: the old `TimeTermination` without `addListener()` stopped the
  algorithm at iteration 1 (`startTime = 0`).
- `JobHandle.startedAt()` exists (set at `register`), so a job-wide deadline needs no new state.

Entry presets (`mapper/OptimizationConfigMapper`): FAST 800 it / 480 s, NORMAL 2000 / 900 s,
THOROUGH 5000 / 3600 s, all 4 threads. Since the timeout was never enforced, every past result
(UI and benchmark) was produced by iterations only. Benchmark rows in
`GVRP_Benchmark/results/schema.csv` show solves of 923–3769 s with `timeout_s=900`.

## Request sizes seen in practice

`vn_n5000` (5000 orders, 1500 vehicles) and `vn_n1000` (1000 orders, 3000 vehicles) instances
exist in `GVRP_Benchmark/datasets/instances`; the stored solutions in the local DB are ≤ 100 orders.

## Tests

- Engine: `controller/*Test` build the controller directly (no MockMvc filter chain);
  `spring-boot-starter-webmvc-test` is on the test classpath. CI excludes `@SpringBootTest` classes
  that need GraphHopper data.
- Entry: no test for `EngineApiClientImpl`.
