# Verification — BULK-EDIT-001

Environment: Windows; JDK 21 Maven offline; Node v22 + Chrome Headless 154.

## Checks (all re-run by driver)

- Entry `compile` SUCCESS; `OrderServiceTest` 4/4 + `OrderBulkControllerTest` 5/5 → 9/9 (filter forwarding, flip+report incl. foreign/missing, real lenient partition, empty report, 400s, partitioned 200 with SNAKE_CASE mapper).
- Client `tsc` exit 0; dialog 3/3 + section 4/4 green.
- Client full Karma: 172 SUCCESS + same 5 pre-existing failures (verified identical: FleetSetup, Navbar, 3× CollectionCache).
- Client `ng build`: only pre-existing login ERROR; no new ERROR/WARNING files (bulk dialog styles under budget).

## Requirement coverage

- BE-001/002/010/011/012/013/014/020/021: VERIFIED (component scope).
- BE-003: VERIFIED (component scope) — live S-3 filter proof pending redeploy.
- BE-030..033: suites green; live S-1..S-3 manual pending user test.

## Not executed / unverified

- Live run (needs user redeploy of Entry + frontend); seeded-branch manual S-1..S-3.

## Repair verification (2026-10-11, cleared-date bug)

- tsc 0; filters 3/3 (omit-on-clear, keep-when-set) + section 5/5 (incl. merge-drops-cleared) + dialog 3/3 green.
- Backend untouched by this repair (missing-date path already covered by design).

## Repair 2 verification — live browser (2026-10-11, as hien)

- Set date picker → request carries `date=2026-10-11`; clear it → next request has NO date param; table renders. Root cause was the merge, proven fixed on the wire.

## Findings routing

No open defects in scope → route `pass` to `await_user_test`.

## Repair 4 verification — live browser via `bsk` (2026-10-11, Branch Huge 1)

- Old UI confirmed broken at scale: 100 SCHEDULED → COMPLETED on a 100/page view rendered 100 identical inline refusal lines ("0 updated100 refused:", missing space), pushing filters/table down.
- New UI verified on the same scenario: banner "0 updated · 100 refused · 1 unique reason" + View details/Hide details + Dismiss; expanded group shows one pattern row with count (100) and expandable order codes; table stays in place; selection cleared after bulk.
- Unit: `tsc` 0; Karma 178 SUCCESS + same 5 pre-existing failures (FleetSetup, Navbar, 3× CollectionCache — names re-checked, none from OrdersSection); 3 new bulk-group specs green.
