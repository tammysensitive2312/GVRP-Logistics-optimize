# Task — SEC-ORDERS-001

You are the implementing engineer. Implementation is authorized by the user (2026-11-04, "Yes, do both"). Follow `AGENTS.md` (§14, §15, §16). Run every command yourself from the repository root on Windows (PowerShell for Maven, `JAVA_HOME` = JDK 21).

## Read first
`request.md`, `context.md`, `design.md` (the specification). If wrong or impossible, stop and report.

## Step 0
`node tools/session-brief.mjs`; `git status --short` clean apart from `work-items/SEC-*`, `.claude/`, `.mcp.json`, `*.log`; `git switch -c fix/sec-orders-001` from `master` (after the user's merge decision for the audit branch).

## Step 1 — ledger
```
node tools/feature-workflow/workflow.mjs init work-items/SEC-ORDERS-001/run.json . SEC-ORDERS-001 GVRP_Entry_API/src
node tools/feature-workflow/workflow.mjs step work-items/SEC-ORDERS-001/run.json ready work-items/SEC-ORDERS-001/01-proof.json
(02, 03 likewise)
```

## Step 2 — baseline `.\mvnw.cmd -B clean test` in `GVRP_Entry_API`; record counts.

## Allowed changes
`service/orders/OrderService.java`, new test file, `work-items/SEC-ORDERS-001/implementation.md`.

## Forbidden
Editing existing tests/fixtures; other files; dependencies; commits/pushes.

## Step 3 — implement; `mvnw -B clean test` green (baseline + new).

## Step 4 — `implementation.md` (usual sections, Modified Tests "none"), then
`node tools/feature-workflow/workflow.mjs step work-items/SEC-ORDERS-001/run.json ready work-items/SEC-ORDERS-001/04-proof.json`. Stop at `verify`.
