# Context — PARETO-003

Static inspection of the working tree on 2026-10-18 (no execution), plus the live-run observations
already recorded in `work-items/PARETO-002/implementation.md`. Paths relative to the repository
root; `C` = `GVRP_Entry_client_v2/src/app`.

## 1. The chart as it exists (PARETO-002, uncommitted)

- Component: `C/features/main/orders/pareto-view/pareto-view.component.{ts,html,scss}`,
  standalone, `OnPush`, signal inputs `solution` and `frontier`.
- Geometry lives in the class: `VIEW_W = 560`, `VIEW_H = 300`, `PLOT_LEFT = 56`, `PLOT_RIGHT = 16`,
  `PLOT_TOP = 24`, `PLOT_BOTTOM = 40`, `PLOT_W = 488`, `PLOT_H = 236`
  (`pareto-view.component.ts:30-37`), and `readonly viewBox = '0 0 560 300'` (line 111).
- `chart()` (lines 165-205) computes the whole plot from the candidates on every change:
  `paddedDomain(costs)` / `paddedDomain(co2s)` (lines 372-384, ±8 % padding, with a fallback
  ±8 % window when `max === min`), then `scaleLinear(domain, [PLOT_LEFT, PLOT_LEFT + PLOT_W])`
  and an **inverted** y scale `[PLOT_TOP + PLOT_H, PLOT_TOP]`, then point positions, x/y ticks via
  `niceTicks(min, max)` and the frontier `path` via `frontierPath(...)`.
- `pareto-view.component.html:20-80`: one `<svg class="pareto-chart" [attr.viewBox]="viewBox">`
  with grid lines, axis lines, two axis titles (`Cost (VND)` at `y = plotBottom + 34`; `CO₂ (kg)`
  rotated at `x = 14, y = 150`), the `frontier-line` path, then one `<g class="point">` per
  candidate containing `.active-ring` (r = 11), `.point-marker--frontier` /
  `.point-marker--dominated` (r = 6), `.point-cross` for service-dominated, `.engine-star`
  (★ at `y - 12`), a per-point `<title>` label and, when selected, `.point-label`
  (`y + 20`). The `↙ better` hint sits at `(plotRight - 4, plotTop + 4)`.
- Point interaction today: `(click)="select(id)"` and `(keydown)="onPointKey($event, id)"`, with
  `role="button"` and `tabindex="0"` (lines 46-52).
- CSS: `.pareto-chart { width: 100%; height: auto; }` and `.chart-wrap { position: relative; }`
  (`pareto-view.component.scss:61-69`) → the SVG scales with the panel
  (`max-width: 640px`, `min-width: 380px`), **no `touch-action`, no cursor rules for dragging**.
- **There is no zoom, pan, wheel, pointer or transform handling anywhere in the chart**
  (verified by grep for `wheel|pan|zoom|pointer|touch|transform|viewBox` over the component and
  `C/shared/utils/pareto-chart.utils.ts`).

## 2. Pure helpers available for reuse (`C/shared/utils/pareto-chart.utils.ts`)

`niceTicks(min, max, count = 4)` (lines 21-34), `scaleLinear(domain, range)` (37-50),
`frontierPath(points)` (56-64), `compactVnd(value)` (67-75), `deltaPct(value, base)` (78-81).
They are DOM-free by design ("No Angular or DOM dependency so they are unit-testable in
isolation", lines 3-6) — the zoom window mathematics belongs in the same module (or a sibling
utility) to keep it testable without a browser.

## 3. Constraints discovered

- **Existing spec assertions that must keep passing unchanged** (`pareto-view.component.spec.ts`
  lines 110-114): 5 `.point` groups, 3 `.point-marker--frontier`, 1 `.point-marker--dominated`,
  1 `.point-cross`, 1 `.frontier-line`. So the DOM structure/class names of the chart must survive
  the change (AGENTS.md §14: existing specs may not be edited).
- **Component style budget**: `angular.json` budgets are `anyComponentStyle` warn 4.10 kB /
  error 8.19 kB. The build log of PARETO-002 (`pareto002-build-GATE.log`) lists 9 component-style
  warnings and the pre-existing `login.component.scss` error; **`pareto-view.component.scss` is not
  in that list**, so its compiled output is under 4.10 kB (source on disk is 5 401 B). Adding rules
  risks introducing a **new** budget warning → CSS must stay minimal and the build warning set must
  be compared before/after.
- **Precedent for pointer drag in this repo**: `C/shared/directives/resizable-divider.directive.ts`
  uses `@HostListener('pointerdown')` + `host.nativeElement.setPointerCapture?.(event.pointerId)` +
  `renderer.listen` for `pointermove`/`pointerup`/`pointercancel`, pushes the returned cleanup
  functions into `cleanupFns`, and releases them in `ngOnDestroy` (lines 55-94, 91-94). Any drag
  implementation must follow this cleanup pattern.
- **Test harness**: Karma + Jasmine, Chrome Headless; `zone.js` 0.14.10 is present, so listeners
  registered through `Renderer2.listen` outside Angular templates still trigger change detection.
- **Pointer→viewBox conversion**: the SVG has a fixed `viewBox` with the default
  `preserveAspectRatio` and `width: 100%`, so client coordinates are not viewBox units. The browser
  API `SVGGraphicsElement.getScreenCTM().inverse()` is the robust conversion; it also depends on
  real layout, so it cannot be exercised in a unit test — the arithmetic that consumes converted
  coordinates must therefore be pure and separately tested.
- **Page scroll**: `wheel` listeners attached to an element (not window/document/body) are
  non-passive by default, so `preventDefault()` works to stop page scrolling while the pointer is
  over the plot; `touch-action` must be set for touch drag/pinch.
- No chart library and no new npm dependency is allowed (PZU-006); `package.json` has no charting
  package.

## 4. Observations carried over from PARETO-002 that touch this work

- The live run confirmed the chart renders correctly with 5 points (4 frontier + 1 dominated) and
  with the degenerate single-frontier-point case, and that a first click can land before the tab
  body is constructed. Zoom controls will live inside the same tab body.
- `.pareto-panel` is a scrolling column (`overflow-y: auto`, `pareto-view.component.scss:1-6`);
  a drag handler that does not `preventDefault`/capture would fight that scroll container.
- PARETO-002 lacks the PlantUML artefact its design node requires; this run must produce its own
  `.puml` plus a rendered image (see `docs/workflows/business-development/nodes.md`, design row).

## 5. Unanswered decisions (for the design node)

- Interaction model: wheel/pinch zoom about the pointer **plus** drag pan **plus** Reset
  (assumed in `request.md`), versus an Expand/Fit toggle only.
- Maximum magnification (proposal: 8×), wheel step (proposal: constant ratio, e.g. 1.25× per notch),
  and whether a double-click should also reset.
- Whether a drag beyond a small threshold must suppress the point selection that would otherwise
  fire on `click`.
- Whether zoom state survives a frontier reload (proposal: no — reset to the full domain).
