# SYNC-001: Thesis report vs. engine code — gap analysis and core rewrite plan

Date: 2026-11-01. Author: Claude (orchestrator session). Type: static comparison.
No solver run, no test run and no database query were made for this note.

- Report: "Vu Dinh Truong 20210215P.docx" (graduation thesis, defended; finished 2026-02).
  The report itself is not in the repository. Equations in it are images; they were read
  visually (Sections 2.2.1, 2.2.2, 5.1, 5.2.2).
- Report-era code baseline: `574eb12` (2026-01-22), the last engine commit before the report.
- Current code: `c200b2a` on `audit/sec-audit-001` (engine identical to `master` for these files).
- Engine diff since baseline: 91 files, +10 870 / −459 lines (`git diff --stat 574eb12 HEAD -- GVRP_Engine_API/src`).

Status words follow AGENTS.md §5. Nothing here is promoted to `PROJECT_STATE.md`.

Gap direction codes:

- **R→C**: the report claims something the code does not do (report overclaims, or code must change).
- **C→R**: the code does something the report does not describe (report must be updated).
- **X**: both are wrong or internally inconsistent; needs a decision, not a copy.

---

## 1. Objective and emission model (report §2.2, §5.1)

| ID | Dir | Report says | Code does (current) | Evidence | Status |
|---|---|---|---|---|---|
| G-01 | R→C | Ch.1 §1.2.2/§1.4: fuel/emission model uses distance, **load and speed**; GraphHopper custom weighting integrates **load, speed, slope** ("Green Routing"). §6.2: adjust speed `v` in the emission formula. | Emission is `E = d · e_k / 1e6` kg: static per-type factor × distance. No load, speed or slope term anywhere. The GraphHopper custom model only caps speed at 50 km/h and halves `road_access == DESTINATION`; it is a car-time profile, not an emission weighting. | `GreenVRPCostCalculator.java:116-117,139-150`; `GraphHoperDistanceProvider.java:102-121` | VERIFIED (static) |
| G-02 | X | §2.2.1: `E = d·EF/10^6` (kg), `C_env = E · P_carbon` (P per kg implied). §5.1 formula `/10^6`. | Report era: `CARBON_PRICE_PER_KG = 100000` VND/kg (comment says 10 000). Now: `CARBON_PRICE_PER_TON = 150000` VND/t and `/1e9`. The two prices differ by ~667×. Report numbers and examples are tied to neither cleanly. | `574eb12:AppConstant.java:15`; `AppConstant.java:14`; KI-030 | VERIFIED (static) |
| G-03 | X | §2.2.2: `C_unit = C_fuel·W_cost + C_env·W_co2`, `W_cost + W_co2 = 1` — a trade-off between fuel and carbon. | `w_c` also scales **fixed cost, time cost and waiting cost**; only carbon is scaled by `w_e`. Since arg-min is scale-invariant, each weight pair is just a carbon-price multiplier `μ = w_e/w_c` on the whole money objective. ECO (`w_c = 1e-4`) gives `μ ≈ 9 999`; it also makes vehicle opening ~free. The report's formula hides this. | `GreenVRPCostCalculator.java:123-133`; OBJ-001; RL-2026-005 | VERIFIED (static + arithmetic) |
| G-04 | R→C | §2.3, §5.1: raising `W_co2` makes the solver prefer low-emission vehicles **or lower-consumption roads**, "even when distance is longer". | Road choice cannot change: the matrix is the same car-time shortest path for every weight. Only vehicle choice, fleet size and sequence change. With a uniform `e` (9/10 live branches use 12.3) the carbon term is proportional to distance. | `DistanceMatrixService` single profile; VQ-003 live reads; KI-008 | VERIFIED (static); live data PARTIALLY_VERIFIED (2026-10-14 reads) |
| G-05 | C→R | Not described. | `MINIMIZE_DISTANCE` goal in Entry maps to 0.5/0.5, identical to `BALANCED`. There is **no pure distance baseline** arm, although the report's evaluation goal (§1.2.2) is "traditional vs green". | `OptimizationConfigMapper.java:101-106` | VERIFIED (static) |
| G-06 | C→R | Not described. | Reported `totalCostVnd` = fixed + fuel + time, **without** carbon cost; carbon cost is reported separately. Solver cost (`jspritCost`) ≠ reported cost. Time cost charges whole route duration including service and waiting. | `SolutionMetricsCalculator.java` (calculate); OBJECTIVE_FUNCTION.md §1 | VERIFIED (static) |
| G-07 | C→R | Not described. | Null `emissionFactor` now throws NPE in the solver path (report era defaulted to 200 g/km); reporting path still defaults to 200. Inconsistent defaults. | `GreenVRPCostCalculator.java:116` vs `:144-146`; KI-031 | SUSPECTED (KI-031) |

## 2. Multi-objective / Pareto (report §2.3, §5.2)

| ID | Dir | Report says | Code does (current) | Evidence | Status |
|---|---|---|---|---|---|
| G-08 | C→R | 3 presets: Economic (1, 0), Balanced (0.5, 0.5), Eco (0.0001, 1). | Report era ran **4** presets (PURE_ECO duplicated ECO exactly since `EPSILON = 1e-4`). Now: `ParetoWeightSampler` with `N = 4`, `p = 2`: `w_c = (k/4)²` → 5 points, `μ ∈ {9999, 15, 3, 0.78, 0}`. `ObjectivePreset` is no longer used by the Pareto branch. | `574eb12:ObjectivePreset.java`; `ParetoWeightSampler.java`; `AppConstant.java:47-55` | VERIFIED (static) |
| G-09 | R→C | §2.4.1, §5.2.2 Step 1: scenarios run **in parallel** ("chạy song song", "kiến trúc xử lý song song"). | Scenarios run **sequentially** in a `for` loop, then and now. Default jsprit threads = 1. | `OptimizationService.java:394-439,726` | VERIFIED (static) |
| G-10 | C→R | §5.2.2 Step 2: plain 2-objective dominance on (cost, CO2). | Service-first lexicographic classification: only candidates with max served orders, then max served demand, can be FRONTIER; others are `SERVICE_DOMINATED`; duplicate vectors keep lowest index. | `ParetoFrontierSelector.classify` (PARETO-004, `6dd718c`) | VERIFIED (static) + unit tests exist (not run here) |
| G-11 | X | §5.2.2 Step 3: min-max score formula (no `+1`). | Report-era code used a `+1` denominator (per selector javadoc); current code matches the report formula with zero-range guard and tie-break. Report formula is now correct; report-era code was not. | `ParetoFrontierSelector.select` javadoc | PARTIALLY_VERIFIED (old code not re-read line by line) |
| G-12 | C→R | Selected solution only. | All frontier alternatives are returned with routes and unassigned diagnosis; Entry persists candidates and has an "apply alternative" API. | `OptimizationService.java:463-511`; PARETO-001 | VERIFIED (static) |
| G-13 | R→C | §5.2.3: "to cut 20% CO2 accept +5% cost" — presented as an experimental result. | No experiment record, CSV or log in the repository supports this number. | `GVRP_Benchmark/results/` holds only `schema.csv` | NOT_VERIFIED |
| G-14 | X | Weighted-sum frontier is "a good approximation" for 2 objectives [Marler & Arora]. | Weighted sum finds only supported (convex-hull) points; with 5 fixed samples and a stochastic heuristic, many points can be dominated or identical. No hypervolume/coverage measurement exists. | design; no experiment | HYPOTHESIS |

## 3. Solver and scale (report §2.4, §4, §5.3)

| ID | Dir | Report says | Code does (current) | Evidence | Status |
|---|---|---|---|---|---|
| G-15 | C→R | Not described. | **Cluster-first, route-second** (Fisher–Jaikumar style) for `N ≥ 1000` and `C = min(⌈N/150⌉, |V|) > 1`: K-means++ → small-cluster merge (union-find) → demand-proportional vehicle quota (Hamilton) → hard `ClusterRouteConstraint`. This is a **problem approximation** (AGENTS.md §9): cross-cluster service is forbidden. Measured: 100/1000 orders unassigned on `vn_n1000` with spare fleet. | `OptimizationService.java:200-293,980-1002`; `clustering/*`; LIMITATIONS.md L1 | VERIFIED (static); L1 numbers from a documented run, not re-run |
| G-16 | C→R | Not described (§2.4.2 only says CH makes matrices "instant" for hundreds of points). | Block-diagonal cost matrix (`O(N·S)` memory instead of `O(n²)`), `MatrixMask` with finite sentinel `1e9 m`, `NoPrunedEdgeConstraint` (CRITICAL) and post-solve sentinel/max-distance asserts; zero-copy `MatrixBasedTransportCosts` adapter. | `distance_matrix/*`; `OptimizationService.java:1018-1048` | VERIFIED (static); semantic equivalence NOT_VERIFIED (STATE-MATRIX-001..003) |
| G-17 | C→R | VRPTW + CVRP only. | Added hard constraints: per-type `maxDistance`, vehicle/order **skills**, LEZ zone skills, shift length from `maxDuration` (vehicle start fixed at 08:00). Unassigned-order diagnostic service. | `OptimizationService.java:579-670,1083-1128`; `UnassignedOrderDiagnosticService` | VERIFIED (static) |
| G-18 | R→C | §5.3: solver work "5–15 minutes"; implied bounded run time. AGENTS.md §15 requires a time limit. | `timeoutSeconds` is accepted but the `TimeTermination` code is **commented out**; only iterations (default 2000) and cancel terminate. | `OptimizationService.java:771-779`; RUNBOOK "Bước 5"; `TimeTerminationBugTest` | VERIFIED (static) |
| G-19 | C→R | Not described. | Fixed seed option (`OptimizationConfig.seed`, reproducible only with 1 thread); progress listener; job cancel registry; file-system result spool with callback retry. | `OptimizationConfig.java:27`; `JobRegistry`; `ResultSpool` | VERIFIED (static) |
| G-20 | C→R | Not described. | `/optimization/evaluate` — independent route re-evaluation from consecutive GraphHopper legs (O(n) calls) with the same time semantics as the extractor. Candidate for the **common independent evaluator** required by AGENTS.md §9. | `RouteEvaluationService` | VERIFIED (static); independence from solver cost NOT_VERIFIED |
| G-21 | R→C | §1.3.4: experiments on real data in inner Hanoi; Ch.6: tested on "standard datasets". | Datasets present: `vn6183` (frozen export), `vn_n1000`, `vn_n5000`. Solomon/Homberger/Schneider folders are planned but **absent**; no adapter. No standard-benchmark result exists. | `GVRP_Benchmark/README.md` status table; `datasets/` listing | VERIFIED (static) |
| G-22 | R→C | §1.5 says Chapter 5 is the experimental evaluation. | The report has **no experiment chapter**: Ch.5 is "contributions", Ch.6 conclusion. Objective §1.2.2 "Về đánh giá" is not delivered. | report structure | VERIFIED |

## 4. Non-core (report §4) — lower priority

| ID | Dir | Note |
|---|---|---|
| G-23 | X | Table 4.1 says ~8 000 LOC / ~120 classes; §4.3.2 text says >150 000 LOC. Engine alone is ~13 600 lines incl. tests now. |
| G-24 | C→R | Security/tenant isolation, auth, LEZ, map/client v2, callback idempotency, retention sweeper — none in report. Mention briefly only. |
| G-25 | X | Report cites "Amazon S3" storage and messaging; current transport is HTTP callback + local spool. Verify before keeping. |

---

## 5. What this means

1. The report's academic core (§2.2 model + §5.1/5.2 Pareto) describes an objective that is
   simpler and partly different from both the report-era and the current code (G-02, G-03, G-08, G-09).
2. The report claims a load/speed/slope-aware emission model that **never existed** (G-01, G-04).
   This is the largest scientific overclaim and must be either implemented or removed.
3. The code's largest new academic content — cluster-first decomposition and block-diagonal
   matrix (G-15, G-16), service-parity Pareto (G-10) — is absent from the report, and is an
   approximation whose cost (unassigned orders) is measured but not yet controlled.
4. No reproducible experiment backs any quantitative claim (G-13, G-21, G-22).

---

## 6. Core rewrite plan (proposal — needs user decisions D1–D4 before any code change)

### Decisions required

- **D1 — Direction.** (a) Rewrite the report to match the current code and its honest limits; or
  (b) rewrite the engine core to deliver what the report promises (load-aware emissions,
  parallel Pareto), then rewrite the report. Recommendation: (b) for the objective/emission
  core only; (a) for everything else.
- **D2 — Objective formulation.** Replace "weights scale everything" with
  `min  C_money(x) + λ · P · E(x)` (λ = carbon-price multiplier, explicit, reported), and keep
  the `(w_c, w_e)` UI as a mapping to λ. Alternative: ε-constraint on emissions. Either changes
  `MeasuredLawsTest` pinned laws → needs explicit approval per AGENTS.md §14.
- **D3 — Emission model.** Keep static factor as baseline `M0`; add a load-dependent model `M1`
  (fuel/emission rate linear in carried load, CMEM/COPERT-style simplification, parameters cited
  from literature). Optional `M2` with arc speed from the GraphHopper time matrix. Needs a cited
  parameter source (LITERATURE.md) before implementation.
- **D4 — Cluster-first.** Keep as a scale mode reported as an approximation, and add a
  boundary repair step (re-insert unassigned orders flat over neighbouring clusters) — or
  report it as-is with L1 as a limitation.

### Phase A — Engine core (research-grade), in order

| Step | Change | Closes | Verification |
|---|---|---|---|
| A1 | `EmissionModel` interface; `StaticFactorModel` (current behaviour, bit-identical); single `SolutionEvaluator` used by every arm and by the Pareto selector. | G-06, G-07, G-20 | New unit tests; existing tests unchanged and green |
| A2 | Explicit objective `C_money + λ·P·E`; `w`→λ mapping; real `DISTANCE_ONLY` baseline arm. | G-03, G-05 | Hand-computed objective tests; pinned-law test changes listed for approval |
| A3 | `LoadDependentModel` inside jsprit: custom `SolutionCostCalculator` + load state updater + soft activity constraint for insertion marginal cost. | G-01, G-04 (partially) | Unit tests vs hand calculation; small instance brute-force check |
| A4 | Restore wall-clock `TimeTermination`; iteration budget rule by `N`; record effective config in the result. | G-18 | `TimeTerminationBugTest` + new test |
| A5 | Pareto: run weight points in parallel (bounded pool), λ grid instead of `(k/N)²`; optional adaptive bisection between neighbours. | G-08, G-09, G-14 | Determinism with seeds; frontier unit tests |
| A6 | Cluster-first boundary repair (if D4 says so). | G-15 | Unassigned rate on `vn_n1000` before/after |

### Phase B — Experiments (GVRP_Benchmark, under EXPERIMENT_PROTOCOL.md)

| Exp | Question | Arms / data | Metrics |
|---|---|---|---|
| E0 | Is the solver sound? | Solomon C1/R1/RC1 (100 nodes) via a new adapter; distance-only objective | gap to BKS, runtime, 5+ seeds |
| E1 | Does the carbon objective lower **independently evaluated** CO2 at equal service? (RQ-001) | DISTANCE_ONLY vs COST vs λ-grid; uniform-e branch (control) vs Huge 1 hetero fleet | served, delivered demand, distance, CO2 (one evaluator), money cost, vehicles, runtime |
| E2 | Does the load-aware model change routes/CO2 vs static? | M0 vs M1 solved, both evaluated with M1 | same + attribution Δdistance/Δvehicles/Δsequence |
| E3 | Frontier quality | old 5-point sampler vs λ grid vs adaptive | hypervolume, #non-dominated points, runtime |
| E4 | Cost of cluster-first (RQ-002) | flat vs cluster-first (± repair) on vn_n1000; vn_n5000 cluster-only | objective gap, unassigned, memory, runtime |
| E5 | Scaling (RQ-003) | N ∈ {100, 500, 1000, 2000, 5000} | runtime, memory; extrapolation labelled |

### Phase C — Report v2

Rewrite §2.2 (model with units and λ), §2.3/§5.2 (service-parity Pareto, sampler), new §5.x
(cluster-first + block matrix as approximation, with L1), new experiment chapter from Phase B,
and remove or qualify G-01/G-04/G-09/G-13 claims.

### Pre-existing state to record before Phase A starts

- Run the full engine test suite once and record failing test names (AGENTS.md §14).
- Open work items (SEC-AUDIT-001, LEZ-001, …) touch neighbouring engine files
  (`OptimizationService` skills/LEZ); sequence Phase A after or around them.
