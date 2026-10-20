# Implementation — PARETO-003 (client v2: chart zoom and pan in the Pareto tab)

Work item: PARETO-003. Branch `feature/pareto-002`, HEAD `4e2f928` (base = `master`,
which already contains PARETO-001). Working tree **DIRTY** (PARETO-002 changes are
uncommitted; PARETO-003 adds to the same tree). Nothing committed, nothing pushed.
Specification: `work-items/PARETO-003/design.md` (§1–§10, authoritative). Dispatch:
the authorized task in this session. Depends on PARETO-002 (uncommitted) per
`request.md`.

Verdict requested by this document: none. This is the implementation record; the run
stops at workflow state `implement`. Independent review is a separate step.

---

## Scope

Client-only change for `GVRP_Entry_client_v2` (Angular 18.2 standalone components,
signals, Angular Material 18). Adds `zoom`/`pan`/`pinch`/`Reset` to the inline-SVG
Pareto trade-off chart by **zooming the data window**, not the SVG picture (design
§1). No new npm dependency, no chart library, no backend change, no storage. Only
files under `GVRP_Entry_client_v2/src/app/**` plus this document are touched.

Base under test: the PARETO-002 working tree, which is **not a commit**, so this
record demonstrates that the current tree behaves as described; it does not establish
that `4e2f928` alone reproduces it (same limitation as PARETO-002).

---

## Baseline (given, not re-derived here)

- `npm test -- --watch=false --browsers=ChromeHeadless` → **220 executed, 215 SUCCESS,
  5 FAILED**; the 5 failures are pre-existing and named:
  1. `FleetSetupComponent keeps the vehicle when the confirmation is dismissed`
  2. `NavbarComponent navigates when a tab is clicked`
  3. `CollectionCache starts a new request once the previous one settled`
  4. `CollectionCache records the error message and rethrows`
  5. `CollectionCache refetches on every load - there is no freshness window`
- `npm run build` → exit 1, the single pre-existing `login.component.scss`
  `anyComponentStyle` budget **ERROR** (9.40 kB vs 8.19 kB), 9 component-style
  budget **WARNING**s and the `leaflet` CommonJS warning; initial total 665.41 kB.
  Reference: `C:\Users\ACER\AppData\Local\Temp\opencode\pareto002-build-GATE.log`.

---

## Changes Made

### `src/app/shared/utils/pareto-chart.utils.ts` (existing file, additive)

Added, after `deltaPct`, all new exports required by design §3; every existing export
and behaviour is unchanged (the existing `pareto-chart.utils.spec.ts` still passes):

| Export | Semantics |
|---|---|
| `interface ChartWindow` | `{ costMin, costMax, co2Min, co2Max }` |
| `MAX_CHART_ZOOM = 8` | maximum magnification |
| `fullWindow(x, y)` | build the default window from the padded domains |
| `windowSpan(w)` | `{ x, y }` spans |
| `isFullWindow(w, full, eps = 1e-9)` | per-bound absolute tolerance |
| `zoomWindow(w, full, fx, fy, factor, maxZoom = 8)` | anchor-preserving zoom; span clamped to `[fullSpan/8, fullSpan]`; result clamped inside `full` |
| `panWindow(w, full, dxFraction, dyFraction)` | shift by a fraction of the current span; `dxFraction > 0` → higher cost; `dyFraction` is screen-down and flipped internally (drag up → higher CO₂); clamped |
| `clampWindow(w, full, maxZoom = 8)` | keep inside `full`, re-apply span limits; zero-span/non-finite → `full` |

All functions are pure, DOM-free, return new objects (no mutation), and fall back to
`full` for degenerate/non-finite input instead of throwing. Private helpers
`isFiniteWindow`, `clampFraction`, `clampSpan` were added next to `niceStep`.

### `…/pareto-view/pareto-view.component.ts`

- New state (design §2): `zoom = signal<ChartWindow | null>(null)`, `dragging =
  signal(false)`, `fullWindow` computed (same `paddedDomain` inputs as before),
  `window = computed(() => zoom() ?? fullWindow())`, `atFullWindow`, `atMaxZoom`,
  `isDefaultView` (`zoom() === null || atFullWindow()`).
- `chart()` now consumes `win = this.window()` for the scales **and** for
  `niceTicks(win.costMin, win.costMax)` / `niceTicks(win.co2Min, win.co2Max)`
  (design §7, invariant 2). The empty-guard now also covers `win === null`.
- Constructor registers one guarded `effect` that resets `zoom` to `null` when
  `frontier()` changes (with `{ allowSignalWrites: true }`, required by Angular 18;
  the callback reads only `frontier` and writes only `zoom`, so it cannot loop).
- Event wiring (design §5):

```ts
onWheel(event: WheelEvent): void {
  if (event.deltaY === 0) return;
  const plot = this.toPlotFraction(event.clientX, event.clientY);
  if (!plot) return;                       // outside the plot rectangle
  event.preventDefault();                  // only over the plot (PZU-004)
  const factor = event.deltaY < 0 ? 1 / WHEEL_STEP : WHEEL_STEP;
  this.applyZoom(plot.fx, plot.fy, factor);
}

onPointerDown(event: PointerEvent): void { // no setPointerCapture (design §4)
  this.dragMoved = false;
  this.activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
  if (this.activePointers.size === 1) { /* record dragStart/lastPan, dragging=true */ }
  else if (this.activePointers.size === 2) { this.pinchDistance = this.pointerDistance(); }
  if (this.cleanupFns.length === 0) {
    this.cleanupFns = [
      this.renderer.listen('document', 'pointermove', e => this.onDocumentPointerMove(e)),
      this.renderer.listen('document', 'pointerup', e => this.onDocumentPointerUp(e)),
      this.renderer.listen('document', 'pointercancel', e => this.onDocumentPointerUp(e))
    ];
  }
}

onPointClick(id: number): void {           // (click) on <g class="point">
  if (this.dragMoved) return;              // a drag suppresses selection
  this.select(id);
}
```

- Drag/click suppression (the critical interaction): `onDocumentPointerMove` ignores
  movement below `DRAG_THRESHOLD_PX = 4` client px; once the threshold is crossed it
  sets `dragMoved = true` and pans by the incremental client delta converted with
  `plotGeometry()` (`PLOT_W * getScreenCTM().a`, `PLOT_H * getScreenCTM().a`).
  `dragMoved` is **not** cleared on pointerup (the `click` fires after pointerup); it
  is cleared on the next `pointerdown`, so a plain click always selects. Document
  listeners are released in `ngOnDestroy` / `endDrag` (divider-directive cleanup
  discipline, but **without** `setPointerCapture`).
- Two-pointer pinch: `handlePinch()` zooms about the midpoint with factor
  `previousDistance / currentDistance`, no pan. Lifting one of two pointers rebases
  `dragStart`/`lastPan` to the remaining pointer.
- Conversion: `toPlotFraction` uses `svg.getScreenCTM().inverse()` applied via
  `createSVGPoint().matrixTransform(...)`, returns `null` outside the plot rectangle,
  and yields `fx = 0` at `PLOT_LEFT`, `fy = 0` at the plot bottom (grows upwards).

### `…/pareto-view/pareto-view.component.html`

- SVG gains `#chartSvg`, `(wheel)="onWheel($event)"`,
  `(pointerdown)="onPointerDown($event)"` and `[class.dragging]="dragging()"`.
- New control strip (design §6) before the SVG:
  `role="group" aria-label="Chart zoom"` with three `mat-icon-button`s —
  `zoom_in` (`atMaxZoom()` disabled), `zoom_out` (`atFullWindow()` disabled),
  `restart_alt` (`isDefaultView()` disabled) — each with `aria-label` and `title`.
- Point click changed from `select(...)` to `onPointClick(...)` (suppression only).
- **All existing chart DOM structure and class names are unchanged** (`.point`,
  `.point-marker--frontier`, `.point-marker--dominated`, `.point-cross`,
  `.frontier-line`, `.active-ring`, `.engine-star`, `.point-label`, `<title>`).

### `…/pareto-view/pareto-view.component.scss` (minimal)

Added only: `cursor: grab` + `touch-action: none` on `.pareto-chart`, a
`.pareto-chart.dragging { cursor: grabbing }` nested rule, and a small
`.chart-controls` flex strip. No other rule changed. Compiled size stayed under the
4.10 kB warning budget (see build result: no `pareto-view.component.scss` entry).

### New spec files

- `src/app/shared/utils/pareto-chart-zoom.utils.spec.ts` — 17 `it`s (pure functions).
- `src/app/features/main/orders/pareto-view/pareto-view-zoom.component.spec.ts` —
  8 `it`s (component interactions).

---

## Commands Executed

All from `GVRP_Entry_client_v2` unless noted.

| # | Command | Result / exit |
|---|---|---|
| 1 | `node_modules\.bin\tsc.cmd -p tsconfig.spec.json --noEmit` | exit 0 (one initial `NodeListOf` iteration error fixed by using `Array.from`) |
| 2 | `npm test -- --watch=false --browsers=ChromeHeadless` (to `pareto003-test.log`) | **245 executed, 240 SUCCESS, 5 FAILED**; exit 1 (5 pre-existing) |
| 3 | `npm run build` (to `pareto003-build.log`) | exit 1 (pre-existing `login.component.scss` ERROR); initial total **665.55 kB** |

Logs (gitignored temp):
`C:\Users\ACER\AppData\Local\Temp\opencode\pareto003-test.log`,
`C:\Users\ACER\AppData\Local\Temp\opencode\pareto003-build.log`.

---

## Verification Results

### Tests

- **245 executed, 240 SUCCESS, 5 FAILED** (baseline 220 / 215 / 5): **+25 executed,
  +25 SUCCESS, failure set unchanged**.
- The 5 failures are exactly the baseline set (names above); no new failure.
- The 25 new tests all pass: 17 utility + 8 component.

### Build

- Exit **1**, with exactly **one** ERROR — the pre-existing `login.component.scss`
  budget error (9.40 kB vs 8.19 kB).
- 11 WARNING lines: the `initial` budget warning (665.55 kB > 512 kB, < 1 MB), the
  `leaflet` CommonJS warning, and the **same 9** `anyComponentStyle` files as the
  baseline (`login`, `fleet-setup`, `sidebar`, `job-monitoring-dialog`,
  `edit-order-dialog`, `import-orders-dialog`, `orders-table`, `orders-section`,
  `route-view`). Programmatic set comparison against `pareto002-build-GATE.log` is
  empty. **No `pareto-view.component.scss` entry appeared; no new/worsened budget
  entry.**
- Initial bundle 665.55 kB vs baseline 665.41 kB → **+0.14 kB (0.02 %)**, WARNING
  only. AOT compilation succeeded (the build reached the budget-review stage).

### Scope integrity

- `git diff --name-only -- "*/src/**/*.spec.ts" "*/src/testing/*"` → **empty** (no
  existing spec/testing file modified).
- `git diff --name-only -- angular.json package.json package-lock.json
  proxy.conf.json` → **empty**.
- File mtimes corroborate this: the pre-existing `pareto-chart.utils.spec.ts`
  (10/18 19:17) and `pareto-view.component.spec.ts` (10/18 19:18) predate this
  session, while the two new spec files and the four edited sources are 10/19 00:01+;
  `angular.json` (7/26) and `package.json` (10/9) are untouched.
- Tree remains DIRTY; nothing committed or pushed.

---

## Artifacts Produced

- Edited: `shared/utils/pareto-chart.utils.ts`, `pareto-view.component.{ts,html,scss}`.
- New: `shared/utils/pareto-chart-zoom.utils.spec.ts`,
  `pareto-view/pareto-view-zoom.component.spec.ts`, this `implementation.md`.
- Test log: `C:\Users\ACER\AppData\Local\Temp\opencode\pareto003-test.log`.
- Build log: `C:\Users\ACER\AppData\Local\Temp\opencode\pareto003-build.log`.

---

## Observed Failures / Blockers

- No blocker. The design was implementable as written; in particular
  `SVGGraphicsElement.getScreenCTM()` **was available** in the Karma ChromeHeadless
  environment (the component specs convert real viewBox coordinates to client
  coordinates through it and pass), so the interaction is not only theoretically
  testable.
- One defect caught and fixed during implementation (worth reviewer attention): the
  template ref `#chart` shadowed the `chart()` computed, so the compiled template
  called the ref instead of the function (`TypeError: chart_r5 is not a function`),
  failing every `ParetoViewComponent` spec. Renamed the ref to `#chartSvg`.

---

## Deviations From Design

1. **Ticks are derived from the window, including at the default view.** Design §7.2
   requires `niceTicks(window.min, window.max)`, and the required component test
   ("wheel … changes the tick labels") can only hold if ticks depend on the window;
   PARETO-002 instead used `niceTicks(dataMin, dataMax)` while scaling by the padded
   domain. Consequence: **at the default (unzoomed) view the tick labels may differ
   from PARETO-002** (the scale/domain and therefore every plotted point position are
   unchanged). This is the one place where design §7.5 ("Reset restores the exact
   default geometry PARETO-002 produces today") is read as the plotted geometry, not
   the tick set. Flagged for the Reviewer to accept or reject.
2. **`preventDefault()` is called only when the pointer is over the plot and
   `deltaY !== 0`.** The task bullet ordered `preventDefault()` first, but PZU-004 and
   design §5/§7 ("`preventDefault` is used only there") require not swallowing page
   scroll outside the plot; the checks therefore run first. No functional difference
   over the plot.
3. **`fullWindow` (imported pure function) is imported under the alias
   `makeFullWindow`** in the component to avoid colliding with the `fullWindow`
   computed property required by design §2. Behaviour is unchanged.
4. **Trackpad pinch is handled via `ctrlKey`+wheel only** (same code path as wheel, per
   design §5); no separate `gesturestart`/`gesturechange` (Safari) path was added.

---

## Remaining Uncertainty (unverifiable by unit tests)

Honest list; the orchestrator plans a live browser pass.

- **Real wheel `preventDefault` / page-scroll suppression** cannot be proven by a unit
  test; the unit test only asserts `defaultPrevented` on a synthetic cancelable event.
- **`touch-action: none`** on the SVG (touch drag pans instead of scrolling the
  `.pareto-panel`; the panel still scrolls by touch outside the chart) — not exercised.
- **Real two-finger pinch** on a touch device/trackpad — only the two-pointer pointer
  sequence is unit-tested.
- **Cursor `grab`/`grabbing`** visuals and the control-strip icon rendering (ligatures
  `zoom_in`/`zoom_out`/`restart_alt` require the Material Icons font in the page).
- **Pan direction feel in a browser**: the pure `panWindow` sign convention is tested
  and matches design §3; the end-to-end gesture direction should be eyeballed live.
- **`getScreenCTM().a` as the uniform rendered scale** assumes `preserveAspectRatio`
  semantics with `width:100%; height:auto`; verified only through the headless layout
  available to Karma, not across real panel widths.
- The tick-label change at the default view (deviation 1) should be confirmed against
  PARETO-002 screenshots during the live pass.

---

## Modified Tests

**none.** No existing spec file or anything under `src/testing/` was edited, renamed,
deleted, disabled, focused, widened or excluded. The only spec changes are the two
**new** untracked files listed above. Evidence:
`git diff --name-only -- "*/src/**/*.spec.ts" "*/src/testing/*"` is empty; the
PARETO-002 spec files keep their pre-session mtimes.

---

## Security-Relevant Changes

**none.** No new endpoint, no `permitAll`, no branch-owned data query, no new
dependency, no credential/configuration change, no `localStorage`/`sessionStorage`.
The feature is a purely client-side view transform on already-loaded data.

---

## Handoff to Reviewer

Claim under review: "PARETO-003 implements PZU-001..PZU-006 in `GVRP_Entry_client_v2`
per `design.md`; the 25 new specs pass; the 5 remaining test failures are exactly the
pre-existing ones; the build introduces no new error and no new/worsened budget
warning; no existing spec or `src/testing/` file was modified; no forbidden file was
touched."

Independent checks:
- `git diff` of `pareto-chart.utils.ts` and `pareto-view.component.{ts,html,scss}`;
  highest signal: the `onPointClick`/`dragMoved` suppression, the document
  `pointermove/up/cancel` registration (no `setPointerCapture`), the `window()`/
  `fullWindow` computeds, the `effect` reset, and the `niceTicks(win.*)` change
  (deviation 1).
- Re-run `npm test -- --watch=false --browsers=ChromeHeadless` and `npm run build`
  (cwd `GVRP_Entry_client_v2`) and compare the failure names and the ERROR/WARNING
  sets against the baseline above.
- Decide on deviation 1 (default-view tick set) and whether the live browser pass must
  confirm wheel/pinch/cursor/`touch-action` before acceptance.
