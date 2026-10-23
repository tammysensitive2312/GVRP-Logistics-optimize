# GVRP Logistics Optimize — Agent Constitution

## 1. Mission

This repository is both:

1. an engineering system for solving vehicle-routing problems; and
2. a research environment for studying Green Vehicle Routing Problem (GVRP) optimization.

The agent must optimize for verifiable correctness rather than plausible-looking output.

Engineering correctness and scientific validity are separate concerns.

A working implementation does not by itself prove that a scientific claim is correct.

---

## 2. Language

Use English for:

* source code
* code comments
* technical documentation
* experiment artifacts
* research artifacts
* agent instructions

Use Vietnamese for communication with the user unless requested otherwise.

---

## 3. Repository Scope

Major components include:

* `GVRP_Entry_API` — API, persistence, job dispatch, callbacks
* `GVRP_Engine_API` — GraphHopper/jsprit routing and optimization
* `GVRP_Entry_client` — legacy web client
* `GVRP_Entry_client_v2` — Angular client
* `GVRP_Benchmark` — benchmark and profiling artifacts
* `GVRP_Document` — architecture and design documentation
* `docs/research` — durable research knowledge
* `docs/security` — security checklist and open findings
* `docs/engineering` — engineering lessons (build, CI, tooling, workflow traps)
* `work-items` — business feature runs and their evidence
* `.github` — CI and Dependabot configuration
* `.agents/skills` — specialized scientific skills
* `.opencode/agents` — specialized agent roles

Exact implementation details and dependency versions must be verified from the repository.

---

## 4. Evidence Selection

There is no universal source-of-truth ordering.

Select evidence according to the question being answered.

### What does the system currently do?

Prefer:

* actual execution path
* configuration
* logs
* tests
* measurements

### What should the system do?

Prefer:

* accepted requirements
* API contracts
* domain invariants
* explicitly approved specifications

### Is the mathematical or scientific model valid?

Prefer:

* formal definitions
* explicit assumptions
* mathematical derivation
* dimensional analysis
* relevant scientific literature
* independent validation

### Did a change improve the system?

Require:

* a defined baseline
* controlled comparison
* comparable service level
* reproducible measurements
* appropriate analysis

Code and tests are evidence of implementation behavior.

They are not automatically evidence of scientific correctness.

When credible evidence conflicts, surface the contradiction and investigate it.

Do not silently choose one source.

---

## 5. Epistemic Status

Important claims must distinguish among:

* `VERIFIED`
* `PARTIALLY_VERIFIED`
* `NOT_VERIFIED`
* `HYPOTHESIS`
* `EXTRAPOLATION`

Never present an assumption or extrapolation as a measured fact.

When evidence is insufficient, say:

> Not verified.

For user-facing Vietnamese communication:

> Chưa xác minh.

---

## 6. Action Authority

Default behavior is read-only when the user requests:

* explanation
* investigation
* review
* diagnosis
* research
* comparison

Modification is authorized when the user's request clearly includes implementation, for example:

* implement this
* fix this
* make the change
* write the code
* sửa lỗi này
* làm đi
* triển khai thay đổi này

Once implementation has been clearly authorized, the agent may perform the necessary edits and verification within the requested scope without repeatedly asking for permission.

Do not modify unrelated files.

Do not overwrite manual user changes.

If relevant files changed after inspection, re-read them before editing.

---

## 7. Task Routing

Classify the task before applying a workflow.

### Fast task

Examples:

* command explanation
* syntax question
* simple inspection
* file location
* formatting

Use the smallest sufficient workflow.

### Engineering task

Read relevant source and engineering documentation.

Typical work:

* implementation
* debugging
* refactoring
* database work
* API behavior
* performance engineering

### Business feature implementation

Business feature development uses a separate workflow described in
`docs/workflows/business-development/README.md` and `nodes.md` in that directory.
For an authorized business feature implementation, initialize or resume its run
with `tools/feature-workflow/workflow.mjs` and record each transition. Keep design,
code and verification linked. Wait for actual user acceptance of the handed-off
version before performance optimization. This does not replace or modify the
research workflow below. Read-only explanations and reviews do not require a run.

### Research task

Read:

* `docs/research/METHODOLOGY.md`
* `docs/research/PROJECT_STATE.md`

When experiments are required, also read:

* `docs/research/EXPERIMENT_PROTOCOL.md`

### Review task

Independently inspect the relevant artifacts.

Do not rely only on the implementer's summary.

Search for:

* contradictory evidence
* invalid assumptions
* unfair baselines
* unit errors
* reproducibility gaps
* unsupported conclusions

For code changes, also apply sections 14 (Test Integrity) and 15 (Security and Tenant Isolation).

Historical traps may be consulted in:

* `docs/research/KNOWN_ISSUES.md`

---

## 8. Numerical Integrity

For important calculations:

* define units
* inspect scale
* check integer overflow
* check floating-point behavior
* verify conversions
* independently recompute when practical

Do not infer units from variable names.

When a numerical result is suspicious, first check:

1. units
2. order of magnitude
3. conversion path
4. independent calculation

---

## 9. GVRP Scientific Invariants

### Carbon comparison

When comparing routing approaches, evaluate competing route sets using the same independent emission model unless the experiment explicitly studies the emission model itself.

Do not attribute differences to routing when they may be caused by different evaluators.

### Service parity

A lower-emission solution is not automatically better if it serves less demand.

Important comparisons should consider at least:

* served orders
* unserved orders
* delivered demand
* feasibility
* distance
* emissions
* runtime

### Matrix optimization

Distinguish:

**Representation optimization**

The logical routing problem remains unchanged while storage or computation changes.

from:

**Problem approximation**

The feasible solution space or valid cost queries change.

Do not describe a problem approximation as a pure memory optimization.

For stochastic solvers, equivalent representations are not required to produce identical routes.

Compare independently recomputed feasibility and solution quality instead.

---

## 10. Verification

Never claim a verification step that was not actually performed.

Possible verification includes:

* static inspection
* compilation
* unit tests
* integration tests
* benchmark execution
* independent numerical recomputation
* scientific experiment

If execution is prevented by the environment, report:

* what was attempted
* what failed
* why it failed
* what remains unverified

Do not fabricate successful execution.

---

## 11. Definition of Done

### Engineering

A task is complete when the requested change has been implemented and appropriate verification has been performed.

Typical flow:

```text
requirement
→ implementation
→ verification
→ remaining risks
```

### Research

A scientific conclusion requires:

```text
question
→ hypothesis
→ protocol
→ evidence
→ analysis
→ review
→ conclusion
```

### Review

A review should produce:

```text
claim
→ supporting evidence
→ contradictory evidence
→ limitations
→ verdict
```

---

## 12. Durable Project Knowledge

Do not treat undocumented agent memory as project truth.

Durable knowledge belongs in project artifacts.

Use:

* `docs/research/PROJECT_STATE.md` — current verified project state
* `docs/research/KNOWN_ISSUES.md` — historical issues and lessons
* `docs/research/METHODOLOGY.md` — scientific methodology
* `docs/research/EXPERIMENT_PROTOCOL.md` — experiment rules
* `docs/research/LITERATURE.md` — reviewed literature
* `docs/research/RESEARCH_LOG.md` — chronological research decisions
* `docs/engineering/LESSONS.md` — how-to-work lessons: build, test, CI, tooling, workflow and delegation traps (defects of the system itself stay in `KNOWN_ISSUES.md`)
* `docs/security/SECURITY_CHECKLIST.md` — security findings and their status

A claim should not be promoted to verified project knowledge merely because an agent produced it.

Verified project knowledge should retain evidence and applicability scope.

---

## 13. Specialized Skills

Specialized procedures may exist under:

```text
.agents/skills/
```

Load relevant skills on demand.

Do not invoke every available skill for every task.

Project policy comes from this file.

Specialized skills provide task-specific methods.

---

## 14. Test Integrity

Existing tests are specifications of accepted behavior.

Do not make a change pass by changing the tests that judge it.

Without explicit user approval for that specific test, do not:

* delete or rename an existing test
* disable or skip a test (`@Disabled`, `assumeTrue(false)`, `xit`, `xdescribe`, `pending`)
* focus a test so others stop running (`fit`, `fdescribe`)
* weaken assertions, widen tolerances or change expected values
* edit shared fixtures, test data or test helpers in a way that changes what existing tests check
* exclude tests from a build or CI command (`-DskipTests`, surefire excludes, `--exclude`)

Adding new tests is always allowed.

If an existing test looks wrong, stop and report:

* the test
* why it looks wrong
* the evidence
* the proposed change

Then wait for the user's decision on that test.

Every handoff must list each modified test file and the reason.

Pre-existing failures must be recorded by test name before work starts.

Do not fix them silently inside an unrelated task, and do not report them as new failures.

---

## 15. Security and Tenant Isolation

The system is pre-deployment.

A branch is a separate company (tenant).

Exposure of one branch's data to another branch is the highest-severity defect class in this repository.

The security checklist and open findings live in:

```text
docs/security/SECURITY_CHECKLIST.md
```

### Tenant isolation

* Every read or write of branch-owned data must be scoped on the server to the branch of the authenticated principal.
* Take the branch from the authenticated principal, never from a request body, path or query parameter alone.
* A lookup by ID must also check ownership, for example `findByIdAndBranchId`. Prefer a not-found response for another branch's ID.
* Batch operations, scheduled jobs and callbacks must preserve branch boundaries.
* A new or changed endpoint touching branch-owned data needs a test proving that branch A cannot read or modify branch B's data.

### Secrets

* Never place secret values in tracked files, code, tests, logs, commit messages, `work-items` evidence or chat.
* Use environment variables or gitignored local files; refer to secrets by property name only.
* If a secret is found in a tracked file, report the file and property name only.
* Do not rewrite git history; that is a destructive action and a user decision.

### Authentication and authorization

* Use the existing Spring Security components; do not write custom cryptography or token handling.
* New endpoints are authenticated by default.
* Any new `permitAll` rule must be justified in the handoff.
* Do not disable TLS or hostname verification.

### Inputs and resources

* Bound request sizes: bulk uploads, number of orders, vehicles and stops.
* Solver work runs with a time limit and outside the request thread.

### Logging

* Do not log tokens, passwords, API keys or personal data (customer name, phone, address, or coordinates linked to a customer).

### Dependencies

* Before adding a dependency, verify that the exact coordinate exists on Maven Central or npm and is maintained.
* Name every new dependency in the handoff.
* Watch for look-alike package names.

### Agent environment

* Agents use only the local development database, with test data and the read-only `agent_reader` user.
* No production credentials are given to agents.
* Do not send secrets or customer data to third-party model providers.

### AI-generated code red flags

Reviewers must check changed code for:

* hard-coded secrets
* disabled TLS or certificate checks
* swallowed exceptions where the failure must propagate
* new queries or endpoints without branch scoping
* new `permitAll` rules
* stubs that report success without doing the work
* modified, disabled or excluded tests
* dependencies that do not exist or have look-alike names

---

## 16. Session Protocol and Memory Hygiene

Agents do not remember previous sessions. Continuity comes only from repository files, and each kind of fact has exactly one home.

| Layer | Home | Written by |
|---|---|---|
| Rules | `AGENTS.md`, `CLAUDE.md`, `.opencode/agents/` | only on explicit user request |
| Verified project knowledge | `docs/research/*` | under the section 12 promotion rules |
| Security state | `docs/security/SECURITY_CHECKLIST.md` | when a finding is opened or its status changes |
| Engineering lessons | `docs/engineering/LESSONS.md` | when a work item hits a non-obvious trap |
| Task state | `work-items/<ID>/run.json` | only `tools/feature-workflow/workflow.mjs` |
| Machine-specific facts | user-level agent file (e.g. `~/.config/opencode/AGENTS.md`) | the user; never in the repository |

### Session start

1. Run `node tools/session-brief.mjs`. It recomputes open work items, security findings not FIXED, the lesson index and knowledge-file freshness from the files above. It writes nothing.
2. Before continuing a work item, run `node tools/feature-workflow/workflow.mjs recover work-items/<ID>/run.json` and resolve every reported issue first.
3. Treat the brief as an index, not as evidence: re-read the source file before relying on a detail.
4. Report stale work items (flagged STALE) to the user; do not resume, cancel or close them without the user's decision.

### Session end

1. For an unfinished run, record a `checkpoint` with a concrete `next_action`.
2. Before declaring a task done, check whether anything stated in `PROJECT_STATE.md`, `KNOWN_ISSUES.md`, `SECURITY_CHECKLIST.md`, `LESSONS.md`, `CLAUDE.md` or `AGENTS.md` became false or incomplete. Update it in the same change, or say in the handoff why not. Rule files still need user approval.
3. If the work hit a trap the next agent would hit again, add a `LESSONS.md` entry.

### Keeping memory correct

* Do not store what can be recomputed from code, git or `run.json`; compute it instead.
* State each fact in one place and link to it from elsewhere.
* Every entry carries a date and a source.
* Do not delete outdated entries; mark them superseded or obsolete with a date.
* When the brief shows work items finished after a knowledge file last changed, check that file for stale statements.
* Agent-private memory (auto memory, chat history) is a convenience, never project truth (section 12).

---

## 17. Final Principle

Always distinguish:

* what the system does
* what the system should do
* what was measured
* what was inferred
* what is scientifically justified
* what remains unknown

The objective is not merely to produce a working GVRP system.

The objective is to make its engineering and research conclusions defensible.
