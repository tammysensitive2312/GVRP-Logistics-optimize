# Findings — DRIVER-MAP-004: scope drift after handoff (2026-10-23)

Source: workflow `accepted` refused on fresh check; recomputed snapshot with
`snapshot(root, scope)` from `tools/feature-workflow/workflow.mjs`.

- Recorded handoff: `7402b776243bbfaccdb9fc6983bc7f7bfedc70bd3c6aacac46ba51ece566d8c6` (await_user_test at 2026-10-12T01:00:09.477Z).
- Current snapshot of scope `GVRP_Entry_client_v2/src`: `7351cc170f26…` — differs.
- No uncommitted changes inside the scope (`git status --short` empty for the scope paths).

## Commits touching the scope since handoff

- 5cd1ae8 2026-10-20 fix(client): stale fleet rows, cache settle order, navbar navigate
- 8851067 2026-10-20 fix(client): sync package-lock and slim login styles under budget (CI-CLIENT-001)
- 21d2805 2026-10-19 feat(client): Pareto tab with chart zoom/pan (PARETO-002, PARETO-003)
- de5eeef 2026-10-16 fix(driver): end depot last in route detail; GPS dot above stop pins
- 13974fd 2026-10-13 new feature - cleanup worker

These come from later work items (SEC-*, PARETO-*, CI-CLIENT-001, cleanup worker)
and follow-up driver fixes; none is a new requirement of DRIVER-MAP-004.

## Routing

`changed` → implement (no new feature edits) → verify (re-run suites on the
current tree) → new handoff. User stated on 2026-10-23 that they tested this
feature on the current code and it meets requirements; that statement is used
for acceptance of the new handoff only.
