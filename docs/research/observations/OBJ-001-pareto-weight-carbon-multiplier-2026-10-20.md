# OBJ-001: What the Pareto weight points actually change in the objective

Date: 2026-10-20. Author: Claude (orchestrator session). Type: static derivation plus arithmetic.
No solver run and no database query were made.

Trigger: an external review by Claude Science of the thesis report against the engine source.
It claimed that the current weights do not measure a cost-versus-environment trade-off, because
only the ECO point has a carbon multiplier μ ≈ 10,000 and the other four have μ ≤ 15. This note
re-derives that claim from the code.

Related: STATE-OBJECTIVE-001/002, STATE-CARBON-002 (`PROJECT_STATE.md`), RQ-001.

## 1. Objective as implemented

Source: `GreenVRPCostCalculator.buildGreenVehicleType`, `ParetoWeightSampler.generate`,
`AppConstant` (revision: working tree on `fix/test-client-001`, based on `master` @ `21d2805`
plus CI-CLIENT-001).

For a vehicle of type v, the cost that jsprit minimises is:

```
c_v(route) = w_c · F_v                                   (fixed, per used vehicle)
           + Σ_arcs d · [ w_c · f_v + w_e · e_v · P / 1e9 ]   (d in m, f_v in VND/m)
           + Σ_arcs t · w_c · h_v                         (t in s, h_v in VND/s)
```

- f_v: fuel cost per metre, `costPerKm / 1000`.
- e_v: emission factor in g/km.
- P: `CARBON_PRICE_PER_TON`, 150,000 VND/t.
- h_v: time cost per second.
- Weights: w_c = max((k/4)², EPSILON), w_e = 1 − w_c, with k = 0..4 and EPSILON = 1e-4.

**VERIFIED (static inspection):**

- Every monetary term (fixed, fuel and time cost) is multiplied by w_c.
- Only the carbon term is multiplied by w_e.
- The arg-min of the objective is unchanged when the whole objective is multiplied by a
  positive constant.
- Therefore each weight point is fully described by one number, the effective carbon-price
  multiplier μ = w_e / w_c. Carbon is valued at μ·P instead of P.

## 2. Effective multiplier per weight point

| Point | w_c | μ = w_e / w_c |
|---|---|---|
| K0 | 0.0001 (EPSILON) | 9,999 |
| K1 | 0.0625 | 15 |
| K2 | 0.25 | 3 |
| K3 | 0.5625 | 0.78 |
| K4 | 1 | 0 |

**VERIFIED (arithmetic, recomputed).** This matches the external review.

## 3. Size of the carbon term relative to the fuel term on the same arc

The ratio is R = μ · e · P / (1e6 · f), with f in VND/km.

f (fuel cost) is **not verified**: it depends on per-branch `vehicle_types.cost_per_km` in the
database, which was not queried. The table therefore shows two assumed values. The values of e
are the emission factors that the review reports for Branch Huge 1 (105 / 190 / 350 g/km); those
are not verified here either.

| Point | f=5000, e=105 | f=5000, e=190 | f=5000, e=350 | f=8000, e=105 | f=8000, e=190 | f=8000, e=350 |
|---|---|---|---|---|---|---|
| K0 | 31.5 | 57.0 | 105.0 | 19.7 | 35.6 | 65.6 |
| K1 | 0.047 | 0.086 | 0.158 | 0.030 | 0.053 | 0.098 |
| K2 | 0.009 | 0.017 | 0.032 | 0.006 | 0.011 | 0.020 |
| K3 | 0.002 | 0.004 | 0.008 | 0.002 | 0.003 | 0.005 |
| K4 | 0 | 0 | 0 | 0 | 0 | 0 |

The break-even multiplier μ* = 1e6·f / (e·P), where carbon equals fuel per km:

- f = 8,000 VND/km: μ* is 508, 281 and 152 for e = 105, 190 and 350.
- f = 5,000 VND/km: μ* is 318, 175 and 95 for the same e values.

Even then, the full monetary objective also contains fixed and time costs, so the true break-even
point is higher still.

### Reading

**EXTRAPOLATION** until it is measured on a solver run:

- Only K0 lets carbon dominate. In K0 the monetary terms are about 1e-4 of their nominal value,
  so K0 approximately minimises Σ e_v·d_v.
- K1–K4 all solve close to the same monetary-cost problem. The carbon term adds at most about
  16% of the fuel term (K1, e = 350, f = 5,000), and much less for other points and vehicles.
- Expected effect: the five points collapse to about **two distinct solutions** (K0, and one
  near-monetary-optimum for K1–K4), with solver noise between K1–K4. The quadratic sampler
  (p = 2), meant to "densify the non-linear region", places four of five points on the side where
  carbon barely matters.

### Supporting observation

PARTIALLY_VERIFIED, from a single run:

- `work-items/PARETO-002/verification.md` records a live job whose five candidates were
  identical (the "degenerate five-identical-candidate case").
- The job-85 chain had 4 frontier points that sat close together in cost.

Both are consistent with the reading above. They are not a controlled test: one dataset, one
seed, and a fleet whose emission factors were not recorded.

### Fleet-uniform emission factor

If every vehicle in a branch has the same e, the carbon term is e·P·Σd, which is proportional to
total distance. K0 then approximately minimises distance, and "the CO2 axis" is a distance axis.

The external review reports that 9 of 10 branches use one emission factor. This is
**NOT_VERIFIED**: it needs a read-only `SELECT branch_id, emission_factor, COUNT(*)` on vehicle
types.

## 4. Consequences for the thesis claims

- A Pareto frontier produced with this sampler does **not** demonstrate a cost-versus-carbon
  trade-off by itself. It mostly contrasts "carbon ×10,000" with "money only".
- STATE-CARBON-002 stays NOT_VERIFIED. Its existing limitation (the vehicle-opening economics
  change about 35× under ECO reweighting, so different routes are possible) is still correct.
  This note sharpens it: that change is confined to K0.
- Any claim of the form "X % less CO2 for Y % more cost" needs a controlled experiment (§5) and
  service parity. Since PARETO-004, the engine frontier requires equal served orders and equal
  delivered demand.

## 5. Proposed hypotheses and experiment (not yet adopted)

- **H1 (sampler degeneracy).** With the current sampler, K1–K4 produce solutions whose
  independently evaluated CO2 and monetary cost differ by less than the run-to-run spread of
  repeated K4 runs (same seed set).
  - Falsified if any of K1–K3 differs from K4 by more than the 95% spread.
- **H2 (carbon-price sensitivity).** On a fleet with non-uniform e (Branch Huge 1 per the review,
  to be confirmed), sweeping μ on a log grid (0, 10, 30, 100, 300, 1,000, 3,000, 10,000) moves
  CO2 monotonically. A visible change appears only once μ is of order μ*.
  - Falsified if CO2 does not change across the grid beyond seed spread.
- **H3 (distance proxy).** On a fleet-uniform branch, the μ sweep changes CO2 and distance by the
  same relative amount, because the carbon term is a distance term.

Design constraints:

- Follow `EXPERIMENT_PROTOCOL.md`.
- Use at least 5 seeds per μ, the same iteration budget, the same dataset and the same matrix.
- Evaluate every route set with the same independent evaluator. `SolutionMetricsCalculator` is
  shared by all arms; its emission model is distance × e only, so state that boundary.
- Report the following for every arm: served orders, unserved orders, delivered demand,
  distance, CO2, monetary cost and runtime.
- Parameterise the sweep by μ directly, not by w_c. This makes the weight points comparable
  across branches with different fuel prices.

Possible engineering follow-up after H1/H2, a design decision and not part of this note:
sample μ log-uniformly (or around μ*) instead of w_c = (k/N)².

## 6. Open checks

1. DB (read-only): `cost_per_km`, `cost_per_hour`, `fixed_cost` and `emission_factor` per
   vehicle type and branch. Fills in f and e, and verifies or refutes the "9 of 10 branches"
   claim.
2. Engine log of a Pareto job: the five candidates' cost, CO2 and distance, to check the
   collapse prediction.
