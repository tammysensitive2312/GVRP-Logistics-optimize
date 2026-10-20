# Implementation addendum (revision 6 → 7) — PARETO-002

No PARETO-002 code was changed in this pass.

The scoped tree differs from the revision-4 implementation only by the PARETO-003 changes
(see `work-items/PARETO-003/implementation.md`, sha256 `b5f73712…`):

- `shared/utils/pareto-chart.utils.ts`: adds the `ChartWindow` helpers. The existing helpers
  (`niceTicks`, `scaleLinear`, `frontierPath`, `compactVnd`, `deltaPct`) are unchanged.
- `pareto-view.component.{ts,html,scss}`: adds zoom, pan and reset. The PARETO-002 apply,
  selection, list and comparison code paths are unchanged (static trace in
  `work-items/PARETO-003/verification.md` §7-2 and `review.md`).
- New specs: `pareto-chart-zoom.utils.spec.ts`, `pareto-view-zoom.component.spec.ts`.

One known user-visible change to PARETO-002 behaviour: the default x-axis tick labels are now
`2.00M / 2.50M` instead of `1.75M / 2.00M / 2.25M`. Point positions are unchanged
(PARETO-003 verification §6-1). The user accepted the screen on 2026-10-19.
