# Scenarios — DRIVER-DELIVERY-001 (markdown)

## S-1 Send route (dispatcher)

Dispatcher opens a route → Send to driver → picks a driver and a revision (latest by default) → system validates (route has ORDER stops, driver is an enabled same-branch DRIVER, solution status sendable) → prior CURRENT sends of the route flip to SUPERSEDED, new CURRENT `RouteSend` recorded → success toast + assignee shown. Failures surface as error toasts with no state change.

## S-2 Driver views (driver)

Driver logs in with role DRIVER (shared username/password auth) on a phone browser → sees only assigned routes, CURRENT first → opens a route: stop order, address, ETA, customer phone, delivery notes, plate, version flag (CURRENT/SUPERSEDED).

## S-3 Re-send (new version)

Plan changes → dispatcher sends again → old send SUPERSEDED → driver sees the new version current plus a "new version available" banner backed by the SUPERSEDED section (no push in chặng 1).

## S-4 Older revision send

Dispatcher picks an older revision in the send dialog instead of the latest; same validation and supersede rules apply.

Diagrams: `../../GVRP_Document/diagrams` (S-1/S-4), `../../GVRP_Document/diagrams` (S-2/S-3), `../../GVRP_Document/diagrams` (new/changed classes).

## Failure cases (per scenario + handling)

### F-S1 send failures (dispatcher)

| Case | Detection | Handling |
|---|---|---|
| Route has zero ORDER stops (DD-031) | Service checks stop list | 422, message names the route; dialog stays open, nothing persisted |
| Selected driver not DRIVER role / other branch / disabled (DD-003, DD-030) | Service checks user record | 404/422 + warning text; dialog stays open |
| Solution INFEASIBLE or revision id unknown (DD-023, DD-032) | Service checks status/revision | 422; dispatcher picks another revision |
| Two dispatchers send the same route concurrently | DB unique/ordering on (route, CURRENT) + re-check in tx | Loser gets 409 with the winning send id; dialog offers reload |
| Backend/engine unreachable during send | Call fails | 502-style error envelope; draft selection kept in dialog for retry |

### F-S2 view failures (driver)

| Case | Detection | Handling |
|---|---|---|
| Forced foreign send id in URL (DD-041) | Ownership check in service | 403; driver view shows "no access" + back to list |
| Assigned route's revision data missing | Null guards on load | Error toast + return to list; send record untouched |
| Driver account disabled after send (DD-030) | Login/auth + list filter | Cannot log in / sees empty list; dispatcher side shows warning on the send record |
| Session expired mid-view | 401 interceptor | Redirect to login, return to the same view after login |

### F-S3 re-send failures

| Case | Detection | Handling |
|---|---|---|
| New send fails validation after old was already SUPERSEDED | Ordered tx: validate → flip → insert | Flip and insert are atomic; failed validation flips nothing, old CURRENT stays |
| Driver opens the app mid re-send | List endpoint is read-committed | Driver sees either old CURRENT or new CURRENT, never a half-flipped list |

### F-S4 wrong-revision failures

| Case | Detection | Handling |
|---|---|---|
| Revision belongs to another solution/branch | Revision ownership check | 422; dialog stays open |
| Revision deleted upstream | Existence check | 404 with message; dialog offers latest instead |
