# EVAL-E4 — Evaluator anchored against published Solomon solutions

Date: 2026-11-10. Evaluator: `gvrp_eval` 0.1.0 (phase E4, uncommitted on top of `bdbaf29`).
Data: `GVRP_Benchmark/datasets/solomon` (56 instances and 56 best-known solutions from CVRPLIB;
source, checksums and conventions in its `SOURCE.md`).
Reproduce: `python -m unittest tests.test_solomon_anchor -v` in `GVRP_Benchmark/evaluator`.

## Question

Does the evaluator's schedule simulation, feasibility checking and distance aggregation agree with
a reference that shares no code and no data path with this repository?

## Method

Each published solution is converted with `gvrp_eval.solomon` and scored twice:

- `trunc1`: every leg is the Euclidean distance truncated to one decimal, travel time equals that
  value — the convention CVRPLIB states for this set;
- `exact`: untruncated double-precision Euclidean legs.

The evaluator's formulas are unchanged from E1 (written from DESIGN.md before this data was seen).

## Result

| Check (convention `trunc1`) | Result |
|---|---|
| Feasible (capacity, time windows STRICT, depot due date) | 56 / 56 |
| All 100 customers served, routes ≤ K | 56 / 56 |
| Total distance rounded to one decimal = published cost | 56 / 56 (largest difference before rounding < 5·10⁻⁷) |
| Leg sanity warnings | 0 |

| Observation (convention `exact`) | Result |
|---|---|
| Total distance vs published cost | longer in all 56, by 1.64 to 5.19 units |
| Feasible | 48 / 56 |
| Infeasible | R102, R105, R107, R108, R112, R211, RC101, RC105 — one late arrival each, 0.07 to 0.40 time units |

Reference values: C101 = 827.3 (10 routes) under `trunc1`; the same routes measure 828.9369 under
`exact`. R101 = 1637.7 (20 routes), RC101 = 1619.8 (15 routes) under `trunc1`.

## Interpretation

1. `VERIFIED` (scope: VRPTW with capacity, hard time windows, waiting, service time and a depot
   closing time; 56 instances, n = 100): the evaluator reproduces published costs exactly and
   accepts every published solution as feasible. This is the external anchor required by
   DESIGN.md §9.3. Evidence strength: STRONG for that scope.
2. The eight `exact`-infeasible solutions are not evaluator errors: those solutions are optimal for
   the truncated problem and use the slack that truncation creates. The evaluator detecting a
   0.07-unit lateness shows its time-window check is sensitive at that scale.
3. **Distance convention is part of the problem definition.** A cost or a feasibility verdict is
   only comparable with a reference under the same convention. Any Solomon result reported for
   this project must state it.

## Not covered by this anchor

- Costs, emissions, skills, LEZ skills, max distance / duration: the benchmark has none of them
  (covered only by hand-computed fixtures and the Engine cross-check, EVAL-E2).
- Road-network legs (GraphHopper): the benchmark is planar.
- The quality of this project's solver: no Engine run on Solomon instances exists yet. That needs
  an Engine path that accepts Euclidean matrices (GVRP_Benchmark/datasets/README.md, mapping
  point 1) and is a separate piece of work.
- The SINTEF best-known lists (hierarchical objective, untruncated distances): sintef.no was not
  reachable by an automated client on 2026-11-10.
