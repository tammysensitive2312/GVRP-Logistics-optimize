# Request — SEC-AUDIT-001 (security audit of the GVRP system)

Source: Claude Code session, 2026-11-01 (+07:00, Vietnamese). Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 2026-11-01 | "Next step chúng ta sẽ thực hiện security audit check cho hệ thống của mình" | A security audit of the system is the next piece of work. |
| 2026-11-01 | "https://github.com/cloudflare/security-audit-skill — /delegate đây này, tôi muốn bạn lập kế hoạch security audit, cài skills và chuẩn bị các bước để cho opencode làm" | Use the Cloudflare `security-audit` skill. Claude plans, installs the skill and writes the task; OpenCode executes it. |
| 2026-11-01 | "\.agents\skills bạn cài skills vào thư mục này này" | Install the skill under `.agents/skills/`. |
| 2026-11-01 | Answers: profile **standard**; scope **Entry API + Engine API + client v2**; execution: "hãy làm thứ bạn thấy để tốt hơn cho hệ thống và dữ liệu người dùng, nếu cần thứ gì có thể yêu cầu tôi giúp"; validator image: **allow pulling `node:22-alpine`** | Profile and scope fixed. Execution policy left to Claude: chosen **source-only** (no target code execution, no DB writes, no customer data reads) as the safest option for the system and user data. |

## Requirements

| ID | Requirement |
|---|---|
| SA-R1 | Run the skill's **full audit mode**, profile `standard`, all six phases, on the scope below. |
| SA-R2 | Scope (`scope_paths`): `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `.github`, and repository-root build/config files. Everything else is `out_of_scope` in the ledger (never `covered`). |
| SA-R3 | Source-only: no target-controlled build, test, process or browser is executed; leads that need execution stay `needs_validation` with a safe validation plan. |
| SA-R4 | Tenant isolation (branch = company) is the top-priority boundary; every finding states the affected principal and branch relation. |
| SA-R5 | Known findings SEC-001…SEC-011 (`docs/security/SECURITY_CHECKLIST.md`) are prior evidence: each is re-checked against current source and mapped in the report as still present, fixed, or changed. New findings are reported as new. |
| SA-R6 | Both skill validators pass on the final `findings.json` and `coverage-ledger.json`. |
| SA-R7 | No secret value, token, password, or customer personal data appears in any artifact, log, report or message. Secrets are referred to by file and property name only. |
| SA-R8 | No source, test, configuration or documentation file outside `work-items/SEC-AUDIT-001/` is modified. The audit describes fixes; it does not apply them. |

## Exclusions

- Fixing findings (each confirmed finding becomes its own fix work item later).
- Legacy client `GVRP_Entry_client`, `GVRP_Benchmark`, `docs/research`, research skills, experiment data.
- Live or deployed endpoints, the running local stack (ports 4200/8080/8081), Docker containers other than the validator container, and the `gvrp_db` data.

## Acceptance expectations

- `work-items/SEC-AUDIT-001/audit/` holds `REPORT.md`, `FINDINGS-DETAIL.md`, `NEEDS-VALIDATION.md`, `findings.json`, `coverage-ledger.json`, `run-metadata.json`, `architecture.md`, copied from the skill output directory.
- `implementation.md` lists commands with exit codes, the two validator results, the SEC-001…011 mapping and the candidate list of new `SEC-xxx` entries.
- Claude reviews independently; the user decides which findings become fix work items.
