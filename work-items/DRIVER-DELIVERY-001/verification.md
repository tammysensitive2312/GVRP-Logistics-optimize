# Verification — DRIVER-DELIVERY-001

Environment: Windows; JDK 21 Maven offline; Node v22 + Chrome Headless 154.

## Checks (all re-run by driver, suites unless noted)

- Entry `compile` SUCCESS; `RouteSendServiceTest` 8/8 + `RouteSendControllerTest` 6/6 → 14/14.
- Client `tsc --noEmit -p tsconfig.spec.json` exit 0.
- Client full Karma: 140 SUCCESS + same 5 pre-existing failures (FleetSetup, Navbar, 3× CollectionCache) — 13 new specs all green (dialog 4, driver view 4, guard 3, route-view dispatch 2).
- Client `ng build`: same ERROR file (`login.component.scss`, pre-existing) and WARNING set as the ROUTE-CUSTOM baseline; new driver/send-route styles under budget (not flagged).
- Real defect caught by tests and fixed: `NG0600` signal write inside effect with synchronous sources → decoupled via `queueMicrotask`; 27/27 route-view green after.

## Requirement coverage

- DD-001..DD-007, DD-020..023, DD-030..032: VERIFIED (component scope).
- DD-040..043: suites green; live walkthrough S-1..S-4 + 403 probes pending user test on redeployed stack (Entry migration + code).

## Not executed / unverified

- Live run (needs user redeploy); concurrent-send 409 and cross-driver 403 probes live (unit-covered only).
- Driver login on a real phone browser (responsive layout by inspection).

## Repair verification (2026-10-08, after user-reported 500)

- Root cause confirmed from backend log: camelCase `driverUserId` vs SNAKE_CASE contract.
- Fix verified: tsc 0; Entry 14/14 (controller spec now pins snake_case wire body); full client suite 140 + same 5 pre-existing (dialog/dispatch specs updated to snake keys, all green).
- Lesson recorded: standalone-MockMvc specs must mirror production Jackson config for request DTOs; added to this run's practice (as done for the earlier template-output lesson).

## Repair 2 verification (2026-10-08, driver landing)

- `login.component.spec.ts` 3/3 (driver → `/driver`, planner unchanged, driver return-URL honoured); full client suite 143 + same 5 pre-existing; tsc 0.
- Backend login needs no change (no role restriction; disabled users blocked via `UserDetails.isEnabled`; JWT already carries role) — verified by inspection.

## Findings routing

No open defects in scope → route `pass` to `await_user_test`.

No open defects in scope → route `pass` to `await_user_test`.
