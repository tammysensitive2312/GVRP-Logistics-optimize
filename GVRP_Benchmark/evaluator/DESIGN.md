# Independent Route-Set Evaluator — Design

Status: phases E1–E3 implemented (E1, E2 2026-11-05; E3 2026-11-09; `gvrp_eval` 0.1.0); E4 adapter
and the Solomon anchor implemented 2026-11-10 (EM1 load-dependent model not started). Design date: 2026-11-04. Author: Claude Code, at the user's request
("Làm bước 2" — build the independent evaluator before any core-algorithm change).

Targets these open project claims (`docs/research/PROJECT_STATE.md`):

| Claim | Why it is blocked today |
|---|---|
| STATE-CARBON-003 — baseline and treatment evaluated by the same independent evaluator | No such evaluator exists. Reported CO2 comes from `SolutionMetricsCalculator`, which reuses `GreenVRPCostCalculator.calculateRouteCO2` — the same code family as the objective. |
| STATE-SERVICE-001 — experiments record enough service metrics | `results/schema.csv` has served/unassigned counts but no delivered demand, no feasibility flag, no violation counts. |
| STATE-CARBON-002 / RQ-001 — does carbon change route choice | Cannot be measured without the two items above. |

## 1. What "independent" means here

AGENTS.md §9 and EXPERIMENT_PROTOCOL §11 require recomputing metrics outside the solver.
The evaluator is independent in **calculation**, not in **input data**.

| Shared with the solver (input data) | Not shared (re-implemented) |
|---|---|
| Instance data: orders, vehicles, vehicle types, depots | Route aggregation (distance, time, load) |
| Leg distance and time from the road network | Schedule simulation (arrival, wait, service, departure) |
| | Feasibility checks |
| | Emission and cost formulas, units, defaults |

Rules that keep it independent:

1. Separate language and code base: Python 3 standard library only, under
   `GVRP_Benchmark/evaluator/`. No import of, call into, or copy from Engine or Entry code.
   Formulas are written from this document, not from Java sources.
2. No silent defaults. A missing emission factor, unknown unit or unknown order id is an error
   (contrast: `calculateRouteCO2` substitutes 200 g/km while the solver throws — KI-031).
3. Every number carries a declared unit in the input schema. Units are never inferred from
   field names (AGENTS.md §8); a missing `unit` field fails validation.
4. Residual dependency, stated openly: leg distances/times come from the same GraphHopper
   data. Level 1 bounds this with sanity checks (§4.7). Level 2 (later) re-routes legs with an
   independently configured router or a different provider and reports the difference.

## 2. Inputs

### 2.1 Instance bundle (frozen, hashed)

One JSON file per instance, built from an `instances/*.json` id list plus a read-only export
of the referenced rows. Fields (all units explicit):

```text
meta: instance_id, source (corpus sha256 | benchmark name), created_at, sha256 of this bundle
params: coordinates (wgs84|planar, required), carbon_price_vnd_per_t, emission_model_id, time_window_mode (STRICT|SOFT),
        planning_date, ev_upstream_kg_per_kwh (null = tank-to-wheel boundary)
depots[]: id, lat, lng
vehicle_types[]: id, capacity {value, unit}, fixed_cost_vnd, cost_per_km_vnd, cost_per_hour_vnd,
        max_distance_km|null, max_duration_h|null, emission_factor {value, unit, provenance},
        skills[]
vehicles[]: id, vehicle_type_id, start_depot_id, end_depot_id, skills[], lez_skills[],
        shift_start|null, shift_end|null   (the Engine fixes start 08:00 and end = start + max_duration_h,
        `OptimizationService.java:610-626`; the adapter must write these explicitly)
orders[]: id, lat, lng, demand {value, unit}, tw_start|null, tw_end|null, service_time_s,
        required_skills[], lez_skills[]
```

Customer names, phones and addresses are not part of the bundle (AGENTS.md §15 logging rule);
orders are referred to by id only.

### 2.2 Route set (what a solver produced)

```text
meta: producer (engine | ortools | manual | ...), producer_version (git sha), run_id,
      seed, instance_sha256
routes[]: vehicle_id, start_time, stops: [order_id, ...] in visit order,
          legs: [{distance, time}] — len(stops)+1 legs, depot → … → depot
leg_units: {distance: m|km, time: s|min}   (required; no unit is assumed)
unassigned: [order_id, ...]
reported: optional producer-side metrics, kept only to compute the discrepancy
```

### 2.3 Adapters

Adapters convert producer formats into this schema:

- **Engine callback payload** (`SolutionConverter` output): `distance_to_next` / `time_to_next`
  are metres / seconds from the solve matrix (verified 2026-11-05, see
  `docs/research/observations/EVAL-E2-engine-cross-check-2026-11-05.md`).
- **Entry database** (`routes`, `route_stops`, job `input_data`): `sql/export_solution.sql`, run by
  `sql/export_solution.sh` as `agent_reader`, scoped to the solution's branch, no customer name,
  phone or address. The export also reports rows edited after the job ran (`input_drift`, KI-033).
  The carbon price is not stored with a job, so `convert` requires `--carbon-price`.
- **Solomon / Gehring-Homberger text format** (`solomon.py`): planar coordinates, Euclidean legs
  under a stated convention (`exact`, or `trunc1` = one-decimal truncation as on CVRPLIB),
  travel time = distance; mapping 1 distance unit = 1 m and 1 time unit = 1 s;
  depot due date = shift end; costs and emissions are 0 by declaration. Solutions are read from
  `Route k : ...` lines.
- **Other solvers** (OR-Tools baseline, literature solutions on Solomon/Homberger): written
  directly in this schema; benchmark instances supply Euclidean legs computed by the adapter.

## 3. Schedule simulation

Per route, with leg time `t`, service time `s`, window `[a, b]`:

```text
depart_0  = start_time (vehicle shift start if absent)
arrive_i  = depart_{i-1} + t_{i-1,i}
wait_i    = max(0, a_i - arrive_i)                (a_i absent → 0)
start_i   = arrive_i + wait_i
late_i    = max(0, start_i - b_i)                 (b_i absent → 0)
depart_i  = start_i + s_i
route_end = depart_n + t_{n,depot}
```

Times are integer seconds from midnight of `planning_date`; no floating-point clock.

## 4. Feasibility checks

Each violation is recorded as `{code, route_index, order_id|null, amount, unit}`.

| # | Code | Rule |
|---|---|---|
| 4.1 | `COVERAGE_DUPLICATE` / `COVERAGE_UNKNOWN` / `COVERAGE_MISSING` | Every instance order appears exactly once across routes ∪ unassigned; no unknown ids. |
| 4.2 | `VEHICLE_REUSED` / `VEHICLE_UNKNOWN` | A vehicle runs at most one route (single-trip model; matches the Engine's `FleetSize.FINITE`, `OptimizationService.java:566`). |
| 4.3 | `OVER_CAPACITY` | Sum of route demand ≤ type capacity, same unit. |
| 4.4 | `TIME_WINDOW` | `late_i > 0`. STRICT: infeasible. SOFT: counted and summed in seconds. |
| 4.5 | `SKILL_MISMATCH` / `LEZ_SKILL_MISMATCH` | Order required skills ⊆ vehicle-type skills; order LEZ skills ⊆ vehicle LEZ skills. |
| 4.6 | `MAX_DISTANCE` / `MAX_DURATION` / `SHIFT_END` | Route totals within type limits and vehicle shift. |
| 4.7 | `LEG_SANITY` | Road distance ≥ great-circle distance × (1 − 0.01); implied speed in [1, 120] km/h; zero distance between distinct coordinates flagged. Warns, does not make the route infeasible. Catches the silent `index == -1 → 0.0` path in `SolutionMetricsCalculator.getTransportDistance`. |

`feasible = no violation of 4.1–4.6` (4.4 only in STRICT mode).

## 5. Metrics

| Group | Metrics |
|---|---|
| Service | served_orders, unserved_orders, delivered_demand, total_demand, served_demand_pct |
| Feasibility | feasible, violation counts per code, total_lateness_s |
| Distance/time | distance_km, driving_h, waiting_h, service_h, route_duration_h, vehicles_used |
| Cost (VND) | fixed, distance (`km × cost_per_km`), time (definition in §8 decision D1) |
| Emissions | co2_kg per emission model, per route and total; co2_cost_vnd at `carbon_price_vnd_per_t` |
| Discrepancy | evaluator − producer-reported value for each metric present in `reported` |

## 6. Emission models (versioned)

| Id | Formula per route | Status |
|---|---|---|
| `EM0-distance-v1` | `D_km × e_g_per_km / 1000` kg. Same definition as the current report, re-implemented; null `e` is an error. | Parity model — reproduces today's definition so differences expose bugs, not model changes. |
| `EM1-load-linear-v1` | Per leg: `D_leg × (e_empty + (e_full − e_empty) × load_leg / capacity) / 1000`, using load on board after each drop. | HYPOTHESIS until `e_empty`/`e_full` have literature provenance in `LITERATURE.md`. For sensitivity only. |
| EV boundary | `e = 0` (tank-to-wheel) unless `ev_upstream_kg_per_kwh` and a kWh/km value are given. | The chosen boundary is written into every result row. |

A result row always records `emission_model_id` and the emission-factor provenance. Note: live
data has 9 of 10 branches at a uniform 12.3 (unit question, OBJECTIVE_FUNCTION.md §8) and branch
1000 mixing 350/190/105/0 (RESEARCH_LOG 2026-10-14). Under a uniform factor, EM0 CO2 is a pure
multiple of distance — the evaluator reports this as `co2_distance_collinear = true` for the run.

## 7. Outputs and comparison

- `evaluate`: writes `evaluation.json` (per route, violations, metrics) and appends one row to a
  results CSV. New columns extend `results/schema.csv`: `evaluator_version, emission_model_id,
  feasible, violations_total, total_lateness_s, delivered_demand, total_demand,
  eval_distance_km, eval_co2_kg, eval_cost_vnd, max_abs_discrepancy_pct`.
- `compare A B` (or two groups of seeds): reports deltas for every metric. A claim
  "B emits less than A" is printed only when served demand and feasibility are equal within a
  declared tolerance (default 0 orders, 0.5 % demand); otherwise the output says
  `SERVICE_PARITY_FAILED` and shows both service levels. Parity is checked across all runs of
  both groups (range of served orders and delivered demand), not on group means. It reports
  mean ± sd per metric and a descriptive Welch t (no p-value); fewer than 5 seeds per side are
  labelled `n<5` (GVRP_Benchmark/README.md rule). When B emits less and costs more, it reports
  the abatement cost `Δcost / −ΔCO2` in VND/kg and VND/t.
- Instance identity is the SHA-256 of the bundle content without `meta`, so bundles exported from
  different jobs with the same input share a hash; `compare` refuses route sets whose declared
  `instance_sha256` differs from the given bundle.

CLI:

```text
python -m gvrp_eval evaluate --instance bundle.json --routes routes.json [--out dir]
python -m gvrp_eval compare  --a routes_a*.json --b routes_b*.json --instance bundle.json
python -m gvrp_eval convert  --from engine-callback payload.json --instance bundle.json
```

## 8. Decisions (accepted by the user 2026-11-05: "Dùng mặc định")

Ids are E-D1…E-D4 in conversation, to avoid confusion with the D1–D4 of the report-sync plan
(`docs/research/observations/SYNC-001-thesis-report-vs-code-gap-2026-11-01.md`).

| Id | Question | Accepted default |
|---|---|---|
| D1 | Does time cost include service and waiting time? The report includes them; the solver prices transport and waiting but not service (OBJECTIVE_FUNCTION.md §6). | Report all three separately; `time_cost` = route duration × cost_per_hour (matches today's report), with driving-only cost as an extra column. |
| D2 | Default time-window mode for benchmarks. | STRICT (matches `instances/README.md` example). The bundle must still state `time_window_mode`; the code has no default. |
| D3 | EV emission boundary. | Tank-to-wheel (0) by default; well-to-wheel only with a sourced grid factor. |
| D4 | Service-parity tolerance. | 0 orders and 0.5 % delivered demand (applies from E3). |

## 9. Verifying the evaluator itself

An evaluator that is wrong in the same way as the solver proves nothing, so:

1. **Hand-computed fixtures**: 3–5 tiny instances (one per check in §4) whose expected metrics
   are computed by hand in the fixture file, including a late arrival, an over-capacity route, a
   skill mismatch and a duplicate order.
2. **Metamorphic tests**: moving an order to `unassigned` changes service metrics and nothing
   else that it should not; scaling every leg distance by k scales distance and EM0 CO2 by k;
   reordering routes leaves totals unchanged.
3. **Benchmark anchor**: on Solomon instances with published best-known solutions, the evaluator
   must reproduce the published distance within rounding.
4. **Cross-check against the Engine**: evaluate stored Engine solutions (e.g. the
   `results/schema.csv` runs) and report discrepancies. A discrepancy is a finding recorded in
   `docs/research/KNOWN_ISSUES.md`; neither side is silently adjusted to agree.
5. **Independent review** of the formulas in this document before results are promoted.

## 10. Implementation phases

| Phase | Content | Done when |
|---|---|---|
| E1 | Schemas, loader with unit validation, simulation, checks 4.1–4.6, EM0, `evaluate` | Fixture + metamorphic tests pass; runs in CI `tools` job via `python -m unittest`. **Done 2026-11-05: 22 tests pass locally (Python 3.11.9).** |
| E2 | Engine-callback and Entry-DB adapters, leg sanity 4.7, discrepancy columns | One stored Engine solution evaluated; discrepancies listed. **Done 2026-11-05: 34 tests; solutions 71, 72, 75 evaluated (EVAL-E2 observation).** |
| E3 | `compare` with service-parity gate and multi-seed summary | A COST vs ECO comparison on branch 1000 data produces a parity-gated report. **Done 2026-11-09: 43 tests; stored goal runs compared (EVAL-E3 observation). Also reports the abatement cost (VND per kg CO2 avoided).** |
| E4 | Solomon/Homberger adapter and anchor test; EM1 after provenance | Published distances reproduced. **Done 2026-11-10 for the anchor: 56/56 published CVRPLIB solutions reproduced exactly and feasible under their one-decimal truncation convention (EVAL-E4 observation, `tests/test_solomon_anchor.py`, 60 tests). EM1 not started (needs sourced parameters).** |
| L2 | Independent re-routing of legs | Distance difference vs GraphHopper legs reported per instance. |

Only after E3 can STATE-CARBON-003 and STATE-SERVICE-001 be proposed for promotion, and only
with review (PROJECT_STATE.md §15).
