# 02 — Use-case catalogue

One entry per user-visible goal. IDs are stable: `UC-Dxx-nn`. `Rules` reference
`03-business-rules.md`; `API` references the rows of the domain README in `03-basic-design/`.

Each use case lists the **main flow** and the **alternatives** that the code actually produces. If an
alternative is listed, a test case for it exists in `04-test-design/test-cases.csv`.

---

## D01 — Authentication and roles

Diagram: [use-case-D01.svg](diagrams/use-case-D01.svg) — source [use-case-D01.puml](diagrams/use-case-D01.puml).

### UC-D01-01 Log in to a branch
- **Actor:** Planner, Driver.
- **Goal:** obtain a token bound to one branch.
- **Trigger:** the user submits the login form.
- **Main flow:** enter branch name, username, password → `POST /api/v1/auth/login` → branch resolved by
  name → credentials checked against that branch → token with `userId`, `branchId`, `role` → client
  stores the token and routes to `/main` (planner) or `/driver` (driver).
- **Alternatives:** unknown branch → `404`/`0040402`; wrong credentials → `401`/`0040101`; missing
  field → `400` with the field message.
- **Rules:** BR-009, BR-008.
- **API:** `POST /api/v1/auth/login`.

### UC-D01-02 Register a user
- **Actor:** Anonymous.
- **Goal:** create an account.
- **Main flow (intended):** submit username, password, e-mail, full name → user created in a branch.
- **Alternatives:** username taken → `400 "Username is already taken"`; e-mail in use →
  `400 "Email is already in use"`.
- **Actual behaviour:** the endpoint returns `200 "User registered successfully"` **without writing a
  user row** (method body is a comment). See `01-actors-and-roles.md`.
- **Rules:** —
- **API:** `POST /api/v1/auth/register`.
- **Status:** implemented as a stub; documented as a gap, not as working behaviour.

### UC-D01-03 Enforce role-based access
- **Actor:** System (on behalf of every caller).
- **Goal:** a driver cannot reach planner functions and vice versa.
- **Main flow:** request passes the JWT filter → path rule matches → role required → controller runs.
- **Alternatives:** anonymous → `401`; wrong role → `403` with the common error envelope; the denied
  handler answers before the controller is invoked (tests assert the service is not called).
- **Rules:** BR-006, BR-007.
- **API:** all endpoints (cross-cutting).

### UC-D01-04 Revoke access of a disabled user
- **Actor:** Branch administrator (out of band), system.
- **Goal:** a disabled user's existing token stops working immediately.
- **Main flow:** set `users.enabled = false` → the next request re-loads the user → the filter rejects
  the token → the request continues unauthenticated → `401`.
- **Alternatives:** deleted user → same result; token for a different user id → rejected; invalid
  signature → rejected.
- **Rules:** BR-008.
- **API:** all authenticated endpoints.

---

## D02 — Master data

Diagram: [use-case-D02.svg](diagrams/use-case-D02.svg) — source [use-case-D02.puml](diagrams/use-case-D02.puml).

### UC-D02-01 Create a depot
- **Actor:** Planner. **Trigger:** setup wizard or admin screen.
- **Main flow:** name, address, coordinates → `POST /api/v1/depots` → branch from token → `201`.
- **Alternatives:** validation failure → `400` with field errors.
- **Rules:** BR-010, BR-001. **API:** `POST /api/v1/depots`.

### UC-D02-02 List depots
- **Actor:** Planner. **Main flow:** `GET /api/v1/depots` → list of the branch's depots.
- **Alternatives:** empty list is a valid `200` (drives `setupGuard`).
- **Rules:** BR-001. **API:** `GET /api/v1/depots`.

### UC-D02-03 Create or update a vehicle type
- **Actor:** Planner.
- **Main flow:** name, capacity, costs, features (including `emissionFactor`), optional legal class,
  gross weight, energy type and emission standard → `POST /api/v1/vehicle-types` or
  `PUT /api/v1/vehicle-types/{id}`.
- **Alternatives:** capacity < 1 or negative cost → `400`; foreign id → `404`; the admin screen's
  Delete action has no server endpoint (`BR-015`).
- **Rules:** BR-011, BR-015. **API:** `POST`, `GET`, `PUT /api/v1/vehicle-types/{id}`.

### UC-D02-04 Create a fleet with vehicles
- **Actor:** Planner.
- **Main flow:** fleet name plus at least one vehicle (plate, type, start depot, end depot) →
  `POST /api/v1/fleets`.
- **Alternatives:** empty vehicle list → `400`; a vehicle type or depot of another branch → `404`
  (`FleetServiceTest`).
- **Rules:** BR-012, BR-013. **API:** `POST /api/v1/fleets`.

### UC-D02-05 List fleets and open a fleet
- **Actor:** Planner. **Main flow:** `GET /api/v1/fleets` → fleet cards with `vehicle_count`;
  `GET /api/v1/fleets/{fleetId}` → fleet detail.
- **Alternatives:** another branch's fleet → `404`.
- **Rules:** BR-001, BR-002. **API:** `GET /api/v1/fleets`, `GET /api/v1/fleets/{fleetId}`.

### UC-D02-06 Create or update a vehicle
- **Actor:** Planner.
- **Main flow:** plate, type, start/end depot → `POST /api/v1/vehicles?fleetId=` → `201`;
  `PUT /api/v1/vehicles/{id}` updates plate, type and depots.
- **Alternatives:** missing/foreign fleet, type or depot → `404`; unknown vehicle → `404`.
- **Rules:** BR-012. **API:** `GET /api/v1/vehicles`, `POST /api/v1/vehicles`,
  `PUT /api/v1/vehicles/{id}`.

### UC-D02-07 Manage vehicle LEZ permits
- **Actor:** Planner.
- **Main flow:** choose a vehicle, add a permit for a zone with validity window and optional schedule
  → `POST /api/v1/vehicles/{vehicleId}/permits`; edit → `PUT .../{permitId}`; remove →
  `DELETE .../{permitId}`.
- **Alternatives:** foreign vehicle or permit → `404`; permit used by a send? — not enforced, permits
  are read at compliance time.
- **Rules:** BR-014. **API:** four permit endpoints.

---

## D03 — Orders

Diagram: [use-case-D03.svg](diagrams/use-case-D03.svg) — source [use-case-D03.puml](diagrams/use-case-D03.puml).

### UC-D03-01 Create an order
- **Actor:** Planner.
- **Main flow:** fill code, customer, address or coordinates, demand, time window, service time,
  priority, skills, delivery date → `POST /api/v1/orders` → coordinates resolved (BR-022) → per-branch
  branch → `201` with the created order.
- **Alternatives:** invalid field → `400` with one error per field; a `status` in the body → `400`
  (BR-023); geocoding miss → `400` on `address`.
- **Rules:** BR-020, BR-022, BR-023. **API:** `POST /api/v1/orders`.

### UC-D03-02 Import orders from a file
- **Actor:** Planner.
- **Main flow:** choose date and file (CSV/XLSX) → `POST /api/v1/orders/import` (multipart) → rows
  parsed, coordinates taken from the file when present, otherwise geocoded → `ImportResultDTO` with
  success count and per-row errors.
- **Alternatives:** two delivery dates → `400`; a row with only one coordinate → error row naming the
  column; unreadable file → `400`.
- **Rules:** BR-025, BR-022. **API:** `POST /api/v1/orders/import`.

### UC-D03-03 List and filter orders
- **Actor:** Planner.
- **Main flow:** open the dashboard → `GET /api/v1/orders?page=&size=&date=&status=&search=` → page of
  orders with total count → map and list render from the same page.
- **Alternatives:** malformed date or unknown status → `400`; no match → empty page.
- **Rules:** BR-026. **API:** `GET /api/v1/orders`.

### UC-D03-04 View an order
- **Actor:** Planner. **Main flow:** `GET /api/v1/orders/{orderId}` → full detail; a foreign id → `404`.
- **Rules:** BR-002. **API:** `GET /api/v1/orders/{orderId}`.

### UC-D03-05 Update an order
- **Actor:** Planner.
- **Main flow:** edit fields → `PUT /api/v1/orders/{orderId}` → branch-scoped load → coordinates
  resolved → saved.
- **Alternatives:** foreign id → `404` and no geocoding call; validation → `400`.
- **Rules:** BR-024, BR-020. **API:** `PUT /api/v1/orders/{orderId}`.

### UC-D03-06 Change the status of many orders
- **Actor:** Planner.
- **Main flow:** select rows (or the current page) → choose target status → `PATCH /api/v1/orders/status`
  → each row validated against the status graph → `{updated[], failed[]}` → the UI toasts the counts
  and lists the failures.
- **Alternatives:** unknown status string → `400`; a row in the wrong state → `failed[]` with the
  transition message; foreign id → `failed[]` "not found in this branch".
- **Rules:** BR-031…BR-034, BR-021. **API:** `PATCH /api/v1/orders/status`.

---

## D04 — Route planning

Diagram: [use-case-D04.svg](diagrams/use-case-D04.svg) — source [use-case-D04.puml](diagrams/use-case-D04.puml).

### UC-D04-01 Submit a planning job
- **Actor:** Planner.
- **Main flow:** select orders of one date and vehicles → optional preferences → `POST /api/v1/jobs/plan`
  → Entry checks the 3-job limit, that every order is `SCHEDULED`, that one date is used, snapshots LEZ
  rules and LEZ skills (BR-082/083) → job `PENDING` → asynchronous engine call.
- **Alternatives:** a non-`SCHEDULED` order → `400` naming up to ten; two dates → `400`; 3 jobs already
  running → `0040903`; no usable vehicle for an in-zone order → `400` naming zone and vehicle and no
  job created; engine unreachable → the job is marked `FAILED` with "Cannot connect to optimization
  engine…".
- **Rules:** BR-059…BR-063, BR-082, BR-083. **API:** `POST /api/v1/jobs/plan`.

### UC-D04-02 Monitor a job
- **Actor:** Planner.
- **Main flow:** the dialog polls `GET /api/v1/jobs/{id}/progress` (Entry proxies the engine) → percent,
  message, remaining seconds → on completion the dialog opens the new solution.
- **Alternatives:** job still `PENDING` → progress unavailable but not an error; engine unreachable →
  the Entry answer degrades (no progress) and the UI keeps the previous value; a cancelled job stops
  polling.
- **Rules:** BR-065, BR-066. **API:** `GET /api/v1/jobs/{id}/progress`, `GET /api/v1/jobs/{id}`.

### UC-D04-03 Cancel jobs
- **Actor:** Planner.
- **Main flow:** cancel one or several running jobs → `PUT /api/v1/jobs` with the ids → Entry forwards
  a cancel to the engine and marks them `CANCELLED` → `204`.
- **Alternatives:** a job not `PROCESSING` → `0040901`; a foreign id → `404`.
- **Rules:** BR-064, BR-069. **API:** `PUT /api/v1/jobs`.

### UC-D04-04 Receive an engine callback
- **Actor:** Engine.
- **Main flow (completion):** `POST /api/v1/solutions/callbacks/complete` → API key checked → job
  resolved → idempotency and state guards (BR-066…BR-068) → solution, routes, stops, unassigned orders
  and Pareto candidates persisted → routes certified with the job's ruleset snapshot → job `COMPLETED`
  → success e-mail.
- **Alternatives:** second completion → `200` ignored; job `PENDING` → `503` and the engine retries;
  job `CANCELLED` → ignored; failure callback → job `FAILED` + failure e-mail sent directly;
  cancellation callback → job `CANCELLED` and **no notification** (the cancellation e-mail method in
  `EmailService` is never called); progress callback → accepted and logged, nothing stored.
- **Rules:** BR-066…BR-069, BR-082. **API:** four callback endpoints.

### UC-D04-05 Browse job history
- **Actor:** Planner.
- **Main flow:** `GET /api/v1/jobs?limit=` → newest jobs of the branch with status, elapsed and
  remaining seconds, `canBeCancelled` and the produced solution id → `/jobs/:id` opens one job.
- **Alternatives:** no jobs → empty list; a job without a solution → `solutionId` null.
- **Rules:** BR-001, BR-065. **API:** `GET /api/v1/jobs`.

---

## D05 — Solution review and customisation

Diagram: [use-case-D05.svg](diagrams/use-case-D05.svg) — source [use-case-D05.puml](diagrams/use-case-D05.puml).

### UC-D05-01 View a solution
- **Actor:** Planner.
- **Main flow:** `GET /api/v1/solutions/{id}` → solution header (status, totals, vehicle count) plus
  routes, stops, unassigned orders and metrics → the client renders map, list and timeline views.
- **Alternatives:** foreign or unknown id → `404`, same envelope.
- **Rules:** BR-090, BR-096. **API:** `GET /api/v1/solutions/{id}`.

### UC-D05-02 Walk the revision chain
- **Actor:** Planner.
- **Main flow:** `GET /api/v1/solutions/{id}/revision-chain` → chain from the root; open an older
  revision read-only; `GET /{id}/latest-revision` tells the editor which base is current.
- **Alternatives:** foreign solution → `404`.
- **Rules:** BR-091, BR-095. **API:** two revision endpoints.

### UC-D05-03 Evaluate manual route edits
- **Actor:** Planner. **Trigger:** the planner moves a stop or changes the sequence.
- **Main flow:** `POST /api/v1/solutions/{id}/evaluate` with the edited sequences → engine totals and
  violations + Entry capacity check → preview shown, nothing saved.
- **Alternatives:** duplicate order → `400`; missing planned order → `400`; engine down → `502`;
  foreign solution → `404` without an engine call.
- **Rules:** BR-092, BR-094. **API:** `POST /api/v1/solutions/{id}/evaluate`.

### UC-D05-04 Save a manual revision
- **Actor:** Planner.
- **Main flow:** confirm the edited plan → `POST /api/v1/solutions/{id}/revisions` → base must still be
  latest → hard violations block → new `MANUAL_EDITED` revision returned.
- **Alternatives:** stale base → `409`; hard violation → `422` with the violations; engine down → `502`.
- **Rules:** BR-093. **API:** `POST /api/v1/solutions/{id}/revisions`.

### UC-D05-05 Compare and apply Pareto alternatives
- **Actor:** Planner.
- **Main flow:** open the Pareto tab → `GET /api/v1/solutions/{id}/pareto` → chart with frontier and
  dominated points → select a point to preview its metrics → apply →
  `POST /api/v1/solutions/{id}/pareto/{candidateId}/apply` → new `PARETO_ALTERNATIVE` revision.
- **Alternatives:** solution without Pareto data → empty frontier; non-frontier candidate → `422`;
  stale base → `409`; different order set → `422` `ORDER_SET_DIFFERS`; foreign solution/candidate →
  `404`; already-active candidate → base returned unchanged.
- **Rules:** BR-041…BR-046. **API:** two Pareto endpoints.

---

## D06 — Dispatch and driver delivery

Diagram: [use-case-D06.svg](diagrams/use-case-D06.svg) — source [use-case-D06.puml](diagrams/use-case-D06.puml).

### UC-D06-01 Send a route to a driver
- **Actor:** Planner.
- **Main flow:** open a route → choose a driver → `POST /api/v1/routes/{id}/send` → LEZ gate → leg
  geometry filled if missing → new send `CURRENT`, previous sends `SUPERSEDED`.
- **Alternatives:** solution not sendable → `400`; route without stops → `400`; driver of another
  branch or disabled → `400`; LEZ violation → `422` with the verdict kept and no send; engine down
  during leg fill → `502` and no send; concurrent send → `409`.
- **Rules:** BR-051…BR-056, BR-079. **API:** `POST /api/v1/routes/{id}/send`.

### UC-D06-02 Review the sends of a route
- **Actor:** Planner. **Main flow:** `GET /api/v1/routes/{id}/sends` → history with driver, sender,
  `sent_at` and status; foreign route → `404`.
- **Rules:** BR-054, BR-002. **API:** `GET /api/v1/routes/{id}/sends`.

### UC-D06-03 List drivers
- **Actor:** Planner. **Main flow:** `GET /api/v1/drivers` → enabled drivers of the branch for the send
  dialog.
- **Rules:** BR-053. **API:** `GET /api/v1/drivers`.

### UC-D06-04 Driver: list my routes
- **Actor:** Driver.
- **Main flow:** open `/driver` → `GET /api/v1/driver/routes` → current sends first, then superseded
  history.
- **Alternatives:** no sends → empty state; a planner token → `403`.
- **Rules:** BR-057, BR-058. **API:** `GET /api/v1/driver/routes`.

### UC-D06-05 Driver: open a sent route
- **Actor:** Driver.
- **Main flow:** `GET /api/v1/driver/routes/{sendId}` → ordered stops with customer info, leg geometry
  and encoded path → map, expandable sections, current stop, per-leg Google Maps link, follow mode.
- **Alternatives:** another driver's send → forbidden; unknown id → `404`.
- **Rules:** BR-057. **API:** `GET /api/v1/driver/routes/{sendId}`.

---

## D07 — Low-emission zones

Diagram: [use-case-D07.svg](diagrams/use-case-D07.svg) — source [use-case-D07.puml](diagrams/use-case-D07.puml).

### UC-D07-01 Browse zone templates
- **Actor:** Planner. **Main flow:** `GET /api/v1/zone-templates` → templates with version, geometry
  summary and rule count; `GET /{id}` → detail with rules.
- **Rules:** BR-070. **API:** two template endpoints.

### UC-D07-02 Create a branch zone
- **Actor:** Planner.
- **Main flow:** pick a template and a mode → `POST /api/v1/zones` → `LINKED` follows the template,
  `COPIED` snapshots it → `201`.
- **Alternatives:** linking the same template twice → `409` (`secondLink_returns409Envelope`); unknown
  template → `404`.
- **Rules:** BR-071, BR-072. **API:** `POST /api/v1/zones`.

### UC-D07-03 Edit a zone
- **Actor:** Planner. **Main flow:** list or open zones (`GET /api/v1/zones`, `/zones/{id}`) → rename or
  enable/disable a copied zone → `PATCH /api/v1/zones/{id}`.
- **Alternatives:** foreign zone → `404`; rename of a linked zone is ignored (name comes from the
  template).
- **Rules:** BR-070, BR-071. **API:** `GET`, `GET /{id}`, `PATCH /{id}`.

### UC-D07-04 Replace zone geometry
- **Actor:** Planner. **Main flow:** draw on the map → `PUT /api/v1/zones/{id}/geometry` with GeoJSON.
- **Alternatives:** > 5000 vertices → `400`; unsupported geometry type → `400`; self-intersection →
  `400` from the geometry parser.
- **Rules:** BR-073. **API:** `PUT /api/v1/zones/{id}/geometry`.

### UC-D07-05 Replace zone rules
- **Actor:** Planner. **Main flow:** edit rules in the editor (selector, schedule, validity, legal
  basis) → `PUT /api/v1/zones/{id}/rules` with the whole list → recompiled and version bumped.
- **Alternatives:** > 50 rules → `400`; malformed selector/schedule → `400`.
- **Rules:** BR-074, BR-075, BR-076. **API:** `PUT /api/v1/zones/{id}/rules`.

### UC-D07-06 Acknowledge a zone version
- **Actor:** Planner. **Main flow:** the UI shows an unacknowledged template version → acknowledge →
  `POST /api/v1/zones/{id}/acknowledge` with `{version}` → DTO reports the acknowledged version.
- **Rules:** BR-076. **API:** `POST /api/v1/zones/{id}/acknowledge`.

### UC-D07-07 Delete a zone
- **Actor:** Planner. **Main flow:** delete an unreferenced zone → `DELETE /api/v1/zones/{id}` → `204`.
- **Alternatives:** a zone still referenced by permits → `409` (`deleteReferencedZone_returns409`).
- **Rules:** BR-070. **API:** `DELETE /api/v1/zones/{id}`.

### UC-D07-08 Evaluate a zone verdict
- **Actor:** Planner. **Main flow:** pick a vehicle and a date → `GET /api/v1/zones/{id}/verdict` →
  allowed/denied with the intervals that apply; nothing is written.
- **Alternatives:** malformed date → `400`; unknown vehicle → `404`.
- **Rules:** BR-078. **API:** `GET /api/v1/zones/{id}/verdict`.

### UC-D07-09 Inspect order zone membership
- **Actor:** Planner. **Main flow:** `GET /api/v1/lez/order-zones?orderIds=` → per order, the zones it
  falls into and the restrictions that apply.
- **Rules:** BR-070, BR-075. **API:** `GET /api/v1/lez/order-zones`.

### UC-D07-10 Inspect solution compliance and recheck
- **Actor:** Planner.
- **Main flow:** `GET /api/v1/solutions/{id}/compliance` → per-route status (`UNCHECKED`, `COMPLIANT`,
  `VIOLATION`, `STALE`), compiled zones and LEZ unassigned orders → recheck →
  `POST /api/v1/solutions/{id}/compliance/recheck` → new statuses written.
- **Alternatives:** foreign solution → `404`; no branch context → `403`.
- **Rules:** BR-077, BR-080, BR-081. **API:** two compliance endpoints.

### UC-D07-11 Fetch route geometry for compliance
- **Actor:** Planner.
- **Main flow:** `GET /api/v1/routes/{id}/geometry` → legs with encoded paths and instructions, used to
  draw the compliance overlay.
- **Alternatives:** foreign route → `404`.
- **Rules:** BR-081. **API:** `GET /api/v1/routes/{id}/geometry`.

---

## D08 — Optimisation engine

Diagram: [use-case-D08.svg](diagrams/use-case-D08.svg) — source [use-case-D08.puml](diagrams/use-case-D08.puml).

### UC-D08-01 Run an optimisation
- **Actor:** Entry API.
- **Main flow:** `POST /api/v1/optimization` → job id validated → `202` with `engine-<jobId>` →
  asynchronous solve: matrix build → optional clustering → jsprit solve → callback post (retry on
  failure).
- **Alternatives:** duplicate job id while running → `409`; missing required arrays → `400`.
- **Rules:** BR-065, BR-066. **API:** `POST /api/v1/optimization`.

### UC-D08-02 Observe progress and cancel
- **Actor:** Entry API, Planner (through Entry).
- **Main flow:** `GET /api/v1/optimization/{jobId}/progress` → percent and message;
  `POST /api/v1/optimization/{jobId}/cancel` → the solver handle is cancelled at the next iteration
  boundary; `GET /{jobId}/result` and `POST /{jobId}/resend` expose and re-post the spooled result.
- **Alternatives:** unknown job → `404`; cancel after completion → `409`.
- **Rules:** BR-064. **API:** four engine endpoints.

### UC-D08-03 Evaluate a plan and fetch leg geometry
- **Actor:** Entry API.
- **Main flow:** `POST /api/v1/optimization/evaluate` → totals, violations and per-route metrics without
  persisting; `POST /api/v1/optimization/legs` → geometry and instructions for the given legs.
- **Alternatives:** unknown order/vehicle id → `400`; GraphHopper unavailable → `502`.
- **Rules:** BR-056, BR-092. **API:** two engine endpoints.

### UC-D08-04 Check engine health
- **Actor:** Operator. **Main flow:** `GET /api/v1/optimization/health` → status and version.
- **API:** `GET /api/v1/optimization/health`.

---

## D09 — Solution deep link

Diagram: [use-case-D09.svg](diagrams/use-case-D09.svg) — source [use-case-D09.puml](diagrams/use-case-D09.puml).

### UC-D09-01 Resolve a solution deep link
- **Actor:** Planner (from an e-mail), anonymous visitor.
- **Main flow:** open `/?solution=<id>` → `SolutionLinkComponent` parses the id → authenticated: load
  the solution and go to `/main` with a toast → anonymous: store the URL as `lastUrl`, go to `/login`,
  and after login return to the link and load it.
- **Alternatives:** missing or non-numeric id → `/main` (authenticated) or `/login` (anonymous); load
  failure → error toast, then `/main` — never a blank page.
- **Rules:** BR-090. **API:** `GET /api/v1/solutions/{id}` (through the client store).

---

## Coverage summary

| Domain | Use cases | Endpoints in the domain |
|---|---|---|
| D01 | 4 | 2 |
| D02 | 7 | 16 (includes `GET /api/v1/drivers`) |
| D03 | 6 | 6 |
| D04 | 5 | 9 |
| D05 | 5 | 7 |
| D06 | 5 | 4 (`GET /api/v1/drivers` is in D02) |
| D07 | 11 | 15 |
| D08 | 4 | 8 |
| D09 | 1 | 0 |
| **Total** | **48** | **59 + 8** |

## Sources

Controller inventory (59 Entry endpoints, 8 engine endpoints), `app.routes.ts`, service classes named in
each entry, and the client specs listed in `04-test-design/01-test-strategy.md`.
