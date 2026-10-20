# Task for the OpenCode engineer — CI-CLIENT-001

You are `gvrp-engineer`. The user has authorized implementation. Follow `AGENTS.md` in full,
including §14 (Test Integrity) and §15 (Security). Run every command yourself.

## Read first

1. `work-items/CI-CLIENT-001/request.md`
2. `work-items/CI-CLIENT-001/context.md`
3. `work-items/CI-CLIENT-001/design.md` (the specification)

If the design is wrong or impossible, stop and report. Do not redesign.

## Step 0: preconditions and branch

1. `work-items/PARETO-002/run.json` and `work-items/PARETO-003/run.json` must both show
   `"state": "done"`, and their work must already be merged into `master`. If not, stop and report.
2. Run `git status --short`. If anything is modified or untracked outside
   `work-items/CI-CLIENT-001/`, `.claude/` and `.mcp.json`, stop and report the list.
3. Run `git switch master`, then `git switch -c fix/ci-client-001`.

## Step 1: workflow ledger (before editing anything)

```
node tools/feature-workflow/workflow.mjs init work-items/CI-CLIENT-001/run.json . CI-CLIENT-001 GVRP_Entry_client_v2/package.json GVRP_Entry_client_v2/package-lock.json GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/CI-CLIENT-001/run.json ready work-items/CI-CLIENT-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/CI-CLIENT-001/run.json ready work-items/CI-CLIENT-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/CI-CLIENT-001/run.json ready work-items/CI-CLIENT-001/03-proof.json
```

The states must come out as `discover`, `design`, `implement`. On any failure, stop and report.

## Step 2: baseline (in `GVRP_Entry_client_v2`)

- Record `node -v` and `npm -v`.
- `npm test -- --watch=false --browsers=ChromeHeadless`: record the totals and the names of the
  failing specs. Exactly 5 known failures are expected.
- `npm run build`: record the exit code, the error line for `login.component.scss`, and the
  list of budget warnings.
- Do the D3 "before" captures: screenshots at 3 widths and in 3 states, plus the computed
  styles. Use `ng serve` and a real browser. The login page needs no backend to render.
  Save the captures under your temp folder, not in the repository.

## Allowed changes

- `GVRP_Entry_client_v2/package-lock.json`, regenerated per D1 only. Do not edit it by hand.
- `GVRP_Entry_client_v2/src/app/features/auth/login/login.component.scss`, per D2.

## Forbidden

- `package.json`, `angular.json` (budgets included), `.github/`, other stylesheets, templates,
  TypeScript files, and i18n files.
- Editing, disabling, focusing or deleting any spec or `src/testing/*`. These will be
  byte-compared.
- Changing any dependency version, or adding or removing a dependency.
- Committing, pushing, or rewriting history.

## Step 3: implement and check

1. Lock file (D1): `npm install --package-lock-only --ignore-scripts`.
   - Run `git diff --stat -- package-lock.json` and check that no version changed apart from
     the transloco subtree. Example: `git diff -U0 package-lock.json | grep '"version"'` must
     show only transloco and its dependencies.
   - Delete `node_modules`, run `npm ci`. It must exit 0.
2. Stylesheet (D2): rewrite it, then check its size:
   `npx sass --no-source-map --style=compressed --load-path=src src/app/features/auth/login/login.component.scss <temp>/login.min.css`.
   Report the byte count. The target is ≤ 6 kB.
3. `npm run build`: it must exit 0, with no `login.component.scss` error. The warning set must
   be the baseline set minus the login style line.
4. `npm test -- --watch=false --browsers=ChromeHeadless`: the same 5 failures by name, and no
   new ones.
5. Do the D3 "after" captures, then compare them with the "before" captures. The computed
   styles must be identical.

## Step 4: implementation record

Write `work-items/CI-CLIENT-001/implementation.md` with these sections:

- Scope
- Baseline
- Changes Made, including the lock diff summary and the scss size before and after
- Commands Executed, with exit codes
- Verification Results, including the D3 comparison table
- Specificity Cases (D2 step 5)
- Modified Tests (must be "none")
- Dependencies (must be "no version change")
- Deviations From Design
- Remaining Uncertainty

Then run:

```
node tools/feature-workflow/workflow.mjs step work-items/CI-CLIENT-001/run.json ready work-items/CI-CLIENT-001/04-proof.json
```

The state must come out as `verify`. Stop there and tell the user you are done.
