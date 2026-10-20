# Handoff — PARETO-003 (chart zoom / pan / Reset in the Pareto tab)

State: `await_user_test` (revision 5). Verified revision fingerprint
(`GVRP_Entry_client_v2/src`): **`f1bb06021795dcd01caa86b6a96346852412e67680e8c0c193a7d53f89425dc1`**.
Evidence: `work-items/PARETO-003/verification.md` (this node), `implementation.md` (rev 4),
`design.md` + `traceability.md`, diagrams `diagrams/pareto-zoom-{component,sequence}.puml` + `.svg`.

## What this is

Client-only feature. The Pareto trade-off chart (PARETO-002) gains **wheel zoom**, **drag pan**,
**two-finger pinch zoom** and **Zoom in / Zoom out / Reset** buttons. The chart zooms its *data
window*, not the SVG picture: the viewBox, plot rectangle, marker sizes and DOM class names are
unchanged. No backend change, no new npm dependency, no chart library.

## Exact version under test

| | |
|---|---|
| Branch / HEAD | `feature/pareto-002` @ `4e2f928` (base = `master`, already contains PARETO-001) |
| Tree state | **DIRTY, nothing committed, nothing pushed** — PARETO-002 and PARETO-003 are both uncommitted |
| Reproducibility caveat | `4e2f928` alone does **not** reproduce this result; only the current working tree does |

Changed / added files (sha256, sizes as verified):

| File | sha256 | bytes |
|---|---|---|
| `GVRP_Entry_client_v2/src/app/shared/utils/pareto-chart.utils.ts` | `2d4e0759da9bfd4d619f210ce11f916715a17ed26e6548336278a5e95853b3f8` | 8675 |
| `GVRP_Entry_client_v2/src/app/features/main/orders/pareto-view/pareto-view.component.ts` | `ed7a4c0c4642068a01176e0a979e0c266b5950e096c6e3850e29d466b3727b21` | 23928 |
| `GVRP_Entry_client_v2/src/app/features/main/orders/pareto-view/pareto-view.component.html` | `7d2782485b0b624e4670bafc9b68e5a625467bc72046b61b36dd6bd677f80e0d` | 9056 |
| `GVRP_Entry_client_v2/src/app/features/main/orders/pareto-view/pareto-view.component.scss` | `9b3d3e65657a478301890ac325e71bbb7628266d3d9eb8fc112ebc9b3db564a2` | 5594 |
| `GVRP_Entry_client_v2/src/app/shared/utils/pareto-chart-zoom.utils.spec.ts` (new) | `fbee3a1777187fb491a19efab82c24b7ab97bd17c761a67a37b23915c8e7d6bd` | 6156 |
| `GVRP_Entry_client_v2/src/app/features/main/orders/pareto-view/pareto-view-zoom.component.spec.ts` (new) | `7e758e3e376fad0b37ac5e76c99166abdd10bbb702207d094bc1432a6305f1b2` | 10123 |

Untouched (verified): `angular.json`, `package.json`, `package-lock.json`, `proxy.conf.json`, and
every pre-existing `*.spec.ts` / `src/testing/*` file.

## How to test (manual, ~4 minutes)

1. Start the stack: Entry API `:8080`, Engine `:8081`, MySQL `:3306`, and the client
   (`cd GVRP_Entry_client_v2; npm start` → `http://localhost:4200`).
2. Log in (dev DB): branch `Branch Huge 1`, user `hien` / `hien`.
3. Open a planning result with several scenarios, e.g. `http://localhost:4200/?solution=64`
   (job-85 chain: 5 scenarios, 4 on the frontier, recommended K3), and open the **Pareto** tab
   (click the tab label twice — the first click only builds the lazy tab body).
4. **Zoom.** Put the cursor over the plot and use the wheel (or the Zoom in button). Expect: the
   points spread around the cursor, the axis ticks/labels change with the window, and the panel
   does **not** scroll while the cursor is over the plot.
5. **Pan.** Drag inside the plot (mouse down, move, release). Expect: the view slides towards the
   higher-cost side when dragging right; the tick spacing (= window span) does not change; the view
   stops at the data edges and does not run away; the cursor is `grab` / `grabbing`.
6. **Click after drag.** After a drag, release over a point: nothing should be selected. Then click
   a point without moving: it is selected and the detail panel updates.
7. **Limits and Reset.** Zoom in until the Zoom in button greys out (8× maximum). Press Reset:
   the chart returns to the default view and the Zoom out / Reset buttons grey out. Zoom out until
   the button greys out: same default view.
8. **Scroll check.** With the cursor *outside* the chart (e.g. on the summary line), the wheel
   scrolls the panel normally.
9. **Touch (optional):** on a touch device/trackpad, one finger drags/pans the plot, two fingers
   pinch-zoom; the panel still scrolls when starting the gesture outside the chart.

Expected at step 4: for the job-85 chain at the default view the x axis reads `2.00M / 2.50M`
(see limitations) and, after one wheel notch, `1.75M / 2.00M / 2.25M` with the CO₂ axis moving from
`12.5 / 15.0 / 17.5` to `12.0 / 14.0 / 16.0 / 18.0`.

## Known limitations (do not over-read the evidence)

1. **Default x tick labels changed** versus PARETO-002: `2.00M / 2.50M` instead of
   `1.75M / 2.00M / 2.25M`. Point positions and all y ticks are identical; only the x tick set
   differs. This follows from design §7 invariant 2. A denser axis is a small optional follow-up.
2. Pan was verified with in-page pointer events, not OS-level mouse input; the wheel and the three
   buttons were verified with native input.
3. Not exercised live: two-finger pinch on real touch hardware, touch drag vs panel scroll, live
   keyboard Tab/Enter on a point, and the moment when a **frontier** point is selected *while
   zoomed* (the run was interrupted by a browser DevTools error, not an app error; the same flow was
   verified live in PARETO-002 and is covered by unit tests).
4. Verified at one viewport (924×753) and one API/DB state only.
5. The tree is uncommitted and dirty; the fingerprint above describes the current tree only.
6. **Separate, pre-existing coordination issue:** because PARETO-003 edits files inside PARETO-002's
   run scope, PARETO-002's recorded handoff (`a67a2637…`) no longer matches the tree
   (`f1bb0602…`). Accepting PARETO-002 first requires a `changed` → re-verify → pass cycle; see
   `verification.md` §8.

## Not changed / not in scope

No backend, no persistence, no API contract, no security surface (no new endpoint, `permitAll`,
branch-owned query, dependency, secret or storage). Optimization (performance work) is explicitly
**not** started; this node waits for the user's own test.
