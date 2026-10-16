# Requirements summary (ACCEPTED by user 2026-10-06, incl. Q-1/2/3 proposals) — DRIVER-DELIVERY-001

Chặng 1 only (backend + responsive web). Flutter reuses these APIs later — no mobile scope here.
Legend: ACCEPTED = user decided 2026-10-06; PROPOSAL = recommended default awaiting call.

## User scenarios

- S-1: Dispatcher opens a plan, picks a route, selects a driver from the directory, sends. Driver logs in on a phone browser and sees only their routes.
- S-2: Driver opens an assigned route: stop order, address, ETA, customer phone, delivery notes.
- S-3: Plan changes → dispatcher sends again; driver sees the new version marked current, the old marked superseded.
- S-4: Dispatcher picks an older revision and sends it instead of the latest.

## Capabilities

| ID | Capability | Verdict |
|---|---|---|
| DD-001 | Driver directory (name, phone, branch; link to user account) | ACCEPTED: include |
| DD-002 | DRIVER role + login (same web auth) | ACCEPTED: include |
| DD-003 | Assign driver per route at send time | ACCEPTED: include |
| DD-004 | Driver-scoped read APIs (only own assigned routes) | ACCEPTED: include |
| DD-005 | Driver view: stop order, address, ETA, customer phone, notes | ACCEPTED: include |
| DD-006 | Send latest incl. manual revisions; older revision selectable | ACCEPTED: include |
| DD-007 | New send supersedes prior sends of the same route (notify driver) | ACCEPTED: include |
| DD-008 | Delivery acknowledgement | ACCEPTED: exclude (no ack) |
| DD-009 | SMS/Zalo/email push to driver | PROPOSAL: exclude (driver pulls via screen; push revisited with Flutter) |
| DD-010 | Fixed driver↔vehicle binding | ACCEPTED: exclude |

## Business rules (PROPOSAL)

- DD-020: a send records (solution revision, route, driver, sender, timestamp); sends are immutable history.
- DD-021: only one current send per route; a newer send flips prior ones to SUPERSEDED (driver sees the flag).
- DD-022: driver APIs never expose other drivers' routes, planner screens, or customer lists beyond assigned stops.
- DD-023: only COMPLETED-job solutions (SUCCESS/PARTIAL_SUCCESS) can be sent; INFEASIBLE cannot.

## Exceptions (draft)

- DD-030: driver account disabled after send → assignment hidden from driver, dispatcher sees warning.
- DD-031: route with zero ORDER stops → reject send.
- DD-032: revision deleted/superseded upstream → send blocked with message.

## Acceptance criteria (draft, measurable)

- DD-040: S-1..S-4 walkthrough on a 2-route fixture with 2 drivers.
- DD-041: driver A cannot fetch driver B's routes (403 on forced IDs) — 100% of fixture cases.
- DD-042: re-send marks exactly the prior sends SUPERSEDED; driver view shows current first.
- DD-043: planner screens and existing tests unregressed.

## Open questions (answered 2026-10-06; see `user-decision-01.md`)

- Q-1: shared username/password auth, new DRIVER role. Q-2: in-app banner, no push. Q-3: same-branch only.
