# Objective function — current implementation audit

> **Audit date:** 2026-09-09
>
> **Code baseline:** repository HEAD `be5d752` (`be5d752bed38d1366cabbc368784133249dd8748`)
>
> **Scope:** the Entry API request mapping, Engine VRP construction, Jsprit cost model,
> reported solution metrics, Pareto branch, and the vehicle/order skill constraint path.
>
> Status vocabulary follows the repository research policy:
> `VERIFIED`, `PARTIALLY_VERIFIED`, `NOT_VERIFIED`, `HYPOTHESIS`, and
> `HISTORICAL`.

## 1. Audit verdict

| Claim | Status | Verdict at the audited revision |
|---|---|---|
| The implemented CO2 conversion is dimensionally correct | `VERIFIED` | Correct **if** `emissionFactor` is expressed in g CO2/km and `CARBON_PRICE_PER_TON` is VND/tCO2. |
| The solver uses vehicle-dependent emission factors | `VERIFIED` | Each Jsprit vehicle type receives a distance cost derived from its Entry-supplied vehicle type. |
| Vehicle skills and order-required skills reach Jsprit | `VERIFIED` | The Entry DTOs carry both fields; the Engine normalizes them and calls `addSkill` / `addRequiredSkill`. The focused mapping test passes. |
| The current production fleet has heterogeneous emission factors | `NOT_VERIFIED` | No live database inspection was performed for this revision. A historical snapshot contained the same value, `12.3`, for 16/16 types. |
| `12.3` is a valid g CO2/km value | `NOT_VERIFIED` | The field contract says g/km, but the provenance and physical meaning of the stored value have not been established. |
| Cost and CO2 weights create a well-scaled multi-objective problem | `PARTIALLY_VERIFIED` | The code normalizes the weights, but it also scales fixed and time costs by `costWeight`. ECO mode therefore changes more than the carbon trade-off. |
| Solver cost and reported cost are the same quantity | `NOT_VERIFIED` | They are different by construction. The report excludes carbon cost from `totalCostVnd` and charges elapsed route time, including service time. |
| Pareto output demonstrates a meaningful cost-emission frontier | `NOT_VERIFIED` | This requires heterogeneous, validated emission data and controlled experiments with service parity. |

The implementation can optimize the configured weighted objective, but the repository does
not yet contain sufficient evidence that the configured emissions and carbon price represent a
scientifically valid GVRP objective. Engineering behavior and scientific validity must therefore
be evaluated separately.

## 2. Evidence inspected

The current behavior is anchored to these execution-path files:

- `GVRP_Entry_API/.../mapper/OptimizationConfigMapper.java`
- `GVRP_Entry_API/.../mapper/VehicleTypeMapper.java`
- `GVRP_Entry_API/.../dto/request/EngineVehicleTypeDTO.java`
- `GVRP_Entry_API/.../dto/request/EngineOrderDTO.java`
- `GVRP_Engine_API/.../model/OptimizationConfig.java`
- `GVRP_Engine_API/.../service/GreenVRPCostCalculator.java`
- `GVRP_Engine_API/.../service/OptimizationService.java`
- `GVRP_Engine_API/.../service/SolutionMetricsCalculator.java`
- `GVRP_Engine_API/.../utils/AppConstant.java`

Focused Engine verification executed on 2026-09-09 covered 90 tests for measured laws,
matrix representation, maximum-distance constraints, skill mapping, clustering, metrics, and
the timeout regression. Result: **86 passed and 4 failed**. All four failures are in
`MeasuredLawsTest` and encode stale expectations: the former carbon price, former break-even
value, a historical result produced with the old emission fallback, and the former expected
weight asymmetry. These failures do not invalidate the source inspection below, but they mean
the full selected suite is not green at this revision.

No live database query, end-to-end HTTP optimization run, calibrated emissions experiment, or
controlled solver benchmark was performed as part of this audit. Claims that require those
forms of evidence remain explicitly unverified.

## 3. Input path and feasibility constraints

### 3.1 Vehicle emission data

The Entry API maps `vehicle_features.emissionFactor` into
`EngineVehicleTypeDTO.emissionFactor`. The Engine request model carries it in
`VehicleType.emissionFactor`, and `OptimizationService.buildGreenVRP` passes that vehicle type
to `GreenVRPCostCalculator.buildGreenVehicleType`.

`VERIFIED`: the code path supports a distinct emission factor for every vehicle type.

`NOT_VERIFIED`: the live data actually contains distinct, physically valid factors.

### 3.2 Vehicle and order skills

The current path is:

```text
vehicle_features.skills
  -> EngineVehicleTypeDTO.skills
  -> Engine VehicleType.skills
  -> VehicleImpl.Builder.addSkill(...)

order.required_skills
  -> EngineOrderDTO.requiredSkills
  -> Engine Order.requiredSkills
  -> Service.Builder.addRequiredSkill(...)
```

The Engine trims skill values, removes blanks, converts them to uppercase, removes duplicates,
and sorts them before adding them to Jsprit. Skills are feasibility constraints: an order that
requires a skill can only be assigned to a compatible vehicle. They do not by themselves create
emission heterogeneity or add a carbon term to the objective.

`VERIFIED`: source inspection and `OptimizationSkillMappingTest` confirm the Engine mapping.

`PARTIALLY_VERIFIED`: persistence-to-HTTP-to-solver behavior has not been demonstrated by a
single end-to-end test in this audit.

## 4. Objective actually optimized by Jsprit

For vehicle type `k`, the Engine builds these unit costs:

$$
p_{dist,k}
= \frac{c^{km}_k}{1000}w_c + 
\frac{e_k P_{CO_2}}{10^9}w_{CO_2}
$$

$$
p_{time,k}=\frac{c^h_k}{3600}w_c,
\qquad
F_k=f_k w_c
$$

where:

- $c^{km}_k$ is `costPerKm` in VND/km;
- $c^h_k$ is `costPerHour` in VND/hour;
- $f_k$ is `fixedCost` in VND per used vehicle;
- $e_k$ is `emissionFactor`, contractually g CO2/km;
- $P_{CO_2}$ is `CARBON_PRICE_PER_TON = 150000` VND/tCO2;
- $w_c$ and $w_{CO_2}$ are normalized so their sum is one.

The resulting Jsprit objective is:

$$
Z_{solver}
= \sum_{k \in K_{used}} F_k
+ \sum_k\sum_{(i,j)\in R_k}
\left(p_{dist,k}d_{ij}+p_{time,k}t_{ij}\right)
+ \sum_k p_{time,k}W_k
$$

Here $d_{ij}$ is in metres, $t_{ij}$ is transport time in seconds, and $W_k$ is
waiting time in seconds. The Engine sets both `costPerTransportTime` and
`costPerWaitingTime` to $p_{time,k}$.

Service duration is added to each Jsprit job with `setServiceTime`, so it affects schedules,
time-window feasibility, and route end time. No explicit `costPerServiceTime` is configured in
the current vehicle cost builder. Consequently, service duration is not priced by the displayed
solver objective.

### 4.1 Unit check for the carbon term

$$
\frac{g}{km}\times\frac{VND}{10^6g}\times\frac{km}{1000m}
=\frac{VND}{10^9m}
$$

Therefore:

$$
p_{CO_2,k}=\frac{e_kP_{CO_2}}{10^9}\quad\text{VND/m}
$$

For the illustrative value $e=12.3$ g/km:

$$
p_{CO_2}=\frac{12.3\times150000}{10^9}=0.001845\ \text{VND/m}
$$

This verifies dimensional consistency only. It does not validate the value `12.3` or the carbon
price.

## 5. Weight defaults and presets

The normal Entry API path currently sends these values:

| Entry goal | Raw weights `(cost, CO2)` | Normalized Engine weights |
|---|---:|---:|
| `MINIMIZE_COST` | `(1.0, 0.0)` | `(1.0, 0.0)` |
| `MINIMIZE_DISTANCE` | `(0.5, 0.5)` | `(0.5, 0.5)` |
| `MINIMIZE_CO2` | `(EPSILON, 1.0)` | approximately `(0.00009999, 0.99990001)` |
| `BALANCED` | `(0.5, 0.5)` | `(0.5, 0.5)` |
| Missing Entry preferences | `(0.5, 0.5)` | `(0.5, 0.5)` |

There is a configuration inconsistency inside the Engine. `OptimizationConfig.getEffectiveWeights`
contains a `(0.7, 0.3)` fallback when both fields are null, but `validateConfig` treats null as
zero and rejects a request when their sum is zero. The `(0.7, 0.3)` both-null fallback is
therefore unreachable through the validated optimization path. The effective default for normal
Entry-originated requests is `(0.5, 0.5)`.

## 6. Solver objective versus reported metrics

`SolutionMetricsCalculator` reports monetary operating cost as:

$$
Z_{report}
=\sum_k f_k
+\sum_k D_kc^{km}_k
+\sum_k H_kc^h_k
$$

where $D_k$ is route distance in km and:

$$
H_k=\frac{arrivalTime_{end}-endTime_{start}}{3600}
$$

is elapsed route time in hours. It includes transport, waiting, and service duration represented
in the Jsprit schedule.

CO2 and its monetary equivalent are reported separately:

$$
CO_2=\sum_k\frac{D_ke_k}{1000}\quad\text{kg}
$$

$$
co2CostVnd=\frac{CO_2}{1000}P_{CO_2}
$$

The following differences are current code behavior:

| Difference | Consequence |
|---|---|
| Solver weights fixed, distance, transport-time, and waiting-time costs; report uses unweighted operating cost | `Z_solver` and `totalCostVnd` cannot be compared as the same scalar. |
| Solver includes carbon in distance cost; report keeps `co2CostVnd` separate | `totalCostVnd` is not the monetized objective used to select the route. |
| Report time includes service duration; solver does not explicitly price service duration | A route can have a higher reported time cost without an equivalent solver penalty. |
| Solver directly unboxes a null emission factor; reporter falls back to `200.0` g/km | Invalid input can fail in optimization while the reporting helper silently substitutes a value. |

The null policy should be unified. Given the repository's fail-loud policy, validation at the
boundary plus no reporting fallback is the clearer contract.

## 7. Why the current weighting is difficult to interpret

Changing `costWeight` changes all three operating-cost components:

- fuel/energy distance cost;
- vehicle fixed cost;
- transport and waiting-time cost.

In ECO mode, `costWeight` is close to zero. Vehicles and time therefore become almost free in
the solver objective. A solution difference between COST and ECO can be caused by suppressed
fixed/time costs even when carbon has little influence. It cannot automatically be attributed to
an environmental trade-off.

For an illustrative Truck 5T with $c^{km}=8000$ VND/km, $f=100000$ VND,
$e=12.3$ g/km, and $P_{CO_2}=150000$ VND/tCO2, the distance at which an extra
vehicle's weighted fixed cost equals its weighted distance cost is:

$$
D_{break-even}=\frac{fw_c}{p_{dist}}
$$

| Preset | Normalized $w_c$ | Normalized $w_{CO_2}$ | $p_{dist}$ (VND/m) | Weighted fixed cost (VND) | Break-even distance | CO2 share of $p_{dist}$ |
|---|---:|---:|---:|---:|---:|---:|
| COST | 1.000000 | 0.000000 | 8.000000 | 100,000.000 | 12,500 m | 0% |
| BALANCED | 0.500000 | 0.500000 | 4.0009225 | 50,000.000 | 12,497 m | 0.0231% |
| ECO | 0.00009999 | 0.99990001 | 0.00264474 | 9.999 | 3,781 m | 69.75% |

The former version of this document mixed VND/km and VND/m in this table. The values above were
independently recomputed from the current source formula.

At the Entry default, the carbon component contributes only about `0.0231%` of this example's
distance coefficient. This numerical observation is conditional on the illustrative `12.3`
g/km input; it is not evidence that the same share applies to the live fleet.

## 8. Conditions for a meaningful green objective

If every usable vehicle has the same emission factor $e$, then:

$$
CO_2=\frac{e}{1000}\sum_k D_k
$$

Under that condition, minimizing tailpipe CO2 is mathematically equivalent to minimizing total
distance. A cost-CO2 frontier may still show different points because fixed and time costs are
weighted differently, but those points do not demonstrate a vehicle-emission trade-off.

Vehicle-specific factors are necessary for assignment choices such as selecting a lower-emission
vehicle for a route. They are not sufficient for scientific validity. The model also needs:

1. a documented emission-factor unit and provenance;
2. factors that represent the intended vehicle, fuel, load, and operating conditions;
3. a declared treatment of zero-tailpipe vehicles and upstream electricity emissions;
4. a justified carbon price or an explicit sensitivity range;
5. equal service levels and independently recomputed feasibility across compared runs.

`HYPOTHESIS`: the historical value `12.3` may have originated as fuel consumption in L/100 km
rather than emissions in g/km. Its magnitude motivates investigation, but the value alone cannot
establish its unit or provenance.

`NOT_VERIFIED`: the configured `150000` VND/tCO2 represents a current market price, shadow
price, social cost of carbon, or organizational policy value. It is presently a source-code
constant rather than an experiment parameter recorded with each result.

## 9. Pareto branch and large-instance comparability

When Pareto analysis is enabled, the Engine generates multiple weight points, solves the full
order set for every point, calculates reported cost and CO2, removes dominated candidates, and
selects from the frontier using normalized reported metrics.

The Pareto branch deliberately does not apply the cluster-first constraint. The single-objective
branch can enable cluster-first processing at the configured order threshold. Cluster-first uses
a block matrix and a hard cluster-route constraint, so it changes the feasible solution space; it
is a problem approximation rather than a storage-only optimization.

Consequences for experiments:

- compare Pareto points with each other only when they use the same order set, vehicles, matrix,
  solver budget, random-seed policy, and feasibility checks;
- do not attribute a difference between clustered single-objective and unclustered Pareto runs
  solely to objective weights;
- compare served orders, unserved orders, delivered demand, feasibility, distance, emissions,
  cost, and runtime together;
- independently recompute metrics with one evaluator for every candidate route set.

The current Pareto implementation is evidence that the software can sample and filter candidate
solutions. It is not evidence that the resulting frontier is scientifically meaningful.

## 10. Implementation status and recommended next work

| Item | Current status | Required action |
|---|---|---|
| Correct g/km and VND/tCO2 conversion | `VERIFIED` | Keep unit tests tied to the current constant and formula. |
| Per-vehicle-type emission field through Entry and Engine | `VERIFIED` statically | Add an end-to-end test with two vehicle types and independently recompute route emissions. |
| Vehicle/order skill path into Jsprit | `VERIFIED` by focused unit test | Add persistence/API/solver integration coverage, including an incompatible order. |
| Live emission-factor distribution | `NOT_VERIFIED` | Audit current database values and record the query, timestamp, row count, unit, and provenance. |
| Emission-factor semantic validity | `NOT_VERIFIED` | Decide whether the source represents g/km, L/100 km, or another measure; migrate field names/data if needed. |
| Carbon price calibration | `NOT_VERIFIED` | Move it to explicit configuration and persist the value and source with every benchmark result. |
| Solver/report time alignment | `PARTIALLY_VERIFIED` | Decide whether service time is an operating cost, then implement and test one shared definition. |
| Null emission behavior | `PARTIALLY_VERIFIED` | Remove the reporting fallback or define one validated fallback policy for both paths. |
| Interpretable carbon trade-off | `NOT_VERIFIED` | Prefer a base operating-cost objective plus a carbon multiplier or explicit emission constraint. |
| Pareto quality | `NOT_VERIFIED` | Run a preregistered controlled experiment with heterogeneous validated vehicles and service parity. |

A more interpretable scalarization would retain the full operating costs and apply a multiplier
only to the carbon term:

$$
p_{dist,k}
=\frac{c^{km}_k}{1000}
+\mu\frac{e_kP_{CO_2}}{10^9},
\qquad \mu\ge0
$$

Then fixed and time costs retain their economic meaning while $\mu$ expresses a carbon-price
sensitivity. This is a recommendation, not current behavior.

## 11. Benchmark metadata required for reproducibility

Each result row should record enough information to reconstruct the objective:

| Field | Meaning | Current implementation status |
|---|---|---|
| `code_revision` | Exact Git commit | Recommended; not established here as an exported result field. |
| `objective_variant` | For example `weighted_cost_co2` or a future `carbon_multiplier` | Only the weighted implementation exists. |
| `cost_weight_raw`, `co2_weight_raw` | Request values before normalization | Available in request/config; persistence in benchmark output not verified. |
| `cost_weight_normalized`, `co2_weight_normalized` | Values actually used by the solver | Logged; durable result persistence not verified. |
| `carbon_price_vnd_per_ton` | Carbon price used by the solver | Currently hard-coded; result persistence not verified. |
| `emission_factor_unit` | Declared unit, expected `g_co2_per_km` in current formula | Semantic provenance not verified. |
| `emission_factor_source` | Dataset/version or measurement source | Not verified. |
| `time_cost_basis_solver` | `transport_plus_wait` at this revision | Derivable from code; should be stored. |
| `time_cost_basis_report` | `elapsed_route_time` at this revision | Derivable from code; should be stored. |
| `cluster_first_enabled` | Whether hard cluster constraints/block matrix were used | Required for fair comparisons. |
| `served_orders`, `unserved_orders`, `delivered_demand` | Service parity | Required for every objective comparison. |
| `evaluator_version` | Independent metric implementation/version | Required for defensible experiments. |

## 12. Historical evidence boundary

Historical job `#21` and earlier benchmark notes were produced under older code and an old
emission fallback. They can be used to diagnose that historical behavior, but their CO2 values
must not be compared directly with results from this revision.

The prior database observation that 16/16 vehicle types used `12.3` is also historical until it
is reproduced against the current database. It supports a risk hypothesis, not a claim about the
live fleet at the audited revision.

## 13. Current conclusion

`VERIFIED`: the source now supports vehicle-dependent CO2 coefficients and skill-based
vehicle-order compatibility, and the carbon unit conversion is internally consistent.

`PARTIALLY_VERIFIED`: the weighted solver objective behaves as implemented, but its weights also
rescale fixed and time costs, and the reporting metric uses a different cost definition.

`NOT_VERIFIED`: the emission data, carbon price, and resulting Pareto frontier are scientifically
valid or operationally representative. That conclusion requires validated input provenance and a
controlled experiment under the repository's research protocol.
