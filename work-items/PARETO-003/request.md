# Request — PARETO-003 (client v2: zoom and pan in the Pareto trade-off chart)

## Why this is a separate work item (explicit user request)

This run was opened **at the user's explicit request**, not by agent initiative.

- User message (2026-10-18 23:47 +07:00, this session): *"tôi chọn B và nói rõ tại sao lại mở
  work-items mới là do tôi yêu cầu"* — i.e. "I choose B, and state clearly that the reason a new
  work item is opened is that **I** requested it".
- Option B was offered in the same conversation as: *"tôi mở work item mới cho zoom/pan thật trong
  chart"*, after the user asked *"có cho zoom in/out cái sơ đồ này được không bạn?"* about the
  PARETO-002 chart. The alternatives offered were (a) keep the chart as is, relying on browser
  zoom, or (b) a new work item for real zoom/pan inside the chart. The user chose **(b)**.
- Two concrete reasons the work is a new item rather than an edit of PARETO-002:
  1. **Scope**: zoom/pan is a capability that was never in the accepted PARETO-002 design
     (`work-items/PARETO-002/design.md` §3.3 specifies a static inline SVG, fixed
     `viewBox="0 0 560 300"`, responsive width, `<title>` tooltip; requirements PUI-001..PUI-009
     contain nothing about zooming). Adding it would silently expand an accepted design.
  2. **Ledger integrity**: PARETO-002 is at `await_user_test` (revision 5) with a frozen handoff
     fingerprint (`a67a263713fe1e88880f55689b8d2a280532fcbd0675e57d93037a142775e3c9`). Editing
     scoped files under that handoff would invalidate the user's pending acceptance. A separate run
     keeps PARETO-002's verification and the user's acceptance auditable.

## Source

User request above, plus the repository rule that UI-facing verification is done in the real browser
(`docs/workflows/business-development/README.md`, "UI-facing verification uses the real browser").

Depends on: PARETO-002 (the Pareto tab, chart utilities and component) — **working tree changes,
uncommitted** on branch `feature/pareto-002` at HEAD `4e2f928`. This run therefore builds on an
uncommitted base; that is a known limitation of the run, not of the feature.

## Requirements

- **PZU-001 — Zoom.** Inside the plot area the user can zoom in and out about the pointer position
  (mouse wheel; two-finger pinch / `Ctrl`+wheel on touch-capable devices). Zoom is bounded, with a
  documented maximum magnification, and cannot invert the axes or zoom out beyond the full domain.
- **PZU-002 — Pan.** With the view zoomed, the user can drag the plot to move the visible window.
  The pan is clamped so that the data window can never be dragged completely out of view; a drag
  must not trigger point selection.
- **PZU-003 — Reset / fit.** A visible, focusable control returns the view to the default full
  domain; it is disabled (or hidden) while the view is already at the default.
- **PZU-004 — Keyboard and accessibility.** Zoom in, zoom out and reset are operable without a
  mouse, with accessible names; the chart keeps `role="img"` and its `aria-label`; point selection
  by click and by `Enter`/`Space` keeps working while zoomed, and the selected/active/recommended
  markers remain recognisable. The chart must not swallow page scrolling: wheel zoom applies only
  while the pointer is over the plot, and `preventDefault` is used only there.
- **PZU-005 — Correctness while zoomed.** Axis ticks, grid lines, axis labels, the frontier path,
  the active ring and the recommended marker stay mutually consistent at every zoom level, and a
  point's screen position always matches its plotted values. Marker/legend readability must not
  degrade with zoom.
- **PZU-006 — Tests and build discipline.** The zoom/pan/clamp mathematics is unit-tested as pure
  functions and the component interactions are tested; **no new npm dependency**; existing specs are
  not modified, disabled or weakened; the build introduces no new error or warning beyond the
  pre-existing `login.component.scss` budget error.

## Exclusions

- Previewing a non-active candidate's routes on the map (still PARETO-003-candidate follow-up work,
  currently excluded as in PARETO-002).
- Any change to what the chart plots (cost vs CO₂), to the candidate list, to the apply flow, or to
  the backend.
- Brush selection, a minimap, animated transitions, and synchronized zoom between chart and list.

## Acceptance expectations

The user opens the Pareto tab on a real job, zooms in and out with the wheel, pans by dragging,
resets, and confirms: ticks/labels stay correct and readable, points are still selectable, the
active/recommended markers stay identifiable, the page does not scroll while zooming over the
chart, and the reset control returns to the original view.

## Authorization

The user chose option B, which is an explicit request to implement real zoom/pan inside the chart;
implementation within this scope is authorized. No commit, push or deployment is authorized by this
request. PARETO-002's pending acceptance is not affected unless the user decides otherwise.

## Open questions

- **Interaction model (assumption to confirm):** the user chose "B" without resolving the sub-choice
  between *wheel-zoom + drag-pan + Reset* and *an Expand/Fit toggle only*. This request assumes the
  **full** model (wheel/pinch zoom about the pointer + drag pan + Reset), because drag-pan without a
  reset is unusable and a reset without zoom is pointless. If the user intended the lighter
  Expand/Fit variant, the design must be revised before implementation.
- Maximum magnification (proposed 8×) and the wheel step (proposed a constant ratio per notch).
- Whether zoom state should survive switching candidates or reloading the frontier (proposed: reset
  on reload, keep while selecting).
