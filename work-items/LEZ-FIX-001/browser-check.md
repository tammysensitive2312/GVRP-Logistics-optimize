# Browser check — LEZ-FIX-001

Run by Claude Code on 2026-10-31 (+07:00), on the user's request ("Test in browser first"), in the Claude desktop app's built-in browser. Kept separate from `implementation.md` so that the revision 4 evidence keeps its hash.

## Environment

- Branch `fix/lez-fix-001`, working tree as handed off at revision 4 (uncommitted).
- MySQL: Docker container `sembackend-db-1` (`mysql:8.0`, port 3306). Claude started Docker Desktop and the container, which were stopped.
- Entry API: `mvnw.cmd spring-boot:run` (JDK 21), started on 8080. Engine API not started (not needed).
- Client: `npm start` (`ng serve` with `proxy.conf.json`) on 4200, through the new local `.claude/launch.json`.
- The user signed in. Claude entered no credentials.
- Zone used: "Test zone 2" (COPIED), opened from Admin → Low-emission zones → Edit. The dialog was closed with **Cancel**. Nothing was saved. The network log shows only GET requests and no write to `/api/v1/zones`.

## Results

| Case | Steps | Observed | Verdict |
|---|---|---|---|
| UL-M4 (LF-R1) | Draw → double-click the map | One draft point appeared and the map did not zoom | PASS |
| UL-M4 (LF-R1) | After Finish (out of draw mode) → double-click the map | The map zoomed in | PASS |
| UL-M2 bow tie (LF-R2) | Draw → corners clicked 1-3-2-4 | The dashed lines crossed, Finish was disabled and the control showed "Edges cross — undo points or cancel and redraw." in red | PASS |
| UL-M2 recover (LF-R2) | Undo point (4 points left, the closing edge still crossed) → Undo point (triangle) | After the first undo the message stayed and Finish stayed disabled, which is correct. After the second, the message was hidden and Finish was enabled | PASS |
| UL-M2 normal (LF-R2) | Finish on the triangle | The geometry text became the drawn `Polygon` and the outline was drawn in red. The draft cleared | PASS |
| UL-M7 (LF-R3) | Rule 1: choose "Trucks over 3.5 t, peak hours" | TRUCK was ticked and the select showed "Choose a preset…" again | PASS |
| UL-M7 (LF-R3) | Untick TRUCK, tick CAR, choose the same preset again | CAR was unticked and TRUCK ticked again. The select was reset | PASS |
| LF-R4 | Look under the preset select | "Only electric vehicles: vehicles with no energy type recorded are banned too." was shown | PASS |

The preset selection was driven with the browser tool's `form_input`, which sets the value and dispatches `change`. It was not done with a mouse on the native dropdown.

## Observations (not defects of this design)

1. **Undo after a double-click.** A double-click adds two identical draft points. `polygonFromPoints` drops the duplicate, but `Undo point` removes one at a time, so the first undo seems to do nothing. This is the behaviour from before this fix (listed in `implementation.md` § Remaining Uncertainty). A possible follow-up is to ignore a click on the same position as the last point.
2. **Control width shift.** While the message is shown, the draw control becomes wider and its buttons move left (Undo point moved about 270 px). On a narrow map this could cover the zoom buttons. A possible follow-up is to show the message on its own line below the buttons.
3. Console: two `404 Not Found` errors, for `media/marker-icon-2x.png` and `media/marker-shadow.png` (Leaflet default marker assets, which come from the depot markers). Unrelated to this change.

## Left running

Docker Desktop, the `sembackend-db-1` container, the Entry API (8080) and `ng serve` (4200) are still running.
