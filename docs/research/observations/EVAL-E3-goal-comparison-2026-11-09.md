# EVAL-E3 — Parity-gated comparison of optimization goals on stored solutions

Date: 2026-11-09. Evaluator: `gvrp_eval` 0.1.0 with `compare` (phase E3).
Method: stored solutions exported read-only (`sql/export_solution.sql`), converted with
`--carbon-price 150000`, re-scored and compared with `gvrp_eval compare` (E-D4 tolerance: 0 orders,
0.5 % delivered demand). Exports stay in the session scratchpad; only aggregates appear here.

## Data

Two instances on branch 1000 (heterogeneous fleet), each solved with several goals, seed 42,
Pareto off. Instance identity is the content hash of the exported bundle (meta excluded).

| Instance hash | Orders / vehicles | Solutions (goal) |
|---|---|---|
| `9317cec9d306` | 60 / 12 | 53 (COST), 55 (COST), 56 (BALANCED), 57 (CO2) |
| `e181f3d6a0ff` | 60 / 12 | 58 (COST), 59 (BALANCED), 60 (CO2) |

All runs are feasible and serve 53 of 60 orders, so every comparison passes the parity gate.

## Results (B − A, n = 1 per group)

| A → B | Vehicles | Distance | CO2 | Cost | Abatement cost |
|---|---|---|---|---|---|
| 53 COST → 55 COST (same seed) | 0 | 0 % | 0 % | 0 % | — |
| 53 COST → 56 BALANCED | 0 | +11.4 % | −12.1 % | −1.7 % | — (B cleaner and cheaper) |
| 53 COST → 57 CO2 | +4 (6→10) | +28.9 % | **−23.5 %** | +51.0 % | 147,331 VND/kg |
| 56 BALANCED → 57 CO2 | +4 | +15.7 % | −12.9 % | +53.6 % | 315,255 VND/kg |
| 58 COST → 59 BALANCED | 0 | −1.2 % | −1.2 % | −0.3 % | — |
| 58 COST → 60 CO2 | +2 (5→7) | −5.4 % | −5.4 % | +30.5 % | 405,325 VND/kg |

Group means (A, B): 53 = 181.324 km, 31.877 kg, 2,161,361 VND; 56 = 202.068 km, 28.008 kg,
2,125,156 VND; 57 = 233.775 km, 24.397 kg, 3,263,477 VND; 58 = 131.880 km, 46.158 kg,
3,316,798 VND; 59 = 130.238 km, 45.583 kg, 3,307,543 VND; 60 = 124.748 km, 43.662 kg,
4,328,613 VND.

## Interpretation

1. **Same seed, same input → identical plan** (53 vs 55): the solve is deterministic for this
   configuration. `VERIFIED` for this one pair only.
2. **On the mixed-fleet instance (`9317…`) the CO2 goal trades distance for cleaner vehicles**:
   −23.5 % CO2 while driving +28.9 % further and opening 4 more vehicles. This is the first
   measured evidence that the carbon term changes vehicle assignment and not only distance
   (relevant to STATE-CARBON-002 / RQ-001). `PARTIALLY_VERIFIED`, `LIMITED`: one instance, n = 1.
3. **On `e181…` CO2 is collinear with distance** (all used vehicles share one emission factor), so
   the CO2 goal can only shorten distance; it did (−5.4 %) by opening 2 more vehicles.
4. **The abatement cost is 147,000–405,000 VND per kg CO2**, i.e. about 1,000–2,700 times the
   configured carbon price (150,000 VND/t = 150 VND/kg). This is consistent with the documented
   weight asymmetry (OBJECTIVE_FUNCTION.md §7: the CO2 preset multiplies the carbon price by about
   10⁴ and nearly removes fixed and time costs). No business would buy CO2 reductions at that
   price; the CO2 preset is an extreme point, not an economic optimum. `EXTRAPOLATION` beyond these
   two instances.
5. **COST is not cost-minimal under re-scoring** on `9317…`: BALANCED is cheaper and cleaner
   (53 → 56). Possible causes, not distinguished: vehicle-type costs edited after the jobs ran
   (KI-033; the costs used here are today's), solver/report time-cost mismatch
   (OBJECTIVE_FUNCTION.md §6), or seed noise (n = 1). `HYPOTHESIS`.

## Limitations

- n = 1 per goal; the tool labels every comparison `n<5`. No claim here is statistically tested.
- Costs are re-scored with today's master data; all types were edited on 2026-10-31, after these
  jobs (input drift reported by the export). Distance and CO2 do not depend on those cost fields;
  emission factors were not observed to change (CO2 reproduced exactly for solution 72 in EVAL-E2).
- 7 of 60 orders are unserved in every run; parity holds because all runs leave the same number
  out, not because the instance is fully served.
- Pareto candidates could not be compared: only frontier candidates store `plan_json`.

## What is needed for a defensible experiment (E-series)

≥ 5 seeds per goal, bundles frozen right after each solve (KI-033), a pure distance-only baseline
(not available: `MINIMIZE_DISTANCE` maps to BALANCED weights), and a carbon-price sweep instead of
the three presets.
