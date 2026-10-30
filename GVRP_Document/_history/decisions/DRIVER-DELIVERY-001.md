# DRIVER-DELIVERY-001 — route send and the driver delivery view

**Problem.** A planned solution existed only inside the planner UI. There was no way to hand a
specific revision of a plan to a driver, no record of what was handed over, and no driver-facing
view of the route, the stops and the delivery state.

**Decision.**

- A route send is an explicit, immutable record (`route_sends`) pointing at a solution revision, the
  driver and the sending user, with `sent_at` and a `SendStatus` of `CURRENT` or `SUPERSEDED`.
  Sending again supersedes the previous send for the same route instead of overwriting it
  (`BR-054`, `BR-055`).
- A send is refused when the solution has not been checked against low-emission zones, and it is
  refused when a concurrent send already moved the route on (`409`, `RevisionConflictException`).
- Driver endpoints are read-only and scoped to the authenticated driver:
  `GET /api/v1/driver/routes` and `GET /api/v1/driver/routes/{sendId}`. The planner list of drivers
  is `GET /api/v1/drivers`.
- The send payload carries the ordered stops with leg geometry and polyline-encoded paths so the
  driver view can render without a second planning round.
- The schema change ships as a manual migration, `2026-10-06-driver-delivery.sql`; the project has no
  migration runner, so deployment must apply it by hand.

**Date.** Accepted 2026-10-23 on the current code (handoff `947e3cdc…`); state `done` rev 18.

**Source.** `work-items/DRIVER-DELIVERY-001/` (deleted): `requirements-draft.md`, `scenarios.md`,
`verification.md`, `verification-rereview.md`, `findings-scope-drift.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D06-dispatch/` (whole domain), `BR-051`…
`BR-058`, `TC-D06-001`…`TC-D06-015`.

**Limitations recorded at closure.** Live behaviour on the current version was verified by the user,
not by an agent browser run. The role model for drivers was still open at that time and was closed
separately in SEC-ROLE-001.
