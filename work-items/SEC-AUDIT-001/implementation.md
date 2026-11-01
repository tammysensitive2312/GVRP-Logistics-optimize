# Implementation — SEC-AUDIT-001 (security audit run-2)

## Scope

Full audit mode of the Cloudflare `security-audit` skill (commit c1c8a8c), profile `standard`, on `GVRP_Entry_API`, `GVRP_Engine_API`, `GVRP_Entry_client_v2`, `.github` and root config, at `c0cdfd55dcd111fbcbb79474a33ee7f4518ae815` (branch `audit/sec-audit-001`). Source only. No repository file outside this work item was changed.

## Setup

- Output directory: `C:\Users\ACER\security-audit-skill\GVRP-Logistics_Optimize\run-2` (outside the repository). Final copies in `audit/`.
- Parents: **Codex** ran Step 0, Phases 1–2 (4 reconnaissance agents, 21 hunters, 4 coverage-critic waves, 47 ledger units) and wrote the 18 Phase 3 prompts, then ran out of monthly quota (2026-11-01). **Claude Code** took over as parent on the user's instruction ("liệu bạn có thể làm nốt không?") and ran Phases 3–6.
- Independence: every Phase 3 and Phase 5 verifier was a fresh sub-agent that had not hunted or earlier validated its record. Phase 3: 19 verifiers (`v3-01`…`v3-19`, one per candidate; `v3-19` checks a separate root cause raised by `v3-16`). Phase 5: first attempt with 15 single-record verifiers ended with an API session limit before any result; re-run as 3 batch verifiers (`v5-01..05`, `v5-06..10`, `v5-11..15`) on a cheaper model to fit the session budget.
- Validators: in `node:22-alpine`, `--network none --read-only` (Windows Node rejects every input).

## Restart Notes

- `run-1` (OpenCode) is discarded: stopped in Phase 5 with agent folders written into the repository. Codex moved its leftovers (repository-root `agents/`, intermediate prompt/verdict files) to `run-1\opencode-leftovers\`.
- Codex recorded workflow checkpoints rev 4–8 while handling tool failures and an over-strict precondition in the first version of `task-codex.md`; the task was corrected (Step 0.4 accepts checkpoints, Step 4 uses a current-revision proof).

## Phases Run

| Phase | Agents | Result |
|---|---|---|
| 1 Reconnaissance | 4 | `architecture.md`, `reconnaissance.md`, ledger seeded |
| 2 Hunting | 21 hunters, 4 critic waves | 47 units; 18 candidate fingerprints |
| 3 Candidate validation | 19 | 15 needs_validation, 4 rejected, 0 confirmed |
| 4 Structured output | parent | `findings.json`; format normalization (trace kinds; library-source evidence folded into the `pom.xml` evidence) logged in `phase4-normalize.log`; decisions recorded in 30 linked units |
| 5 Record verification | 3 (batched) | 15/15 verified, no replacement |
| 6 Reports | parent (script) | `REPORT.md`, `NEEDS-VALIDATION.md`, `FINDINGS-DETAIL.md` |

## Commands Executed

| Command | Exit / result |
|---|---|
| validators after Phase 4 (first) | findings exit 1 (22 format errors), ledger exit 0 |
| `node phase4-normalize.cjs`, validators | findings PASS 19 (0), ledger PASS 47 (0) |
| `node phase3-record-ledger.cjs`, validators | PASS / PASS (0, 0) |
| `node phase6-reports.cjs` | reports written |
| final validators (after restarting Docker Desktop) | findings PASS 19 (0), ledger PASS 47 (0) |
| secret scan of run-2 outputs and of this work item | values checked 12; matches 0 |
| `git status --short` | `?? work-items/SEC-AUDIT-001/` only; no `agents/` at repository root |

## Verification Results

| Check | Result |
|---|---|
| SA-T1 ledger validator | PASS |
| SA-T2 findings validator | PASS |
| SA-T3 git status | only the work item |
| SA-T4 secret scan | 0 matches |
| SA-T5 record contracts | validator + all 15 retained records verified in Phase 5 |
| SA-T6 SEC-001…011 mapping | 11 rows in `REPORT.md` §6 |
| SA-T7 run_status | `complete` |

## Findings Summary

0 confirmed, 15 needs_validation, 4 rejected. Leads in priority order are in `audit/REPORT.md` §4; the top four:

1. `callback-api-key-filter-encoded-path-bypass` — raw-URI prefix check in `ApiKeyAuthenticationFilter` may let `/api/v1/solutions/%63allbacks/...` skip the Engine API key while still reaching the `permitAll` callback handler (per Spring Security 6.5.6 / Framework 6.2.12 library sources).
2. `callback-cross-branch-materialization` — completion callback resolves order/vehicle IDs with unscoped `findAllById` and re-statuses them without a branch check.
3. `OrderService:bulk-update-cross-branch-order-code` — bulk status update returns another branch's order codes in failed rows.
4. `leaflet.raw-api-text-html` — stored names interpolated unescaped into Leaflet popup/tooltip HTML; no CSP.

## Known Findings Mapping

See `audit/REPORT.md` §6: still present SEC-001, 004, 007, 008 (changed), 010; no regression found SEC-002, 003, 006; fixed with residual lead SEC-009, SEC-011; not assessed SEC-005 (out of scope).

## Proposed New Checklist Entries (not applied)

- SEC-012 (lead, likely High if reproduced): Engine callback API key skippable via encoded callback path.
- SEC-013 (lead, likely High): completion callback writes orders/vehicles of other branches.
- SEC-014 (lead, Medium): bulk order status discloses other branches' order codes.
- SEC-015 (lead, Medium): stored HTML in Leaflet popups/tooltips (stored XSS) with no CSP.
- SEC-016 (lead): MapService not reset on session change (SEC-011 residual).
- SEC-017 (lead): JWT branch claim not reconciled with the reloaded user.
- SEC-018 (lead): order import without row cap, one paid geocoding call per row.
- SEC-019 (lead): Engine solver work not bounded (iterations, threads, timeout ignored).
- SEC-020 (lead): unpinned `@bytebase/dbhub@latest` in tracked MCP configs.
- SEC-021 (lead, reliability): retention sweep stalls on nested revision chains.

## Modified Files

None outside `work-items/SEC-AUDIT-001/`.

## Deviations From Design

- Parent changed mid-run from Codex to Claude Code; Claude Code also wrote the design, so the final independent review is weaker than planned (the verifiers remain independent).
- Phase 5 verifiers were batched (5 records each) on a cheaper model because of the session budget.
- Phase 4 normalized verifier records (format only, logged).

## Remaining Uncertainty

- No lead is confirmed; all 15 need a local dummy-branch test or an owner deployment check (`audit/NEEDS-VALIDATION.md`).
- One ledger unit is `blocked` (CI pull-request workflow).
- **Incident:** Phase 3 verifier `v3-14` printed several live secret values from `application.properties` into its own tool output while classifying them. They were not written to any file, but they reached the model provider in that agent's transcript. Treat the SEC-001 credentials as exposed; rotation is now more urgent.

## Lesson Candidates

- Agents asked to check credentials must be told to print property names only (the `v3-14` incident).
- Proof files must carry the current run revision; checkpoints raise it (Codex blocked on this).
- The skill validators need Linux (`O_NOFOLLOW`); Docker Desktop must be running.

## Handoff to Reviewer

Read `audit/REPORT.md`, then `audit/NEEDS-VALIDATION.md` items 1–4. Re-run both validators with the container commands in `design.md` §5.
