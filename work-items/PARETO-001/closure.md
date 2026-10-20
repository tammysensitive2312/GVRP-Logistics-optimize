# Closure — PARETO-001

- Accepted: 2026-10-18 18:03 (+07:00), user message "được rồi bạn, tôi xác nhận test API thành công"
  (also confirming review finding F1, the third excluded benchmark test).

## Follow-ups

1. PARETO-002 — client v2 Pareto tab (design ready; issue `task-opencode.md` against the merged API).
2. Run the three GraphHopper benchmark/measurement tests in full once (overnight) before release.
3. Add `@UniqueConstraint(job_id, candidate_index)` to `ParetoCandidate` (review F5).
4. Optional: validate the Pareto block before order-status transitions in the callback (F4).
5. Research: compare clustered vs dense Pareto frontiers on a ≥1000-order instance with the
   same independent emission evaluator (AGENTS.md §9).
6. Pre-existing: `JobCleanupService` deletes only first-level revisions (context OBS-1);
   Pareto apply makes deeper chains more likely.

## Delegation notes

- Full Engine suite takes >1 h because of real-matrix benchmark tests; future tasks should
  name the exclusions up front and get user approval per test.
- Line-ending-only differences in test files after branch switches are expected under
  `core.autocrlf`; byte comparison must normalise line endings.
