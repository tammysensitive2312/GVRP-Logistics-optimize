# Implementation — DRIVER-DELIVERY-001 (chặng 1)

## Backend (`GVRP_Entry_API`)

- Data: `UserRole.DRIVER`, `User.phone` (nullable), new `RouteSend` (solution/route/driver/sender/sentAt/CURRENT|SUPERSEDED) + `SendStatus` + repository; migration `2026-10-06-driver-delivery.sql` (role ENUM, phone, `route_sends` table; manual apply, no runner).
- `RouteSendService`: `listDrivers` (PLANNER, enabled same-branch DRIVER users), `sendRoute` (PLANNER; route lock + validation: order stops, solution ownership/status/branch, driver role/enabled/branch; atomic flip+insert; post-save single-CURRENT check → 409), `routeSends` (PLANNER history), `mySends` (DRIVER, CURRENT first), `sendDetail` (DRIVER + ownership → 403 otherwise; order join for customer fields).
- Role enforcement is service-level via `CurrentUserUtil` (no precedent of Security annotations; old endpoints untouched). New `ForbiddenException` → 403 + `FORBIDDEN` code.
- Controllers: `RouteSendController` (POST `/routes/{id}/send`, GET `/routes/{id}/sends`), `DriverController` (GET `/drivers`, GET `/driver/routes[/{sendId}]`); `SolutionController` + `SolutionService.revisionChain` (GET `/solutions/{id}/revision-chain`).
- `RouteRepository.findWithLockById` (pessimistic write); `AppConstant.ROUTE`.
- Tests: `RouteSendServiceTest` (8: send/supersede, role, empty, infeasible, branch, ordering, 403, customer info) + `RouteSendControllerTest` (6 envelopes incl. 403/422). Entry suite for this run: 14/14 with the new tests.

## Frontend (`GVRP_Entry_client_v2`)

- Models `driver.model.ts` (+ `RouteDTO.id?`, `StopDTO` already carries `order_id` — both already on the wire via SNAKE_CASE); `ApiService` 6 new methods.
- `AuthService.getRole()` (from stored session); new `DriverGuard` (DRIVER → in; planner → `/main`; anonymous → login with return).
- `SendRouteDialogComponent` (drivers + revision chain + current-assignee note; returns driver/revision) wired to route cards via new "Send to driver" button + assignee label (effect-loaded history; `queueMicrotask` avoids NG0600 with sync sources).
- `DriverViewComponent` at `/driver` (guarded): CURRENT-first list, superseded banner/flags, detail with phone/notes/`tel:` link, back + logout.
- `login.component.ts`: `navigate` → `navigateByUrl` was already done in DEEP-LINK-001 (reused for post-login return).
- Tests: dialog 4, driver view 4, guard 3, route-view dispatch 2. Full suite 140 + same 5 pre-existing failures.

## Repair 1 — wire-case mismatch (user-reported, 2026-10-08, `user-feedback-01.md`)
- Frontend sent camelCase `driverUserId`; backend SNAKE_CASE demands `driver_user_id` → 500 on save.
- Fix: `SendRoutePayload` keys → snake_case (repo wire convention); controller spec now uses a SNAKE_CASE mapper + snake body to pin the contract. No requirement/design change.

## Repair 2 — driver post-login landing (user-reported gap, 2026-10-08, `user-feedback-02.md`)

- Gap: every role landed on `/main` after login; drivers never reached `/driver` on their own.
- Fix: `login.component.ts` routes DRIVER to `/driver` unless the stored return URL already targets a driver route; planner flow byte-identical. New `login.component.spec.ts` (3 landing cases). No requirement/design change (DD-002/DD-005).

## Not changed

Auth mechanism, guards for existing routes, email/Slack notifications, engine, order lifecycle, old endpoints. No push, no ack (accepted exclusions).
