# Task — SEC-CALLBACK-001

You are the implementing engineer. Implementation is authorized by the user (2026-11-04, "Yes, do both"). Follow `AGENTS.md` (§14, §15, §16) and `CLAUDE.md`. Run every command yourself from the repository root on Windows (PowerShell for Maven).

## Read first
`request.md`, `context.md`, `design.md` (the specification), `work-items/SEC-AUDIT-001/runtime-validation.md`, `docs/engineering/LESSONS.md` L-2026-001/003/004/007/012. If the design is wrong or impossible, stop and report.

## Step 0
1. `node tools/session-brief.mjs`; `git status --short` — only `work-items/SEC-*`, `.claude/`, `.mcp.json`, `*.log` may be untracked; otherwise stop.
2. `git switch master`, merge state as the user decides (the audit branch holds the checklist rows); then `git switch -c fix/sec-callback-001`.

## Step 1 — ledger
```
node tools/feature-workflow/workflow.mjs init work-items/SEC-CALLBACK-001/run.json . SEC-CALLBACK-001 GVRP_Entry_API/src
node tools/feature-workflow/workflow.mjs step work-items/SEC-CALLBACK-001/run.json ready work-items/SEC-CALLBACK-001/01-proof.json
(02, 03 likewise)
```

## Step 2 — baseline
`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\mvnw.cmd -B clean test` in `GVRP_Entry_API` (last known 331 tests). Record counts and failures by name. Read every test named in `context.md`.

## Allowed changes
`config/SecurityConfig.java`, `security/ApiKeyAuthenticationFilter.java`, a filter-registration config if needed, `service/OptimizationCallbackService.java`, new `exception/CallbackRejectedException.java`, `exception/GlobalExceptionHandler.java` (one handler), new test files; `work-items/SEC-CALLBACK-001/implementation.md`.

## Forbidden
Editing/disabling/excluding existing tests or fixtures; `application*.properties`; `pom.xml` (no new dependency); Engine API; client; docs; printing the API key; commits/pushes.

## Step 3 — implement per design, `mvnw -B clean test` all green (count = baseline + new).

## Step 4 — `implementation.md` (Scope, Baseline, Changes, Commands with exit codes, Verification, Modified Tests "none", Security-Relevant Changes incl. the removed `permitAll`, Deviations, Remaining Uncertainty, Lesson Candidates, Handoff incl. the two manual re-probes), then
`node tools/feature-workflow/workflow.mjs step work-items/SEC-CALLBACK-001/run.json ready work-items/SEC-CALLBACK-001/04-proof.json`. Stop at `verify`.
