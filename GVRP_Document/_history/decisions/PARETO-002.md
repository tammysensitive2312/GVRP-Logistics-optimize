# PARETO-002 — the Pareto tab in the client

**Problem.** The engine returned alternatives, but the planner had no way to see or choose them.

**Decision.**

- The solution screen gains a Pareto tab that draws the trade-off chart (served orders, cost, CO₂)
  from `GET /api/v1/solutions/{id}/pareto`.
- Selecting a point on the chart switches the displayed plan to that candidate's metrics and route
  without leaving the screen.
- Applying a candidate calls `POST /api/v1/solutions/{id}/pareto/{candidateId}/apply`; the response
  is a revision, and the screen then shows it as the current plan.
- The chart is drawn by the client from the returned metrics only; it never recomputes emissions or
  cost.
- Empty and single-point frontiers render as an explicit message rather than an empty chart.

**Date.** Accepted 2026-10-19 00:35 (+07:00) on the combined PARETO-002 + PARETO-003 tree; state
`done` rev 10.

**Source.** `work-items/PARETO-002/` (deleted): `design.md`, `verification.md`, `verification-r2.md`,
`review.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D05-solution/README.md` (screens, API rows),
`TC-D05-012`…`TC-D05-015`.

**Limitations recorded at closure.** Live paths not exercised: a `SERVICE_DOMINATED` candidate, the
422 and the 400-`0040402` apply paths, a clustered job (≥ 1000 orders), and the send-route picker with
a `PARETO_ALTERNATIVE` revision. Review noted a duplicate frontier GET after apply. The verification
runs left test data in the dev database (jobs #86/#87, solutions 65–67); it was not rolled back.
