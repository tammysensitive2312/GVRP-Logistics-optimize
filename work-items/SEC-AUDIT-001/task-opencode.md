# Task for OpenCode — SEC-AUDIT-001 (security audit)

You are the **parent agent** of a security audit, running as the primary agent `gvrp-orchestrator`. The audit is authorized by the user (2026-11-01). Follow `AGENTS.md` in full (sections 14, 15 and 16) and the installed skill `.agents/skills/security-audit/SKILL.md`. Run every command yourself from the repository root, on Windows, in PowerShell.

This is an **audit, not a fix**: you change no source, test, configuration or documentation file. Everything you write goes to the skill output directory or to `work-items/SEC-AUDIT-001/`.

## Read first
1. `work-items/SEC-AUDIT-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `.agents/skills/security-audit/SKILL.md`, then the phase files it names, when you reach each phase
5. `docs/security/SECURITY_CHECKLIST.md` (prior evidence) and `docs/engineering/LESSONS.md` entries L-2026-006, 009, 012, 013, 014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/session-brief.mjs` — include the output in your report.
2. `git status --short`. Anything modified or untracked outside `work-items/SEC-AUDIT-001/`, `.agents/skills/security-audit/`, `.claude/`, `.mcp.json`, `*.log` → stop and report the list.
3. `git switch -c audit/sec-audit-001`
4. `docker image inspect node:22-alpine --format '{{.Id}}'` — must succeed (image already pulled). If it fails, stop and ask the user; do not pull or install anything else.
5. Confirm you can launch sub-agents (Task tool with the built-in `explore` and `general` agents). If not, stop here and report — do not run the audit in a single context.

## Step 1 — workflow ledger (before the audit)
```
New-Item -ItemType Directory -Force work-items/SEC-AUDIT-001/audit | Out-Null
node tools/feature-workflow/workflow.mjs init work-items/SEC-AUDIT-001/run.json . SEC-AUDIT-001 .agents/skills/security-audit
node tools/feature-workflow/workflow.mjs step work-items/SEC-AUDIT-001/run.json ready work-items/SEC-AUDIT-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/SEC-AUDIT-001/run.json ready work-items/SEC-AUDIT-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/SEC-AUDIT-001/run.json ready work-items/SEC-AUDIT-001/03-proof.json
```
Each must print a new state (`discover`, `design`, `implement`); on failure stop and report.

## Step 2 — audit setup
- Output directory: `$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-<N>`, next unused `<N>`. Never inside the repository.
- Record source ref: `git rev-parse HEAD`, `git status --short`.
- Write `run-metadata.json` as `design.md` §2 says (profile `standard`, scope paths, prior evidence = the checklist, no prior skill runs).
- Map skill roles: `research` → `explore`, `general` → `general`. Each hunter/verifier gets its own `agents/<agent-id>/` root as the skill requires.

## Step 3 — run the audit (skill phases 1–6)
Follow the skill exactly, with the priorities of `design.md` §4.

Execution policy (`design.md` §3):
- **Source only.** Do not build, test, run or browse the target; do not call ports 4200, 8080, 8081, 3306; do not use dbhub, MySQL or any DB tool; do not read customer data.
- Leads that need execution stay `needs_validation` with an exact blocker and a dummy-tenant test plan.
- **Secrets:** name the file and property only. Never copy a secret value anywhere.

Validators — **only in the container**, never with Windows Node (Windows has no `O_NOFOLLOW`; Windows Node rejects every input):
```powershell
$skill = (Resolve-Path .agents/skills/security-audit).Path
$out   = "$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-<N>"
docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-coverage-ledger.cjs /run/coverage-ledger.json
docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-findings.cjs /run/findings.json
```
Run them whenever the skill says to. Fix every error before continuing.

`REPORT.md` must contain a section "Known findings SEC-001…SEC-011": one row per ID — still present / fixed in audited source / changed — with source evidence. "FIXED on branch X" in the checklist does not prove the fix is on this branch; check the source.

## Allowed changes
- Files under the skill output directory (outside the repository).
- `work-items/SEC-AUDIT-001/audit/` — byte-for-byte copies of `REPORT.md`, `FINDINGS-DETAIL.md`, `NEEDS-VALIDATION.md`, `findings.json`, `coverage-ledger.json`, `run-metadata.json`, `architecture.md`.
- `work-items/SEC-AUDIT-001/implementation.md`, `run.json` (through `workflow.mjs` only).

## Forbidden
- Editing any source, test, fixture, configuration, `pom.xml`, `package*.json`, `.github/`, `docs/`, `AGENTS.md`, `CLAUDE.md`, `.opencode/`, `.agents/skills/` file.
- Building, testing or running the target; network calls other than none; installing packages; pulling images other than the one present.
- DB access of any kind.
- Copying secret values, tokens, passwords or customer data into any file, log or message.
- Committing, pushing, rewriting history.

## Step 4 — checks, record and hand off
Run the checks of `design.md` §6:
- SA-T1, SA-T2: both validators on the final files (container), exit 0.
- SA-T3: `git status --short` → only `work-items/SEC-AUDIT-001/**`.
- SA-T4: secret scan without printing values:
  ```powershell
  $vals = Get-ChildItem -Recurse -File -Filter 'application*.properties' GVRP_Entry_API/src, GVRP_Engine_API/src |
    Get-Content | ForEach-Object { $m = [regex]::Match($_, '^\s*[^#!][^=]*(?i:password|secret|key|token)[^=]*=\s*(.+?)\s*$'); if ($m.Success) { $m.Groups[1].Value } } |
    Where-Object { $_.Length -ge 6 -and $_ -notmatch '^\$\{' }
  $hits = 0
  Get-ChildItem -Recurse -File work-items/SEC-AUDIT-001 | ForEach-Object { $t = Get-Content $_.FullName -Raw; foreach ($v in $vals) { if ($t -and $t.Contains($v)) { $hits++ } } }
  "values checked: $($vals.Count); matches: $hits"
  ```
  Expected `matches: 0`. Report only the two numbers.
- SA-T5…SA-T7: read the records and metadata.

Write `work-items/SEC-AUDIT-001/implementation.md` with: Scope, Setup (output dir, source ref, profile, roles), Phases Run (agents launched per phase, waves, critic passes), Commands Executed (with exit codes, incl. Steps 0–1 and every validator run), Verification Results (SA-T1…T7), Findings Summary (counts by verdict and severity; table of confirmed findings with ID, title, severity, affected boundary), Known Findings Mapping (SEC-001…011), Proposed New Checklist Entries (`SEC-012`… candidates, one line each — do not edit the checklist), Modified Files ("none outside the work item"), Deviations From Design, Remaining Uncertainty (coverage gaps, deferred units, `needs_validation` count), Lesson Candidates, Handoff to Reviewer.

Then:
```
node tools/feature-workflow/workflow.mjs step work-items/SEC-AUDIT-001/run.json ready work-items/SEC-AUDIT-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done, in Vietnamese.

If the run must stop early (budget, error, interruption): set `run_status` as the skill defines, write what exists, and record a checkpoint. Copy `docs/workflows/business-development/checkpoint-input.example.json` to `work-items/SEC-AUDIT-001/checkpoint-input.json`, fill it (current `revision`, `completed`, `remaining`, `read_next`, `blockers`, `decisions`, `processes`, a concrete `next_action`, `evidence` paths), then:
`node tools/feature-workflow/workflow.mjs checkpoint work-items/SEC-AUDIT-001/run.json work-items/SEC-AUDIT-001/checkpoint-input.json`
