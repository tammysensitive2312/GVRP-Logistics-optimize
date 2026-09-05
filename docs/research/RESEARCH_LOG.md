# GVRP Research Log

This document records durable research decisions, discoveries, experiment interpretations, and changes in scientific understanding.

It is chronological.

Current project state belongs in `PROJECT_STATE.md`.

Historical technical traps belong in `KNOWN_ISSUES.md`.

---

# Entry Format

Each entry should use:

```text
## RL-YYYY-NNN — Title

Date:

Context:

Question:

Observation:

Evidence:

Interpretation:

Decision:

Impact:

Remaining Uncertainty:

Related Experiment:

Related Issue:

Related Commit:
```

---

# Rules

## 1. Record discoveries, not every action

Do not turn this document into a command history.

Record information that changes how the project should be understood.

---

## 2. Distinguish observation from interpretation

Example:

```text
Observation:
Runtime increased from X to Y.

Interpretation:
The increase may be caused by matrix construction overhead.

Decision:
Run an isolated matrix-construction experiment.
```

Do not write:

```text
Matrix construction is the cause.
```

until verified.

---

## 3. Record rejected hypotheses

A rejected hypothesis is valuable.

Example:

```text
Hypothesis:
Increasing solver iterations will always improve solution quality.

Result:
Improvement plateaued after a certain range.

Conclusion:
The hypothesis is not supported under the tested conditions.
```

---

## 4. Record methodological changes

If the experiment protocol changes, record:

- previous protocol
- new protocol
- reason
- expected impact

---

## 5. Link evidence

Where possible reference:

- benchmark ID
- test
- source file
- dataset
- paper
- Git revision

---

# Current Log

No research-log entries should be invented.

New entries should be added as experiments and investigations are actually performed.

---

## RL-2026-001 — Carbon-vs-distance Stage-0 loop and C0 status correction

Date: 2026-09-05

Context:
First real research loop on RQ "Does the current carbon-aware objective produce
lower independently evaluated CO2 than distance-only optimization under
equivalent service conditions?" Workflow: Researcher protocol → Engineer
Stage-0 (no solver runs) → Reviewer → corrected re-review. Revision ba68918
(dirty tree; one uncommitted test-only divisor fix at
GVRP_Engine_API MeasuredLawsTest.java:434).

Question:
C0 effectiveness of the carbon-aware objective; decomposed into C1 (does the
carbon term carry routing info independent of distance under homogeneous
emissionFactor?) and C2 (can the whole-objective reweighting still change
routes and evaluated CO2?).

Observation:
Stage-0 first concluded C0 CONTRADICTED from C1 collinearity alone and skipped
any solver experiment. Re-open showed this equated term-level collinearity
with objective-level non-contrast.

Evidence:
- GreenVRPCostCalculator.java:88-132 (÷1e9 dimensionally correct under
  g/km × VND/ton → VND/m; fixed/time scale by costWeight only, :119-120).
- MeasuredLawsTest full run: 25 tests, 21 pass / 4 fail; all 4 failures
  recomputed as stale expectations vs current constants (break-even (0.7,0.3)
  = 12,481.9 m not 6364; ECO swing ≈35× not >10,000×).
- ECO_FOCUSED normalization (≈9.999e-5/0.9999) collapses fixed cost
  100000→≈10 and fuel 8→≈0.0008 VND/m; break-even vehicle-opening economics
  change ~35× (12500 m → ≈360 m on reference Truck 5T), recomputed twice
  independently.
- No solver A/B run exists; live fleet e-distribution and HTTP round-trip
  remain unobserved (DB client unavailable).

Interpretation:
C1 confirmed (carbon leg ∝ distance under fleet-uniform e, no independent
ranking info). C2 mechanism confirmed arithmetically: reweighting MAY/LIKELY
produce different fleet sizes, routes, and evaluated CO2 — direction,
magnitude, and service shift unknown without running. Prior CONTRADICTED
verdict on C0 overreached (term→objective fallacy).

Decision:
C0 status corrected CONTRADICTED → NOT_VERIFIED. A small preregistered
solver experiment (COST_FOCUSED vs ECO_FOCUSED, same hashed small dataset,
same budget, ≥5 seeds, service-parity rule, common independent evaluator,
attribution into Δdistance / Δvehicle-count / Δservice + real unweighted
cost) is scientifically required before any effectiveness promotion.

Impact:
STATE-CARBON-002 keeps status NOT_VERIFIED with an added limitation guarding
against the term/objective conflation. No emission-reduction claim promoted.

Remaining Uncertainty:
Live e-distribution; HTTP round-trip survival; unassigned-penalty scaling
across arms; stale break-even table and docstring example still unrepaired
in-tree.

Related Issue: KI-008, KI-009

Related Commit: ba68918 (dirty)

---

## RL-2026-002 — Experiment-governance reproducibility gap: missing command invocation

Date: 2026-09-06

Context:
Small end-to-end verification of experiment governance. Question: can an
accepted artifact be reproduced unambiguously from schema + validator +
protocol? Workflow: Researcher definition -> Engineer verification on
EXP-2026-999 -> Reviewer independent re-verification -> Orchestrator fix.

Question:
Does the current schema + validator enforce the minimum provenance needed
to re-execute an accepted experiment without guessing?

Observation:
EXP-2026-999 (synthetic, PLANNED) passes every promote-check gate except
execution_status. Configs are trivial ({"name":...}), raw/result.json is
{"runtime":1.23} with matching SHA-256, no command recorded anywhere.
Schema has no command/procedure slot (additionalProperties:false) and the
validator has no command check. A temp copy with execution_status flipped
to COMPLETED reaches PROMOTION: ALLOWED; a temp copy adding a procedure
block is rejected as an additional property.

Evidence:
- experiments/schema/experiment.schema.json: no command field; validate
  rejects added procedure before fix.
- tools/experiment_validator.py: validate_config/validate_raw_artifacts
  check existence only; validate_dataset_hash passes format-only when
  path missing; run_promote_check has no procedure gate before fix.
- Validator runs: validate exit 0 VALID; promote-check exit 1 DENIED
  (sole FAIL execution_status=PLANNED); temp COMPLETED copy exit 0
  ALLOWED (Reviewer, production untouched).
- SHA-256 of raw/result.json F9F9...29F matches metadata.

Interpretation:
One record maps to two materially different procedures (manual echo vs
solver run) with no field to disambiguate. Protocol Section 33 mentions
command for exploratory records but Section 6 + schema omit/forbid it
for promotable records. Severity MAJOR per Reviewer (promotion-gate
hole, synthetic demonstration, needs ACCEPTED review to exploit).

Decision:
Minimal governance fix applied (no solver change): optional top-level
procedure {commands[]: {command, produces[], arm?, working_dir?}} in
experiment schema; validate_procedure() covering every artifacts.raw
entry, enforced only in promote-check; protocol note added. Post-fix:
validate EXP-2026-999 still VALID; promote-check now FAILs on missing
procedure too; temp COMPLETED+procedure copy reaches ALLOWED. Nothing
promoted to PROJECT_STATE.md.

Impact:
Future COMPLETED records without invocation cannot reach promotion.
EXP-2026-999 remains PLANNED and unpromoted.

Remaining Uncertainty:
Free-text command strings can still record placeholders; working_dir/env
capture stays lightweight; effective-config content, dirty-diff capture,
and solver/env/seed strictness remain optional. Follow-ups deferred.

Related Experiment: EXP-2026-999 (synthetic fixture, not scientific)

Related Commit: working tree (dirty, governance files untracked)