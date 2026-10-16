# Requirements summary (ACCEPTED by user 2026-10-11: Q-1 agreed, Q-2 lenient, Q-3 yes, Q-4 single page) — BULK-EDIT-001

Status legend: ACCEPTED = already agreed (only the goal); PROPOSAL = recommended default; UNDECIDED = your call.

## User scenarios (PROPOSAL)

- S-1: Dispatcher ticks several SCHEDULED orders → sets ON_ROUTE → all flip, table refreshes.
- S-2: Mixed selection (some cannot transition) → dispatcher sees exactly which rows flipped and which refused, with reasons.
- S-3: Dispatcher filters (e.g. status SCHEDULED) → selects all filtered → bulk edits only those.

## Capability scope — draft IDs

| ID | Capability | Proposed v1 verdict |
|---|---|---|
| BE-001 | Bulk set status for selected orders | ACCEPTED: include |
| BE-002 | Per-row success/failure report in UI | ACCEPTED: include |
| BE-003 | Server-side order filters (status/date/search) so S-3 works | ACCEPTED: include |
| BE-004 | Bulk edit other fields (priority, etc.) | ACCEPTED: exclude from v1 |
| BE-005 | Bulk delete | ACCEPTED: exclude |

## Business rules

- BE-010: every row validated against the guarded transition graph before any write.
- BE-011: lenient (ACCEPTED — Q-2): legal rows flip via `bulkTransitionLenient`, illegal rows listed per row.
- BE-012: only orders of the dispatcher's branch; orders locked in an active plan behave per the graph (ON_ROUTE→SERVICING→COMPLETED…), no special-casing.
- BE-013: empty selection → action disabled, no request.
- BE-014 (from Q-4): selection is single-page; changing page/filter clears selection (explicit, prevents cross-page surprises).

## Exceptions (draft)

- BE-020: unknown order id / other-branch id → reported as failed rows, never partial writes without report.
- BE-021: backend down mid-batch → whole batch reported failed, table reloaded to show truth.

## Acceptance criteria (draft, measurable)

- BE-030: S-1 on a 3-order fixture flips all + table refresh shows new statuses.
- BE-031: S-2 mixed fixture reports exact refused rows with transition reasons.
- BE-032: S-3 filter + select-all edits exactly the filtered set.
- BE-033: existing suites green; no single-edit behavior change.

## Open questions (answered 2026-10-11; see `user-decision-01.md`)

- Q-1: any graph-legal target. Q-2: lenient. Q-3: yes, fix filters. Q-4: single page.
