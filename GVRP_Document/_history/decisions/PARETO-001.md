# PARETO-001 — Pareto alternatives in the engine

**Problem.** The planner received one route plan per job. Cost and CO₂ were traded off by fixed
weights, which hid the alternatives that a planner would want to compare.

**Decision.** The Engine API computes several plans for the same job and returns the non-dominated
set, so the planner can compare alternatives on served orders, cost and CO₂.

- Eligibility is service-first: a plan is only eligible if it serves the maximum number of orders
  (and, after PARETO-004, the maximum delivered demand among those). Cost and CO₂ then rank the
  eligible plans.
- Alternatives come from sampling the objective weights: `ParetoWeightSampler` draws N = 4 weight
  vectors with exponent p = 2.0 over the cost/CO₂ trade-off.
- For large instances the problem is clustered first (threshold and target in
  `03-basic-design/D08-engine/README.md`); the frontier is then computed per cluster and merged.
- Each alternative is persisted as a `pareto_candidates` row with its metrics,
  its `ParetoFrontierStatus` (`FRONTIER`, `DOMINATED`, `SERVICE_DOMINATED`) and a serialized payload.
- Applying an alternative creates a new solution revision of type `PARETO_ALTERNATIVE` and re-checks
  that the chosen plan serves the same order set; otherwise the apply is refused.
- Carbon pricing used for the trade-off is 150 000 VND per tonne.

**Date.** Accepted 2026-10-18 18:03 (+07:00); state `done` rev 7.

**Source.** `work-items/PARETO-001/` (deleted): `request.md`, `design.md` (23 KB), `verification.md`,
`review.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D05-solution/diagrams/sequence-pareto.puml`,
`BR-041`…`BR-046`, `TC-D04-016`, `TC-D05-012`…`TC-D05-015`, `TC-D08-003`, `TC-D08-004`, and the engine parameters in
`03-basic-design/D08-engine/README.md`.

**Limitations recorded at closure.** The full Engine suite exceeds one hour because of real-matrix
benchmark tests; three benchmark tests were excluded from the accepted run. A unique constraint on
`(job_id, candidate_index)` was proposed and is not in the code. The comparison of clustered and dense
frontiers on a ~1000-order instance with an independent emission evaluator has not been run.
