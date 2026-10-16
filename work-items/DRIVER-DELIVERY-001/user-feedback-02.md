# User feedback 2 — DRIVER-DELIVERY-001 (gap report, 2026-10-08)

Source: user question "driver login kiểu gì?" — investigation found a real gap, treated as failure → repair path.

- Login itself works for DRIVER (same form; backend has no role restriction; disabled users blocked via `UserDetails.isEnabled`; JWT carries role).
- Gap: post-login landing sends EVERYONE to `/main` (planner dashboard). Drivers never reach `/driver` on their own, and `/main` has no role guard.
- Fix (within accepted scope, DD-002/DD-005): role-based post-login landing — DRIVER goes to `/driver` unless the stored return URL already targets a driver/legal route. Planner flow byte-identical. Server remains authoritative for data isolation.
