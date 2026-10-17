# Task for the OpenCode engineer — SEC-SESSION-001

You are `gvrp-engineer`. Implementation is authorized by the user. Follow
`AGENTS.md` in full (sections 14 and 15 especially). Run every command
yourself from the repository root; the user does not relay commands this time.

## Read first

1. `work-items/SEC-SESSION-001/request.md` (SS-001..SS-006)
2. `work-items/SEC-SESSION-001/context.md`
3. `work-items/SEC-SESSION-001/design.md` (specification and test table)

If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch

1. `git status --short`. If anything is modified or untracked outside
   `work-items/SEC-SESSION-001/`, `docs/security/SECURITY_CHECKLIST.md`,
   `.claude/`, `.mcp.json`, stop and report the list (the previous work item
   may not be committed yet).
2. `git switch -c fix/sec-session-001`

## Step 1 — workflow ledger (before editing code)

```
node tools/feature-workflow/workflow.mjs init work-items/SEC-SESSION-001/run.json . SEC-SESSION-001 GVRP_Entry_API/src docs/security
node tools/feature-workflow/workflow.mjs step work-items/SEC-SESSION-001/run.json ready work-items/SEC-SESSION-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/SEC-SESSION-001/run.json ready work-items/SEC-SESSION-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/SEC-SESSION-001/run.json ready work-items/SEC-SESSION-001/03-proof.json
```

Each must print a new state; if one fails, stop and report the output.

## Step 2 — baseline

`mvnw.cmd -B test` in `GVRP_Entry_API` (set `JAVA_HOME` to JDK 21 if needed).
Record pass count and failing test names.

## Allowed changes

- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/security/jwt/JwtAuthenticationFilter.java`
- new `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/security/jwt/JwtAuthenticationFilterTest.java`
- `work-items/SEC-SESSION-001/implementation.md`

## Forbidden

- Editing, disabling, skipping or excluding any existing test.
- Tests needing MySQL or any running service.
- Any dependency change, any other source file, `application*.properties`,
  `.github/`, `AGENTS.md`, `docs/`.
- Committing, pushing, rewriting git history.
- Logging or reporting token values or secrets.

## Step 3 — implement and verify

Implement per `design.md`, then `mvnw.cmd -B clean test`. All tests must pass.

## Step 4 — report and hand over

Write `work-items/SEC-SESSION-001/implementation.md` with the sections:
Scope, Baseline, Changes Made, Commands Executed (with exit codes, including
Step 0 and Step 1), Verification Results, Modified Tests (must be "none"),
Security-Relevant Changes, Deviations From Design, Remaining Uncertainty,
Handoff to Reviewer.

Then record the transition to verify:

```
node tools/feature-workflow/workflow.mjs step work-items/SEC-SESSION-001/run.json ready work-items/SEC-SESSION-001/04-proof.json
```

Do not go further than `verify`. Tell the user you are done.
