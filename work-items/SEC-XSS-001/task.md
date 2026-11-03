# Task — SEC-XSS-001

You are the implementing engineer. Implementation is authorized by the user (2026-11-04, "Yes, do both"). Follow `AGENTS.md` (§14, §15, §16). Run every command yourself from the repository root on Windows.

## Read first
`request.md`, `context.md`, `design.md` (the specification), `work-items/SEC-AUDIT-001/runtime-validation.md`, client lessons in `docs/engineering/LESSONS.md`. If the design is wrong or impossible, stop and report.

## Step 0
`node tools/session-brief.mjs`; `git status --short` clean apart from `work-items/SEC-*`, `.claude/`, `.mcp.json`, `*.log`; `git switch -c fix/sec-xss-001` from `master` (after the user's merge decision for the audit branch). Do not stop the user's `ng serve`.

## Step 1 — ledger
```
node tools/feature-workflow/workflow.mjs init work-items/SEC-XSS-001/run.json . SEC-XSS-001 GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/SEC-XSS-001/run.json ready work-items/SEC-XSS-001/01-proof.json
(02, 03 likewise)
```

## Step 2 — baseline
In `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` (last known 471 SUCCESS) and `rm -rf dist && npm run build` (12 warnings). Record counts.

## Allowed changes
The five files in `context.md`, new `shared/utils/html.utils.ts`, new spec files, `work-items/SEC-XSS-001/implementation.md`.

## Forbidden
Editing/disabling existing specs or stubs; `package*.json`, `angular.json`, `index.html` (CSP is out of scope); server code; docs; new dependencies (no sanitizer library); commits/pushes.

## Step 3 — implement per design; specs green (baseline + new); build without new warning.

## Step 4 — `implementation.md` (Scope, Baseline, Changes per sink, Commands with exit codes, Verification, Modified Tests "none", Security-Relevant Changes, Deviations, Remaining Uncertainty incl. CSP, Lesson Candidates, Handoff incl. the manual re-check with depot 9005), then
`node tools/feature-workflow/workflow.mjs step work-items/SEC-XSS-001/run.json ready work-items/SEC-XSS-001/04-proof.json`. Stop at `verify`.
