# Design — DRIVER-MAP-004

All in `driver-view.component.{ts,html,scss}` (+ specs). No service/backend change.

## Behavior

- Guidance: header row (title + "All N steps"/"Hide steps" toggle) → always-on
  next-step card (`NEXT`, instruction, distance, "Step 1 of N") → full `<ol>`
  only when open, scrolling internally (`max-height: 240px`).
- Stops: header row ("Stops (M)" + "Show all"/"Hide all") → always-on next-stop
  card (same stop markup via shared `ng-template`, incl. Navigate link) →
  full `<ol>` only when open.
- Both toggles reset on open/back; follow-mode, map, GPS lifecycle untouched.

## Failure cases

- No legs (routing fail): guidance section absent, as today; stops unaffected.
- No stops: next-stop card absent; toggles still safe (empty list).
- Map/GPS errors: unchanged paths (display-only aid never breaks the view).

## Verification

- Specs: next-step card + toggle reveal; stop preview + toggle reveal;
  disclosure reset on back; all prior driver specs updated where the
  collapsed default changed them.
- Full suite + tsc; live `bsk` check as driver (cuong) on route 30H-108480.LG:
  collapsed cards, both toggles, scroll-bound list.
