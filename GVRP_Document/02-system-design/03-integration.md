# 03 — Integration

Every boundary the system crosses, with the contract that is actually implemented: the endpoint, the
payload, the error mapping, and what happens when the other side is unavailable.

Diagram: [sequence-dispatch-callback.svg](diagrams/sequence-dispatch-callback.svg)
([source](diagrams/sequence-dispatch-callback.puml)),
[sequence-auth.svg](diagrams/sequence-auth.svg) ([source](diagrams/sequence-auth.puml)).

---

## 1. Integration inventory

| # | Boundary | Direction | Protocol | Authenticated | Failure mode |
|---|---|---|---|---|---|
| I-1 | Entry → Engine: run an optimisation | outbound | HTTP JSON | **no** | job marked `FAILED` immediately, no retry |
| I-2 | Entry → Engine: read progress | outbound | HTTP JSON | **no** | answer degrades to `204 No Content` |
| I-3 | Entry → Engine: cancel | outbound | HTTP JSON | **no** | warning logged; DB stays `CANCELLED` |
| I-4 | Entry → Engine: evaluate / legs | outbound | HTTP JSON | **no** | `502` to the caller, nothing written |
| I-5 | Engine → Entry: completion / failure / cancelled | inbound | HTTP JSON + `X-API-Key` | shared key | spool retry, then poison |
| I-6 | Engine → Entry: progress | inbound | HTTP JSON + `X-API-Key` | shared key | no-op endpoint, never called |
| I-7 | Entry → Goong: geocode | outbound | HTTPS JSON | API key in query | order rejected with a `400` on `address` |
| I-8 | Entry → SMTP: mail | outbound | SMTP | username/password | logged error; the job is unaffected |
| I-9 | Entry → Slack: completion notice | outbound | HTTPS webhook | webhook secret URL | logged warning; the job is unaffected |
| I-10 | Client → OSRM: fallback geometry | outbound (browser) | HTTPS JSON | none | manoeuvres missing, map still drawn |
| I-11 | Client → Google tiles | outbound (browser) | HTTPS | none | blank map background |

Two asymmetries are worth stating up front, because they shape the failure behaviour:

* The Entry→Engine direction is **unauthenticated**. The engine has no Spring Security dependency,
  no filter and no security configuration; it accepts any caller on port 8081. The `X-API-Key` it
  holds is used only as an outbound header on callbacks.
* The Engine→Entry direction is authenticated, but by a filter, not by the security chain: the
  callback paths are `permitAll`, so the API key comparison is the only gate.

---

## 2. Entry → Engine

### I-1 Dispatch

`POST {spring.optimization.engine.url}/optimization`, body `EngineOptimizationRequest`:

| Field group | Content |
|---|---|
| `job_id` | Entry's job id, set immediately before the call |
| depots, orders, vehicles | flattened projections of the branch's master data and the selected orders |
| `config` | weights, iterations, timeout, threads, penalty, Pareto flag (from the planner's preferences) |
| LEZ | compiled zones and vehicle permissions from the job's frozen snapshot |

The call is made from an `@Async` method registered in an `afterCommit` transaction synchronization,
so it happens after the job row is committed and never inside the request transaction. It is fire and
forget: the HTTP response is used only to set the job status.

| Engine answer | Entry state change |
|---|---|
| `202 Accepted` | job → `PROCESSING`, `started_at` set, `external_job_id` taken from the body (`engine-<jobId>`) |
| any other 2xx/3xx | job → `FAILED`, message `Engine returned: <status>` |
| 4xx / 5xx | job → `FAILED`, message `Engine API error: <detail>` |
| connection refused / read timeout | job → `FAILED`, message `Cannot connect to optimization engine. Please check if engine is running.` |

There is **no retry** on dispatch. A job whose dispatch failed is terminal: the planner must submit it
again. This is the main asymmetry with the callback direction, which retries twenty times.

Because the job row is already committed as `PENDING` when dispatch runs, a process crash inside the
`@Async` method leaves the job `PENDING` forever. Nothing sweeps it.

### I-2 Progress

`GET {engine}/optimization/{jobId}/progress` → `{job_id, percent, message, phase, ...}` from the
engine's in-memory `JobRegistry`.

| Outcome | Entry behaviour |
|---|---|
| 200 | body returned to the client unchanged |
| 404 | treated as "no progress yet" |
| any other error or timeout | logged at WARN, treated as "no progress" |

Entry's controller answers `200` with the body, or `204 No Content` when progress is unavailable. The
client treats `204` as "keep the last value", not as an error.

Progress is lost on engine restart, and there is no push mechanism: the client polls Entry every
3 s, and Entry polls the engine synchronously inside that request.

### I-3 Cancel

`POST {engine}/optimization/{jobId}/cancel` with an empty body. Best effort: the method swallows every
exception and logs a warning, because the database already says `CANCELLED`. The engine cancels the
solver handle at the next iteration boundary and posts the `cancelled` callback.

Consequence: if the engine is unreachable, the job is `CANCELLED` in Entry while the solver keeps
running. The result arrives later, is rejected by the state guard, and the spool marks it sent — the
work is wasted but no data is corrupted.

### I-4 Evaluate and legs

| Entry service call | Engine endpoint | Purpose |
|---|---|---|
| `evaluateRoutes` | `POST {engine}/optimization/evaluate` | totals, violations and per-route metrics for a candidate plan; preview only |
| `legs` | `POST {engine}/optimization/legs` | geometry and manoeuvres for a list of points |

Error mapping in the client of these two calls:

| Engine answer | Entry result |
|---|---|
| 2xx with body | returned |
| 4xx | `400` with `Engine rejected route sequences: <body>` or `Engine rejected leg points: <body>` |
| 5xx / timeout | `502 EngineUnavailableException` with a retry-later message |

The distinction is deliberate: a `4xx` means Entry sent something the engine considers invalid, so the
planner must fix the edits; a `5xx` means try again. For route sending, `502` means the route was **not**
sent.

### Timeouts

One shared `RestTemplate` bean: connect timeout 1 minute, read timeout 3 minutes. These apply to every
Engine call, including `evaluate` and `legs`, which are interactive. A 3-minute read timeout on an
interactive request is far longer than any client-side timeout, so the browser gives up first.

The property `spring.optimization.engine.timeout` (value 900) is declared and never read; it does not
change these values.

---

## 3. Engine → Entry (callbacks)

Base path `/api/v1/solutions/callbacks`. All four are `permitAll` in the security chain and gated only
by `ApiKeyAuthenticationFilter`, which requires header `X-API-Key` equal to Entry's `engine.api.key`
and then authenticates the request as `ENGINE_SERVICE`.

| Endpoint | Payload | Effect |
|---|---|---|
| `POST /complete` | `job_id`, `external_job_id`, solution with routes, stops, unassigned orders, Pareto candidates | persists the solution, certifies LEZ, sets job `COMPLETED`, publishes the completion event |
| `POST /failed` | `job_id`, `external_job_id`, `error_message` | job → `FAILED`, `completed_at`, message stored, failure e-mail |
| `POST /cancelled` | `job_id`, `reason` | job → `CANCELLED`, `cancelled_at` |
| `POST /progress` | `job_id`, `progress` | loads the job and logs; **nothing is written** |

### Completion rules

| Situation | Answer | Why |
|---|---|---|
| job not `PENDING`-blocked and no solution exists | `200`, work done | normal path |
| a solution already exists for the job | `200`, ignored | idempotency: `solutions.job_id` is `UNIQUE` and the service checks `findByJobId` first |
| job still `PENDING` | `503 JobNotReadyException` | the dispatch transaction has not committed yet; the engine must retry |
| job `CANCELLED` | `200`, ignored | the planner cancelled; a late result must not resurrect the job |
| job `FAILED` | accepted | a retry after a transient failure may still deliver a valid result |

The `503` case is why the engine's retry exists and why a 5xx is retried while a 4xx is poisoned: a
`503` from a race is temporary, a `400` payload error is not.

Order status is part of the same transaction: planned orders move to `ON_ROUTE`, unplanned ones to
`UNASSIGNED`, and a business-rule violation aborts the whole callback.

### Failure rules

`/failed` is accepted for a job in any non-terminal state and marks it `FAILED`. It also sends the
failure e-mail **directly**, not through the event: the completion listener is bound to a successful
persistence transaction and a failure has no solution to point at.

`/cancelled` marks the job `CANCELLED` and sends **no** notification. `EmailService` contains a
cancellation template and a method for it, but nothing calls it. The cancellation is visible in the UI
and nowhere else.

`/progress` is a stub. Entry has no progress column, the engine never posts to it, and the handler's
body is three commented-out TODOs. It exists and answers `200`.

### Idempotency and ordering

Callbacks may arrive twice (engine read timeout after Entry committed, spool retry after a partial
success) and out of order (a late `cancelled` after `complete`). The guards are:

1. the job state machine (terminal states ignore most events),
2. `solutions.job_id UNIQUE` plus an explicit existence check,
3. LEZ certification keyed on the job's frozen ruleset hash, so re-certifying is stable.

---

## 4. Engine outbox and retry

The engine has no database. Before posting a completion callback it writes the payload to
`.data/results/pending/job-<id>.json.gz`; the file is the record of a result that has not been
acknowledged.

| Step | Behaviour |
|---|---|
| Write | temp file then atomic move, so a crash never leaves a half-written payload |
| Post | `POST /solutions/callbacks/complete` with `X-API-Key` |
| 2xx | move to `sent/` |
| 4xx | move to `poison/` with the status and body recorded — retrying an identical payload cannot help |
| 5xx, timeout, connection refused | stay in `pending/`, attempt counter incremented |
| Retry schedule | first retry ~60 s after start-up, then fixed 120 s; at most 20 attempts, then poison |
| Retention | `sent/` 7 days, `poison/` 30 days, daily at 03:00; `pending/` never deleted |

Because every retry re-reads from disk, a result survives an engine restart. Because the spool is a
local directory, it does not survive a lost volume and is not shared between replicas.

Operational endpoints exist for the spool: `GET /api/v1/optimization/{jobId}/result` returns the stored
payload and `POST /api/v1/optimization/{jobId}/resend` re-posts it. Entry never calls either; they are
manual recovery tools.

Failure and cancellation callbacks are **not** spooled. If they fail, they are only logged: a job may
therefore stay `PROCESSING` in Entry with no pending spool entry to ever fix it. This is the worst
recovery gap in the system.

---

## 5. Geocoding

`GeocodingService` calls Goong (`geocoding.goong.uri`, default `https://rsapi.goong.io/Geocode`) with
`address` and `api_key` as query parameters.

| Aspect | Behaviour |
|---|---|
| Enabled | `geocoding.enabled=true`; when false, `geocode()` returns `null` without any call |
| Trigger | order create, order update, order import — only when the address is present and coordinates are absent |
| Success | first result's `geometry.location` → latitude/longitude |
| No result | `null` → the order is rejected with a validation error on `address` |
| Exception | `null`, and only the exception class is logged — the message can contain the URL, which carries the key |
| Caching | none; the same address is geocoded again on the next order |

The key is in a query parameter, so it appears in any proxy access log that records URLs. The code's
own log line deliberately avoids it.

---

## 6. Client-side routing and tiles

| Integration | URL | When used | Notes |
|---|---|---|---|
| OSRM | `https://router.project-osrm.org` | only when a stored leg has no geometry or no instruction | driver view and planner map; public demo server, no SLA, no key |
| Google raster tiles | `https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}` | every Leaflet map | no attribution configured, no key; usage outside Google's terms is a licence question, not a technical one |

Both are hard-coded in five components. There is no configuration property and no environment
override, so a deployment cannot point them at a self-hosted service without a code change.

The server never calls OSRM: stored leg geometry comes from the engine's GraphHopper graph.

---

## 7. Notification integrations

| Channel | Trigger | Recipient | Failure handling |
|---|---|---|---|
| E-mail (success) | `JobCompletionEvent` after commit, `@Async` | the planner who submitted the job | logged; the job stays `COMPLETED` |
| E-mail (failure) | failure callback, direct call | the planner | logged |
| Slack | `JobCompletionEvent` after commit | `branches.branch_webhook_url` | logged warning; skipped when the branch has no URL |

The event is published inside the callback transaction and handled after commit, so a rollback sends
nothing. Slack is one webhook per branch, set out of band — there is no endpoint and no screen to
change it, and `users.slack_user_id` is stored but never read.

Both channels run on the Entry async executor (core 2, max 5, queue 100). Under load, notifications
queue behind dispatch work.

Known defect, `PARTIALLY_VERIFIED`: `EmailService` renders templates through a Thymeleaf
`TemplateEngine` with template names like `/resources/templates/optimization-success`. No custom
resolver is configured, so the default resolver's prefix (`classpath:/templates/`) applies and the
templates would be looked up under a path that does not exist. The names suggest the code expects a
`RESOURCE`-style resolver. If confirmed at runtime, every e-mail send throws, is caught, and is logged
as a failure — notifications would silently never arrive. This needs one runtime check before it can
be stated as fact.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/integration/external_api/EngineApiClientImpl.java`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/{OptimizationJobService,OptimizationCallbackService,EmailService,GeocodingService}.java`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/{notify,event}/**`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{OptimizationCallbackController,OptimizationJobController}.java`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/security/ApiKeyAuthenticationFilter.java`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/config/RestTemplateConfig.java`,
`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/{controller/OptimizationController,service/CallbackService,service/ResultSpool,service/CallbackRetryScheduler,service/SpoolRetentionSweeper,job/JobRegistry}.java`,
`GVRP_Engine_API/src/main/resources/application.properties`, `GVRP_Engine_API/pom.xml`,
`GVRP_Entry_API/src/main/resources/application.properties`,
`GVRP_Entry_client_v2/src/app/features/**` (OSRM fallback, tile layers).
