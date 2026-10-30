# D06 — Dispatch and driver delivery

A planner sends one route of a solution to one driver; the driver reads what was sent. A send is the only way
a driver receives work, and sends are immutable history.

Screens: `/driver`; the send dialog opens from the Route tab of `/main`.
Diagrams: [sequence-route-send.svg](diagrams/sequence-route-send.svg) ([source](diagrams/sequence-route-send.puml)),
[sequence-driver-view.svg](diagrams/sequence-driver-view.svg) ([source](diagrams/sequence-driver-view.puml)),
[state-route-send.svg](diagrams/state-route-send.svg) ([source](diagrams/state-route-send.puml)),
[class-dispatch-domain.svg](diagrams/class-dispatch-domain.svg) ([source](diagrams/class-dispatch-domain.puml)).

Use cases: UC-D06-01 … UC-D06-05. Rules: BR-051 … BR-058, BR-079.

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/driver` | `DriverViewComponent` | the driver's sends (current first), stop list, map, leg instructions, zone names per stop |
| — (from `/main`) | `SendRouteDialogComponent` | revision picker (revision chain, D05), route, driver; shows the send history of the route |

`/driver` is guarded by `DriverGuard` only (role `DRIVER`); it does not use the planner layout or `setupGuard`.

---

## 2. API

Four endpoints. `GET /api/v1/drivers` (the driver picker of the send dialog) is listed in
`D02-master-data/README.md` because it reads users, not sends.

| Method | Path | Role | Request | Success | Notes |
|---|---|---|---|---|---|
| POST | `/api/v1/routes/{id}/send` | PLANNER | `SendRouteRequest {solution_id, driver_user_id}` | `200 RouteSendDTO` | see §3 |
| GET | `/api/v1/routes/{id}/sends` | PLANNER | — | `200 RouteSendDTO[]` | newest first; foreign route answers like a missing one |
| GET | `/api/v1/driver/routes` | DRIVER | — | `200 RouteSendDTO[]` | the caller's sends, `CURRENT` first, then `SUPERSEDED` |
| GET | `/api/v1/driver/routes/{sendId}` | DRIVER | — | `200 DriverRouteDTO` | only the caller's own send (BR-057) |

`RouteSendDTO` = `{id, solution_id, route_id, vehicle_license_plate, driver_id, driver_name, sender_name,
sent_at, status}`.
`DriverRouteDTO` = `{send_id, solution_id, route_id, vehicle_license_plate, status, stops[]}`; each stop has
`sequence_number, type (ORDER|DEPOT), order_id, location_name, address, latitude, longitude, arrival_time,
departure_time, customer_name, customer_phone, delivery_notes, demand, geometry_to_next,
instructions_to_next, zone_names`. Zone **names** only; no zone polygon leaves the server.

Besides the URL rule (`/api/v1/driver/**` → `ROLE_DRIVER`), `RouteSendService` checks the role again in code
(`requireRole`, `requireDriver`), so a mis-mapped role is refused twice.

---

## 3. Sending a route

`RouteSendService.sendRoute`, one transaction with `noRollbackFor = UnprocessablePlanException`
(see `sequence-route-send.puml`):

1. Load the route **with a row lock** and the solution; unknown id → `400` + `0040402`.
2. The route must belong to `solution_id`; the solution must be `SUCCESS` or `PARTIAL_SUCCESS` (BR-051) and of
   the caller's branch; the route must have at least one `ORDER` stop (BR-052) — each failure `400` + `0040001`.
3. The driver must exist (`0040402`) and be an enabled `DRIVER` of the branch (`0040001`, BR-053).
4. **LEZ guard** (BR-079): several delivery dates → `422` `LEZ_MIXED_DATES`; when the branch has zones,
   missing leg geometry is fetched from the engine in one call (engine down → `502`, BR-056), the route is
   checked and certified with the **current** rules, and any violation → `422` naming code, stop position and
   zone. The verdict and fetched legs commit with the refusal; no send row is created.
5. Flip the route's `CURRENT` sends to `SUPERSEDED`, insert the new `CURRENT` send, re-count; a count other
   than one → `409` with the latest send id (BR-054, BR-055).

Observation: step 2 answers a foreign-branch solution with *"Solution N belongs to another branch"* (`400`),
which confirms the id exists. The planner-side history endpoint does not leak (it answers like a missing
route). Recorded as a finding for the security checklist owner, not changed here.

---

## 4. Errors

| Situation | Status | Code |
|---|---|---|
| unknown route, solution, driver, send | 400 | `0040402` |
| route not in solution, solution not sendable, foreign solution, no delivery stop, not a driver of the branch | 400 | `0040001` |
| another driver's send | 403 | `0040301` (`ForbiddenException`) |
| mixed dates or LEZ violation | 422 | `0042201` |
| engine unavailable while filling legs | 502 | `0050002` |
| concurrent send lost | 409 | `0040901`, message carries the latest send id |

---

## 5. Data touched

`route_sends` (insert, status flip), `routes` (row lock; LEZ fields and leg geometry written by the guard),
`route_stops` (`geometry_to_next`, `instructions_to_next` filled), `solutions`, `orders`, `users` (read).
Nothing in this domain changes an order's status: delivery progress is not recorded (BR-058).

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{RouteSendController,DriverController}.java`,
`.../service/RouteSendService.java`, `.../service/lez/RouteComplianceService.java`,
`.../dto/request/SendRouteRequest.java`, `.../dto/response/{RouteSendDTO,DriverRouteDTO,DriverStopDTO}.java`,
`.../entity/{RouteSend}.java`, `.../entity/enums/SendStatus.java`,
`GVRP_Entry_client_v2/src/app/features/driver/driver-view/**`,
`.../features/main/orders/route-view/send-route-dialog/**`, `.../core/guards/driver.guard.ts`,
`01-requirements/03-business-rules.md`.
