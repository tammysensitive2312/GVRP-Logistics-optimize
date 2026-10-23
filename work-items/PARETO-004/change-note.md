# PARETO-004: delivered-demand service tier, deprecate `dominates()`

Date: 2026-10-20. Claude implemented this directly at the user's request ("có, bạn làm đi"),
without an OpenCode run. It is a small engine refinement of PARETO-001.

## Why

- A review (Claude Science) noted that `SolutionCandidate.dominates()` compares only cost and
  CO2. That method is dead code: since PARETO-001 the engine classifies with
  `ParetoFrontierSelector.classify` (service-first). It still misleads readers.
- Service-first compared only the **count** of served orders. Two plans with the same count but
  different delivered demand competed on cost and CO2 alone. AGENTS.md §9 lists delivered demand
  as a parity metric.

## Changes (Engine only)

| File | Change |
|---|---|
| `model/SolutionMetrics.java` | New field `servedDemand` (raw demand units, not `DEMAND_SCALE`) |
| `service/SolutionMetricsCalculator.java` | Sums route loads into `servedDemand` |
| `service/ParetoFrontierSelector.java` | Eligibility is now lexicographic: max order count first, then max delivered demand (relative tolerance 1e-9). A null demand counts as 0, so the old behaviour holds when demand is not populated. |
| `model/SolutionCandidate.java` | `dominates()` is now `@Deprecated`, with javadoc pointing to the selector |
| `test/.../ParetoFrontierSelectorServiceTierTest.java` | **New** test class, 5 cases |

Unchanged: existing tests, the callback payload (`SolutionConverter` maps fields explicitly),
the `selection_rule` string, and the Entry API. Modified tests: none.

## Verification

- `ParetoFrontierSelector` as it is on disk, run with the existing `ParetoFrontierSelectorTest`
  and the new test class: **12/12 pass** (7 existing, 5 new).
- That run used hand-written stubs for `SolutionMetrics`, `SolutionCandidate`,
  `GreenVRPCostCalculator` and JUnit, because the sandbox has no Maven Central access.
- **Not verified:** compiling the real module with Lombok, the full Engine suite, and an
  end-to-end Pareto job.
- To verify, the user runs this in `GVRP_Engine_API`:
  `mvnw.cmd -Dtest=ParetoFrontierSelectorTest,ParetoFrontierSelectorServiceTierTest,OptimizationServiceParetoTest,SolutionConverterParetoTest test`

## Residual

Equal count and equal demand do not imply the same order set. Entry refuses to apply such a
candidate (`ORDER_SET_DIFFERS`), but the frontier can still show it.
