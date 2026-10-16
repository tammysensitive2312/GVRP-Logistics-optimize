 # Request — DRIVER-DELIVERY-001 (chặng 1: backend + web; Flutter là chặng 2)

Source: user messages 2026-10-05/06 (Vietnamese). Phase 1 business analysis; no implementation until requirements are explicitly accepted.

## Goal

Dispatcher sends detailed route plans to drivers; drivers view their assigned routes (responsive web in chặng 1; Flutter app in chặng 2 reusing the same driver-scoped APIs).

## User decisions recorded (2026-10-06, plan mode)

- Drivers: directory + DRIVER role; driver assigned per route at send time (not fixed per vehicle).
- Channel: driver screen (responsive web now; Flutter later — 2-phase approach accepted).
- Content: stop order, address, ETA + customer phone, delivery notes.
- Plan: latest (incl. manual revisions), older revisions selectable.
- No acknowledgement; plan changes notify a new version and mark the old superseded.

## Authorization boundary

- Phase 1 only until the requirements summary is confirmed. Later phases only after recorded acceptance.
- No optimization; no deployment. Vietnamese communication; English code/docs/artifacts.
