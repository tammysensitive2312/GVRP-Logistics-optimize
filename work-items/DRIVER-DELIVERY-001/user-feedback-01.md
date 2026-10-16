# User feedback — DRIVER-DELIVERY-001 (failure report, 2026-10-08)

Source: user pasted Entry log testing the handoff.

- `POST /api/v1/routes/40602/send` → 500 `Unrecognized field "driverUserId"` (expected `driver_user_id`).
- Root cause: backend Jackson uses global SNAKE_CASE (`WebConfig`), but the new frontend `SendRoutePayload` used camelCase keys. Repo wire convention is snake_case (all other frontend models already snake).
- Why tests missed it: controller spec posts raw JSON through standalone MockMvc with a default (non-snake) mapper, so the mismatch never surfaced.
- Fix: snake_case payload keys on the frontend + pin the wire contract in the controller spec with a SNAKE_CASE mapper. No requirement/design change.
