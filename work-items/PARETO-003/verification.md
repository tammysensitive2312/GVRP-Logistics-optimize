# Verification — PARETO-003 (chart zoom / pan / Reset in the Pareto tab)

Work item: PARETO-003. Node: `verify` (revision 4). Base: branch `feature/pareto-002`,
HEAD `4e2f928`, working tree **DIRTY** (PARETO-002 + PARETO-003 uncommitted).
Specification: `work-items/PARETO-003/design.md`. Implementation record:
`work-items/PARETO-003/implementation.md` (sha256 `b5f73712…`, ledger evidence for rev 4).

Method: **independent re-execution + live browser exercise** — the implementation record was
not trusted as evidence. Every number below was produced in this session, not copied from
`implementation.md`. Scoped-file fingerprint of the verified tree
(`GVRP_Entry_client_v2/src`): `f1bb06021795dcd01caa86b6a96346852412e67680e8c0c193a7d53f89425dc1`.

Verdict: **PASS with documented limitations** (see §6, §7). No blocking mismatch found.

---

## 1. Environment

| Item | Value |
|---|---|
| Client build | `ng serve` watch build on `http://localhost:4200` (picks up the PARETO-003 edits) |
| API / Engine / DB | `localhost:8080` (Entry API), `localhost:8081` (Engine), `localhost:3306` (MySQL) — all reachable |
| Browser (live) | `bsk` 0.3.3 daemon pid 12144, instance `58215c1f`, Edge 155.0.0.0, viewport 924×753 |
| Live session | `bsk session start --name p003` → actual session id **`tiur`** (the `--name` value is a label only) |
| Node/Karma | Karma + Jasmine, ChromeHeadless 155, `zone.js` 0.14.10, Angular 18.2.14 (vite cache) |
| Logs (gitignored temp) | `…\Temp\opencode\pareto003-test-VERIFY.log`, `pareto003-build-VERIFY.log` |

---

## 2. Commands executed (independent re-run)

| # | Command (cwd `GVRP_Entry_client_v2`) | Result |
|---|---|---|
| 1 | `npm test -- --watch=false --browsers=ChromeHeadless` | **245 executed, 240 SUCCESS, 5 FAILED**, exit 1 |
| 2 | `npm run build` | exit 1 — single ERROR, pre-existing; **initial total 665.55 kB** |
| 3 | `git diff --name-only -- "*/src/**/*.spec.ts" "*/src/testing/*"` | **empty** |
| 4 | `git diff --name-only -- angular.json package.json package-lock.json proxy.conf.json` | **empty** |
| 5 | `git status --porcelain`, `git rev-parse HEAD` | dirty tree; `4e2f928`; branch `feature/pareto-002` |

**Test comparison.** The 5 failures are exactly the pre-existing baseline set
(`FleetSetupComponent keeps the vehicle when the confirmation is dismissed`,
`NavbarComponent navigates when a tab is clicked`, `CollectionCache starts a new request once
the previous one settled`, `CollectionCache records the error message and rethrows`,
`CollectionCache refetches on every load - there is no freshness window`). Baseline
PARETO-002 = 220 executed / 215 SUCCESS / 5 FAILED → delta **+25 executed, +25 SUCCESS, failure
set unchanged**, matching the +25 new specs claimed (17 utility + 8 component).

**Build comparison.** Programmatic `Compare-Object` of the `src/app/**/*.scss` files named in the
budget diagnostics against `pareto002-build-GATE.log`: **empty in both directions** (9 baseline
files, 9 now). `pareto-view.component.scss` does **not** appear → no new/worsened component-style
budget entry. The only ERROR remains the pre-existing
`login.component.scss` (9.40 kB vs 8.19 kB `anyComponentStyle`). Bundle grew 665.41 → 665.55 kB
(+0.14 kB, WARNING only). No new npm dependency (`package.json` untouched, check 4).

**Test-integrity note (AGENTS §14).** `pareto-view.component.spec.ts` and
`pareto-chart.utils.spec.ts` live inside the untracked PARETO-002 directory, so `git diff` has no
baseline for them. Available evidence that they were not edited: their mtimes predate this session
(`pareto-chart.utils.spec.ts` 10-18 19:17, `pareto-view.component.spec.ts` 10-18 19:18; all
PARETO-003 files 10-19 00:01–00:07). Callers should treat this as mtime-level evidence, not a
content-level proof.

---

## 3. Static inspection of the changed code

Read directly (the files are untracked/modified in an untracked tree, so `git diff` shows nothing).
Confirmed against `design.md`:

- `shared/utils/pareto-chart.utils.ts` (+151 lines, 253 total): `ChartWindow`,
  `MAX_CHART_ZOOM = 8`, `fullWindow`, `windowSpan`, `isFullWindow`, `zoomWindow`, `panWindow`,
  `clampWindow` + private `isFiniteWindow` / `clampFraction` / `clampSpan`. All pure, non-mutating,
  degenerate input → `full` instead of throwing; anchor invariance algebraically correct
  (`costMin = anchor − fx·nextSpan`). No DOM/Angular import.
- `pareto-view.component.ts` (734 lines): `zoom` signal (null = default), `fullWindow`/`window`/
  `atFullWindow`/`atMaxZoom`/`isDefaultView` computeds; `chart()` scales **and** ticks from
  `window()`; guarded `effect(…, {allowSignalWrites:true})` resetting zoom on frontier change
  (reads `frontier`, writes `zoom` only → cannot loop); `onWheel` returns before
  `preventDefault()` when the pointer is outside the plot; `onPointerDown` registers
  document `pointermove/pointerup/pointercancel` through `Renderer2.listen` with `cleanupFns`
  + `ngOnDestroy`/`endDrag` release, **no `setPointerCapture`**; `dragMoved` cleared on
  `pointerdown` and not on `pointerup` (so the trailing `click` is suppressed); `activePointers`
  map for pinch; `toPlotFraction` uses `getScreenCTM().inverse()` and returns `null` off-plot.
- `pareto-view.component.html`: `#chartSvg`, `(wheel)`, `(pointerdown)`, `[class.dragging]`,
  `role="group" aria-label="Chart zoom"` with three `mat-icon-button`s (`zoom_in` / `zoom_out` /
  `restart_alt`) each with `aria-label` + `title` and the correct `[disabled]` binding; point
  `(click)` now `onPointClick(...)`. All PARETO-002 chart class names preserved.
- `pareto-view.component.scss` (5594 B): adds only `cursor: grab`, `touch-action: none`,
  `&.dragging { cursor: grabbing }` and a small `.chart-controls` strip.
- New specs present: `pareto-chart-zoom.utils.spec.ts` (6156 B), `pareto-view-zoom.component.spec.ts` (10123 B).

---

## 4. Live browser verification (session `tiur`)

Login `hien` / branch `Branch Huge 1` → `/main`; deep link `http://localhost:4200/?solution=64`;
Pareto tab (`#mat-tab-label-0-3`, clicked twice — the lazy tab body swallows the first click).
Panel reported `5 scenarios · 4 on the frontier · recommended PARETO_K3_OF_4`, active plan
`PARETO_K0_OF_4`, i.e. the PARETO-002 job-85 chain: a dataset with 4 frontier points, 1 DOMINATED
point and no SERVICE_DOMINATED point.

### 4.1 Default view is unchanged in geometry (and matches the PARETO-002 capture)

| Candidate id | cx | cy |
|---|---|---|
| 1 | 510.344827586207 | 243.72413793103448 |
| 2 | 156.0835394031009 | 53.506240744658385 |
| 3 | 155.398807332663 | 62.992172625343784 |
| 4 | 89.65517241379314 | 40.27586206896552 |
| 5 | 152.52983409024742 | 55.75290882166283 |

These are numerically identical to the PARETO-002 live capture (e.g. K3 at `89.66 / 40.28`), so the
default plotting is bit-for-bit preserved. Y ticks are also identical (12.5 @ 212.02559763063252,
15.0 @ 149.6181510471758, 17.5 @ 87.21070446371911). Buttons: Zoom in enabled, Zoom out disabled,
Reset disabled. Computed `touch-action` on the SVG = `none`.

### 4.2 PZU-004 — wheel scope (scroll still works outside the plot)

Wheel (`bsk wheel`, native input) at the `.summary-strip` centre (638.7, 589.5),
`delta-y = +200`: the panel scroll container went `scrollTop 0 → 222.22` and the tick labels stayed
`2.00M / 2.50M` → outside the plot the wheel **scrolled** and did **not** zoom.

### 4.3 PZU-001 — wheel zoom, and scroll suppression over the plot

Wheel at the SVG centre (638.7, 656.75), `delta-y = −120` (native):

- x ticks `2.00M@287.20, 2.50M@521.34` → **`1.75M, 2.00M, 2.25M`**
- y ticks `12.5 / 15.0 / 17.5` → **`12.0 / 14.0 / 16.0 / 18.0`**
- point cx `510, 156, 155, 90, 153` → **`568, 125, 124, 42, 121`** (spread outwards about the centre)
- scroll container `scrollTop` **176 → 176** (unchanged) → `preventDefault()` fired only over the plot
- buttons → Zoom out + Reset become enabled

### 4.4 PZU-001/PZU-003 — button zoom to the 8× clamp

Nine real `Zoom in` clicks were needed from the 1.25× wheel state to reach the clamp, after which
`Zoom in` is `disabled`. The window at the clamp, derived from the rendered tick geometry
(`span = PLOT_W / pxPerUnit`, using two labelled ticks):

```
computed window span = 130265   expected (padded full span) / 8 = 1042120 / 8 = 130265
```

→ the clamp is exactly 8× of the PARETO-002 padded domain, numerically exact.

### 4.5 PZU-003 — Reset and zoom-out restore the default exactly

- `Reset zoom` from the 8× clamp → all five point coordinates, both x ticks and all three y ticks
  **identical to §4.1 to full float precision**; buttons back to
  `[Zoom in enabled, Zoom out disabled, Reset disabled]`.
- Two `Zoom in` then `Zoom out` until disabled → back to the full window with the first point at
  `510.344827586207` (baseline) and `Zoom out` disabled → the zoom-out path normalises to the
  default, not to an off-by-epsilon window.

### 4.6 PZU-002 — drag pan

Synthetic in-page `PointerEvent`s (see §7 limitation 1), drag +60 client px in two steps at a
1.5625× zoom:

| | x ticks (label@viewBox-x) | point cx |
|---|---|---|
| before | 1.80M@134, 2.00M@280, 2.20M@426 | 629, 75, 74, −29, 70 |
| after | 1.80M@78, 2.00M@225, 2.20M@371, **2.40M@517** | 573, 20, 19, −84, 14 |

All labels shifted left by ~56 px with the 146 px inter-tick spacing preserved → the window moved
towards higher cost and its **span is invariant** under pan (design invariant 1). CSS state:
`class="dragging"` is `true` on `pointerdown` and during the move, and `false` after `pointerup`.
(Methodology caveat: a synchronous DOM read inside the same JS turn reports `false`, because the
signal→DOM update needs a change-detection pass; measured with an awaited 80 ms gap instead.)

Clamp: after dragging +2000 px (in 300 px steps) the window stops at the right edge
(`2.00M@143, 2.20M@289, 2.40M@435`); repeating the identical drag changes **nothing**
(`stable: true`) → clamped and idempotent, no drift.

### 4.7 PZU-002/PZU-004 — drag suppresses the point click, plain click still selects

After a drag (down → −30 px → up) a `click` on a point selected **nothing** (`selected: 0`), while
a fresh down/up without movement followed by a `click` selected the point (`selected: 1`,
`data-candidate-id = 1`) → the PARETO-002 selection path is intact and the pan does not steal it.

### 4.8 PZU-005 — correctness invariants while zoomed (1.953×)

- The active ring stays on its own point: `active-ring` belongs to the group with
  `data-candidate-id = 1`, and its `cx/cy` equal that group's marker `cx/cy` exactly.
- `.frontier-line` still has **4 vertices** = 4 `.point-marker--frontier` markers; `1` `.engine-star`,
  `1` `.point-marker--dominated`, `0` `.point-cross` → the PARETO-002 DOM structure is preserved
  under zoom (and matches what a 4-frontier / 1-dominated dataset should show).
- Selecting a point while zoomed works (see §4.7).
- At a strong zoom all candidates can lie outside the window; they are then positioned by the same
  scale and clipped by the SVG viewport (observed `cx` values far negative), which is what design
  §7 invariant 1 prescribes. Note the plot has no `clip-path`, so a candidate only slightly outside
  the window would still be drawn inside the axis margin — not observed live (every observed
  out-of-window point fell outside the viewBox), recorded as a residual cosmetic observation only.

### 4.9 Console

`bsk console` over the whole session: **no error or exception entry**. Only
`[vite] connecting…/connected`, `Angular is running in development mode`, the Chromium
`autocomplete` hint, `✅ Login successful`, `0 orders selected` (×2) and
`Data loaded. Stats calculated` — none related to zoom/pan.

---

## 5. Requirement coverage

| Req | Verified by | Status |
|---|---|---|
| PZU-001 zoom | §4.3 (native wheel), §4.4 (buttons, exact 8× clamp), unit specs | **VERIFIED** |
| PZU-002 pan | §4.6 (direction, span invariance, clamp, `dragging` class), §4.7, unit specs | **VERIFIED_WITH_LIMITATION** (§7-1: drag injected in-page, not OS-level) |
| PZU-003 reset | §4.5 (bit-exact restore, disabled states), §4.2/§4.4 button states | **VERIFIED** |
| PZU-004 scope/a11y | §4.2 (scroll outside the plot), §4.3 (no scroll over the plot), static check of `aria-label`/`title`/`[disabled]`/`touch-action` | **PARTIALLY_VERIFIED** (§7-3: live keyboard Tab/Enter and touch drag/pinch not exercised) |
| PZU-005 while zoomed | §4.8 (ring attachment, frontier path, marker counts, selection), §4.1 (default geometry preserved) | **VERIFIED_WITH_LIMITATION** (§7-2: Apply-enable with a *frontier* point selected while zoomed not re-run live) |
| PZU-006 tests/build discipline | §2 (independent re-run, identical failure names and budget set), §2 checks 3–4 | **VERIFIED** |

---

## 6. Deviations from design: disposition

1. **Ticks derived from the padded window, including at the default view** (implementation
   deviation 1) — **CONFIRMED live, and narrower than it sounds.** At the default view the x axis
   now shows **`2.00M / 2.50M` (2 ticks)** instead of PARETO-002's `1.75M / 2.00M / 2.25M`
   (3 ticks); the y axis is unchanged (`12.5 / 15.0 / 17.5`), and **all five point coordinates are
   identical to PARETO-002**. So design §7.5 "Reset restores the exact default geometry" holds for
   the plotted geometry, but not for the x tick label set. This is the only user-visible change to
   the pre-existing view. It is *required* by design §7 invariant 2 (labels must come from
   `niceTicks(window.*)` so that the required component test "wheel … changes the tick labels" can
   hold). Disposition: **accept as a documented deviation**; a denser default axis is available as a
   one-line follow-up (raise the tick count for the window) if the user prefers the old density.
2. **`preventDefault()` after the scope checks, not first** (deviation 2) — confirmed correct by
   §4.2/§4.3: outside the plot the page still scrolls; over the plot it does not. Accepted.
3. **`fullWindow` pure function imported as `makeFullWindow`** (deviation 3) — cosmetic, no
   behavioural effect. Accepted.
4. **Pinch limited to `ctrlKey`+wheel** (deviation 4) — no separate Safari gesture path; the
   two-pointer pointer path exists in code and in unit tests. Accepted as scoped.

No unrecorded deviation was found between `implementation.md` and the actual code.

---

## 7. Limitations / not verified

1. **Pan was injected as in-page synthetic `PointerEvent`s** (`Runtime.evaluate`), not OS-level
   input: the browser's own mouse capture/hit-testing is therefore not part of this evidence. The
   handler path, the maths and the DOM effects are real; the ingest layer is not. The native-input
   paths verified are the wheel (§4.3) and all button clicks (§4.4, §4.5) — those used `bsk`'s
   native input.
2. **Not re-verified live in this run:** selecting a **frontier** point while zoomed and observing
   `Apply this plan` become enabled. The session's last attempt was interrupted by a DevTools-level
   error (`bsk click … browser rejected the underlying CDP call — DOM Error while querying`), which
   is a tooling/browser-session failure, not an application error; the page stayed consistent
   (selection remained on the DOMINATED id 1, where `Apply` is correctly disabled — as PARETO-002
   specified). Mitigation: the same path was verified live in PARETO-002, and PARETO-003 does not
   touch `select()`, `selected()` or `canApply()` (static trace). Test instructions for the user are
   in `handoff.md`.
3. **Touch behaviour not exercised:** real two-finger pinch, and touch drag panning instead of
   scrolling the `.pareto-panel` under `touch-action: none`. Unit-tested only (two-pointer pointer
   sequence). Live keyboard focus/Enter activation of the points was also not re-run this session
   (unchanged from PARETO-002 and unit-covered).
4. **`getScreenCTM().a` as the uniform rendered scale** is exercised live at one panel width
   (924×753 viewport) only; other widths/zoom levels are not covered.
5. The working tree is **DIRTY and uncommitted**: the fingerprint `f1bb0602…` describes the current
   tree, and no commit alone reproduces it.

---

## 8. Cross-run integrity consequence (recorded, needs a decision)

`PARETO-002` is parked at `await_user_test` with `handoff = a67a2637…`. PARETO-003 edits files in
the same run scope (`GVRP_Entry_client_v2/src`), so the current fingerprint is
`f1bb0602…` ≠ `a67a2637…`. The workflow graph marks `await_user_test.accepted` as `fresh: true`,
and `workflow.mjs` enforces `run.handoff === current` for a fresh edge → **accepting PARETO-002 as
it stands will be rejected with "Handoff changed; use changed event and reverify"**. The honest
routes are (a) `changed` → re-verify PARETO-002 at the current revision (its features were partly
re-exercised live today: chart, ring, star, selection, Apply disabled for DOMINATED) and then accept,
or (b) accept PARETO-002 only for the revision it was verified at, which the tool does not allow.
This is a coordination fact, not a PARETO-003 defect; it is surfaced to the user rather than fixed
silently.

---

## 9. Verdict

The claim "PARETO-003 implements PZU-001..PZU-006 per `design.md`, with no regression in the
PARETO-002 chart and no change to existing tests, build budgets or dependencies" is **supported** by
independent execution and live browser evidence, with the limitations in §7 and the deviation in
§6-1 recorded. Promotion scope must not exceed: *zoom/pan/reset work as specified in a real browser
at the tested viewport, the unit suite and build budgets are unchanged apart from the +25 new
specs and +0.14 kB bundle, and the default x tick labels differ from PARETO-002 as described.*
