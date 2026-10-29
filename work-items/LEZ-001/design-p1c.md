# Design addendum — LEZ-001, increment P1c (client)

Status: technical design by Claude, 2026-10-28, within the accepted `design.md` (§7, §8, §9 D1, §15) and the user decisions in `user-decision-02.md` (D7, D8). Facts: `context-p1c.md`.
Kept outside `design.md` so the recorded design evidence does not change again.

## 1. Scope

Requirements R12 (driver and planner maps on stored server geometry and instructions) and R15 (visibility), plus the client side of R01–R05, R09, R11 and D4:

| Area | Content |
|---|---|
| Zones page (admin) | Templates (read-only, D2), enable (LINKED) / copy (COPIED), my zones with enable toggle, update notice + acknowledge, delete, map preview with depots, depot-in-zone warnings, verdict check for one vehicle and date |
| COPIED editor | Geometry by paste or file upload of GeoJSON with map preview (no drawing plugin, design §15); rules with a **structured form** (D7) |
| Vehicle types | Legal attributes in the admin dialog, suggestion shown but applied only on click (design §3.4), missing-data badge |
| Vehicles | Permits dialog per vehicle |
| Orders | "LEZ: <zone>" badge; client refusal of a multi-date selection and the server's D4 message on submit |
| Route tab | Compliance panel (per-route status, violations with stop and zone name, LEZ-unassigned orders, re-check); send disabled while a route is known not to be `COMPLIANT` |
| Maps | Planner map and driver view draw stored server polylines; driver view lists stored instructions; zone overlay on the planner map; a violation can be focused on the map |

Out of scope: P1-seed (Hanoi templates), any change to P1b behaviour, review-p1b F2–F8, Engine API, schema changes, the legacy client, onboarding (`/setup`) screens.

## 2. Server additions (read-only, DB-free tests)

### 2.1 Order zone membership (deviation from design §7)
Design §7 put `zoneCodes[]` on the order list. `OrderService`/`OrderController`/`OrderDTO` are covered by accepted tests that construct them directly, so P1c adds a **separate endpoint** instead and leaves the order code untouched:

`GET /api/v1/lez/order-zones?orderIds=1,2,3` → `[{ "order_id": 1, "zones": [{ "branch_zone_id": 17, "name": "…" }] }]`

- Branch from the principal only; null branch → 403 (existing `LezSupport.requireBranch`).
- 1 ≤ ids ≤ 200, else 400. Ids that are missing or belong to another branch are **omitted** (no existence leak, nothing about them is returned).
- Only **enabled** zones of the caller's branch; geometry through `ZoneRuleLoader.effectiveGeometry` (LINKED → template, COPIED → copy); membership with `GeoJsonGeometryParser.covers` (boundary = inside, same as the compiler).
- One row per returned order; `zones` empty when outside every zone. No coordinates in logs.

### 2.2 Compliance read additions
`SolutionComplianceDTO` gains, on both `GET …/compliance` and `POST …/recheck`:
- `zones`: `[{key, branch_zone_id, name}]` from the same compilation used for the hash (empty when there is none);
- `lez_unassigned`: `[{order_id, order_code, reason_code, reason}]` for the solution's unassigned rows whose `reason_code` starts with `LEZ_`.

The `STALE` / `UNCHECKED` logic and the constructor of `ComplianceReadService` do not change. `compliance()` compiles once and uses the result for both the hash and `zones`.

## 3. Client technical design

1. **One LEZ API service.** New `core/services/lez-api.service.ts` holds every LEZ call; `ApiService` is unchanged. Models in `core/models/lez.model.ts`, keys exactly as on the wire (snake_case; `selector`/`schedule` inner keys camelCase).
2. **No new dependency** (design §15). GeoJSON is drawn with `L.geoJSON`; polylines are decoded by a local `decodePolyline` (Google algorithm, precision 1e5, returns `[lat, lng]`).
3. **Text.** New screens use hard-coded English, like the existing admin and driver screens. `public/i18n` is outside the run scope. Server-produced texts (Vietnamese GraphHopper instructions, validation messages) are shown verbatim.
4. **Geometry source rule (maps).** A route uses server geometry only when **every** leg has a polyline. Then no OSRM request is made for it (AC-09). Otherwise today's OSRM / dashed-line behaviour is kept unchanged (routes made before P1b, or legs not yet routed). The planner draws legs in plan order from `GET /routes/{id}/geometry`; markers are unchanged.
5. **Driver instructions rule.** Server instructions replace OSRM maneuvers only when geometry is complete **and** at least one leg has a non-empty instruction list; the same `distance > 0` filter as today applies. Otherwise today's behaviour is kept.
6. **Send button rule (design §8).** When the compliance of the route is known and is not `COMPLIANT`, the send action is disabled with the reason (`VIOLATION`: list; `STALE`/`UNCHECKED`: "Recheck before sending"). When compliance is unknown (not loaded, request failed), today's behaviour is kept; the server guard is the authority (design §5.4). After a 422 on send, the panel reloads.
7. **Compliance panel placement.** A new component at the top of the Route tab, inside the existing `@if (solution())` block. It owns the compliance request and emits the result; `OrdersSection` passes it to `RouteView` through a new optional input (default `null` = unknown).
8. **Violation location.** `seq` is the 0-based plan index of the stop; the panel shows the stop's name from the route's stops in plan order and the zone **name** from `zones` (falls back to the key).
9. **D4 on the client.** "Plan routes" with selected orders on more than one `delivery_date` shows the dates and does not open the dialog. Submit errors show the server message (`error.error.errors[0].message`), falling back to today's text.
10. **Vehicle-type payload.** The dialog always sends the four legal keys (null when empty), so an edit never clears them by omission (P1a Deviation 9). `suggested_legal_class` is displayed and applied only by an explicit click.
11. **Rule form (D7).** Pure mapping functions convert the form model to the request and a `ZoneRuleDTO` back to the form. Empty selector fields are omitted (absent = no constraint); a fully empty selector is allowed but flagged "applies to every vehicle". No days selected → `daysOfWeek` omitted (= every day). `ALWAYS_FORBIDDEN` sends no intervals. Client checks mirror `RuleValidator` (start ≠ end, `24:00` only as end, ≥ 1 interval for the two interval types, ≤ 48 intervals, `effective_to` ≥ `effective_from`, ≤ 50 rules, weight measure and at least one bound together, non-negative numbers); the server stays the authority and its 400 text is shown.
12. **Permit schedule.** "Whole day" → `schedule: null`; "Only during" → `{type: "ALLOWED_ONLY", intervals, daysOfWeek?}` (P1a Deviation 2: a permit covers the minutes its schedule does not forbid). `ALWAYS_FORBIDDEN` is not offered.
13. **Existing specs (D8).** Only the five named specs may gain mock methods / providers in `beforeEach`. Everything new is designed so that a missing optional input keeps today's behaviour.

## 4. Test plan (summary; the full table is in `task-opencode-p1c.md`)

Server: membership (enabled only, boundary, other branch omitted, bounds, roles), compliance additions (zones, LEZ-only unassigned, recheck). Client: polyline decoder, rule mapping and validation, zones page, COPIED editor, vehicle-type dialog payload, permits dialog, order badge, D4 refusal, compliance panel, send rule, planner map source rule, driver view source rule. Browser check by the user after review.
