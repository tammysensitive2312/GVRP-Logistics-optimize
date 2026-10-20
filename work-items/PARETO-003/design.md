# Design — PARETO-003 (zoom and pan in the Pareto trade-off chart)

Companion diagrams (design-node obligation): [`diagrams/pareto-zoom-component.puml`](diagrams/pareto-zoom-component.puml)
(+ rendered `diagrams/pareto-zoom-component.svg`) and
[`diagrams/pareto-zoom-sequence.puml`](diagrams/pareto-zoom-sequence.puml)
(+ rendered `diagrams/pareto-zoom-sequence.svg`).

## 1. Chosen model: zoom the **data window**, not the SVG picture

The SVG keeps `viewBox="0 0 560 300"` and every existing geometry constant. Zooming changes the
*visible data window* `[costMin, costMax] × [co2Min, co2Max]`; positions, ticks and the frontier
path are recomputed by the same `scaleLinear` / `niceTicks` / `frontierPath` helpers that PARETO-002
already uses.

Rationale (and why the obvious alternative is rejected):

- An SVG `transform: scale(k)` on a group would enlarge the markers (r = 6), the active ring
  (r = 11), the ★ and the stroke widths, blur the text, and leave the axis labels **wrong for the
  visible area** (they would need an inverse transform to stay readable). It produces a magnified
  picture, not a chart.
- The data-window model keeps every marker the same size and keeps the ticks "nice" numbers whose
  values are true for the visible sub-range. It is also pure arithmetic, therefore unit-testable
  without a browser — which matters because the pointer→viewBox conversion itself is not testable.
- Cost of the model: ticks must be recomputed per interaction (already the case — `chart()` is a
  `computed`), and a very tight window can yield few ticks (`niceTicks` returns `[min]` for a
  degenerate range; acceptable, must not divide by zero).

## 2. State

```ts
interface ChartWindow { costMin: number; costMax: number; co2Min: number; co2Max: number }
```

- `zoom = signal<ChartWindow | null>(null)` — `null` means "default view" (the full padded domain
  that PARETO-002 computes).
- `fullWindow = computed(...)` — `paddedDomain(costs)` / `paddedDomain(co2s)` as today.
- `window = computed(() => this.zoom() ?? this.fullWindow())` — fed into `chart()` in place of the
  two `paddedDomain` calls; the plotted point coordinates are unchanged in meaning.
- `isDefaultView = computed(() => this.zoom() === null)` (or window equals full) — drives the Reset
  button's disabled state.
- **Zoom survives candidate selection; it resets when the frontier input changes** (a new revision,
  a reload after apply, or a different job), because the data and the padded domain change and a
  stale window could hide all points. Implemented with an `effect` that sets `zoom` to `null` when
  `frontier()` changes (guarded so it does not loop).

## 3. Pure mathematics (extends `C/shared/utils/pareto-chart.utils.ts`, still DOM-free)

```ts
export const MAX_CHART_ZOOM = 8;
export function fullWindow(x: [number, number], y: [number, number]): ChartWindow;
export function windowSpan(w: ChartWindow): { x: number; y: number };
export function isFullWindow(w: ChartWindow, full: ChartWindow, eps?: number): boolean;
export function zoomWindow(w: ChartWindow, full: ChartWindow, fx: number, fy: number,
                           factor: number, maxZoom?: number): ChartWindow;
export function panWindow(w: ChartWindow, full: ChartWindow,
                          dxFraction: number, dyFraction: number): ChartWindow;
export function clampWindow(w: ChartWindow, full: ChartWindow, maxZoom?: number): ChartWindow;
```

Semantics:

- `fx`, `fy` ∈ [0, 1] are fractions **inside the plot rectangle** of the pointer position
  (`fx = 0` at `PLOT_LEFT`, `fy = 0` at `plotBottom`, i.e. `fy` grows upwards to keep the screen
  convention). The anchor point's data value stays under the pointer:
  `newMin = anchor - fx * newSpanX`, then clamped.
- `factor < 1` zooms in, `factor > 1` out. Spans are clamped to
  `[fullSpan / maxZoom, fullSpan]` (default `maxZoom = MAX_CHART_ZOOM = 8`).
- `panWindow` shifts by a fraction of the **current window span**, positive `dxFraction` moving the
  window to the right (data increases); `dyFraction` is in screen-down units and is flipped
  internally so dragging the plot up shows higher CO₂.
- `clampWindow` keeps the window inside `full` (`min ≥ full.min`, `max ≤ full.max`) and re-applies
  the span limits. A zero-span or non-finite window falls back to `full` instead of throwing.
- All functions return new objects (no mutation) so Angular signal equality is predictable.

## 4. Pointer → viewBox conversion, and why no `setPointerCapture`

- Conversion uses `svg.getScreenCTM().inverse()` applied to `(clientX, clientY)`; the uniform
  rendered scale is `getScreenCTM().a`, giving the rendered plot width/height as
  `PLOT_W * scale` / `PLOT_H * scale` for the drag deltas. Both come from real layout, so only the
  arithmetic *after* conversion lives in the pure functions above.
- **`setPointerCapture` on the SVG root is deliberately not used**, unlike
  `resizable-divider.directive.ts`. Pointer capture retargets `pointerup` to the capture element,
  and the compatibility mapping then dispatches `click` to the nearest common ancestor of the
  `pointerdown`/`pointerup` targets — i.e. to the `<svg>`, not to the `<g class="point">`, which
  would silently break point selection (the exact behaviour the existing
  `pareto-view.component.spec.ts` asserts). Instead, drag tracking uses `Renderer2.listen` on
  `document` for `pointermove` / `pointerup` / `pointercancel`, with the returned cleanup functions
  released in `ngOnDestroy` (the cleanup discipline of the divider directive is kept).
- `touch-action: none` is set **only on the SVG** (inside a small new rule), so a touch drag pans
  instead of scrolling the panel (`overflow-y: auto`), while the panel still scrolls by touch
  outside the chart.

## 5. Event wiring in `ParetoViewComponent`

| Event | Behaviour |
|---|---|
| `(wheel)` on `<svg>` | `preventDefault()` (element listener ⇒ non-passive) and zoom by `factor = deltaY < 0 ? 1 / WHEEL_STEP : WHEEL_STEP` with `WHEEL_STEP = 1.25`, `ctrlKey` (trackpad pinch) taking the same path; ignored when `deltaY === 0` or the pointer is outside the plot rectangle |
| `(pointerdown)` on `<svg>` | records the start point and `dragMoved = false` |
| document `pointermove` while down | beyond `DRAG_THRESHOLD_PX = 4` client px sets `dragMoved = true` and calls `panWindow` with the delta converted to span fractions |
| document `pointerup` / `pointercancel` | ends the drag and removes the listeners |
| second simultaneous pointer | pinch: zoom about the midpoint by `previousDistance / currentDistance`, no pan |
| `(click)` on `<g class="point">` | **unchanged**, but the handler returns early when `dragMoved` is true; `dragMoved` is reset on the next `pointerdown`, so a normal click always selects |
| `(keydown)` on `<g class="point">` | unchanged (`Enter` / `Space`) |
| Zoom in / Zoom out / Reset buttons | `zoomWindow` about the plot centre `(0.5, 0.5)`, or `zoom.set(null)` |

Cursor: `.pareto-chart` gets `cursor: grab`, and `cursor: grabbing` while dragging, via a bound class.

## 6. Controls and accessibility (PZU-003, PZU-004)

- A `role="group" aria-label="Chart zoom"` strip next to the summary holds three `mat-icon-button`s —
  `zoom_in` / `zoom_out` / `restart_alt` — each with `aria-label` and `title`
  ("Zoom in", "Zoom out", "Reset zoom"). `MatButtonModule` and `MatIconModule` are already imported
  by the component, so no new dependency and no new module import.
- Reset is `disabled` while `isDefaultView()`; Zoom in is `disabled` at `MAX_CHART_ZOOM`; Zoom out is
  `disabled` at the full window. Native buttons give keyboard access for free.
- The `<svg>` keeps `role="img"` + `aria-label`; point groups keep `role="button"` + `tabindex="0"`.

## 7. Correctness invariants (must hold at every zoom level)

1. For every candidate, the rendered point position equals
   `xScale(total_cost)`, `yScale(total_co2)` for the **current window**; therefore a point is inside
   the plot rectangle iff its values are inside the window.
2. Tick label ↔ grid line consistency: a label is drawn only at a tick returned by
   `niceTicks(window.min, window.max)`, and its grid line uses the same scale.
3. The frontier path joins FRONTIER points in ascending cost order and excludes DOMINATED and
   SERVICE_DOMINATED points — unchanged by zooming.
4. Markers keep a constant on-screen size (`r` stays in viewBox units), and the active ring/★ stay
   attached to their points.
5. `isFullWindow(window, full)` is true exactly when no zoom is applied, and Reset restores the
   exact default geometry that PARETO-002 produces today.

## 8. Tests (PZU-006)

- **New** `C/shared/utils/pareto-chart-zoom.utils.spec.ts`: `zoomWindow` anchor invariance
  (the anchored value is unchanged for both axes), span clamping at 1× and 8×, `factor = 1`
  is the identity, clamping against the window edges, `panWindow` direction and clamping,
  `clampWindow` fallback for a degenerate window, `isFullWindow` tolerance.
- **New** `C/features/main/orders/pareto-view/pareto-view-zoom.component.spec.ts`: wheel over the
  plot shrinks the window and changes the tick labels; wheel with the pointer outside the plot is
  ignored; Reset restores the default and is disabled at default; a drag beyond the threshold does
  **not** select a point while a plain click still does; pinch with two pointers zooms; the frontier
  changing resets the zoom.
- **Unchanged and must keep passing:** the 5 assertions in `pareto-view.component.spec.ts`
  (5 `.point`, 3 frontier markers, 1 dominated marker, 1 `.point-cross`, 1 `.frontier-line`) and all
  existing `pareto-chart.utils` tests. No existing spec file is edited (AGENTS.md §14).
- Build: budgets are `anyComponentStyle` 4.10 kB warn / 8.19 kB error. `pareto-view.component.scss`
  is currently **not** in the warning list, so new CSS must stay minimal; the engineer must compare
  the build's warning/error set against `C:\Users\ACER\AppData\Local\Temp\opencode\pareto002-build-GATE.log`
  and report any new entry as a deviation rather than hiding it.

## 9. Files expected to change

- `C/shared/utils/pareto-chart.utils.ts` (add the window functions; existing exports untouched),
- `C/features/main/orders/pareto-view/pareto-view.component.ts` (window signal + event handlers),
- `…/pareto-view.component.html` (wheel/pointer bindings, cursor class, control strip),
- `…/pareto-view.component.scss` (few rules: `touch-action`, cursors, control strip),
- two new spec files listed in §8.

## 10. Assumptions (confirm or correct)

- The full interaction model was requested (wheel/pinch zoom about the pointer + drag pan + Reset);
  see `request.md` §Open questions. If the user wanted only an Expand/Fit toggle, §1–§7 shrink to a
  single button and the pure functions become unnecessary.
- Maximum magnification 8×; wheel step 1.25× per notch; drag threshold 4 px; double-click does not
  reset (not requested).
- Keyboard shortcut keys for zoom are not added (buttons only).
- Zoom is not shared with the map view, the candidate list, or the timeline.
