# Review — PARETO-003 (Pareto chart zoom / pan / reset)

Reviewer: Claude (orchestrator), 2026-10-19. Independent static review of the code on disk at
fingerprint `f1bb0602…`, plus a reading of `verification.md`. The reviewer did not re-run tests
or the build; the numbers quoted come from the verifier.

## Claim

PZU-001..PZU-006: zoom in and out (wheel, buttons, pinch), drag pan, and reset, with an 8× limit;
the window stays inside the padded full domain; the default view is preserved; no regression in
PARETO-002; no change to existing tests, dependencies or config.

## Supporting evidence

- `pareto-chart.utils.ts`:
  - The window helpers are pure and return new objects.
  - Degenerate or non-finite input falls back to `full`; nothing throws.
  - `zoomWindow` keeps the anchored data value fixed: `min' = anchor − f·span'`.
  - `clampSpan` bounds every span to `[full/8, full]`.
  - `clampWindow` re-centres the window, then shifts it back inside `full`.
  - `panWindow` flips the screen-down y fraction.
  - The existing PARETO-002 helpers are unchanged.
- `pareto-view.component.ts`:
  - `zoom = null` means the default view.
  - The frontier-change effect reads `frontier` and writes only `zoom`, so it cannot loop.
  - `preventDefault()` runs only when the pointer is inside the plot, so the page still scrolls elsewhere.
  - Document listeners go through `Renderer2` and are released on `pointerup`, on `pointercancel` and in `ngOnDestroy`.
  - A drag beyond 4 px suppresses the trailing click.
  - The apply, selection and comparison paths are untouched.
- Template: zoom buttons have an `aria-label`, a `title` and correct `[disabled]` states.
  `touch-action: none` applies only to the SVG.
- Verifier evidence:
  - 245 / 240 / 5 known failures, which is +25 new specs with the same failure set.
  - The budget set is unchanged and the bundle grew by 0.14 kB.
  - The 8× clamp is numerically exact live.
  - Reset restores the point geometry bit for bit.
- Test integrity: the pre-existing client specs are content-identical to the pre-PARETO-002
  snapshot (`PARETO-002/review.md`).

## Findings (non-blocking)

| # | Finding | Disposition |
|---|---|---|
| Z1 | The default x ticks changed from `1.75M/2.00M/2.25M` to `2.00M/2.50M`, a documented deviation (verification §6-1). | Accepted by the user's screen test. A denser default is a one-line follow-up if wanted. |
| Z2 | Drag pan was verified with synthetic in-page events; touch and pinch were not exercised live. | Residual risk. |
| Z3 | No `clip-path`; see PARETO-002 review R3. | Follow-up. |
| Z4 | The shared scope made the PARETO-002 handoff stale; this was reported by the verifier, not hidden. | Handled by the PARETO-002 `changed` route. |

## Verdict

**PASS.** The claim is supported within the verifier's limits (§7).
