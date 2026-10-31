# Implementation (rework) — LEZ-FIX-001

Engineer: Claude Code, 2026-10-31, Windows host. Branch `fix/lez-fix-001`, uncommitted. Reason: the user asked to fix two observations from `browser-check.md` before committing ("Fix the 2 UX notes first"). `implementation.md` is left unchanged (evidence of revision 4).

## Findings Addressed

| Observation (`browser-check.md`) | Fix | Evidence |
|---|---|---|
| 1. A double-click adds two identical draft points, so the first `Undo point` seems to do nothing | `ZoneMapComponent.addPoint` ignores a point equal to the previous draft point (exact `lat`/`lng` match, which is what a double-click sends). The same spot clicked again later, not straight after, is still added | LF-C6 (2 specs); browser: double-click, then one Undo, leaves an empty draft |
| 2. The crossing message widens the control and moves the buttons | The buttons are in their own flex row (`div.zone-draw-buttons`). The message is a block on its own line under them, with `width: 0; min-width: 100%`, so it wraps to the row's width and does not add to the control's width | LF-C7; browser: the buttons stayed in place and the message wrapped onto two lines under them |

Both changes are in `zone-map/zone-map.component.ts` (an allowed file). There is still no new `@Input`/`@Output`. The control's `button`s are still exactly Draw, Undo point, Finish, Cancel; they are now one level deeper (inside the row), and UL-T4 queries `querySelectorAll('button')` on the control, so it is not affected.

## Commands Executed

| Step | Command | Exit / result |
|---|---|---|
| 0 | `workflow.mjs step … code_failed 05-proof-code-failed.json` (findings = `browser-check.md`) | 0; `implement` rev 5 |
| 1 | Python edit of `zone-map.component.ts` (UTF-8, LF kept) | `ok` |
| 2 | `npx ng test --watch=false --browsers=ChromeHeadless` | 0; **451 SUCCESS** (448 + 3) |
| 2 | `rm -rf dist && npm run build` | 0; 0 ERROR; 12 warnings, sorted text identical to the baseline |
| 3 | Browser recheck (same environment as `browser-check.md`) | see below |
| 4 | `git status --short` | allowed files, plus `.claude/` (local `launch.json` for `ng serve`; allowed uncommitted by the task) |

## Browser Recheck

Zone "Test zone 2" (COPIED), dialog closed with Cancel. Nothing was saved, and the network log has no request to `/api/v1/zones/…`.

| Case | Observed | Verdict |
|---|---|---|
| Draw → double-click → one `Undo point` | One point appeared and one Undo removed it | PASS |
| Bow tie (1-3-2-4) | Draw / Undo point / Finish / Cancel stayed at the same place. "Edges cross — undo points or cancel and redraw." showed in red on two lines under the buttons, and Finish was disabled | PASS |

While driving the browser, the dialog's content scrolled by about 27 px after the first click on the map. This made one coordinate click land on the map instead of on Undo. That click was a tool-side miss, and the stray points were cancelled. Not an application defect, and not related to this change.

## Tests Added

`zones/zone-map/zone-map-draw-ux.component.spec.ts`:
- `LF-C6: the same point clicked twice in a row is kept once; one Undo removes it`
- `LF-C6: returning to an earlier, non-consecutive point is still added`
- `LF-C7: the crossing message sits under the buttons and does not widen the control`

## Modified Tests

None. No existing spec, stub or helper was edited.

## Security-Relevant Changes

None (client layout and input handling only).

## Deviations From Design

These two changes are additions requested by the user after the browser check. They are not in `design.md`. LF-R1…LF-R4 behaviour is unchanged.

## Remaining Uncertainty

- LF-C7 relies on Karma's real layout. The browser recheck confirms it at a dialog width of about 550 px, but narrower phone widths were not checked.
- Two quick clicks that are a few pixels apart (not a double-click) still add two points, which is intended.

## Handoff to Reviewer

Re-run `npx ng test --watch=false --browsers=ChromeHeadless` (451) and `npm run build` (12 warnings) in `GVRP_Entry_client_v2`. Read `addPoint` and `buildDrawControl` in `zone-map.component.ts`.
