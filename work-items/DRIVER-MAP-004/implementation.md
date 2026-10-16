# Implementation — DRIVER-MAP-004

Parent: DRIVER-MAP-003 (done). Frontend only (`GVRP_Entry_client_v2`).

- `driver-view.component.ts`: `guidanceOpen`/`stopsOpen` signals (default
  false; reset in `openSend`/`backToList`); `nextStop` computed (first
  non-DEPOT stop, else first, else null); `toggleGuidance()`/`toggleStops()`.
- `driver-view.component.html`: side column restructured — shared `#stopItem`
  `ng-template` (single markup source for preview + list rows); guidance
  header + next-step card + conditional full `<ol class="driver-maneuvers">`;
  stops header + next-stop card + conditional full `<ol class="driver-stops">`.
- `driver-view.component.scss`: `.guide-head/.stops-head`, `.btn-toggle`,
  `.guide-current`, `.next-stop`, `.guide-scroll` (max-height 240px, internal scroll).
- Specs: replaced 'lists maneuver guidance when legs resolve' with
  'shows the next step up front and the full list on toggle'; added stop
  preview/toggle and disclosure-reset specs. No backend change (DM4-003).

## Verification

- `tsc` exit 0.
- Karma: 180 SUCCESS + same 5 pre-existing failures (FleetSetup, Navbar,
  3× CollectionCache — re-checked by name, none driver-related).
- Live `bsk` as driver (cuong), route 30H-108480.LG: collapsed view shows
  "NEXT Head out (146 m) / Step 1 of 82" + "All 82 steps", and "NEXT STOP
  ORD-1163" + "Show all"; both toggles expand/collapse; expanded step list
  scrolls internally; table/map/follow untouched.
