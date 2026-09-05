# GVRP Known Issues and Historical Lessons

## 1. Purpose

This document records historical defects, scientific traps, implementation hazards, and lessons learned in the GVRP Logistics Optimize project.

An entry in this file does **not** automatically mean the issue is currently present.

Every issue must distinguish:

* historical existence
* current status
* evidence strength
* applicable scope
* regression protection
* re-verification conditions

Current verified project behavior belongs in:

```text
docs/research/PROJECT_STATE.md
```

Chronological investigation and decisions belong in:

```text
docs/research/RESEARCH_LOG.md
```

---

# 2. Issue Status

Use one of the following values.

## OPEN

The issue is currently verified to exist within the stated scope.

## FIXED

A corrective change has been implemented and verified within the stated scope.

## MITIGATED

The issue still exists or may exist, but controls reduce its practical impact.

## NOT_REPRODUCED

The issue was investigated in the stated environment but could not be reproduced.

This does not prove that the issue never existed.

## HISTORICAL

There is evidence that the issue existed in an earlier revision, but its current applicability has not yet been verified.

## SUSPECTED

There is some indication of a possible issue, but insufficient evidence to classify it as present.

## UNKNOWN

The current state cannot be determined from available evidence.

---

# 3. Evidence Strength

Status and evidence strength are independent.

Use:

```text
STRONG
MODERATE
LIMITED
NONE
```

Example:

```yaml
status: FIXED
evidence_strength: LIMITED
```

means:

> A fix exists and passed some verification, but evidence is too narrow to generalize broadly.

---

# 4. Issue Record Format

Use the following structure.

```yaml
id: KI-NNN
title: "<short issue title>"

category:
  - ENGINEERING | SCIENTIFIC | NUMERICAL | PERFORMANCE | DATA | CONCURRENCY | INTEGRATION

historical_claim: >
  What is believed to have happened historically.

status: OPEN | FIXED | MITIGATED | NOT_REPRODUCED | HISTORICAL | SUSPECTED | UNKNOWN

evidence_strength: STRONG | MODERATE | LIMITED | NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules: []
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Why this issue matters.

root_cause: >
  Verified cause, if known.

mitigation_or_fix: >
  Fix or mitigation, if known.

regression_protection:
  tests: []
  guards: []

limitations:
  - "<what remains uncertain>"

reverify_when:
  - "<condition>"
```

Do not invent unknown fields.

---

# 5. KI-001 — Duration Unit Confusion

```yaml
id: KI-001
title: Duration Unit Confusion

category:
  - NUMERICAL
  - ENGINEERING

historical_claim: >
  The project has historically been at risk of interpreting duration-related
  values using inconsistent units such as hours and minutes.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules: []
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  A unit mismatch can invalidate route feasibility, runtime calculations,
  benchmark interpretation, or API behavior while still producing plausible numbers.

root_cause: >
  Not yet recorded with verified evidence.

mitigation_or_fix: >
  Trace duration values from input through parsing, internal representation,
  solver usage, and output. Define units explicitly.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current applicability has not yet been verified.
  - Historical evidence must be attached before this is treated as established project history.

reverify_when:
  - duration representation changes
  - API time fields change
  - solver time configuration changes
```

---

# 6. KI-002 — Thousand-Fold Runtime Calculation Error

```yaml
id: KI-002
title: Thousand-Fold Runtime Calculation Error

category:
  - NUMERICAL
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  A previous runtime calculation may have contained an approximately
  1000x scaling error.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Incorrect scaling can produce a mathematically plausible but scientifically invalid
  runtime law and misleading scalability conclusions.

root_cause: >
  Not yet attached to verified evidence.

mitigation_or_fix: >
  Important performance formulas should be checked using direct substitution
  and independent reverse calculation.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current regression coverage must be verified from the repository.
  - The exact historical calculation has not yet been attached.

reverify_when:
  - performance laws change
  - benchmark formulas change
```

---

# 7. KI-003 — Floating-Point Decimal Artifacts

```yaml
id: KI-003
title: Floating-Point Decimal Artifacts

category:
  - NUMERICAL
  - DATA

historical_claim: >
  Calculations involving values that appear conceptually decimal or integer-like
  may produce floating-point artifacts.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules: []
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Unexpected decimals may affect comparisons, persistence, serialization,
  financial calculations, or metric reporting.

root_cause: >
  Depends on the specific execution path and numeric types.

mitigation_or_fix: >
  Inspect arithmetic types, conversions, rounding, serialization,
  and database precision before diagnosing the business formula.

regression_protection:
  tests: []
  guards: []

limitations:
  - This is a general numerical hazard until tied to a verified project-specific example.

reverify_when:
  - numeric representation changes
  - persistence types change
```

---

# 8. KI-004 — Dense Matrix Memory Assumptions

```yaml
id: KI-004
title: Dense Matrix Memory Assumptions

category:
  - PERFORMANCE
  - NUMERICAL

historical_claim: >
  Routing-matrix memory may have been estimated using theoretical raw storage
  without fully accounting for actual Java/JVM representation overhead.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Underestimating actual memory can produce invalid scalability claims
  or runtime failures at large problem sizes.

root_cause: >
  Not yet verified.

mitigation_or_fix: >
  Distinguish theoretical matrix storage, Java representation,
  JVM heap usage, and total process memory.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current matrix representation and instrumentation must be inspected.

reverify_when:
  - matrix representation changes
  - memory instrumentation changes
  - JVM configuration changes
```

---

# 9. KI-005 — Block Matrix Semantic Drift

```yaml
id: KI-005
title: Block Matrix Semantic Drift

category:
  - ENGINEERING
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  A block-oriented or block-diagonal routing representation may reduce memory
  while accidentally changing valid routing-cost queries or the feasible solution space.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  A representation change may be incorrectly reported as a pure memory optimization
  even if it changes the optimization problem itself.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Compare cost-query semantics against a trusted reference and separately validate
  solution feasibility and quality.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current implementation has not yet been classified as representation-preserving or approximate.

reverify_when:
  - matrix partitioning changes
  - clustering changes
  - node indexing changes
  - edge filtering changes
```

---

# 10. KI-006 — Sentinel Edge Penalty Risk

```yaml
id: KI-006
title: Sentinel Edge Penalty Risk

category:
  - NUMERICAL
  - ENGINEERING

historical_claim: >
  Large sentinel values used for unreachable or forbidden edges may interact
  badly with arithmetic or optimization logic.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Bad sentinel values may overflow, distort objective values,
  or allow invalid transitions to influence the solver.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Validate sentinel magnitude together with cost arithmetic and solver behavior.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current sentinel representation must be inspected.

reverify_when:
  - matrix cost representation changes
  - objective arithmetic changes
```

---

# 11. KI-007 — Integer Overflow in Quadratic Calculations

```yaml
id: KI-007
title: Integer Overflow in Quadratic Calculations

category:
  - NUMERICAL
  - PERFORMANCE

historical_claim: >
  Calculations proportional to N x N may overflow 32-bit integer intermediates
  even when the final result is assigned to a wider type.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Overflow may corrupt matrix-size calculations, memory estimates,
  indexes, or allocation logic.

root_cause: >
  Integer arithmetic may overflow before widening.

mitigation_or_fix: >
  Widen operands before multiplication and add boundary tests where relevant.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current vulnerable expressions have not yet been recorded.

reverify_when:
  - matrix sizing logic changes
  - indexing types change
```

---

# 12. KI-008 — Carbon Objective May Collapse to Distance

```yaml
id: KI-008
title: Carbon Objective May Collapse to Distance

category:
  - SCIENTIFIC
  - ENGINEERING

historical_claim: >
  The carbon-emission objective may be mathematically proportional to distance
  under some implementations or configurations.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  A nominal carbon objective would not provide an independent optimization signal,
  so claims of carbon-aware route improvement could be misleading.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Trace the active emission equation and test whether vehicle, load, speed,
  energy, or other scientifically relevant inputs can change route ranking
  independently from distance.

regression_protection:
  tests: []
  guards: []

limitations:
  - Do not report this as a current defect until the active model is verified.

reverify_when:
  - emission model changes
  - objective changes
  - vehicle energy model changes
```

---

# 13. KI-009 — Emission Parameter Propagation

```yaml
id: KI-009
title: Emission Parameter Propagation

category:
  - INTEGRATION
  - SCIENTIFIC

historical_claim: >
  Emission-related fields may exist in upstream inputs without reaching
  the active engine objective.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  The API may appear to support richer carbon modeling while the optimization
  engine ignores part of the supplied information.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Trace each emission parameter end-to-end from request to solver cost.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current parameter propagation has not yet been verified.

reverify_when:
  - API DTOs change
  - engine input changes
  - emission model changes
```

---

# 14. KI-010 — Solver Configuration Assumptions

```yaml
id: KI-010
title: Solver Configuration Assumptions

category:
  - ENGINEERING
  - PERFORMANCE

historical_claim: >
  Solver behavior may be incorrectly inferred from configuration names,
  defaults, or library knowledge rather than the effective runtime configuration.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Performance or solution-quality conclusions may be attributed to the wrong parameter values.

root_cause: >
  Effective runtime configuration may differ from assumed defaults.

mitigation_or_fix: >
  Trace and record effective solver values used by the active execution path.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current effective values must be independently inspected.

reverify_when:
  - solver configuration changes
  - jsprit integration changes
```

---

# 15. KI-011 — Vehicle Type Recreation Overhead

```yaml
id: KI-011
title: Vehicle Type Recreation Overhead

category:
  - PERFORMANCE
  - ENGINEERING

historical_claim: >
  Equivalent vehicle-type objects may be recreated repeatedly during large solver setup.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Repeated object creation may create avoidable setup overhead at scale.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Measure object-creation and setup cost before optimizing.

regression_protection:
  tests: []
  guards: []

limitations:
  - This remains a performance hypothesis until measured.

reverify_when:
  - vehicle construction logic changes
```

---

# 16. KI-012 — Effective Solver Thread Count

```yaml
id: KI-012
title: Effective Solver Thread Count

category:
  - PERFORMANCE
  - ENGINEERING

historical_claim: >
  Some solver paths may effectively execute with a single worker thread.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Hardware scaling assumptions may be incorrect if solver execution is effectively serial.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Inspect effective solver configuration and measure actual concurrency.

regression_protection:
  tests: []
  guards: []

limitations:
  - Do not report a current one-thread limitation without evidence.

reverify_when:
  - solver configuration changes
  - threading model changes
```

---

# 17. KI-013 — Fixed Iteration Budget Across Problem Sizes

```yaml
id: KI-013
title: Fixed Iteration Budget Across Problem Sizes

category:
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  A fixed jsprit iteration budget may be used across substantially different problem sizes.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Comparing different problem sizes with an unexamined fixed search budget may confound
  scalability and solution-quality conclusions.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Measure solution quality and runtime under controlled iteration budgets.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current effective iteration policy must be verified.

reverify_when:
  - solver stopping criteria change
  - benchmark protocol changes
```

---

# 18. KI-014 — Start/End Depot Semantics

```yaml
id: KI-014
title: Start and End Depot Semantics

category:
  - ENGINEERING
  - DATA

historical_claim: >
  Routing or matrix refactors may risk losing distinct vehicle start and end depot semantics.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Entry_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Incorrect depot handling changes route feasibility and optimization semantics.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Preserve and independently verify startDepotId and endDepotId through the full solver path.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current regression coverage must be inspected.

reverify_when:
  - vehicle model changes
  - matrix indexing changes
  - solver builder changes
```

---

# 19. KI-015 — Time-of-Day vs Duration Representation

```yaml
id: KI-015
title: Time-of-Day vs Duration Representation

category:
  - ENGINEERING
  - NUMERICAL

historical_claim: >
  Clock-of-day types such as LocalTime may be used where elapsed duration semantics are required.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Durations beyond 24 hours or elapsed-time calculations may be represented incorrectly.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Use time-of-day types only for clock semantics and duration types for elapsed time.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current field semantics must be inspected.

reverify_when:
  - time model changes
  - persistence mapping changes
```

---

# 20. KI-016 — Database Identifier Generation Mismatch

```yaml
id: KI-016
title: Database Identifier Generation Mismatch

category:
  - DATA
  - ENGINEERING

historical_claim: >
  JPA/Hibernate identifier generation may conflict with the actual MySQL schema strategy.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Identifier mismatches can cause duplicate keys, failed inserts, or inconsistent persistence behavior.

root_cause: >
  Not yet recorded with verified evidence.

mitigation_or_fix: >
  Verify entity generation strategy against the actual database schema.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current production schema may differ from repository initialization scripts.

reverify_when:
  - entity ID annotations change
  - schema changes
  - migration strategy changes
```

---

# 21. KI-017 — Duplicate-Key Diagnosis

```yaml
id: KI-017
title: Duplicate-Key Diagnosis

category:
  - DATA
  - ENGINEERING

historical_claim: >
  Duplicate-key errors may have multiple causes and should not automatically
  be treated as SQL-generation problems.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Incorrect diagnosis can hide retry, concurrency, seed-data, or business-key issues.

root_cause: >
  Depends on the failing constraint.

mitigation_or_fix: >
  Identify the exact violated constraint and inspect the corresponding data flow.

regression_protection:
  tests: []
  guards: []

limitations:
  - This is primarily a diagnostic lesson until attached to concrete historical evidence.

reverify_when:
  - persistence flows change
```

---

# 22. KI-018 — Callback Idempotency

```yaml
id: KI-018
title: Callback Idempotency

category:
  - INTEGRATION
  - CONCURRENCY

historical_claim: >
  Distributed job execution may deliver or retry callbacks more than once.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Non-idempotent callbacks may duplicate state transitions or persist inconsistent results.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Trace callback delivery and retry behavior and enforce idempotent processing where required.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current delivery semantics have not yet been recorded.

reverify_when:
  - callback logic changes
  - retry strategy changes
```

---

# 23. KI-019 — Missing Cancellation Checkpoints

```yaml
id: KI-019
title: Missing Cancellation Checkpoints

category:
  - ENGINEERING
  - INTEGRATION

historical_claim: >
  Long-running optimization may not observe cancellation at enough execution boundaries.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Cancelled jobs may continue consuming compute or publish late results.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Identify long-running phases and verify cancellation checks at meaningful boundaries.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current cancellation flow has not yet been inspected.

reverify_when:
  - engine execution pipeline changes
  - cancellation mechanism changes
```

---

# 24. KI-020 — API Race Conditions

```yaml
id: KI-020
title: API Race Conditions

category:
  - CONCURRENCY
  - DATA
  - ENGINEERING

historical_claim: >
  Concurrent requests may create inconsistent state, duplicate resources,
  or invalid state transitions if operations assume sequential execution.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Entry_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Race conditions may corrupt business state despite individually valid requests.

root_cause: >
  Not verified.

mitigation_or_fix: >
  Evaluate transactions, uniqueness constraints, locking, and idempotency
  according to the actual business invariant.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current concurrency behavior is unknown.

reverify_when:
  - service transaction boundaries change
  - state-transition logic changes
```

---

# 25. KI-021 — Time-Window Dataset Coverage

```yaml
id: KI-021
title: Time-Window Dataset Coverage

category:
  - SCIENTIFIC
  - DATA

historical_claim: >
  Some benchmark datasets may include orders without time windows.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Benchmark conclusions may overstate performance on VRPTW-like problems
  if the actual constraint coverage is substantially weaker.

root_cause: >
  Dataset composition.

mitigation_or_fix: >
  Record time-window coverage as benchmark metadata.

regression_protection:
  tests: []
  guards: []

limitations:
  - Actual current dataset coverage has not yet been measured.

reverify_when:
  - benchmark datasets change
```

---

# 26. KI-022 — Unused Optimization Infrastructure

```yaml
id: KI-022
title: Unused Optimization Infrastructure

category:
  - ENGINEERING
  - PERFORMANCE

historical_claim: >
  Optimization-related classes or components may exist in the repository
  without being used by the active runtime path.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Repository inspection may incorrectly conclude that an optimization is active.

root_cause: >
  Presence of unused or experimental code.

mitigation_or_fix: >
  Trace the active runtime call path rather than inferring behavior from class existence.

regression_protection:
  tests: []
  guards: []

limitations:
  - Specific historical examples must be attached before treating them as verified history.

reverify_when:
  - engine wiring changes
```

---

# 27. KI-023 — Runtime Extrapolation Beyond Measured Range

```yaml
id: KI-023
title: Runtime Extrapolation Beyond Measured Range

category:
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  Performance predictions may be extended beyond the range of directly measured problem sizes.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Extrapolated values may be misreported as measured scalability.

root_cause: >
  Not applicable as a single code defect.

mitigation_or_fix: >
  Explicitly label predictions outside the measured range as extrapolation.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current benchmark reporting must be inspected.

reverify_when:
  - performance report changes
  - benchmark range changes
```

---

# 28. KI-024 — Load Utilization Misinterpretation

```yaml
id: KI-024
title: Load Utilization Misinterpretation

category:
  - SCIENTIFIC

historical_claim: >
  Vehicle load utilization may be interpreted as a standalone measure of solution quality.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  High utilization can coexist with poor distance, emissions, service level,
  or feasibility.

root_cause: >
  Metric interpretation error.

mitigation_or_fix: >
  Interpret utilization together with other operational and objective metrics.

regression_protection:
  tests: []
  guards: []

limitations:
  - This is primarily a scientific interpretation hazard.

reverify_when:
  - benchmark metrics change
```

---

# 29. KI-025 — Memory Improvement vs Overall Improvement

```yaml
id: KI-025
title: Memory Improvement vs Overall Improvement

category:
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  Reduced memory consumption may be interpreted as an overall optimization
  without considering runtime or solution quality.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  A representation may save memory while increasing CPU cost or degrading solution quality.

root_cause: >
  Incomplete evaluation.

mitigation_or_fix: >
  Evaluate memory, runtime, feasibility, and solution quality together.

regression_protection:
  tests: []
  guards: []

limitations:
  - Specific historical experiments must be attached where available.

reverify_when:
  - matrix representation changes
```

---

# 30. KI-026 — Matrix-Level Verification Without Solution-Level Verification

```yaml
id: KI-026
title: Matrix-Level Verification Without Solution-Level Verification

category:
  - SCIENTIFIC
  - ENGINEERING

historical_claim: >
  A routing representation may pass local matrix checks without having been
  validated at the final solution level.

status: SUSPECTED
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Engine_API
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Local representation correctness does not guarantee equivalent solver behavior.

root_cause: >
  Verification may stop before end-to-end comparison.

mitigation_or_fix: >
  Compare independently recomputed feasibility and objective quality against
  an appropriate reference across repeated runs where stochasticity applies.

regression_protection:
  tests: []
  guards: []

limitations:
  - Current solution-level coverage must be verified.

reverify_when:
  - routing matrix changes
  - solver changes
```

---

# 31. KI-027 — Benchmark Scope Overclaim

```yaml
id: KI-027
title: Benchmark Scope Overclaim

category:
  - SCIENTIFIC
  - PERFORMANCE

historical_claim: >
  Scalability conclusions may extend beyond the largest validated problem size.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules:
    - GVRP_Benchmark
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Research conclusions may claim support for scales that were never directly tested.

root_cause: >
  Evidence scope may not be carried into the written conclusion.

mitigation_or_fix: >
  Record maximum validated scale in PROJECT_STATE.md and experiment metadata.

regression_protection:
  tests: []
  guards: []

limitations:
  - The current maximum verified scale is not defined here.

reverify_when:
  - new large-scale experiments are accepted
```

---

# 32. KI-028 — Dead Configuration

```yaml
id: KI-028
title: Dead Configuration

category:
  - ENGINEERING
  - INTEGRATION

historical_claim: >
  Configuration values may exist but not affect the active runtime path.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules: []
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Users or researchers may believe they changed solver behavior when the runtime
  execution is unaffected.

root_cause: >
  Unused configuration or incomplete propagation.

mitigation_or_fix: >
  Trace configuration from declaration to runtime consumption.

regression_protection:
  tests: []
  guards: []

limitations:
  - Specific current dead parameters must not be assumed.

reverify_when:
  - configuration wiring changes
```

---

# 33. KI-029 — Scientific Claim vs Implementation Claim

```yaml
id: KI-029
title: Scientific Claim vs Implementation Claim

category:
  - SCIENTIFIC

historical_claim: >
  The presence of a feature implementation may be interpreted as evidence
  that the intended scientific effect has been demonstrated.

status: HISTORICAL
evidence_strength: NONE

first_observed:
  revision: null
  date: null

last_verified:
  revision: null
  date: null

evidence:
  source: []
  tests: []
  experiments: []
  runtime: []
  issues: []
  documentation: []

scope:
  modules: []
  configurations: []
  datasets: []
  problem_size:
    min: null
    max: null

impact: >
  Working code can be mistaken for validation of a scientific hypothesis.

root_cause: >
  Engineering verification and scientific verification are conflated.

mitigation_or_fix: >
  Require controlled experiments before promoting scientific claims.

regression_protection:
  tests: []
  guards: []

limitations:
  - This is a project-wide methodological hazard rather than a single defect.

reverify_when:
  - scientific conclusions are updated
```

---

# 34. Promotion Rule

An issue must not move to `OPEN` merely because:

* an agent remembers it
* an old document mentions it
* a suspicious class or field exists
* the issue is theoretically possible

To classify an issue as `OPEN`, attach current evidence.

Preferred transition:

```text
HISTORICAL / SUSPECTED
        ↓
current investigation
        ↓
reproduction or direct evidence
        ↓
OPEN
```

---

# 35. Fix Rule

Do not classify an issue as `FIXED` merely because a patch exists.

Preferred transition:

```text
OPEN
  ↓
patch
  ↓
targeted verification
  ↓
regression protection
  ↓
FIXED
```

The verification scope must be recorded.

---

# 36. Evidence Scope Rule

A fix verified on:

```text
small synthetic dataset
```

does not imply validation on:

```text
large production-like dataset
```

Likewise:

```text
unit test passes
```

does not necessarily imply:

```text
scientific behavior is validated
```

Always retain the scope of evidence.

---

# 37. Issue vs Project State

Use `KNOWN_ISSUES.md` to answer:

> What has gone wrong, may go wrong, or has taught us an important lesson?

Use `PROJECT_STATE.md` to answer:

> What do we currently know about the active system?

Example:

```text
KNOWN_ISSUES:
KI-008 — Carbon objective may collapse to distance.

PROJECT_STATE:
STATE-CARBON-002 — Current carbon-vs-distance relationship.
```

The historical issue can remain even after the current state becomes verified as fixed.

---

# 38. Maintenance Rule

When investigating an issue:

1. verify current applicability
2. attach evidence
3. update status
4. record revision and date
5. record scope
6. add regression protection where appropriate
7. update related `PROJECT_STATE.md` claims
8. log important decisions in `RESEARCH_LOG.md`

Never delete a valuable historical lesson merely because the current implementation is fixed.
