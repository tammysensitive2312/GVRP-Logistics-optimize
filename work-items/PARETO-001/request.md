# Request — PARETO-001 (Pareto branch: clustering, frontier transport, persistence, apply)

Source: user message 2026-10-18 12:22 (+07:00) in the Claude session, via `/delegate`:

> "hiện tại phần tối ưu bằng cách clustering mới chỉ cho một nhánh single, kiến trúc
> nghiệp vụ của dự án hiện tại cũng khá ổn rồi. Tôi muốn bạn điều tra luồng code hiện
> tại của nhánh Pareto, liên kế hoạch áp dụng clustering cho nhánh, và thiết kế giao
> diện hiển thị cho pareto."

Business decisions taken by the user on 2026-10-18 (AskUserQuestion, same session):

| Question | Decision |
|---|---|
| What can the planner do with frontier points? | **View and switch**: every frontier point keeps full routes; choosing another point makes its routes the active plan. |
| Points serving a different number of orders | **Service first**: dominance is only compared among points with the maximum served-order count; points serving fewer orders are shown but never selected or applied. |
| When does clustering apply to Pareto? | **Same as single branch**: N >= `CLUSTER_FIRST_ORDER_THRESHOLD` and C > 1; computed once and shared by every weight point. |
| Work split | **Two work items**: PARETO-001 backend (this run), PARETO-002 client v2 UI. |

## Requirements

- PAR-001: The Pareto branch uses the same cluster-first pipeline as the single branch
  (eligibility, K-means, merge, vehicle assignment, sparse matrix mask). The cluster
  assignment and the matrix are computed once per job and reused for every weight point;
  every weight-point solve applies `ClusterRouteConstraint` and passes the assignment to the
  unassigned-order diagnosis.
- PAR-002: Frontier classification is service-first: candidates with fewer served orders
  than the maximum are `SERVICE_DOMINATED`; among the rest, (cost, CO2) dominance yields
  `FRONTIER` / `DOMINATED`; identical objective vectors keep only the lowest index on the
  frontier.
- PAR-003: Selection of the engine-recommended point uses min-max normalisation without the
  `+1` denominator bias, with a guard for a zero range, and a deterministic tie-break.
- PAR-004: The completion callback carries every candidate (metrics, weights, status,
  selected flag) and full routes + unassigned orders for every `FRONTIER` candidate.
  Single-objective payloads are unchanged.
- PAR-005: Engine progress for a Pareto job reports the current scenario (1-based) and the
  scenario count, and an overall percent across scenarios.
- PAR-006: Entry persists every candidate of a Pareto job (branch-scoped), and links the
  engine-selected candidate to the job's root solution.
- PAR-007: `GET /api/v1/solutions/{id}/pareto` returns the candidates of the job behind the
  solution's revision chain, with per-candidate `applicable` / `not_applicable_reason`
  relative to the latest revision. Empty candidate list for non-Pareto jobs.
- PAR-008: `POST /api/v1/solutions/{id}/pareto/{candidateId}/apply` creates a new solution
  revision of type `PARETO_ALTERNATIVE` from the candidate's stored plan, parented on the
  latest revision. 409 when `{id}` is not the latest revision; 422 when the candidate is not
  on the frontier or its assigned-order set differs from the latest revision's; no order
  status changes; idempotent when the candidate is already active.
- PAR-009: Tenant isolation — another branch's solution or candidate id answers exactly like
  a missing one (404) for both endpoints, with tests.
- PAR-010: Existing tests stay unchanged and green; new behaviour is covered by DB-free
  tests (no MySQL, no GraphHopper graph).

## Exclusions

- Client v2 UI (PARETO-002).
- Changing the weight sampler (N = 4, p = 2.0), the cluster eligibility threshold, or
  `VehicleClusterAssigner` (it ignores vehicle type / emissions; noted as a scientific
  limitation, not changed here).
- Re-transitioning order statuses when switching plans (the order state machine has no
  `ON_ROUTE <-> UNASSIGNED` edge); points with a different order set are view-only.
- Benchmarks of clustered vs dense Pareto quality (research follow-up, not this run).

## Acceptance expectations

- `mvnw.cmd -B clean test` green in `GVRP_Engine_API` and `GVRP_Entry_API`, with the new
  tests listed in design.md.
- Manual test on the local stack (user, after review): a Pareto job on a small order set
  produces candidates in `pareto_candidates`; GET returns them; applying a frontier point
  creates a `PARETO_ALTERNATIVE` revision; a second branch gets 404.

## Open checks before manual test

- Run `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-18-pareto-candidates.sql`
  on the local DB (the `solutions.type` MySQL ENUM is not widened by `ddl-auto=update`).
