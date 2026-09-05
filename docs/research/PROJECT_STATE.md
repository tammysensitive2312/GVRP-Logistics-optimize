# GVRP Project State

## 1. Purpose

This document records the current known state of the GVRP Logistics Optimize project.

It is not a general project description and it is not a historical issue log.

Every important technical or scientific claim should indicate:

* epistemic status
* evidence
* applicable revision or configuration
* verification scope
* limitations
* conditions requiring re-verification

Historical failures and lessons belong in:

```text
docs/research/KNOWN_ISSUES.md
```

Chronological discoveries and decisions belong in:

```text
docs/research/RESEARCH_LOG.md
```

---

# 2. Status Vocabulary

Use only the following primary knowledge states.

## VERIFIED

The claim has direct evidence sufficient for the stated scope.

## PARTIALLY_VERIFIED

Some aspects of the claim are verified, but important dimensions remain unverified.

## NOT_VERIFIED

The claim exists as a relevant project question but has not been sufficiently checked.

## HYPOTHESIS

The claim is proposed for scientific or engineering investigation.

## EXTRAPOLATION

The claim predicts behavior outside directly measured conditions.

## UNKNOWN

Available evidence is insufficient even to classify the claim more specifically.

---

# 3. Evidence Strength

Epistemic status and evidence strength are separate.

A claim may be technically `VERIFIED` within a very narrow scope while still having weak generalization evidence.

Use:

```text
STRONG
MODERATE
LIMITED
NONE
```

Example:

```text
status: VERIFIED
evidence_strength: LIMITED
```

may mean:

> The behavior was reproduced on one small dataset and one revision, but has not been validated at scale.

---

# 4. Claim Record Format

Use this format for significant project knowledge.

```yaml
id: STATE-<DOMAIN>-NNN

claim: >
  Concise statement of what is believed to be true.

status: VERIFIED | PARTIALLY_VERIFIED | NOT_VERIFIED | HYPOTHESIS | EXTRAPOLATION | UNKNOWN

evidence_strength: STRONG | MODERATE | LIMITED | NONE

verified_at: YYYY-MM-DD | null

revision:
  commit: "<git-sha>" | null
  branch: "<branch>" | null

evidence:
  source:
    - "<file:line or symbol>"
  tests:
    - "<test name>"
  experiments:
    - "<experiment id>"
  runtime:
    - "<log, benchmark, or observation>"
  external:
    - "<paper, specification, or official documentation>"

scope:
  modules:
    - "<module>"
  configuration:
    - "<configuration>"
  datasets:
    - "<dataset>"
  problem_size:
    min: null
    max: null

limitations:
  - "<known limitation>"

contradictions:
  - "<conflicting evidence if any>"

reverify_when:
  - "<condition>"
```

Not every field must contain a value.

Unknown fields should remain empty rather than being invented.

---

# 5. System Architecture

## STATE-ARCH-001 — Repository Components

```yaml
id: STATE-ARCH-001

claim: >
  The project contains separate Entry API, Engine API, client,
  benchmark, and documentation components.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Entry_API
    - GVRP_Engine_API
    - GVRP_Entry_client
    - GVRP_Entry_client_v2
    - GVRP_Benchmark
    - GVRP_Document

limitations:
  - Repository structure must be inspected before this claim is promoted.

contradictions: []

reverify_when:
  - repository structure changes
```

---

## STATE-ARCH-002 — Entry API Responsibility

```yaml
id: STATE-ARCH-002

claim: >
  GVRP_Entry_API is responsible for external API behavior,
  persistence, job dispatch, and callbacks.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Entry_API

limitations:
  - Exact responsibilities must be confirmed from current source code.

contradictions: []

reverify_when:
  - module boundaries change
```

---

## STATE-ARCH-003 — Engine API Responsibility

```yaml
id: STATE-ARCH-003

claim: >
  GVRP_Engine_API is responsible for route optimization and
  currently contains GraphHopper/jsprit-related solver logic.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Exact active solver path has not yet been recorded in this state file.

contradictions: []

reverify_when:
  - solver architecture changes
  - routing libraries change
```

---

# 6. Solver State

## STATE-SOLVER-001 — Active Solver Stack

```yaml
id: STATE-SOLVER-001

claim: >
  The active optimization engine uses GraphHopper for routing-related
  functionality and jsprit for vehicle-routing optimization.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Dependency presence alone is not sufficient.
  - Active runtime call paths must be confirmed.

contradictions: []

reverify_when:
  - solver implementation changes
  - GraphHopper configuration changes
  - jsprit configuration changes
```

---

## STATE-SOLVER-002 — Solver Configuration

```yaml
id: STATE-SOLVER-002

claim: >
  The effective solver configuration, including iteration budget,
  threading, constraints, and cost configuration, is fully understood.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Effective runtime values have not yet been recorded here.

contradictions: []

reverify_when:
  - solver configuration changes
```

---

# 7. Objective Function State

## STATE-OBJECTIVE-001 — Active Objective Terms

```yaml
id: STATE-OBJECTIVE-001

claim: >
  The exact objective terms used by the active solver have been traced
  from configuration/input to the final solver cost.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Declared fields or documentation do not prove that a term affects optimization.

contradictions: []

reverify_when:
  - objective code changes
  - solver cost configuration changes
  - API model changes
```

---

## STATE-OBJECTIVE-002 — Objective Units and Normalization

```yaml
id: STATE-OBJECTIVE-002

claim: >
  Units, scaling, normalization, and weights of all active objective
  terms are scientifically and numerically consistent.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Mathematical definition and implementation still require independent comparison.

contradictions: []

reverify_when:
  - objective weights change
  - normalization changes
  - units or input representation changes
```

---

# 8. Carbon Emission State

## STATE-CARBON-001 — Active Carbon Model

```yaml
id: STATE-CARBON-001

claim: >
  The exact carbon-emission model currently used by the active
  optimization path is known and has been traced end-to-end.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Entry_API
    - GVRP_Engine_API

limitations:
  - Carbon-related fields may exist without affecting solver decisions.
  - Parameter propagation has not yet been recorded.

contradictions: []

reverify_when:
  - emission model changes
  - objective implementation changes
  - API emission parameters change
```

---

## STATE-CARBON-002 — Carbon vs Distance Dependence

```yaml
id: STATE-CARBON-002

claim: >
  The carbon objective contains information meaningfully distinct
  from distance and can therefore change route preference independently.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

evidence:
  source:
    - "GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/service/GreenVRPCostCalculator.java:88-132"
    - "GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/model/ObjectivePreset.java:20-38"
    - "GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/utils/AppConstant.java:14"

limitations:
  - Correlation with distance must be measured rather than inferred.
  - Presence of additional parameters does not prove they affect route ranking.
  - Term-level collinearity (carbon leg proportional to distance under
    fleet-uniform e) must not be equated with objective-level non-contrast:
    ECO_FOCUSED reweighting changes break-even vehicle-opening economics by
    ~35× on the reference vehicle (2026-09-05 Stage-0 review), so different
    routes remain possible; direction, magnitude, and service shift are
    unverified and require a preregistered solver experiment.

contradictions: []

reverify_when:
  - carbon model changes
  - cost calculation changes
  - vehicle energy model changes
  - fleet emission-factor distribution changes
```

---

## STATE-CARBON-003 — Independent Carbon Evaluation

```yaml
id: STATE-CARBON-003

claim: >
  Baseline and experimental route sets can be evaluated with the same
  independent carbon-emission evaluator.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Benchmark
    - GVRP_Engine_API

limitations:
  - An optimizer-internal objective is not automatically an independent evaluator.

contradictions: []

reverify_when:
  - emission evaluator changes
  - benchmark methodology changes
```

---

# 9. Service-Level Comparison State

## STATE-SERVICE-001 — Comparable Service Metrics

```yaml
id: STATE-SERVICE-001

claim: >
  Routing experiments record enough service-level metrics to prevent
  a lower-emission result from being caused merely by serving less demand.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Benchmark

limitations:
  - At minimum the benchmark should be able to distinguish served and
    unserved demand and route feasibility.

contradictions: []

reverify_when:
  - benchmark output format changes
  - solution metrics change
```

Important comparison metrics should normally include:

```text
served_orders
unserved_orders
delivered_demand
feasibility
distance
emissions
runtime
```

Additional metrics may be required depending on the research question.

---

# 10. Routing Matrix State

## STATE-MATRIX-001 — Matrix Representation Semantics

```yaml
id: STATE-MATRIX-001

claim: >
  The current optimized routing-matrix representation preserves the
  logical cost-query semantics of the intended routing problem.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Memory reduction alone does not demonstrate semantic equivalence.

contradictions: []

reverify_when:
  - matrix construction changes
  - node indexing changes
  - depot handling changes
```

---

## STATE-MATRIX-002 — Representation Optimization vs Approximation

```yaml
id: STATE-MATRIX-002

claim: >
  It has been determined whether the current matrix optimization is
  only a representation optimization or whether it changes the feasible
  routing problem.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API

limitations:
  - Removing otherwise valid cross-region edges changes the problem,
    not merely its representation.

contradictions: []

reverify_when:
  - clustering logic changes
  - edge filtering changes
  - matrix block construction changes
```

---

## STATE-MATRIX-003 — Solution-Level Validation

```yaml
id: STATE-MATRIX-003

claim: >
  Optimized matrix representations have been validated at the solution
  level against an appropriate reference.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark

limitations:
  - Stochastic solvers are not expected to return identical routes.
  - Validation should compare independently recomputed feasibility and
    solution quality rather than route identity alone.

contradictions: []

reverify_when:
  - matrix representation changes
  - solver behavior changes
```

---

# 11. Benchmark and Performance State

## STATE-BENCH-001 — Maximum Verified Problem Size

```yaml
id: STATE-BENCH-001

claim: >
  The maximum problem size for which runtime, memory, feasibility,
  and solution-quality behavior have been verified is known.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Benchmark
    - GVRP_Engine_API

limitations:
  - Historical benchmark numbers must not be copied here without evidence.

contradictions: []

reverify_when:
  - new larger benchmark is accepted
  - benchmark methodology changes
```

---

## STATE-BENCH-002 — Runtime Scaling

```yaml
id: STATE-BENCH-002

claim: >
  Runtime scaling behavior is supported by measurements over a defined
  problem-size range.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Benchmark
    - GVRP_Engine_API

limitations:
  - Predictions outside the measured interval must be labeled extrapolation.

contradictions: []

reverify_when:
  - algorithm changes
  - hardware changes materially
  - solver configuration changes
  - matrix implementation changes
```

---

## STATE-BENCH-003 — Memory Scaling

```yaml
id: STATE-BENCH-003

claim: >
  Memory behavior is supported by measurements using a clearly defined
  memory metric.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark

limitations:
  - Theoretical matrix storage must not be confused with JVM heap or
    total process memory.

contradictions: []

reverify_when:
  - matrix representation changes
  - JVM configuration changes
  - benchmark instrumentation changes
```

---

# 12. Data and Persistence State

## STATE-DATA-001 — Database Schema

```yaml
id: STATE-DATA-001

claim: >
  The current database schema and identifier-generation strategy have
  been verified against the persistence implementation.

status: NOT_VERIFIED
evidence_strength: NONE

verified_at: null

revision:
  commit: null
  branch: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  external: []

scope:
  modules:
    - GVRP_Entry_API

limitations:
  - Entity annotations alone are not enough if production schema differs.

contradictions: []

reverify_when:
  - schema changes
  - JPA mapping changes
  - migration strategy changes
```

---

# 13. Research Questions

Research questions are not verified project facts.

Keep them explicitly separate.

Example candidate questions:

```text
RQ-001
Does a carbon-emission objective containing information independent
from route distance produce materially different and lower-emission
solutions under comparable service levels?

RQ-002
Does the optimized routing-matrix representation preserve solution
quality while reducing memory consumption?

RQ-003
How does solver iteration budget affect solution quality and runtime
as problem size increases?
```

These remain candidate questions until explicitly adopted into the research plan.

---

# 14. Verification Queue

The following items are high-value candidates for initial repository verification.

```text
VQ-001
Identify the active Engine optimization execution path.

VQ-002
Trace every active objective term from input/configuration to jsprit cost.

VQ-003
Identify the exact active carbon-emission equation.

VQ-004
Determine whether carbon cost contains information independent of distance.

VQ-005
Determine whether emission-related API parameters reach the Engine.

VQ-006
Determine whether matrix optimization preserves cost-query semantics.

VQ-007
Identify the largest reproducibly benchmarked dataset.

VQ-008
Identify the current solver iteration and threading configuration.
```

Do not mark these as resolved without evidence.

---

# 15. Promotion Rule

Information must not be promoted into `VERIFIED` project state merely because:

* an agent states it
* documentation states it
* a class name suggests it
* a field exists
* one implementation path appears to support it

A state should be promoted only after evidence appropriate to the claim has been inspected.

Example:

```text
NOT_VERIFIED
    ↓
source inspection
    ↓
test or measurement
    ↓
scope recorded
    ↓
VERIFIED / PARTIALLY_VERIFIED
```

Scientific conclusions may require experiment review before promotion.

---

# 16. Contradiction Rule

When evidence conflicts, record the contradiction.

Example:

```yaml
contradictions:
  - >
    Documentation defines emission in kgCO2/km,
    but implementation appears to multiply by travel time.
```

Do not silently rewrite the project state to match whichever source was inspected last.

Resolve the contradiction through investigation.

---

# 17. Re-Verification Rule

Verification is not permanent.

Re-check a state when a relevant dependency changes.

Typical triggers include:

* source implementation changes
* solver version changes
* objective changes
* configuration changes
* data representation changes
* experiment methodology changes
* benchmark scope expands
* emission-model assumptions change

A previously verified claim may become stale.

---

# 18. Maintenance Rule

When updating this file:

1. update the smallest affected claim
2. attach evidence
3. record revision and date
4. record verification scope
5. preserve unresolved contradictions
6. avoid turning observations into general conclusions
7. move historical narratives to `KNOWN_ISSUES.md`
8. move chronological decisions to `RESEARCH_LOG.md`

This file should answer:

> What do we currently know, how do we know it, and within what scope?