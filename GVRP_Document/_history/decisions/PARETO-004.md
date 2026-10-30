# PARETO-004 — delivered demand in the service tier

**Problem.** Two plans could serve the same *number* of orders but deliver different *amounts* of
demand. The selector compared them on cost and CO₂ alone, so a plan that dropped heavy orders could
win. `SolutionCandidate.dominates()` also still classified by cost and CO₂ only, which contradicted
the selector and misled readers.

**Decision.**

- `SolutionMetrics` gains `servedDemand` in raw demand units (not `DEMAND_SCALE` units);
  `SolutionMetricsCalculator` fills it from the route loads.
- `ParetoFrontierSelector` eligibility becomes lexicographic: maximum served order count first, then
  maximum delivered demand, compared with a relative tolerance of 1e-9. A null demand counts as zero,
  so behaviour is unchanged when demand is not populated.
- `SolutionCandidate.dominates()` is deprecated with javadoc pointing at the selector. It is dead code
  but is kept for compatibility.

**Date.** 2026-10-20, implemented directly at the user's request ("có, bạn làm đi"); no OpenCode run,
so there is no `run.json` and no state to check.

**Source.** `work-items/PARETO-004/change-note.md` (deleted; `git log -- work-items/PARETO-004`).

**Verification status.** `PARTIALLY_VERIFIED`. The selector and a new five-case test class were run
with hand-written stubs (12/12 pass) because the sandbox had no Maven Central access. **Not verified:**
compiling the module with Lombok, the full Engine suite, and an end-to-end Pareto job. To verify:
`mvnw.cmd -Dtest=ParetoFrontierSelectorTest,ParetoFrontierSelectorServiceTierTest,OptimizationServiceParetoTest,SolutionConverterParetoTest test`
in `GVRP_Engine_API`.

**Still valid in this documentation.** `BR-045`, `03-basic-design/D08-engine/README.md`.

**Residual risk recorded in the note.** Equal count and equal demand do not imply the same order set.
The Entry API refuses to apply such a candidate (`ORDER_SET_DIFFERS`), but the frontier can still show
it.
