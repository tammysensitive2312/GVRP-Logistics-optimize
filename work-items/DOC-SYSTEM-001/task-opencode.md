# Task for the OpenCode engineer — DOC-SYSTEM-001

You are `gvrp-engineer`. The work is authorized by the user (2026-10-29 23:07 and 23:12 +07:00; decisions in `request.md`). This is a documentation task: no source code, test or build file changes. Follow `AGENTS.md` in full (sections 2, 5, 10, 15, 16). Run every command yourself from the repository root, on Windows (L-2026-014).

## Read first
1. `work-items/DOC-SYSTEM-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `docs/workflows/business-development/README.md` (section "Documentation synchronization")
5. `.agents/skills/architecture-diagrams/SKILL.md` and the reference files for each diagram type you draw
6. `docs/engineering/LESSONS.md`: L-2026-005, -006, -009, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/session-brief.mjs` — include the output in your report.
2. `git status --short`. Allowed uncommitted: `work-items/DOC-SYSTEM-001/`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report the list (the user commits open work first).
3. Record `git log -1 --oneline` and `git branch --contains HEAD`.
4. `git switch -c docs/system-design-001`.

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/DOC-SYSTEM-001/run.json . DOC-SYSTEM-001 GVRP_Document
node tools/feature-workflow/workflow.mjs step work-items/DOC-SYSTEM-001/run.json ready work-items/DOC-SYSTEM-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DOC-SYSTEM-001/run.json ready work-items/DOC-SYSTEM-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DOC-SYSTEM-001/run.json ready work-items/DOC-SYSTEM-001/03-proof.json
```
Each must print a new state; on failure stop and report.

## Step 2 — baseline
- `node --test tools/feature-workflow/workflow.test.mjs` and `node --test tools/session-brief.test.mjs` — record results.
- `java -version` (JDK 21 expected) and render one existing `.puml` with `java -jar .agents/skills/architecture-diagrams/.temp/plantuml.jar -tsvg <file>` into a temp folder outside the repo — record whether rendering works.
- `py -c "import openpyxl; print(openpyxl.__version__)"` — record the result (do not install anything).
- List every file under `GVRP_Document` with `git ls-files GVRP_Document` and the untracked ones.

## Allowed changes
- Everything under `GVRP_Document/` (create, move with `git mv`, update, delete with `git rm`).
- `git rm -r work-items/<ID>` for: BULK-EDIT-001, CI-CLIENT-001, DRIVER-DELIVERY-001, DRIVER-MAP-001, DRIVER-MAP-002, DRIVER-MAP-003, DRIVER-MAP-004, PARETO-001, PARETO-002, PARETO-003, PARETO-004, SEC-ROLE-001, SEC-SESSION-001, SEC-TENANT-001 — each only after its `status` shows `done` (PARETO-004: no run) and after its content is recorded per design §2 Part A.
- `work-items/DOC-SYSTEM-001/implementation.md` and checkpoint input files in `work-items/DOC-SYSTEM-001/`.

## Forbidden
- Any change under `work-items/LEZ-001`, `MAP-ORDERS-001`, `BROWSER-FIX-001`, `UX-ORDERS-001`, `UX-LEZ-001` (read-only sources).
- Source code, tests, `pom.xml`, `package*.json`, `.github/`, `docs/`, `tools/`, `AGENTS.md`, `CLAUDE.md`, `.opencode/`, `.agents/`; installing packages; adding dependencies.
- Secret values, `.env` content, property values of credentials or keys; customer data; real customer coordinates; SELECTs of business rows in `gvrp_db` (schema queries with `agent_reader` only).
- Inventing endpoints, classes, flows or test names. Unverified statements must carry `ASSUMPTION:`.
- Committing, pushing, rewriting history.

## Step 3 — implement per `design.md`, part by part
Order: Part A → B → C → D → E → F. After each part, record a checkpoint (template `docs/workflows/business-development/checkpoint-input.example.json`, current run revision, concrete `next_action`):
```
node tools/feature-workflow/workflow.mjs checkpoint work-items/DOC-SYSTEM-001/run.json work-items/DOC-SYSTEM-001/checkpoint-<part>.json
```
Then run the verification table of `design.md` §4 (DS-V1 … DS-V9) and record each result.

## Step 4 — record and hand off
Write `work-items/DOC-SYSTEM-001/implementation.md` with sections: Scope, Baseline, Changes Made (structure tree; documents per level; removed designs; deleted work items with their index rows), Commands Executed (with exit codes, incl. Steps 0–2 and each checkpoint), Verification Results (DS-V1…V9), Modified Tests ("none"), Security-Relevant Changes (secret scan result; data used), Deviations From Design, Remaining Uncertainty (every `ASSUMPTION:` count per document; anything not rendered; `openpyxl` status), Lesson Candidates, Handoff to Reviewer (list the references in `docs/` that point to deleted work items — Claude fixes them at closure).
Then:
```
node tools/feature-workflow/workflow.mjs step work-items/DOC-SYSTEM-001/run.json ready work-items/DOC-SYSTEM-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done.
