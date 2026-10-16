# Discovery — DRIVER-DELIVERY-001 (read-only)

Two read-only sweeps (delivery channels; driver plan data) performed 2026-10-06 in plan mode. No product file modified.

## What exists (VERIFIED)

- Users: roles `PLANNER`/`CUSTOMER` only (`UserRole.java`); users carry `email`, `slackUserId`, branch — no phone, no driver link.
- Vehicles/fleets: no driver FK, name, phone, or contacts.
- Notifications: email to job owner (Thymeleaf templates) + Slack branch channel via `JobCompletionEvent` fan-out; dispatcher→planner, never to drivers.
- No dispatch/send/share/assign-driver endpoint; orders-section Export/Assign actions are `Coming soon` toasts.
- Plan data per route is send-ready: ordered stops with sequence, type, location name + lat/lon, arrival/departure, demand, load-after, wait, distance/time-to-next; vehicle plate + depots. Customer phone/notes joinable via `orderId → Order`.
- Only driver handoff today: manual "Copy delivery stops" (names only).

## What does NOT exist (NOT_FOUND)

Driver role/directory/assignment/contacts, SMS/Zalo channels, driver screens, send history, plan version/supersede flags, delivery receipts.

## Scope consequence (accepted)

Chặng 1 needs: driver directory + DRIVER role/auth, per-send driver assignment, driver-scoped read APIs, versioned sends with supersede, responsive driver view. Flutter (chặng 2) reuses the driver APIs — no mobile work in this run.
