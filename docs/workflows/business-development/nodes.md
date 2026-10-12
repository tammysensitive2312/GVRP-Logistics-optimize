# Node contracts

Shared inputs: original user request, applicable repository instructions, run
history and evidence from prior nodes. Record facts, assumptions and proposals
separately. Existing user authorization persists; do not introduce repeated
approval gates for routine implementation decisions.

Within any active node, use the controller `checkpoint` command after meaningful
progress or before yielding. On a fresh session, start with `recover`, inspect
warnings and relevant files, and read the current node contract. Recovery never
counts as a user decision or test acceptance. See README for the input schema.

| Node | Work and allowed changes | Output contract / exit evidence |
|---|---|---|
| intake | Capture user intent; modify task records only | `request`: source message, goal, scope, explicit requirements with IDs, exclusions, acceptance expectations, authorization and open questions. Enough detail to investigate; do not invent business policy. |
| discover | Read relevant code, documentation, tests and available code index; modify task records only | `context`: inspected revision/files/symbols, current behavior with sources, document gaps, contradictions, affected areas and unanswered decisions. |
| design | Create or update feature documentation before product code. When the design is accepted, proactively load the `architecture-diagrams` skill and express the design as PlantUML (`.puml` source plus rendered SVG/PNG) next to the markdown documents; do not wait to be asked and do not leave diagrams markdown-only | `design`: intended behavior, acceptance cases, interfaces/data/UI as relevant, failure cases and explicit assumptions, including references to the committed `.puml` and rendered diagram files. `traceability`: requirement-to-document links and planned code/test links. Resolve material business decisions through `ask`; do not demand approval for every design edit. |
| implement | Make scoped product, test and documentation edits authorized by user | `implementation`: changes by requirement, actual code references, rationale and remaining work. Re-read files modified since discovery. No unrelated refactors or premature performance optimization. |
| verify | Run appropriate checks and independently inspect behavior/document alignment; record evidence. For UI-facing changes, proactively load the `browser-skill` and exercise the UI on screen with the `bsk` CLI instead of relying on code inspection alone | `verification`: commands, outcomes, environment, requirement coverage and limitations, including `bsk` session steps and observations for UI-facing changes (or an honest record when `bsk` is unavailable). `handoff`: exact version, user test steps, expected results and known limitations. Pass only with required checks satisfied and no unresolved blocking mismatch; record unavailable checks honestly. `findings` routes code defects back to implementation, design changes to design. |
| await_user_test | Present handoff and wait; no product edits or optimization | `user_acceptance`: actual user decision for this run and fingerprint. `user_feedback`: rejection details. `findings`: changes detected since handoff. Never interpret silence as acceptance. |
| accepted | Close the feature or prepare explicitly intended optimization | `closure`: final requirement coverage, linked documents/tests and limitations. Or `optimization_plan`: objective, constraints, measurement protocol; `baseline`: reproducible measurements of the accepted version. Do not optimize just because the state permits it. |
| optimize | Optimize against the accepted baseline, maintain documentation | `comparison`: baseline/candidate identities, comparable workload/environment, measured results and behavior checks. `implementation`: edits and traceability updates. Return to verify even if optimization failed; revert or explicitly report no improvement. |
| await_decision | Preserve state and wait for the required business/scope decision | `question`: exact missing decision, alternatives and affected work. `user_decision`: actual reply reference and interpretation. Resume the saved node; update affected documents before further implementation. |
| done | Read-only completed record | No further transitions; use a new linked run for new work. |
| cancelled | Record cancellation without rolling back user files | `cancellation`: user instruction or explicit stop reason and remaining changes. |

## Review responsibilities not mechanically provable

The tool checks graph edges, evidence existence, revisions and scoped fingerprints.
The agent/reviewer must check evidence content, scope completeness, actual user
authorization and the quality of requirements/tests. A green transition is not a
scientific or semantic correctness verdict. No automatic multi-agent delegation
is required by this workflow.

## Intake dry run: driver route delivery

This is an illustrative input only; no feature run is initialized automatically.

- User goal: a dispatcher sends an accepted optimized plan to assigned drivers.
- Explicit requirements: drivers receive ordered stops and a route; navigation
  is available; the dispatcher interface is usable and responsive.
- Acceptance expectations: all assigned drivers receive matching plan data;
  user checks presentation, behavior, responsiveness and navigation.
- Open questions: driver platform, definition of matching route geometry,
  receipt semantics, offline behavior and performance test conditions.
- Outcome: `ready` for discovery. These questions do not prevent repository
  inspection and must not be silently replaced by agent-selected policies.
