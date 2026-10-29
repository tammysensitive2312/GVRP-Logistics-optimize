# Browser test scenarios — MAP-ORDERS-001 + BROWSER-FIX-001

Setup: branch `fix/browser-fix-001`; **restart the Entry API** (JDK 21) so E1–E7 are loaded; Engine running; `ng serve` running (hot reload). Planner account of Branch Huge 1. Test data: all orders are on 2026-08-21; orders `ORD-00378*`, `ORD-00349*`, `ORD-00032*` are no longer `SCHEDULED` (jobs #88–#90); the COPIED zone covers lon 105.825–105.830, lat 20.990–20.995 with an every-vehicle all-day ban.

## A. Map follows the table (MAP-ORDERS-001)

| ID | Steps | Expected |
|---|---|---|
| A1 | Open `/main` | Date filter = today (local); Add Order default date = today |
| A2 | Set date 08/21/2026 | Map shows 5 order markers = the 5 table rows |
| A3 | Page 2; then page size 20; then search `ORD-00349` | Markers follow each reload (5 → 20 → 23 matching rows on the page) |
| A4 | History → View solution of job #90 → Route tab; Orders tab, change page; Clear solution | Order markers hidden while the solution is shown; after clearing: depots + the current page's orders |

## B. Defect fixes (BROWSER-FIX-001)

| ID | Steps | Expected |
|---|---|---|
| B1 | Add Order with code `LEZT-01`, name, **phone `0912345678`**, address, lat 20.9915, lon 105.8265, demand 10 | Created; visible on today's date (switch filter); `LEZ: …` chip |
| B2 | Add Order `LEZT-02` as B1 but **phone empty** | F1 check: expected today **400 "Invalid phone number format"** (record what you see) |
| B3 | Add Order `LEZT-03` with lat `95` | 400; toast "Latitude must be between -90 and 90" (not "Internal server error.") |
| B4 | Date 08/21, search `ORD-00837`, Select All Orders | Toast "Selected N orders…"; notice line; Plan Routes badge N; change page → notice gone, count 0 |
| B5 | Date 08/21, status All, Select All Orders (25 054) | Same as B4 with 25 054; do **not** start the plan |
| B6 | B4 selection + 4 vehicles → Plan Routes | Total Demand at most 2 decimals |
| B7 | Search `ORD-00378`, select page, plan | Toast "Only SCHEDULED orders can be planned… ORD-… (+N more)." |
| B8 | Admin → Vehicles | Capacity column shows numbers, e.g. `5000 kg` |
| B9 | Vehicles → Permits → add a permit → check the row → delete it | Edit and 🗑️ visible without horizontal scroll |
| B10 | Admin → Zones → Edit the COPIED zone | "Name" field = "Test zone"; rename to `Test zone copy HN` → saved; list shows the new name |
| B11 | Edit again, name `test zone` (lower case) | Error "Another zone of this branch is already named test zone"; nothing else saved |
| B12 | Edit, blank name | "Name is required.", no request |
| B13 | Templates → Copy | New zone named `Test zone (copy)` (or `(copy 2)`); delete it afterwards |
| B14 | Plan B4's orders (FAST, allow unassigned) | Job monitor: unserved alert has the LEZ sentence; Route tab panel: `ORD-00837296 — No compliant vehicle for zone "Test zone copy HN"`; "Zones in force" shows two different names |
| B15 | Recheck a route with a violation (e.g. job #90) | Details read `… zone "Test zone copy HN" …`, no `Z2` |

Record each result (PASS / FAIL + what you saw) in `review.md` §Results, and every row created (orders `LEZT-*`, jobs, permit, zone copy).
