# Traceability — PARETO-003

Status column updated at the verify node.

| Req | Design | Code (to implement) | Test | Status |
|---|---|---|---|---|
| PZU-001 zoom | §1 window model, §3 `zoomWindow`, §5 wheel/pinch | `shared/utils/pareto-chart.utils.ts`, `pareto-view.component.{ts,html}` | Z-T1, Z-T2 (utils), Z-T6..Z-T8 (component) | VERIFIED — `verification.md` §4.3 (native wheel), §4.4 (8× clamp exact: 130265 = 1042120/8) |
| PZU-002 pan | §3 `panWindow`/`clampWindow`, §4 no pointer capture, §5 drag | `pareto-view.component.ts` (document pointermove/up) | Z-T3, Z-T4 (utils), Z-T9 (component) | VERIFIED_WITH_LIMITATION — `verification.md` §4.6/§4.7 (direction, span invariance, idempotent clamp, `dragging` class, click suppressed); drag injected in-page, not OS-level (§7-1) |
| PZU-003 reset | §2 `isDefaultView`, §6 controls | `pareto-view.component.{ts,html}` | Z-T8 (component) | VERIFIED — `verification.md` §4.5 (bit-exact restore, disabled states; also via zoom-out) |
| PZU-004 keyboard/a11y | §5 suppress-click, §6 group + labels, §4 `touch-action` on the SVG only | `pareto-view.component.{ts,html,scss}` | Z-T9, Z-T10 (component) | PARTIALLY_VERIFIED — `verification.md` §4.2/§4.3 (scroll outside the plot works, none over it), static `aria-label`/`title`/`touch-action`; live keyboard and touch not exercised (§7-3) |
| PZU-005 correctness while zoomed | §7 invariants | `pareto-view.component.ts` `chart()` fed by the window | Z-T5 (utils), Z-T7, existing chart assertions kept | VERIFIED_WITH_LIMITATION — `verification.md` §4.8 (ring attachment, frontier path, marker counts, selection) + §4.1 (default geometry preserved); Apply-enable while zoomed not re-run live (§7-2) |
| PZU-006 tests/build discipline | §8 | no new dependency; 2 new spec files only | full Karma run + `npm run build` warning/error set comparison | VERIFIED — `verification.md` §2 (245/240/5 with identical failure names; 9-file budget warning set identical; forbidden files untouched) |

Base under test: PARETO-002 working tree on branch `feature/pareto-002`, HEAD `4e2f928`
(uncommitted). Existing PARETO-002 chart assertions must keep passing unchanged.

Verification evidence: `work-items/PARETO-003/verification.md` (independent re-run + live `bsk`
session `tiur`). Verified revision fingerprint: `f1bb06021795dcd01caa86b6a96346852412e67680e8c0c193a7d53f89425dc1`.
