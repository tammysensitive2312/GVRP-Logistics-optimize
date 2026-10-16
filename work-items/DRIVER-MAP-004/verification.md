# Verification — DRIVER-MAP-004

Environment: Windows; Node v22 + Chrome Headless 154; live stack
(:4200 client, :8080 Entry API); `bsk` 0.3.2 + Edge agent window.

## Checks (all re-run by driver)

- Client `tsc` exit 0.
- Karma full suite: 180 SUCCESS + same 5 pre-existing failures
  (FleetSetup, Navbar, 3× CollectionCache — names re-checked, none
  driver-related). 3 driver specs new/updated for this change.
- Live `bsk` as driver (cuong), route 30H-108480.LG (9 stops, 82 steps):
  collapsed cards + both toggles expand/collapse + internal scroll.

## Requirement coverage

- DM4-001/002: VERIFIED live + specs.
- DM4-003: VERIFIED (no backend/routing/follow diff; prior driver specs pass).
- DM4-004: VERIFIED (suites + live check above).

## Not executed / unverified

- Real-device GPS follow drive (same honest limitation as DRIVER-MAP-003);
  disclosure UI itself is fully verified in automation + desktop browser.
