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

---

## RL-2026-003 — Literature-first GVRP gap investigation (search 2026-09-05)

Date: 2026-09-06

Context:
Literature-first investigation (no brainstorming first). Workflow: Orchestrator
Stage-1 context -> Researcher protocol + corpus + landscape + gaps + triangulation ->
Reviewer adversarial re-verification (OpenAlex/publisher/arXiv + own falsification
searches F1-F4) -> Orchestrator fit + logging. No production code changed. Corpus and
verdicts recorded in docs/research/LITERATURE.md (created this entry; previously absent).

Question:
Which GVRP research gaps are scientifically defensible AND feasible for this project
(GraphHopper + jsprit, vn_n1000/vn_n5000 + vn6183)?

Observation:
Corpus: 8 surveys + 20 primaries/context (DOIs + levels recorded; 2 full-text excerpts:
Turkensteen 2017, Koc 2014; rest abstract/metadata). Four broad gap framings were
killed or narrowed by targeted falsification (Liu ALNS-1000 + XL-CVRP + SVRPBench kill
"no large green"; Koc/E-HFFVRP/hybrid-fleet kill "hetero unstudied"; Franceschetti/Liu
kill "no TD green"; Demir2011/Turkensteen/CIRRELT kill "no model comparison").
Reviewer downgrades: GAP-5 broadly near-contradicted (PLOS 2026 sensitivity; Tehran
MOSVRP 2025); GAP-4 reclassified as protocol, not gap.

Evidence:
- LITERATURE.md: full citation table with DOI + inspection level + verdicts.
- Reviewer citation re-verification: Asghari, Bektas&Laporte, Koc, Raeesi via OpenAlex;
  Turkensteen via RePEc/ScienceDirect/Semantic Scholar; Schneider via publisher;
  Liu BCP multi-source; SVRPBench verified with non-green caveat; Shahin2025 partial
  (future-dated volume).
- Falsification: F1 Wang2025/Li2018 hetero (narrow GAP-1); F2 VN e-moto routing no OR
  hit (strengthen GAP-3); F3 Pareto sensitivity hits (narrow GAP-5); F4 real-network
  PRP cases (narrow GAP-2).

Interpretation:
Strongest: GAP-3 motorcycle-specific VN last-mile (SUPPORTED_GAP, MODERATE).
Plausible conjunctions: GAP-1 VN-calibrated hetero factors; GAP-2 large-scale real-matrix
green + hetero (both PLAUSIBLE, LIMITED-MODERATE). GAP-4 is hygiene, not novelty
(STRONG prescription, NONE novelty). GAP-5 only as in-house experiment gated on GAP-1+GAP-4.

Decision:
Top-3 directions: (1) VN-calibrated hetero emission factors + evaluator/parity protocol;
(2) motorcycle-specific VN last-mile optimisation; (3) large-scale real-matrix green
routing conditioned on (1). Primary RQ + hypotheses + minimum experiment recorded in
the final report; pre-registration required before runs. Open items: source-trace
16-factor + UNASSIGNED premises; VN-language pass; truck->moto energy boundary; CMEM
reconciliation.

Impact:
No PROJECT_STATE.md promotion (reviewer-forbidden as verified fact). LITERATURE.md
created as triangulation record; this log entry preserves the decision trail.

Remaining Uncertainty:
Single-round searches; VN-language sources unsearched; paywalled full texts mostly
abstract-judged; 2025-2026 items evolving; internal prior-work overlap not fully excluded.

Related Commit: working tree (governance/docs untracked)

---

## RL-2026-004 — GAP-3 hostile falsification loop (2026-09-06)

Date: 2026-09-06

Context:
Re-open of GAP-3 only, with intent to falsify (not confirm). Researcher ran expanded
Vietnamese + SE-Asian terminology searches (8 websearch queries incl. Vietnamese, OpenAlex
recall query count 556 with top-10 triaged) and bucketed every hit as
ROUTING_OPTIMIZATION vs FLEET_SELECTION/MCDM vs POLICY/DESCRIPTIVE vs GENERIC_VRP_VN.
Reviewer independently re-verified the two load-bearing narrowers via OpenAlex DOI lookup
+ DOI-resolution search (no direct MDPI PDF parse or KCI page fetch achieved).

Question:
Should GAP-3 ("motorcycle-specific Vietnamese last-mile green routing is underexplored")
remain as-stated, be upgraded, narrowed, or rejected?

Observation:
Two peer-reviewed nearest neighbours surfaced. N1: Lee et al. 2020, Sustainability
12:1077, doi:10.3390/su12031077 — VN-framed moto-inclusive VRP-EL, cost/distance objective,
ex-post carbon estimation (0.343 truck / 0.044405 moto kgCO2/km per excerpts), existence +
moto/VN + ex-post-only treatment corroborated by Reviewer (instance/scale details NOT
re-verified). N2: Dao & Cho 2025, JKMS 28(1):46-57, doi:10.9717/kmms.2025.28.1.046 —
moto-specific MDVRPTW for HCMC, distance/cost objectives, no green content in
abstract/keywords (full text unchecked; KCI-indexing unverified). No strict bucket-(a)
killer found (moto-specific energy/emission model + explicit emission objective + VN
instances). Supporting context: AhaMove industrial moto VRP without green content (grey);
Rhouzali 2024 IJETT moto-inclusive green VRP non-VN tiny instances (anti-drift guard);
ITDP context reports (no routing); NEU student proposal (grey intent only).

Evidence:
- Researcher query/bucket table (Q1-Q8, O1; failed O2 alias call reported).
- Reviewer OpenAlex DOI verifications: N1 W3003750123, N2 W4407182630; verdict NARROW, MODERATE.
- Flagged confounder (untriaged): "A Practical Green Vehicle Routing Solution — A Case
  in HCMC" (IEOM Rome 2021, CMEM-based, truck framing).

Interpretation:
Original wording is loose enough that N1 fairly counts against it (peer-reviewed,
VN-framed, moto-inclusive, "sustainable" + quantified carbon numbers). N2 kills any
"no moto-VRP for Vietnam" reading but leaves the green leg intact. Excluding N1 on
"emission term must influence route selection" is principled (reporting vs optimisation).
KEEP-as-stated indefensible; REJECT overstates (no bucket-(a) killer). Correct: NARROW
to the (a)+(b)+(c) conjunction with "located" discipline.

Decision:
GAP-3 narrowed in LITERATURE.md to the amended conjunction wording with DOI pair,
"located as of 2026-09-06" qualifier, and per-item limits. Strength stays MODERATE
(conjunction only). No PROJECT_STATE.md change. Other gaps untouched in this loop.

Impact:
GAP-3 remains the strongest project direction but now with explicit exclusions (N1, N2,
industrial deployments, non-VN moto-green, non-green e-moto routing) that any future
thesis/design must cite to avoid overclaim.

Remaining Uncertainty:
N2 full text unchecked (green-absence unproven); N1 direct PDF equation-level check
outstanding; VN login-walled repositories, VJOL, RAISD-beyond-P16, citation-chasing
N1/N2, and the IEOM-2021 HCMC item untriaged; single-round searches; 2026 items evolving.

Related Issue: none (no code changed)

Related Commit: working tree (docs untracked)