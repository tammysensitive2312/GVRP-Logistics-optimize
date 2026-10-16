# User feedback 3 — BULK-EDIT-001 (failure report, 2026-10-11)

Source: user live test — bulk edit fails with "Invalid CORS request".

- Root cause: `WebConfig.corsConfigurer` allows `GET/POST/PUT/DELETE/OPTIONS` but not `PATCH`. This run introduced the first PATCH endpoint (`PATCH /orders/status`), so the browser preflight fails before auth. No prior endpoint ever needed PATCH, which is why nothing caught it.
- Fix: add `PATCH` to allowed methods. No requirement/design change (PATCH was the designed verb).
