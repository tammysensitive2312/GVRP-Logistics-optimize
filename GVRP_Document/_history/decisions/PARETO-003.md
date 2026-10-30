# PARETO-003 — zoom and pan in the trade-off chart

**Problem.** On a dense frontier the interesting points were a few pixels wide, and the chart could
not be inspected closer.

**Decision.**

- The Pareto chart supports zoom (wheel and buttons) and pan (drag) with a reset control.
- Zoom and pan are client-side only; the data and the selected candidate are unchanged by the gesture.
- Touch pan was implemented and is expected to work, but was verified only by pointer events, not on
  a real touch device.

**Date.** Accepted 2026-10-19 00:35 (+07:00); state `done` rev 7.

**Source.** `work-items/PARETO-003/` (deleted): `design.md`, `verification.md`, `review.md` (PASS,
findings Z1–Z4), `closure.md`.

**Still valid in this documentation.** `03-basic-design/D05-solution/README.md`, `TC-D05-015`.

**Open follow-ups recorded at closure.** Optionally restore denser default x ticks (Z1) and add a
`clip-path` to the plot area (Z3); test pan and pinch on a touch device (Z2).
