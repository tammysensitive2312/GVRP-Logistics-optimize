# Handoff — DRIVER-DELIVERY-001 (awaiting user test)

Dispatcher-to-driver delivery, chặng 1 (backend + responsive web). No Flutter work in this run.

## Deploy first (required)

Rebuild + restart Entry API (new code + `2026-10-06-driver-delivery.sql`: `users.role` ENUM, `users.phone`, `route_sends` table — manual apply, no runner). Frontend served from this tree picks up automatically.

## Test steps (S-1..S-4 on a 2-route fixture)

1. Planner: create/enable a DRIVER user (needs phone) in the same branch (via existing user admin or DB).
2. Route tab → **Send to driver** → pick driver + revision → expect success toast + "Driver: X" on the card.
3. Log in as the driver (phone browser OK) → `/driver` shows only their routes; open one: stop order, address, ETA, customer phone/`tel:` link, notes.
4. Send the same route to another driver / again → old send SUPERSEDED; driver sees banner + flags.
5. Exceptional: try opening another driver's send id directly (expect no access); try sending an empty route (expect reject).

## Automated verification

Entry 14/14, client 140 + 5 pre-existing, tsc 0, budgets match baseline. Details: `verification.md`.

## Links

Requirements: `requirements-draft.md` · Design: `GVRP_Document/DRIVER_DELIVERY.md` + `../../GVRP_Document/diagrams` + `scenarios.md` · Traceability: `traceability.md` · Run: `work-items/DRIVER-DELIVERY-001/run.json`.

Reply with acceptance or rejection; silence is not acceptance.
