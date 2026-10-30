# D08 — Optimisation engine (Engine API)

The separate Spring Boot service that solves the vehicle-routing problem with jsprit over a GraphHopper road
network, reports progress, evaluates manual plans and builds leg geometry. Its only client is the Entry API;
it has no user interface, no database and no security layer of its own
(`02-system-design/04-security-and-tenancy.md`).

This document describes the engine **as implemented**. It makes no claim that the cost or emission model, or
the solution quality, is scientifically validated (`AGENTS.md` §4, §9).

Diagrams: [engine-architecture.svg](diagrams/engine-architecture.svg) ([source](diagrams/engine-architecture.puml)),
[sequence-optimize.svg](diagrams/sequence-optimize.svg) ([source](diagrams/sequence-optimize.puml)),
[activity-solve.svg](diagrams/activity-solve.svg) ([source](diagrams/activity-solve.puml)),
[class-domain.svg](diagrams/class-domain.svg) ([source](diagrams/class-domain.puml)),
[class-overview.svg](diagrams/class-overview.svg) ([source](diagrams/class-overview.puml)).

Use cases: UC-D08-01 … UC-D08-04. Rules: BR-041 (Pareto sampling), BR-059 (configuration mapping on the
Entry side).

---

## 1. Screens

None. The planner sees the engine only through D04 (job progress, cancel) and D05 (evaluate).

---

## 2. API

Eight endpoints under `/api/v1/optimization`, unauthenticated. Error bodies are maps
`{status, message}` (or `{jobId, status, …}`), **not** the Entry API envelope.

| Method | Path | Request | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/optimization` | `EngineOptimizationRequest` | **`202`** `{external_job_id: "engine-<jobId>", status: ACCEPTED}` | `400 REJECTED` when job id, orders, vehicles, depots or vehicle types are missing, or an order/depot has no coordinates; `500 ERROR` otherwise |
| GET | `/api/v1/optimization/{jobId}/progress` | — | `200` map | `404` when the job is not in the registry (never seen, or evicted after its TTL) |
| POST | `/api/v1/optimization/{jobId}/cancel` | — | **`202`** `{status: CANCEL_REQUESTED}` | `404` unknown job; `409` already finished (message in Vietnamese) |
| GET | `/api/v1/optimization/{jobId}/result` | — | `200` spooled callback payload | `404` nothing spooled |
| POST | `/api/v1/optimization/{jobId}/resend` | — | `200 {delivered: true}` | `404` nothing spooled; `502 {delivered: false}` Entry did not accept |
| GET | `/api/v1/optimization/health` | — | `200 {status: UP, service, timestamp}` | — |
| POST | `/api/v1/optimization/evaluate` | `RouteEvaluateRequest` | `200 RouteEvaluateResponse` | `400 REJECTED` invalid plan; status of a `ResponseStatusException`; `500` |
| POST | `/api/v1/optimization/legs` | `RouteLegsRequest` | `200 RouteLegsResponse` | `400 REJECTED`; `500` |

`progress` fields: `jobId, status, phase, startedAt, finishedAt`, and when a snapshot exists `iteration,
maxIterations, percent, bestCost, routes, unassigned, elapsedSeconds, updatedAt`; Pareto runs add
`scenario` (1-based) and `scenarioCount`, and `percent` then spans all scenarios.

`LegsService` is injected with `required = false`; `ASSUMPTION:` in a deployment without it (no GraphHopper
bean), `/legs` fails with `500` rather than a clear "unavailable" answer.

---

## 3. Processing a job

See `sequence-optimize.puml` and `activity-solve.puml`.

1. `POST /optimization` validates and calls `OptimizationService.optimizeAsync` (background thread); the
   job is registered in `JobRegistry` (status, phase, cancel flag, progress snapshot, TTL eviction).
2. **Pre-processing:** with at least `CLUSTER_FIRST_ORDER_THRESHOLD` = 1000 orders (and more than one
   cluster) the orders are clustered with K-means (seed 42), small clusters merged, vehicles assigned by
   demand, and the distance matrix is computed sparse (block-diagonal mask). Smaller jobs and Pareto runs
   use a dense matrix.
3. **Distance matrix:** GraphHopper per needed pair; a failed pair gets the sentinel `MatrixMask.PRUNED_METERS`
   = 1e9 m, never 0, and a hard constraint forbids traversing it; a final check fails loudly if a sentinel
   edge is used.
4. **Solve:** jsprit ruin-and-recreate; the progress snapshot is refreshed every 50 iterations; the cancel flag
   is checked during the search. With Pareto enabled, several weightings are solved (BR-041) and the frontier
   is built and classified.
5. **Return:** the completion payload is written to the result spool (`pending/`) **before** the callback is
   sent; on a non-2xx answer the spool keeps it and a sweeper retries with exponential backoff up to
   `gvrp.result-spool.max-attempts`; a `4xx` answer or exhausted attempts move it to `poison/`, a `2xx` to `sent/`. Failure and cancellation callbacks are
   sent once (see D04 §4).

---

## 4. Configuration (property names only)

`spring.optimization.entry.url`, `entry.api-key`, `entry.callback.connect-timeout-ms`,
`entry.callback.read-timeout-ms`, `graphhopper.osm.file`, `graphhopper.graph.location`,
`gvrp.matrix.parallelism`, `gvrp.evaluate.max-stops`, `gvrp.result-spool.dir`,
`gvrp.result-spool.max-attempts`, `gvrp.result-spool.sent-retention-days`,
`gvrp.result-spool.poison-retention-days`. Values are not documented here.

---

## 5. Data touched

No database. State lives in memory (`JobRegistry`), in the result spool directory on disk
(`pending/`, `sent/`, `poison/`; old files removed by `SpoolRetentionSweeper`), and in the GraphHopper graph cache built from the OSM file.

## Sources

`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/controller/OptimizationController.java`,
`.../service/{OptimizationService,CallbackService,RouteEvaluationService,LegsService,GreenVRPCostCalculator}.java`,
`.../clustering/KMeansClusterer.java`, `.../distance_matrix/MatrixMask.java`, `.../utils/AppConstant.java`,
`.../job/JobRegistry.java`, `.../service/{ResultSpool,SpoolRetentionSweeper}.java`, `02-system-design/03-integration.md`.
