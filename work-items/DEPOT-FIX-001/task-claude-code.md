# Task for Claude Code — DEPOT-FIX-001

You are the implementing engineer. Implementation is authorized by the user (2026-10-30 23:27 +07:00, "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm"). Follow `AGENTS.md` in full (sections 14, 15, 16) and `CLAUDE.md`. Run every command yourself from the repository root, on Windows (L-2026-014); run Maven through PowerShell with `JAVA_HOME` set to JDK 21 for the command (L-2026-007).

Run order of the three fix items: **DEPOT-FIX-001 → ORDERS-FIX-001 → LEZ-FIX-001**, one at a time (one workspace writer).

## Read first
1. `work-items/DEPOT-FIX-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `work-items/DOC-SYSTEM-001/review.md` (F2)
5. `docs/engineering/LESSONS.md`: L-2026-001, -003, -007, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `git switch master`, `git log -1 --oneline` (expect `081640a` or a later commit made by the user — record it), `git status --short`. Allowed uncommitted: `work-items/DEPOT-FIX-001/`, `work-items/ORDERS-FIX-001/`, `work-items/LEZ-FIX-001/`, `work-items/*/review.md`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report.
2. `node tools/session-brief.mjs` — include the output.
3. `git switch -c fix/depot-fix-001`.
4. Do not stop the user's servers; if a running Entry API locks `target/`, record it and ask the user.

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/DEPOT-FIX-001/run.json . DEPOT-FIX-001 GVRP_Entry_API/src
node tools/feature-workflow/workflow.mjs step work-items/DEPOT-FIX-001/run.json ready work-items/DEPOT-FIX-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DEPOT-FIX-001/run.json ready work-items/DEPOT-FIX-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/DEPOT-FIX-001/run.json ready work-items/DEPOT-FIX-001/03-proof.json
```

## Step 2 — baseline
- Entry (in `GVRP_Entry_API`): `.\mvnw.cmd -B clean test` (last recorded 314/0/0/0 after UX-ORDERS-001; record what you get, with any pre-existing failure by name).
- **Assertion check (record):** search `GVRP_Entry_API/src/test` for `validateCoordinates`, `createPoint(`, `DepotInputDTO`, `/depots`, `DepotService`; search `GVRP_Entry_client_v2/src` for `depots` handling of a `400`. An existing assertion that expects the current `400` → **stop and report**.

## Allowed changes
- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/mapper/GeometryMapper.java` (S1 only).
- New test classes under `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/`.
- `work-items/DEPOT-FIX-001/implementation.md`.

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture or helper. A failing existing test → stop and report.
- Any other file: `GVRP_Document/**` and other documentation (user 23:27), `pom.xml`, `application*.properties`, `SecurityConfig`, migrations, client, Engine; new dependencies.
- New `@SpringBootTest` classes (L-2026-001).
- Committing, pushing; DB writes other than the optional live check through the application (record every row created).

## Step 3 — implement per `design.md`, then verify
- S1, then DF-T1…T4.
- `.\mvnw.cmd -B clean test`: all green, count = baseline + new.
- `git status --short` shows only allowed files.
- Optional live check (design §3), recorded with the depot name created.

## Step 4 — record and hand off
`work-items/DEPOT-FIX-001/implementation.md`: Scope, Baseline (incl. assertion check), Changes Made, Commands Executed (exit codes), Verification Results, Modified Tests ("none"), Security-Relevant Changes (none expected; tenant scoping unchanged), Deviations From Design, Remaining Uncertainty, Documentation Now Stale (D02 — not updated by decision of 23:27), Lesson Candidates, Handoff to Reviewer. Then:
```
node tools/feature-workflow/workflow.mjs step work-items/DEPOT-FIX-001/run.json ready work-items/DEPOT-FIX-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done, in Vietnamese.
