# Task for Codex — SEC-AUDIT-001 (restart of the security audit)

You are the **parent agent** of a security audit. The audit is authorized by the user (2026-11-01). Follow `AGENTS.md` in full (sections 14, 15 and 16) and the installed skill `.agents/skills/security-audit/SKILL.md` (full audit mode). Work from the repository root, on Windows, in PowerShell. Talk to the user in Vietnamese; write every file in English.

This replaces an earlier OpenCode attempt (`task-opencode.md`), which was stopped by the user at Phase 5. That attempt is **not** evidence for this run (see Step 0). This is an **audit, not a fix**: change no source, test, configuration or documentation file.

## Read first
1. `work-items/SEC-AUDIT-001/request.md`  2. `context.md`  3. `design.md` (**the specification**; where it names `gvrp-orchestrator` or OpenCode agents, read "you" and "your sub-agents")
4. `.agents/skills/security-audit/SKILL.md`, then each phase file it names when you reach that phase
5. `docs/security/SECURITY_CHECKLIST.md` (prior evidence) and `docs/engineering/LESSONS.md` L-2026-006, 009, 012, 013, 014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and cleanup of the stopped attempt
1. Ask the user to confirm OpenCode is no longer running. Do not continue until they confirm.
2. `node tools/session-brief.mjs` — include the output in your report.
3. `git branch --show-current` must be `audit/sec-audit-001` (created by the earlier attempt from `master` @ `c0cdfd5`). `git log -1 --oneline` must be `c0cdfd5`. Otherwise stop and report.
4. `node tools/feature-workflow/workflow.mjs status work-items/SEC-AUDIT-001/run.json` → state `implement`, and the last history event that is **not** a `checkpoint` must be `ready` `design → implement` at revision 3. Later `checkpoint` events are expected (each one adds a revision; revision 7 on 2026-11-01 after your first attempt) and are not a mismatch. Note the current revision. Do **not** run `init` again. If the state is not `implement` or another transition happened after revision 3, stop and report.
5. Move — do not delete — the earlier attempt's leftovers out of the repository into `$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-1\opencode-leftovers\`:
   - the repository-root folder `agents\` (it must never exist in the repository);
   - in `work-items/SEC-AUDIT-001/`: every `hunter-*`, `verifier-*`, `phase5-verifier-*` file and everything inside `audit\` (empty folders `general-agent-01`, `general-agent-02`, `run-1`), leaving `audit\` itself empty.
   Keep `request.md`, `context.md`, `design.md`, `traceability.md`, `task-opencode.md`, `task-codex.md`, `0*-proof.json`, `checkpoint-input.json`, `run.json`.
6. `git status --short` must then show only `?? work-items/SEC-AUDIT-001/` (plus `.claude/`, `.mcp.json`, `*.log` if present). Otherwise stop and report the list.
7. `docker image inspect node:22-alpine --format '{{.Id}}'` must succeed. If Docker is not reachable from your sandbox, ask the user to allow it or to run the validator commands for you; do not pull or install anything.

Tooling note: if `node`, `git` or `docker` fail inside your sandbox (your first attempt needed a second shell to run Node), ask the user to approve running that command outside the sandbox. Do not record a checkpoint just because a tool failed; record one only when you stop the run.

## Step 1 — sub-agents and independence
The skill requires isolated hunters and verifiers, and a verifier must never be the agent that found the candidate.
- If you can launch sub-agents, use them (read-only explorers for the skill's `research` role, general agents for `general`).
- Otherwise run every hunter and every verifier as a **fresh non-interactive Codex process** (`codex exec`) that receives only its own prompt file and the paths it may read, with its writable root set to its own `agents/<agent-id>/scratch` under the output directory. Never review a candidate in the same context that produced it.
- If neither is possible, stop and report; do not run the whole audit in one context.

## Step 2 — run setup
- Output directory: `$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-2` (next unused `run-<N>`). **Everything the audit writes goes under it**: shared run files, `agents/<agent-id>/scratch|artifacts`, prompt files, intermediate verdicts, helper scripts. Nothing goes into the repository until Step 4.
- Source ref: `git rev-parse HEAD` (`c0cdfd5…`) and `git status --short`.
- `run-metadata.json` per `design.md` §2: profile `standard`, `scope_paths` = `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `.github`, root build/config files; `budget` null unless the user gives one; prior skill runs: `run-1` recorded as **discarded** (stopped mid Phase 5, write isolation broken) — use none of its coverage or findings; prior evidence: the checklist.

## Step 3 — the audit (skill phases 1–6)
Follow the skill exactly, with the priorities of `design.md` §4. Lessons from the stopped attempt:
- **Depth:** the earlier attempt marked 33 of 38 units covered with 4 hunters. Do not repeat that. Seed units per surface × boundary × attack class as `RECONNAISSANCE.md` defines — for tenant isolation at least one unit per Entry API controller (18) and per callback/scheduled path — and give each hunter a small set of units it can actually read end to end (controller → service → repository). Run coverage-critic waves until a clean pass, as `standard` requires.
- **Execution policy (`design.md` §3): source only.** Do not build, test, run or browse the target; do not call ports 4200, 8080, 8081, 3306; no DB access of any kind; no customer data. A candidate whose decisive result needs execution stays `needs_validation` with the exact blocker and a dummy-tenant test plan. Never describe a runtime observation that did not happen; if the skill's schema asks for an observed result, state that it is a source observation.
- **Secrets:** file and property name only; never copy a value.
- **Validators only in the container** (Windows Node rejects every input — no `O_NOFOLLOW`):
  ```powershell
  $skill = (Resolve-Path .agents/skills/security-audit).Path
  $out   = "$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-2"
  docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-coverage-ledger.cjs /run/coverage-ledger.json
  docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-findings.cjs /run/findings.json
  ```
  Run them whenever the skill says to; fix every error before continuing.
- `REPORT.md` must contain "Known findings SEC-001…SEC-011": one row per ID — still present / fixed in audited source / changed — with source evidence. "FIXED on branch X" in the checklist does not prove the fix is in `c0cdfd5`; check the source.

## Allowed changes
- Anything under the `run-2` output directory, and moving the leftovers in Step 0.5.
- `work-items/SEC-AUDIT-001/audit/` — byte-for-byte copies of `REPORT.md`, `FINDINGS-DETAIL.md`, `NEEDS-VALIDATION.md`, `findings.json`, `coverage-ledger.json`, `run-metadata.json`, `architecture.md` from `run-2`.
- `work-items/SEC-AUDIT-001/implementation.md`; `run.json` through `workflow.mjs` only.

## Forbidden
- Editing any source, test, fixture, configuration, `pom.xml`, `package*.json`, `.github/`, `docs/`, `AGENTS.md`, `CLAUDE.md`, `.opencode/`, `.agents/` file.
- Writing audit scratch, prompts, agent folders or helper scripts inside the repository.
- Building, testing or running the target; network access; installing packages; pulling images.
- DB access; copying secret values, tokens, passwords or customer data anywhere.
- Committing, pushing, rewriting history.

## Step 4 — checks, record and hand off
Run the checks of `design.md` §6:
- SA-T1, SA-T2: both validators on the final `run-2` files (container), exit 0.
- SA-T3: `git status --short` → only `work-items/SEC-AUDIT-001/**`; no `agents/` at the repository root.
- SA-T4: secret scan without printing values:
  ```powershell
  $vals = Get-ChildItem -Recurse -File -Filter 'application*.properties' GVRP_Entry_API/src, GVRP_Engine_API/src |
    Get-Content | ForEach-Object { $m = [regex]::Match($_, '^\s*[^#!][^=]*(?i:password|secret|key|token)[^=]*=\s*(.+?)\s*$'); if ($m.Success) { $m.Groups[1].Value } } |
    Where-Object { $_.Length -ge 6 -and $_ -notmatch '^\$\{' }
  $hits = 0
  Get-ChildItem -Recurse -File work-items/SEC-AUDIT-001 | ForEach-Object { $t = Get-Content $_.FullName -Raw; foreach ($v in $vals) { if ($t -and $t.Contains($v)) { $hits++ } } }
  "values checked: $($vals.Count); matches: $hits"
  ```
  Expected `matches: 0`; report only the two numbers.
- SA-T5…SA-T7: read the records and metadata.

Write `work-items/SEC-AUDIT-001/implementation.md` with: Scope, Setup (agent: Codex, independence method from Step 1, output dir, source ref, profile), Restart Notes (what was moved in Step 0.5, why run-1 is discarded), Phases Run (agents per phase, waves, critic passes, units per hunter), Commands Executed (with exit codes, incl. Step 0 and every validator run), Verification Results (SA-T1…T7), Findings Summary (counts by verdict and severity; table of confirmed findings with title, severity, affected boundary), Known Findings Mapping (SEC-001…011), Proposed New Checklist Entries (`SEC-012`… candidates, one line each — do not edit the checklist), Modified Files ("none outside the work item"), Deviations From Design, Remaining Uncertainty (gaps, deferred units, `needs_validation` count), Lesson Candidates, Handoff to Reviewer.

Then move the run to `verify`. A proof must carry the run's **current** revision, and `04-proof.json` was written for revision 3, so do not use or edit it. Read the current revision `<R>` with `workflow.mjs status`, write `work-items/SEC-AUDIT-001/04-proof-rev<R>.json`:
```json
{"revision": <R>, "artifacts": {"implementation": "work-items/SEC-AUDIT-001/implementation.md"}}
```
and run
```
node tools/feature-workflow/workflow.mjs step work-items/SEC-AUDIT-001/run.json ready work-items/SEC-AUDIT-001/04-proof-rev<R>.json
```
It must print `{"state":"verify","revision":<R+1>}`. Do not go further than `verify`. Tell the user you are done.

If the run must stop early (budget, error, interruption): set `run_status` as the skill defines, write what exists, copy `docs/workflows/business-development/checkpoint-input.example.json` to `work-items/SEC-AUDIT-001/checkpoint-input.json`, fill it (current `revision`, `completed`, `remaining`, `read_next`, `blockers`, `decisions`, `processes`, a concrete `next_action`, `evidence`), then run
`node tools/feature-workflow/workflow.mjs checkpoint work-items/SEC-AUDIT-001/run.json work-items/SEC-AUDIT-001/checkpoint-input.json`.
