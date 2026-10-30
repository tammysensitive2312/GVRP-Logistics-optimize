# D05 — Solution review and customisation

What the planner does with a plan once the engine has produced it: read it, walk its revision history, try
manual changes without saving them, save a manual revision, and compare or apply Pareto alternatives.

Screens: tabs of `/main` (no route of their own).
Diagrams: [sequence-save-revision.svg](diagrams/sequence-save-revision.svg) ([source](diagrams/sequence-save-revision.puml)),
[sequence-pareto.svg](diagrams/sequence-pareto.svg) ([source](diagrams/sequence-pareto.puml)),
[state-solution-revision.svg](diagrams/state-solution-revision.svg) ([source](diagrams/state-solution-revision.puml)).

Use cases: UC-D05-01 … UC-D05-05. Rules: BR-041 … BR-046 (Pareto), BR-090 … BR-096.

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/main` (Route tab) | `RouteViewComponent` | routes and stops of the active solution, drag-and-drop editing, evaluate/save, send (D06) |
| `/main` (Timeline tab) | `TimelineViewComponent` | stop times per vehicle |
| `/main` (Pareto tab) | `ParetoViewComponent` | cost/CO₂ chart of the job's candidates, apply |
| — | `ExpandedViewDialogComponent` | the Route or Timeline tab in a large dialog (Expand/Collapse) |

The Pareto tab appears only when the active solution's job has candidates. The active solution is held in the
client's `SolutionStore`; it is opened from `/jobs` (D04) or from a deep link (D09).

---

## 2. API

Seven endpoints, `ROLE_PLANNER`, branch-scoped (BR-090). All live under `/api/v1/solutions`.

| Method | Path | Request | Success | Notes |
|---|---|---|---|---|
| GET | `/api/v1/solutions/{id}` | — | `200 SolutionDetailResponseDTO` | routes, stops, totals, unassigned ids |
| GET | `/api/v1/solutions/{id}/latest-revision` | — | `200 {id}` | newest revision of the chain, or the id itself (BR-095); used to recover after a `409` |
| GET | `/api/v1/solutions/{id}/revision-chain` | — | `200 RevisionDTO[]` | root first: `{id, created_at, type, status}` (BR-091) |
| POST | `/api/v1/solutions/{id}/evaluate` | `CustomizeRoutesRequest` | `200 RouteEvaluateResponseDTO` | preview, nothing persisted (BR-092) |
| POST | `/api/v1/solutions/{id}/revisions` | `CustomizeRoutesRequest` | `200 SolutionDetailResponseDTO` | new `MANUAL_EDITED` child (BR-093) |
| GET | `/api/v1/solutions/{id}/pareto` | — | `200 ParetoFrontierDTO` | candidates of the job behind the chain |
| POST | `/api/v1/solutions/{id}/pareto/{candidateId}/apply` | — | `200 SolutionDetailResponseDTO` | new `PARETO_ALTERNATIVE` child (BR-044) |

### Payloads

* `CustomizeRoutesRequest` = `{routes: [{vehicle_id, stop_order_ids: [...]}]}` — the whole plan, not a diff.
* `RouteEvaluateResponseDTO` = `{feasible, violations[{code, route_index, order_id, detail}], routes[],
  totals{total_distance, total_time, total_cost, total_co2, vehicles_used, orders_served}, warnings[]}`.
* `SolutionDetailResponseDTO` = `{id, job_id, branch_id, status, type, total_cost, total_distance, total_co2,
  total_time, total_vehicles_used, served_orders, unserved_orders, error_message, created_at, routes[],
  unassigned_order_ids[], pareto_candidate_id}`.
* `ParetoFrontierDTO` = `{job_id, solution_id, latest_solution_id, active_candidate_id, clustered,
  cluster_count, candidates[]}`; each candidate carries weights, totals, served/unserved counts,
  `frontier_status` (`FRONTIER`, `DOMINATED`, `SERVICE_DOMINATED`, BR-042), `engine_selected`, `applicable`
  and `not_applicable_reason`.

---

## 3. Evaluate and save

See `sequence-save-revision.puml`.

* **Validation (both calls):** a plan that repeats an order, or drops an order that was planned in the base,
  answers `400` (BR-094).
* **Evaluate:** the engine's `POST /optimization/evaluate` returns totals, violations and feasibility; the
  Entry API adds its own capacity violations and LEZ violations computed with the branch's **current**
  restrictions. An unreachable engine is `502`.
* **Save:** the base must still be the latest revision, else `409` with the latest id
  (`RevisionConflictException`, message *"… Latest revision id: N"*). The plan is evaluated again
  server-side; `!feasible` or any violation other than `UNASSIGNED_INCREASE` is `422` + `0042201`. Otherwise
  a new solution row is inserted with `type = MANUAL_EDITED`, `parent_solution_id = base`, status
  `SUCCESS` or `PARTIAL_SUCCESS` (by unassigned count), and its routes certified for LEZ.

## 4. Pareto apply

See `sequence-pareto.puml`. `ParetoService.apply` (BR-044) checks, in this order:

1. the candidate belongs to the job behind the solution, in the caller's branch — otherwise `400` + `0040402`
   (BR-044 says `404`);
2. the base is the latest revision — otherwise `409` with the latest id;
3. the candidate is already the base's active candidate — `200` with the base, nothing saved;
4. the candidate is `FRONTIER` and serves the same order set — otherwise `422` (`ORDER_SET_DIFFERS` for the
   order set);
5. insert the `PARETO_ALTERNATIVE` child and certify its routes for LEZ.

The client never recomputes cost or CO₂ for the chart (BR-046).

---

## 5. Errors

| Situation | Status | Code |
|---|---|---|
| unknown or foreign solution, unknown candidate | **400** | `0040402` — `SolutionService`, `RouteCustomizationService` and `ParetoService` throw `DataInvalidException`, which the handler maps to `400` (BR-044/BR-090 say `404`) |
| order or vehicle repeated, or the plan's order set differs from the base (*v1 excludes adding or removing stops*) | 400 | `0040001` |
| an order id of the plan does not exist | 400 | `0040402`, `resource` = *Orders [ids]* |
| base is not the latest revision | 409 | `0040901` with the latest id in the message |
| infeasible plan / hard violation / non-frontier candidate / different order set | 422 | `0042201` |
| engine unavailable | 502 | `0050002` |

---

## 6. Data touched

`solutions` (read; insert on save and apply), `routes`, `route_stops` (insert with the revision),
`unassigned_orders`, `pareto_candidates` (read), `orders` and `vehicles` (read). Existing solution rows are
never updated; the chain grows by inserts only (`state-solution-revision.puml`).

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{SolutionController,ParetoController}.java`,
`.../service/{SolutionService,RouteCustomizationService,ParetoService}.java`,
`.../dto/request/{CustomizeRoutesRequest,RouteSequenceRequest}.java`,
`.../dto/response/{SolutionDetailResponseDTO,RouteEvaluateResponseDTO,RouteViolationDTO,EvaluateTotalsDTO,RevisionDTO,ParetoFrontierDTO,ParetoCandidateDTO}.java`,
`.../entity/{Solution}.java`, `.../entity/enums/{SolutionStatus,SolutionType,ParetoFrontierStatus}.java`,
`GVRP_Entry_client_v2/src/app/features/main/orders/{route-view,timeline-view,pareto-view,expanded-view-dialog}/**`,
`01-requirements/03-business-rules.md`.
