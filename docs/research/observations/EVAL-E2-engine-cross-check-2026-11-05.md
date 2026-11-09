# EVAL-E2 — Independent evaluator vs stored Engine solutions

Date: 2026-11-05. Evaluator: `gvrp_eval` 0.1.0 (phase E2, on top of commit `4edb34f`).
Method: `sql/export_solution.sql` (read-only, `agent_reader`) → `gvrp_eval convert --from entry-export
--carbon-price 150000` → `gvrp_eval evaluate`. Exports and bundles stayed in the session scratchpad
(they hold customer coordinates) and are not committed.

## Unit facts used by the adapters

| Fact | Evidence | Status |
|---|---|---|
| Callback `distance_to_next` / `time_to_next` are metres / seconds from the solve matrix | `SolutionConverter.convertStops` reads `matrix.distance/time`; DB mean 2727 m / 196 s = 50.1 km/h, the GraphHopper speed cap | VERIFIED (source + data) |
| The solve-path extractor never sets these fields; only `SolutionConverter` and `RouteEvaluationService` do | grep of `GVRP_Engine_API/src/main` | VERIFIED (static) |
| A location missing from the matrix yields a silent `0.0` leg | `SolutionConverter.convertStops` fallback branch | VERIFIED (static); now flagged as `LEG_ZERO_DISTINCT` |
| `orders.service_time` is minutes | Engine multiplies by 60 (`OptimizationService.java:649`) | VERIFIED (static) |
| Depot stops are stored with `sequence_number` NULL; start depot has the earliest arrival, end depot a NULL departure | dbhub read, solution 72 | VERIFIED (data) |

## Results

| Solution (job) | Routes | Served | Feasible | Distance | CO2 | Duration | Cost | Leg warnings | Input drift |
|---|---|---|---|---|---|---|---|---|---|
| 71 (90) | 4 | 28/29 | yes | 0.001 % | 0.017 % | 0.008 % | 0.000 % | 0 | types 9003, 9006 edited later; no cost effect |
| 72 (91) | 12 | 100/100 | yes | 0.001 % | 0.000 % | 0.002 % | **10.138 %** | 1 | types 9003–9006 edited later |
| 75 (95) | 1 | 1/1 | yes | 0.007 % | 0.064 % | 0.623 % | 0.000 % | 0 | none |

Percentages are |evaluator − stored| / evaluator. The stored values are rounded to 2 decimals,
which explains the small residuals (largest: 0.62 h-rounding on a 0.62 h route).

Interpretation:

1. Distance, CO2, duration, served/unserved counts and vehicle counts are reproduced by an
   independent calculation for all three solutions, and every stored solution passes the
   evaluator's feasibility checks (capacity, time windows STRICT, skills, max distance/duration,
   shift). `PARTIALLY_VERIFIED`, `LIMITED` evidence: 3 solutions, 1 + 2 branches, n ≤ 100.
2. The cost difference of solution 72 is input drift, not a formula defect: see KI-033. Only
   solution 72 uses type 9004 (6 routes), the gap is 420,000 = 6 × 70,000 VND, and type 9004 was
   edited after the job ran. The old value is not recoverable, so the attribution is a
   `HYPOTHESIS` with strong circumstantial support.
3. One leg (157.8 m road vs 165.1 m great-circle, solution 72) is shorter than the great-circle
   distance between the raw coordinates. Likely GraphHopper snapping both points onto the road
   network; it is reported as a warning and the threshold was not loosened to hide it.
4. Under EM0 the CO2 of solution 75 is collinear with distance (one vehicle); 71 and 72 use mixed
   emission factors (branch 1000) and are not.

## Side findings

- KI-032: Entry stores the Engine's wait time in `route_stops.service_time`; `wait_time` stays NULL.
- KI-033: jobs store input ids only, so stored solutions are not reproducible after master-data edits.

## What this does not show

- Nothing about solution quality or the green objective; the evaluator only re-scores.
- LEZ compliance: LEZ skills are not exported (bundle note); the routes table has its own LEZ status.
- Pareto candidates other than the selected one were not evaluated (their plans are in
  `pareto_candidates.plan_json`; adapter not written).
