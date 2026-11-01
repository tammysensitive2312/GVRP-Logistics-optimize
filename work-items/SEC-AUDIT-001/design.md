# Design — SEC-AUDIT-001

Inputs: `request.md` (SA-R1…R8), `context.md`. Plan by Claude Code, 2026-11-01. The audit itself is executed by OpenCode (`task-opencode.md`).

## 1. Roles

| Skill term | Who |
|---|---|
| Parent (owns shared run files) | OpenCode primary agent `gvrp-orchestrator`, run by the user |
| `research` agent | OpenCode built-in `explore` (read-only) |
| `general` agent | OpenCode built-in `general` |
| Independent reviewer of the finished audit | Claude Code (`review.md`, after `verify`) |

If the parent cannot launch sub-agents (no Task tool), it stops before reconnaissance and reports; it must not run hunters and verifiers in one context, which would break the skill's independence rule.

## 2. Run setup (skill "Full audit setup")

- **Skill directory:** `<repo>/.agents/skills/security-audit` (absolute path).
- **Target:** the repository root, on branch `audit/sec-audit-001` cut from `master`.
- **Repo name:** `GVRP-Logistics_Optimize`.
- **Output directory:** `C:\Users\ACER\security-audit-skill\GVRP-Logistics_Optimize\run-<N>` (outside the repository, next unused `<N>`). Agent roots `agents/<agent-id>/scratch|artifacts` live there.
- **Source ref:** `git rev-parse HEAD` and `git status --short` at start; the worktree must be clean except `work-items/SEC-AUDIT-001/`.
- **`run-metadata.json`:** `profile: "standard"`, `scope_paths` = `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `.github`, root build/config files; `budget: null` unless the user sets one; `execution_policy: "sandboxed-source-and-local-only"`; companions from `context.md`; prior runs: none; prior evidence: `docs/security/SECURITY_CHECKLIST.md`.

## 3. Execution policy (SA-R3, SA-R7)

- Source inspection only. Do not build, test, start, or browse the target; do not call ports 4200, 8080, 8081 or 3306; do not use dbhub or any DB client.
- A lead that needs execution to confirm stays `needs_validation` with the exact unresolved fact and a dummy-tenant validation plan (e.g. a JUnit test with two branches, MockMvc request, expected status).
- Secrets: report file path + property/key name only. If a value is visible in source, never copy it into scratch, artifacts, findings or reports.
- No customer data is read; no data appears in reports.

## 4. Priorities for reconnaissance and hunting

Seed ledger units for at least these surfaces × boundaries (the skill defines the unit format):

1. **Tenant isolation (Entry API):** every controller → service → repository path reading or writing branch-owned data; ID lookups without branch; batch, scheduled and callback paths (`OptimizationCallbackController`, job polling, route sends, Pareto, LEZ compliance and membership, permits, file import).
2. **Authentication and session:** JWT issue/validate/expiry, disabled users, role mapping (PLANNER/DRIVER/CUSTOMER), `permitAll` routes (swagger, actuator, auth, callbacks), register/login/reset flows.
3. **Engine API trust boundary:** unauthenticated endpoints, callback API key handling, job-id guessing, `/legs`, `/evaluate` input size.
4. **Input handling and resource limits:** CSV/JSON/text import, GeoJSON parsing (size, nesting, coordinate count), request sizes, number of orders/vehicles/stops, solver time limits, regex/parse cost.
5. **Client v2:** token storage, session reset (SEC-011 fix), DOM injection sinks (`innerHTML`, `bypassSecurityTrust*`, Leaflet `divIcon`/`bindTooltip`/`bindPopup` HTML built from user data), open redirects (`lastUrl`), cross-branch state.
6. **Supply chain and CI:** `.github/workflows/ci.yml` permissions and secrets usage, Dependabot, lockfiles, plugin and dependency provenance (no installs, no network lookups beyond what is already local).
7. **Secrets in tracked files:** configuration files, work items, tests, fixtures (names only).

## 5. Evidence and output

- All six phases, ledger and findings validated after every update as the skill requires.
- Validators run in a Linux container, never with Windows Node (context: Windows trap):

```powershell
$skill = (Resolve-Path .agents/skills/security-audit).Path
$out   = "$env:USERPROFILE\security-audit-skill\GVRP-Logistics_Optimize\run-<N>"
docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-coverage-ledger.cjs /run/coverage-ledger.json
docker run --rm --network none --read-only -v "${skill}:/skill:ro" -v "${out}:/run:ro" node:22-alpine node /skill/validate-findings.cjs /run/findings.json
```

  Both must print a pass and exit 0. Record every run (phase, exit code) in `implementation.md`.
- Final copies (byte-for-byte) into `work-items/SEC-AUDIT-001/audit/`: `REPORT.md`, `FINDINGS-DETAIL.md`, `NEEDS-VALIDATION.md`, `findings.json`, `coverage-ledger.json`, `run-metadata.json`, `architecture.md`. Agent `scratch/` and `artifacts/` stay outside the repository.
- `REPORT.md` must include a section "Known findings SEC-001…SEC-011" with, for each ID: still present / fixed in audited source / changed, and the source evidence.

## 6. Checks before handoff (test table)

| Case | Check | Expected |
|---|---|---|
| SA-T1 | Coverage ledger validator (container) | exit 0 |
| SA-T2 | Findings validator (container) | exit 0 |
| SA-T3 | `git status --short` | only `work-items/SEC-AUDIT-001/**` changed |
| SA-T4 | Secret scan of `work-items/SEC-AUDIT-001/` for values of properties named in SEC-001/SEC-005 (compare against the local files without printing them; report only match counts) | 0 matches |
| SA-T5 | Every `confirmed` record has severity, source trace and remediation; every `needs_validation` record has no severity and an exact blocker | validator + manual read |
| SA-T6 | `REPORT.md` maps SEC-001…SEC-011 | 11 rows |
| SA-T7 | `run_status` in `run-metadata.json` | `complete`, or `incomplete` with the skill's exact reason disclosed in `REPORT.md` |

If any check cannot be run, record what was attempted, what failed and why; do not report it as passed.

## 7. Risks

- Cost: `standard` profile on three modules may need many agent calls. If the user sets a budget, follow the skill's budget gate; never thin evidence silently.
- Source-only: many leads will be `needs_validation`. That is expected; Claude turns them into fix or validation work items with dummy-tenant tests.
- The validators' Windows refusal is environmental, not an audit failure; running them with Windows Node is forbidden (it would report false failures).
