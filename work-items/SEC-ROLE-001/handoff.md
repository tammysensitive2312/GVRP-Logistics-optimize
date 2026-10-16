# Handoff — SEC-ROLE-001

Version: working tree on `fix/sec-role-001` at the `pass` transition (hash in `run.json`).

## User test (already performed 2026-10-16 23:48 before recording)

1. DRIVER token: `GET /api/v1/orders` → 403 with code `0040301`; `GET /api/v1/driver/routes` → 200.
2. PLANNER: normal web-client use unchanged.

## Known limitations

- Callback path protected by rule order, no dedicated test.
- `@MockBean` deprecation warnings in `RoleAccessTest`.
- SEC-009 (disabled users keep token access) open.
