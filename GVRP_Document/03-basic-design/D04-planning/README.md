# D04 — Route planning (jobs and engine callbacks)

The planner hands a set of orders and vehicles to the optimisation engine; the engine answers later through
callbacks. This domain owns the `optimization_jobs` row from submission to its terminal status, and the
Entry API side of every callback.

Screens: `/jobs`, `/jobs/:id`; the planning and monitoring dialogs open from `/main`.
Diagrams: [sequence-job-submit.svg](diagrams/sequence-job-submit.svg) ([source](diagrams/sequence-job-submit.puml)),
[sequence-engine-callback.svg](diagrams/sequence-engine-callback.svg) ([source](diagrams/sequence-engine-callback.puml)),
[sequence-failure-callback.svg](diagrams/sequence-failure-callback.svg) ([source](diagrams/sequence-failure-callback.puml)),
[state-job-status.svg](diagrams/state-job-status.svg) ([source](diagrams/state-job-status.puml)).
The end-to-end dispatch/callback view is `02-system-design/diagrams/sequence-dispatch-callback.puml`.

Use cases: UC-D04-01 … UC-D04-05. Rules: BR-059 … BR-069 (planning section), BR-082, BR-083.

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/jobs` | `JobHistoryComponent` | the branch's jobs, newest first |
| `/jobs/:id` | `JobHistoryComponent` | the same screen focused on one job (deep link) |
| — (from `/main`) | `RoutePlanningDialogComponent` | goal, speed, time-window mode, unassigned flag, Pareto flag for the selected orders and vehicles |
| — (from `/main`) | `JobMonitoringDialogComponent` | progress polling, cancel, result summary, LEZ hint for unserved orders |
| — (from `/main`) | `BackgroundJobDialogComponent` | tells the planner the job keeps running after the dialog is closed |

Both `/jobs` routes use `MainLayoutComponent` and are guarded by `AuthGuard` and `setupGuard`.

---

## 2. API

Nine endpoints. The five job endpoints require `ROLE_PLANNER` and are branch-scoped. The four callback
endpoints are `permitAll` at the chain level and gated only by the engine API key filter
(`02-system-design/04-security-and-tenancy.md` §3).

| Method | Path | Request | Success | Notes |
|---|---|---|---|---|
| POST | `/api/v1/jobs/plan` | `RoutePlanningRequest` | **`202`** `OptimizationJobDTO` (`PENDING`) | engine call happens after commit, see §3 |
| GET | `/api/v1/jobs` | `limit` (default 10) | `200 OptimizationJobDTO[]` | `ORDER BY created_at DESC`; no upper bound on `limit` |
| GET | `/api/v1/jobs/{id}` | — | `200 OptimizationJobDTO` | |
| GET | `/api/v1/jobs/{id}/progress` | — | `200` map, or **`204`** | proxied to the engine; `204` when the engine does not know the job (404) **or is unreachable** |
| PUT | `/api/v1/jobs` | `BulkEditJobRequest {ids}` | `204` | cancels every listed job, all-or-nothing |
| POST | `/api/v1/solutions/callbacks/complete` | `EngineCallbackRequest.CompletionCallback` | `200` (or `503`) | persists the solution, see §4 |
| POST | `/api/v1/solutions/callbacks/failed` | `FailureCallback {job_id, error_message}` | `200` | |
| POST | `/api/v1/solutions/callbacks/cancelled` | `CancellationCallback {job_id, reason}` | `200` | |
| POST | `/api/v1/solutions/callbacks/progress` | `ProgressCallback` | `200` | stub: nothing calls it and the handler writes nothing |

### `RoutePlanningRequest`

| Field | Constraint / default |
|---|---|
| `order_ids` | `@NotEmpty` |
| `vehicle_ids` | `@NotEmpty` |
| `preferences.goal` | `MINIMIZE_COST` (default), `MINIMIZE_DISTANCE`, `MINIMIZE_CO2`, `BALANCED` |
| `preferences.speed` | `FAST`, `NORMAL` (default), `HIGH_QUALITY` |
| `preferences.allow_unassigned_orders` | default `false` |
| `preferences.time_window_mode` | `STRICT` (default), `FLEXIBLE` |
| `preferences.enable_pareto_analysis` | default `null` (off) |
| `preferences.seed` | optional; `null` = random |

The mapping of goal/speed to engine weights and iterations is in `OptimizationConfigMapper` (BR-059).

### `OptimizationJobDTO`

`id, branch_id, user_id, user_name, status, external_job_id, estimated_duration_minutes, error_message,
created_at, completed_at, cancelled_at, elapsed_seconds, remaining_seconds, can_be_cancelled, solution_id`.

---

## 3. Submission

`OptimizationJobService.submitJob`, one transaction, in this order (see `sequence-job-submit.puml`):

1. **Concurrency limit** — at most 3 jobs of the branch in `PROCESSING` (BR-060), else `400` + `0040903`.
2. **Orders** — every id exists (`400` + `0040402`, `resource: orders`), belongs to the branch (same code,
   `field` = the codes), is `SCHEDULED` (`400` + `0040901`, message names ≤ 10 codes, BR-061) and has a
   location (`400` + `0040001`).
3. **Vehicles** — every id exists (`0040402`, `field` = missing ids), belongs to a fleet of the branch
   (`0040402`), is `AVAILABLE` (`0040901`) and has a start and an end depot (`0040901`, `resource: depots`).
4. **One delivery date** (BR-062) — two or more dates answer `400` + `0040001`, `field: deliveryDate`, before
   any LEZ compilation.
5. **LEZ** — `RestrictionCompiler.compile(branch, date, vehicles, orders)`; vehicles whose depot lies in a zone
   that bans them all day are dropped. If none is left: `400` + `0040001` naming zones and vehicle ids
   (BR-083). In-zone orders and allowed vehicles receive `LEZ_<key>` skills.
6. **Persist** — the job row (`PENDING`, serialised input, LEZ snapshot, estimated duration BR-063).
7. **Dispatch after commit** — `EngineApiClient.submitOptimizationAsync` runs in a
   `TransactionSynchronization.afterCommit`, so a rolled-back submission never reaches the engine. The async
   client sets `PROCESSING` on a `202` and `FAILED` on anything else (BR-065).

The response is `202 Accepted` with the `PENDING` job; the client polls `GET /jobs/{id}` and
`GET /jobs/{id}/progress`.

---

## 4. Callbacks

Callback processing is described in detail in `02-system-design/03-integration.md` (§ callbacks). The
basic-design facts the screens depend on:

| Callback | Accepted when job is | Effect | Otherwise |
|---|---|---|---|
| complete | `PROCESSING` or `FAILED` | solution, routes, stops and unassigned rows persisted; served orders `ON_ROUTE`, unassigned `UNASSIGNED`; routes certified with the job's LEZ snapshot (BR-082); job `COMPLETED` | `PENDING` → `503` (engine retries, BR-067); `COMPLETED`/`CANCELLED` → ignored (BR-066, BR-068) |
| failed | `PROCESSING` | job `FAILED`, `completed_at`, `error_message`; failure e-mail (errors swallowed) | ignored with a warning (`200`) |
| cancelled | `PROCESSING` | job `CANCELLED` | ignored |
| progress | any | none | — |

An unknown `job_id` on the failure callback raises `DataInvalidException` (`0040402`), answered `400`.
`ASSUMPTION:` the engine's failure path does not spool or retry (`sequence-failure-callback.puml`), so such a
callback is lost; only the completion payload is spooled.

---

## 5. Cancellation

`PUT /api/v1/jobs` with `{ids: [...]}`:

* the whole list is loaded; any missing or foreign id → `400` + `0040402` for the **whole request**, nothing
  cancelled;
* any job not `PROCESSING` → `400` + `0040901` *Job can't be cancelled*, nothing cancelled (BR-064);
* otherwise every job is set `CANCELLED` with one `cancelled_at`, saved, and then
  `EngineApiClient.requestCancel` is called per job — best effort: an unreachable engine only logs a warning,
  the Entry row stays `CANCELLED`.

BR-064 says a foreign id answers `404`; the implemented status is `400` (same exception-class mapping as
D03, `common/README.md` §5).

---

## 6. Errors

| Situation | Status | Code |
|---|---|---|
| bean validation (empty `order_ids`/`vehicle_ids`) | 400 | `0040001`, one detail per field |
| 3 jobs already processing | 400 | `0040903` |
| unknown/foreign order, vehicle, job | 400 | `0040402` |
| order not `SCHEDULED`, vehicle not `AVAILABLE`, vehicle without depots, job not cancellable | 400 | `0040901` |
| two delivery dates; no usable vehicle (LEZ); order without location | 400 | `0040001` |
| completion callback while `PENDING` | 503 | `0050001` (`JobNotReadyException`) |
| callback without a valid API key | 401 | filter body, not the envelope (`common/README.md` §6) |

---

## 7. Data touched

`optimization_jobs` (all), `orders` and `vehicles` (read at submission; orders' status written by the
completion callback), `solutions`, `routes`, `route_stops`, `unassigned_orders`, `pareto_candidates`
(written by the completion callback), LEZ tables (read by the compiler). `input_data` and `lez_snapshot` are
JSON text columns on the job.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{OptimizationJobController,OptimizationCallbackController}.java`,
`.../service/{OptimizationJobService,OptimizationCallbackService}.java`,
`.../service/integration/external_api/EngineApiClientImpl.java`, `.../repository/OptimizationJobRepository.java`,
`.../dto/request/{RoutePlanningRequest,BulkEditJobRequest,EngineCallbackRequest}.java`,
`.../dto/response/OptimizationJobDTO.java`, `.../entity/enums/OptimizationJobStatus.java`,
`GVRP_Entry_client_v2/src/app/app.routes.ts`, `.../features/jobs/job-history/**`, `.../features/main/jobs/**`,
`01-requirements/03-business-rules.md`.
