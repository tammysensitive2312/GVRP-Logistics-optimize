# GVRP Experiment and Verification Protocol

## 1. Purpose

This document defines how verification and scientific experiments are planned, executed, recorded, reviewed, and promoted into durable project knowledge.

It complements:

* `AGENTS.md` — project policy and task routing
* `METHODOLOGY.md` — scientific reasoning
* `PROJECT_STATE.md` — current project knowledge
* `KNOWN_ISSUES.md` — historical defects and lessons

This protocol answers:

> What evidence is required for this claim, and how must that evidence be recorded?

---

# 2. Verification Is Claim-Dependent

Not every claim requires an experiment.

Before gathering evidence, classify the claim.

## 2.1 Implementation Verification

Use when the question is:

> What does the current software do?

Examples:

* Which solver path is active?
* Which objective terms reach jsprit?
* Which configuration value is actually used?
* Does `emissionFactor` reach the Engine?

Typical evidence:

* source trace
* configuration
* runtime trace
* targeted test
* logs

Example:

```text
Claim:
The active Engine passes emissionFactor into GreenVRPCostCalculator.

Verification:
request/config
→ DTO
→ mapping
→ solver construction
→ calculator invocation
```

An A/B scientific experiment is not required merely to establish an execution path.

---

## 2.2 Mathematical Verification

Use when the question concerns a mathematical property.

Examples:

* Is the CO₂ term proportional to distance?
* Can an integer expression overflow?
* Are objective units compatible?
* Does a transformation preserve a formula?
* What is the theoretical memory complexity?

Typical evidence:

* explicit formula
* assumptions
* derivation
* dimensional analysis
* boundary analysis
* independent recomputation
* source trace when verifying the implemented formula

Example:

```text
Given:

CO2(D) = eD / 1000

Assumption:

e is constant across all relevant alternatives.

Then:

CO2(D) = kD

where k = e / 1000 is constant.

Therefore the CO2 term contains no ranking information independent
of distance within this assumption scope.
```

A controlled routing experiment is not required to establish this mathematical property.

The scope of the assumptions must be retained.

---

## 2.3 Experimental Verification

Use when the claim concerns observed system or scientific behavior.

Examples:

* Does carbon-aware routing reduce emissions?
* Does a matrix representation reduce memory?
* Does a new heuristic improve solution quality?
* Does increasing iterations improve the quality/runtime trade-off?

Typical evidence:

* defined baseline
* treatment
* controlled variables
* dataset identity
* repeated runs where needed
* raw measurements
* independent evaluation
* analysis
* review

Experimental evidence is required when the conclusion depends on observed outcomes rather than derivation alone.

---

## 2.4 Literature Verification

Use when the claim concerns external scientific knowledge.

Examples:

* Is a particular emission model established in the literature?
* What methods are commonly used for Green VRP?
* What assumptions are supported by previous studies?

Evidence should identify:

* source
* publication metadata
* exact supported claim
* applicability to this project

Literature authority does not prove that the current implementation follows the literature.

---

## 2.5 Mixed Verification

Many important questions require more than one verification type.

Example:

```text
Question:
Does the carbon-aware objective reduce real routing emissions?

Implementation verification:
What carbon objective is actually active?

Mathematical verification:
What information does that objective contain?

Experimental verification:
Does optimization produce lower independently evaluated emissions?

Literature verification:
Is the evaluator scientifically defensible?
```

Do not collapse these into one evidence category.

---

# 3. Verification Record

Every significant verification should record:

```yaml
verification_id: VER-YYYY-NNN

claim: >
  The exact claim being evaluated.

verification_type:
  - IMPLEMENTATION
  - MATHEMATICAL

status: SUPPORTED | PARTIALLY_SUPPORTED | NOT_SUPPORTED | INCONCLUSIVE

revision:
  commit: "<git-sha>"
  branch: "<branch>"

verified_at: "<timestamp>"

scope:
  modules: []
  configurations: []
  datasets: []
  assumptions: []

evidence:
  source: []
  tests: []
  runtime: []
  derivations: []
  literature: []

contradictions: []

limitations: []

conclusion: >
  Conclusion limited to the recorded scope.
```

`verification_type` may contain multiple values.

Do not fill unknown metadata by guessing.

---

# 4. Experiment Identity

Every scientific experiment must have a unique identifier.

Use:

```text
EXP-YYYY-NNN
```

Example:

```text
EXP-2026-001
```

An experiment ID refers to one research comparison or tightly related experimental design.

Repeated stochastic runs belong under the same experiment when they implement the same protocol.

---

# 5. Experiment Directory

Recommended structure:

```text
experiments/
└── runs/
    └── EXP-2026-001/
        ├── metadata.json
        ├── config.json
        ├── protocol.md
        ├── raw/
        ├── processed/
        ├── analysis/
        └── review.json
```

Meaning:

```text
metadata.json
    experiment identity and environment

config.json
    solver and experimental configuration

protocol.md
    question, hypothesis, baseline, treatment,
    controlled variables and falsification criteria

raw/
    immutable raw outputs

processed/
    derived datasets and normalized results

analysis/
    calculations, plots and interpretation artifacts

review.json
    independent review verdict
```

Do not overwrite raw results during analysis.

---

# 6. Required Experiment Metadata

A scientific experiment is not eligible for knowledge promotion unless the following are recorded.

## Identity

```text
experiment_id
title
created_at
```

## Revision

```text
git_commit
git_branch
working_tree_state
```

If the working tree is dirty, record that fact.

A commit hash alone does not completely identify an experiment performed with uncommitted changes.

---

## Research Definition

```text
research_question
hypothesis
falsification_criteria
```

---

## Comparison

```text
baseline
treatment
controlled_variables
```

---

## Dataset

```text
dataset_id
dataset_hash
dataset_description
```

When the dataset is generated:

```text
generator
generator_version
seed
generation_parameters
```

should be recorded when applicable.

---

## Environment

Record relevant environment information such as:

```text
OS
Java version
JVM configuration
CPU
memory
solver/library versions
```

Only record fields relevant to reproducibility.

---

## Measurement

Every metric must define:

```text
name
unit
measurement_method
```

Examples:

```text
distance
unit: km

emissions
unit: kgCO2e

runtime
unit: s

peak_heap
unit: MiB
```

Do not store ambiguous names such as:

```text
time: 123
```

---

## Measurement Procedure

For promotion-eligible (`COMPLETED`) experiments, record the exact
procedure that produced each raw artifact:

```text
procedure.commands[]: exact shell invocation(s)
procedure.commands[].produces[]: raw artifact refs produced by that command
procedure.commands[].working_dir (optional): working directory, defaults to repo root
procedure.commands[].arm (optional): baseline | treatment | seed label
```

`validate` allows `PLANNED` records without `procedure`;
`promote-check` requires every `artifacts.raw` entry to be covered by
at least one command and rejects `produces` entries not listed in
`artifacts.raw`.

---

# 7. Pre-Execution Protocol

Before running the experiment, define:

```text
research question
        ↓
hypothesis
        ↓
falsification criterion
        ↓
baseline
        ↓
treatment
        ↓
controlled variables
        ↓
metrics
        ↓
evaluation method
        ↓
repetitions / seeds
        ↓
stopping criteria
```

Do this before inspecting treatment results whenever practical.

The purpose is to reduce post-hoc reinterpretation.

---

# 8. Baseline and Treatment

The protocol must state exactly what differs.

Example:

```text
Baseline:
distance-only objective

Treatment:
load-dependent carbon-aware objective
```

Then explicitly state what remains constant:

```text
same dataset
same fleet
same routing graph
same constraints
same solver
same iteration budget
same independent evaluator
same hardware
```

If multiple factors differ, record them.

Do not describe a multi-factor comparison as isolating one factor.

---

# 9. Dataset Identity

Dataset identity must be reproducible.

Preferred evidence:

```text
dataset path
+
cryptographic hash
```

For generated datasets also preserve the generation parameters.

Example:

```text
dataset_id: SYNTH-5000-V3
dataset_hash: SHA256:...
seed: 42001
```

If a dataset changes, it is a different experimental input even if the filename remains unchanged.

---

# 10. Service-Level Comparability

For routing comparisons, record service outcomes.

At minimum when applicable:

```text
total_orders
served_orders
unserved_orders
total_demand
delivered_demand
feasible
```

A lower objective or lower emission value must not automatically be interpreted as improvement if service differs materially.

The experiment must define the required service-parity condition.

Example:

```text
served_orders_treatment == served_orders_baseline
```

or another justified rule.

---

# 11. Independent Objective Recalculation

Important solution metrics should be recomputed outside the solver's internal objective when practical.

For a returned route set:

```text
solver solution
      ↓
independent evaluator
      ↓
distance
emissions
service
feasibility
```

This reduces the risk of validating an implementation using the same faulty calculation that produced the solution.

For carbon experiments, baseline and treatment should normally use the same evaluator.

---

# 12. Carbon Experiment Requirements

A carbon-routing experiment must record:

```text
emission model
emission model version
units
system boundary
parameters
parameter sources
```

The system boundary must be explicit.

Examples:

```text
tailpipe
energy-use
electricity-generation
lifecycle
```

Do not mix different boundaries in one comparison without explicit transformation or justification.

If the optimization model differs from the evaluation model, record both separately:

```text
optimization_emission_model
evaluation_emission_model
```

---

# 13. Stochastic Solver Protocol

When solver stochasticity may affect results, one run is generally insufficient for comparative claims.

Record:

```text
number_of_runs
seed strategy
per-run seed
```

Preserve each run separately.

Example:

```text
raw/
├── baseline-seed-001.json
├── baseline-seed-002.json
├── ...
├── treatment-seed-001.json
└── treatment-seed-002.json
```

Do not select only the best treatment run unless the baseline is evaluated under the same selection policy.

---

# 14. Computational Budget

Algorithm comparisons must record computational budget.

Depending on the solver:

```text
iterations
runtime limit
termination criteria
threads
parallel workers
```

A method given substantially more search budget is not a clean algorithm comparison unless that difference is part of the research question.

---

# 15. Runtime Measurement

Define runtime boundaries.

Possible measurements include:

```text
matrix construction
problem construction
solver runtime
post-processing
end-to-end runtime
```

Do not compare:

```text
solver-only runtime
```

against:

```text
end-to-end runtime
```

without explicitly stating the difference.

Use consistent clock semantics and units.

---

# 16. Memory Measurement

Distinguish:

```text
theoretical storage
Java object allocation
JVM heap
process resident memory
system memory
```

A theoretical matrix-size formula is not a measured heap value.

Record the memory metric and measurement method.

---

# 17. Matrix Experiments

Before evaluating performance, classify the matrix change.

## Representation-Preserving

The logical cost-query semantics remain unchanged.

Then test:

```text
semantic equivalence
memory
runtime
solution-level behavior
```

## Approximate

Some routing information or feasible transitions change.

Then test:

```text
memory
runtime
feasibility
solution quality loss
service level
objective degradation
```

Do not report an approximate representation solely as a memory optimization.

---

# 18. Raw Evidence

Raw experimental output must remain traceable to the run that produced it.

Raw artifacts should not be manually edited to improve readability.

If normalization or cleanup is needed:

```text
raw
 ↓
processing step
 ↓
processed
```

Preserve the transformation method.

---

# 19. Derived Metrics

Every derived metric should retain its formula.

Example:

```text
emission_reduction_pct =
    (baseline_emissions - treatment_emissions)
    / baseline_emissions
    * 100
```

For important derived results, independently recompute when practical.

Never copy a derived number into a report without preserving how it was produced.

---

# 20. Statistical Analysis

Statistical analysis must follow the experimental design.

Possible outputs include:

```text
mean
median
standard deviation
confidence interval
effect size
distribution
```

Use hypothesis tests only when justified.

Do not interpret:

```text
p < threshold
```

as proof of practical importance.

Report effect magnitude and uncertainty where appropriate.

---

# 21. Extrapolation

Measured and predicted values must remain separate.

Example:

```text
Measured:
N = 1k, 5k, 10k

Extrapolated:
N = 25k
```

An extrapolated value must record:

```text
model
measured fitting range
assumptions
validation if available
```

Do not place extrapolated values in a table labeled simply "benchmark results" without distinction.

---

# 22. Failure Is Evidence

Failed runs should be retained when scientifically relevant.

Examples:

```text
out of memory
timeout
infeasible solution
solver exception
invalid configuration
```

Do not silently remove failures from aggregate results.

Record whether the failure belongs to:

```text
algorithm behavior
implementation defect
environment limitation
invalid experiment
```

when determinable.

---

# 23. Tooling Failure

If an experiment cannot run because of:

```text
missing dependency
uv failure
sandbox restriction
network restriction
JDK mismatch
unavailable dataset
insufficient memory
```

record:

```text
execution_status: BLOCKED
```

and explain the reason.

Do not fabricate results.

A blocked experiment cannot support an empirical conclusion.

---

# 24. Experiment Completion Status

Use:

```text
PLANNED
RUNNING
COMPLETED
BLOCKED
INVALID
```

These describe execution, not scientific outcome.

A `COMPLETED` experiment may still fail review.

---

# 25. Review Status

Review is separate from execution.

Use:

```text
NOT_REVIEWED
ACCEPTED
ACCEPTED_WITH_LIMITATIONS
REJECTED
REQUIRES_RERUN
```

Examples:

```text
execution_status: COMPLETED
review_status: REQUIRES_RERUN
```

or:

```text
execution_status: COMPLETED
review_status: ACCEPTED_WITH_LIMITATIONS
```

Do not equate successful execution with accepted evidence.

---

# 26. Independent Review

The reviewer should inspect:

```text
protocol
metadata
configuration
raw results
processing
analysis
source revision
```

The reviewer should attempt independent recomputation of critical metrics when practical.

The reviewer must not rely solely on the Engineer's summary.

---

# 27. Review Questions

The reviewer should ask:

```text
Was the intended revision actually tested?

Was the working tree state recorded?

Was the dataset identified?

Were baseline and treatment comparable?

Was service parity satisfied?

Were units defined?

Was the evaluation method common and independent?

Were computational budgets fair?

Was stochasticity handled?

Can critical derived values be recomputed?

Were failures retained?

Does the conclusion exceed the evidence?
```

---

# 28. Review Verdict

`review.json` should eventually contain a machine-readable verdict.

Conceptually:

```json
{
  "experiment_id": "EXP-2026-001",
  "review_status": "ACCEPTED_WITH_LIMITATIONS",
  "protocol_compliance": true,
  "critical_issues": [],
  "limitations": [
    "Validated only for problem sizes up to 5000 orders."
  ],
  "knowledge_promotion_allowed": true
}
```

The exact schema will be defined separately.

---

# 29. Knowledge Promotion Gate

An experimental conclusion may be promoted to `PROJECT_STATE.md` only when:

```text
execution_status
    = COMPLETED

AND

review_status
    = ACCEPTED
      OR ACCEPTED_WITH_LIMITATIONS

AND

required metadata
    = valid

AND

evidence artifacts
    = traceable
```

If accepted with limitations, those limitations must be transferred into the project-state claim.

---

# 30. Verification Promotion

Non-experimental verification may also update `PROJECT_STATE.md`.

For example:

```text
source trace
+
runtime verification
```

may establish an implementation fact.

Or:

```text
verified implementation formula
+
mathematical derivation
```

may establish a mathematical property.

Therefore:

```text
PROJECT_STATE
      ↑
      │
 ┌────┴─────────────┐
 │                  │
verification     experiment
record           + review
```

Do not require experiments for claims that can be established directly by stronger appropriate evidence.

---

# 31. Claim Scope

Every accepted result must preserve applicability.

Example:

```text
SUPPORTED:

Under homogeneous emission factors,
the implemented CO2 term is proportional to distance.
```

does not imply:

```text
UNSUPPORTED GENERALIZATION:

The project carbon objective can never influence routing.
```

Likewise:

```text
Treatment reduced emissions on datasets <= 5,000 orders.
```

does not imply:

```text
Treatment reduces emissions at all scales.
```

---

# 32. Contradictory Evidence

If new evidence contradicts an accepted result:

```text
do not silently overwrite old conclusion
```

Instead:

```text
record contradiction
      ↓
identify scope/revision differences
      ↓
investigate
      ↓
update PROJECT_STATE
      ↓
preserve historical context
```

The new result may reveal:

* regression
* configuration difference
* dataset dependence
* incorrect prior conclusion
* narrower applicability than previously believed

---

# 33. Minimal Experiment Record

For exploratory work, a lightweight experiment may initially contain:

```text
experiment_id
question
revision
dataset
config
command
raw output
```

But exploratory evidence is not automatically eligible for scientific knowledge promotion.

Before promotion it must satisfy the full required protocol.

This distinction prevents bureaucracy from blocking quick investigation while preserving rigor for final claims.

---

# 34. Experiment Lifecycle

The intended lifecycle is:

```text
Researcher
   │
   │ question
   │ hypothesis
   │ protocol
   ▼
PLANNED
   │
   ▼
Engineer
   │
   │ implementation/configuration
   │ execution
   │ raw evidence
   ▼
COMPLETED
   │
   ▼
Reviewer
   │
   ├── ACCEPTED
   ├── ACCEPTED_WITH_LIMITATIONS
   ├── REQUIRES_RERUN
   └── REJECTED
   │
   ▼
Orchestrator
   │
   ├── promote knowledge
   ├── revise experiment
   ├── create new hypothesis
   └── stop
```

A review failure returns to the smallest responsible stage.

Examples:

```text
missing metadata
→ Engineer

invalid baseline
→ Researcher

implementation bug
→ Engineer

unsupported hypothesis
→ Researcher

insufficient repetitions
→ Engineer / rerun

fundamental research-question problem
→ Researcher
```

---

# 35. Core Rule

The purpose of the protocol is not to create paperwork.

It is to preserve the chain:

```text
claim
  ↓
appropriate verification method
  ↓
reproducible evidence
  ↓
independent review
  ↓
scope-aware conclusion
  ↓
durable project knowledge
```

No stage should claim more certainty than the evidence supports.