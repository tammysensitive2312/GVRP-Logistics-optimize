# GVRP Research Methodology

## 1. Purpose

This document defines the scientific reasoning workflow used in the GVRP Logistics Optimize project.

It describes how a research question becomes a defensible conclusion.

Project-wide agent behavior is defined in:

```text
AGENTS.md
```

Experiment execution, metadata, artifacts, and reproducibility requirements are defined in:

```text
docs/research/EXPERIMENT_PROTOCOL.md
```

Current verified knowledge is recorded in:

```text
docs/research/PROJECT_STATE.md
```

Historical defects and methodological traps are recorded in:

```text
docs/research/KNOWN_ISSUES.md
```

---

# 2. Research Lifecycle

The default scientific lifecycle is:

```text
Problem
  ↓
Research Question
  ↓
Prior Evidence
  ↓
Hypothesis
  ↓
Operational Definitions
  ↓
Experimental Design
  ↓
Experiment
  ↓
Analysis
  ↓
Independent Review
  ↓
Conclusion
  ↓
Knowledge Promotion
```

The process is iterative.

A rejected hypothesis is a valid result when the experiment is valid.

Do not force experiments to confirm the original expectation.

---

# 3. Start With the Question

Research should begin with a precise question, not with an algorithm that needs justification.

Weak:

```text
Can we use algorithm X for GVRP?
```

Stronger:

```text
Does algorithm X reduce carbon emissions while maintaining comparable
service level and acceptable computational cost relative to baseline Y?
```

A useful research question should identify:

* what is being changed
* what is being measured
* what it is compared against
* under what conditions
* what outcome would matter

If these are unclear, refine the question before implementation.

---

# 4. Separate Engineering Questions From Scientific Questions

These are different:

```text
Does the implementation calculate carbon cost?
```

and:

```text
Does carbon-aware optimization reduce emissions?
```

The first is an engineering verification question.

The second is a scientific research question.

Likewise:

```text
Does the solver return feasible routes?
```

does not imply:

```text
The proposed optimization method improves routing quality.
```

Do not use engineering verification as a substitute for scientific evidence.

---

# 5. Prior Evidence

Before proposing a new hypothesis, inspect relevant existing evidence.

Depending on the question, this may include:

* current project state
* previous experiments
* known issues
* scientific literature
* implementation behavior
* benchmark results

The purpose is to determine:

```text
What is already known?
What remains uncertain?
What contradiction exists?
What experiment would reduce that uncertainty?
```

Do not repeat an experiment merely because the previous result was inconvenient.

Do repeat it when evidence quality, applicability, or reproducibility is insufficient.

---

# 6. Research Gap

A research gap is not:

```text
The project does not implement feature X.
```

A meaningful gap identifies something unresolved in existing evidence or literature.

Example structure:

```text
Existing methods optimize A and B under assumption C,
but the target operating context violates or relaxes C.

Therefore it remains unclear whether...
```

A gap should lead directly to a research question.

If no evidence supports the claimed gap, classify it as a candidate gap rather than an established one.

---

# 7. Hypothesis

A hypothesis must be falsifiable.

It should define:

* independent variable
* dependent variable
* expected relationship
* controlled conditions
* falsification criterion

Example:

```text
H1:

Using a load-dependent carbon model in route optimization will produce
lower independently evaluated carbon emissions than distance-only
optimization while maintaining comparable served demand.
```

A corresponding falsification condition may be:

```text
H1 is not supported if the treatment does not reduce independently
evaluated emissions under comparable service levels.
```

Avoid hypotheses such as:

```text
The new algorithm will perform better.
```

because "better" is undefined.

---

# 8. Operational Definitions

Before experimentation, translate conceptual terms into measurable quantities.

For example:

```text
Lower emissions
```

must define:

* emission model
* emission unit
* system boundary
* evaluator
* aggregation method

```text
Better solution quality
```

must define the relevant metrics.

```text
Comparable service level
```

should define metrics such as:

* served orders
* unserved orders
* delivered demand
* feasibility

Do not interpret undefined concepts after seeing the results.

---

# 9. Mathematical Model

The mathematical formulation should exist independently from implementation details.

Define:

* sets
* parameters
* decision variables
* objective functions
* constraints
* units
* assumptions

Then trace the relationship:

```text
mathematical model
        ↓
software representation
        ↓
solver configuration
        ↓
measured output
```

A mismatch between formulation and implementation is evidence of an engineering problem.

A mathematically consistent implementation may still represent a scientifically weak model.

These are separate evaluations.

---

# 10. Objective Functions

For every objective term, determine:

```text
What does it represent?
What unit does it use?
How is it computed?
How is it scaled?
How does it affect solver decisions?
```

For weighted objectives such as:

```text
F = w1 f1 + w2 f2
```

do not interpret equal weights as equal importance without considering scale and normalization.

When multiple objectives represent genuine trade-offs, preserve that trade-off explicitly rather than hiding it behind unexplained constants.

---

# 11. Carbon-Emission Reasoning

Carbon-related research must separate:

```text
optimization model
```

from:

```text
evaluation model
```

The optimizer may use a simplified carbon signal.

Scientific comparison should preferably use a common independent evaluator for all competing route sets.

Given route sets:

```text
R_baseline
R_treatment
```

compare:

```text
E(R_baseline, M)
E(R_treatment, M)
```

using the same evaluation model `M`, unless the experiment explicitly studies different emission models.

Otherwise the observed difference may combine:

```text
routing effect
+
evaluation-model effect
```

and cannot be attributed purely to routing.

The carbon model must also state its system boundary.

Possible boundaries differ substantially, for example:

* tailpipe emissions
* energy-use emissions
* electricity-generation emissions
* lifecycle emissions

Do not compare values whose boundaries differ without explicitly accounting for the difference.

---

# 12. Service-Level Parity

Environmental improvement must not be obtained merely by reducing service.

A treatment that emits less because it leaves more orders unserved is not automatically a better routing solution.

Therefore environmental comparisons should evaluate service outcomes alongside emissions.

Relevant quantities may include:

```text
served_orders
unserved_orders
delivered_demand
feasibility
distance
emissions
runtime
```

The exact parity condition must be defined by the experiment.

---

# 13. Baseline Selection

The baseline should answer:

> Better than what?

A baseline must represent a meaningful alternative for the research question.

Possible baselines include:

* current production behavior
* distance-only routing
* existing solver configuration
* known algorithm
* previous research method

Do not choose an artificially weak baseline merely to increase the apparent improvement.

When comparing algorithms, computational budget and stopping conditions should be considered.

---

# 14. Controlled Comparison

A scientific comparison should isolate the intended variable.

For example, when testing a new carbon objective:

Prefer keeping constant:

* dataset
* fleet
* constraints
* routing graph
* solver
* iteration budget
* hardware
* evaluation model

and changing:

```text
objective formulation
```

If several factors change together, the experiment may demonstrate that the complete system differs, but it cannot isolate which factor caused the result.

---

# 15. Representation Optimization vs Problem Approximation

This distinction is especially important for routing-matrix research.

## Representation Optimization

A change affects:

* storage
* computation
* data structure

while preserving the same logical cost-query semantics and feasible solution space.

Example intention:

```text
Dense representation
        ↓
more memory-efficient representation

same routing problem
```

## Problem Approximation

A change removes, restricts, or approximates previously valid information.

For example:

```text
valid cross-region edges removed
```

may change the feasible solution space.

This is not merely a storage optimization.

It is an approximation of the routing problem and must be evaluated for solution-quality loss.

---

# 16. Stochastic Solvers

A stochastic solver may return different solutions for equivalent problem representations.

Therefore representation equivalence should not normally require:

```text
identical route sequence
```

Instead compare:

* independently recomputed feasibility
* objective values
* service level
* relevant scientific metrics
* distributions across repeated runs where appropriate

Differences must be interpreted in light of solver stochasticity.

---

# 17. Measurement Before Explanation

Do not create an explanation before establishing the observation.

Preferred order:

```text
measure
  ↓
verify measurement
  ↓
identify pattern
  ↓
propose explanation
  ↓
test explanation
```

Avoid:

```text
assume explanation
  ↓
select supporting measurements
```

For surprising results, first investigate:

* units
* data integrity
* configuration
* measurement boundaries
* implementation path
* randomness

before proposing deeper algorithmic explanations.

---

# 18. Analysis

Analysis should answer the research question rather than merely summarize output.

At minimum distinguish:

## Observation

What was measured.

## Comparison

How treatment and baseline differ.

## Uncertainty

How stable or variable the result is.

## Interpretation

What the difference may mean.

## Alternative explanation

What else could produce the observation.

## Applicability

Under what conditions the result is supported.

Statistical tools should be selected based on experimental design.

Do not use statistical sophistication as a substitute for a valid experiment.

---

# 19. Effect Size and Practical Significance

A statistically detectable difference may be operationally irrelevant.

For example:

```text
0.1% emission reduction
```

may be statistically stable but not practically meaningful if it requires:

```text
5x runtime
```

or substantially higher infrastructure cost.

Research conclusions should consider both:

```text
statistical evidence
```

and:

```text
practical effect
```

when applicable.

---

# 20. Threats to Validity

Before accepting a conclusion, examine at least four dimensions.

## Internal Validity

Could another changed variable explain the result?

## Construct Validity

Do the chosen metrics actually represent the intended concept?

Example:

Does the chosen CO2 formula represent the emission scope claimed by the research?

## External Validity

Does the result generalize beyond the tested:

* datasets
* fleet
* geography
* traffic assumptions
* problem sizes

## Statistical Conclusion Validity

Is there enough evidence to justify the claimed relationship?

A limitation does not automatically invalidate the experiment.

It defines the scope of the conclusion.

---

# 21. Negative and Null Results

Do not discard experiments because:

```text
the treatment was worse
```

or:

```text
no meaningful difference was observed
```

Record such results when the experiment is valid.

They may show:

* a hypothesis is unsupported
* an optimization is unnecessary
* a model parameter has little influence
* a research direction should change

Negative results are part of project knowledge.

---

# 22. Independent Review

Important research conclusions should be reviewed independently.

The reviewer should inspect the actual artifacts rather than relying only on the researcher's or engineer's summary.

The reviewer should attempt to find:

* incorrect assumptions
* protocol violations
* measurement errors
* inconsistent units
* unfair baselines
* service-level differences
* data leakage
* unsupported generalization
* contradictory evidence

The purpose is falsification, not confirmation.

---

# 23. Conclusion Strength

The strength of the conclusion must not exceed the evidence.

Prefer:

```text
Under the tested configuration and datasets, treatment A reduced
independently evaluated emissions relative to baseline B.
```

over:

```text
Treatment A is a better GVRP algorithm.
```

unless broader evidence supports the broader claim.

Always preserve:

* tested scope
* important assumptions
* limitations
* remaining uncertainty

---

# 24. Knowledge Promotion

An experiment result is not automatically verified project knowledge.

Preferred lifecycle:

```text
hypothesis
    ↓
experiment
    ↓
raw evidence
    ↓
analysis
    ↓
independent review
    ↓
accepted conclusion
    ↓
PROJECT_STATE.md
```

Only durable conclusions should be promoted.

Raw output belongs with experiment artifacts.

Chronological decisions belong in:

```text
RESEARCH_LOG.md
```

Historical failures belong in:

```text
KNOWN_ISSUES.md
```

---

# 25. Iteration

An accepted conclusion should usually produce one of:

```text
new verified knowledge
new limitation
new research question
new hypothesis
engineering action
stop condition
```

Do not continue generating experiments indefinitely.

Stop a research branch when:

* the question has sufficient evidence for its intended scope
* evidence shows the hypothesis is unsupported and no useful refinement remains
* the cost of further evidence exceeds its expected research value
* required data or environment is unavailable
* the question is outside project scope

---

# 26. Core Scientific Test

Before accepting a research claim, ask:

```text
What exactly was changed?

What exactly was measured?

What remained constant?

Was the comparison fair?

Was service comparable?

Was the metric independently valid?

Could another explanation produce the result?

What scope does the evidence actually support?

What remains unknown?
```

If these questions cannot be answered, the conclusion is not ready for promotion.
