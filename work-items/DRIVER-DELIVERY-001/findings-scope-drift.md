# Findings — DRIVER-DELIVERY-001: scope drift after handoff (2026-10-23)

Source: workflow `accepted` refused on fresh check; recomputed snapshot with
`snapshot(root, scope)` from `tools/feature-workflow/workflow.mjs`.

- Recorded handoff: `1de49bc84f82a1d8d891323b89a9c99f1f4a7fa02842ff028500de3eb404e236` (await_user_test at 2026-10-07T23:36:42.482Z).
- Current snapshot of scope `GVRP_Entry_API/src, GVRP_Entry_client_v2/src, GVRP_Document`: `947e3cdc3afd…` — differs.
- No uncommitted changes inside the scope (`git status --short` empty for the scope paths).

## Commits touching the scope since handoff

- 5cd1ae8 2026-10-20 fix(client): stale fleet rows, cache settle order, navbar navigate
- 8851067 2026-10-20 fix(client): sync package-lock and slim login styles under budget (CI-CLIENT-001)
- 21d2805 2026-10-19 feat(client): Pareto tab with chart zoom/pan (PARETO-002, PARETO-003)
- 4e2f928 2026-10-18 PARETO-001: cluster-first Pareto branch, frontier transport, candidate persistence and apply API
- e98ec95 2026-10-17 Reject JWTs of disabled users or mismatched user ids (SEC-SESSION-001)
- d67daa7 2026-10-16 Restrict Entry API by role: DRIVER only driver API, CUSTOMER blocked (SEC-ROLE-001)
- 6160126 2026-10-16 Fix cross-branch access in solution, vehicle, fleet and route-send endpoints (SEC-TENANT-001)
- de5eeef 2026-10-16 fix(driver): end depot last in route detail; GPS dot above stop pins
- 6cc4396 2026-10-14 research: VQ-003 carbon equation locked; re-scope stale 180 references; archive live reads
- 3f9178d 2026-10-13 research: re-baseline carbon price pins to 150000 VND per ton (KI-030 fixed)
- 045a13a 2026-10-13 chore: retention sweep config (dry-run) + drop tracked .idea metadata
- 13974fd 2026-10-13 new feature - cleanup worker
- 40f431b 2026-10-12 new feature

These come from later work items (SEC-*, PARETO-*, CI-CLIENT-001, cleanup worker)
and follow-up driver fixes; none is a new requirement of DRIVER-DELIVERY-001.

## Routing

`changed` → implement (no new feature edits) → verify (re-run suites on the
current tree) → new handoff. User stated on 2026-10-23 that they tested this
feature on the current code and it meets requirements; that statement is used
for acceptance of the new handoff only.
