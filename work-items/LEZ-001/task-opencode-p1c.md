# Task for the engineer — LEZ-001, increment P1c (client + two read endpoints)

You are `gvrp-engineer` (or a Claude Code session acting as the engineer). Implementation of P1 is authorized by the user (2026-10-26 22:54 +07:00). This task is **increment P1c** (`design.md` §15). The user asked for it on 2026-10-28 07:43 +07:00 ("tôi muốn tạo P1c luôn, tạm thời bỏ qua việc test P1b"). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root.

**P1b is not runtime-verified.** The user postponed the P1b manual test (`review-p1b.md` §5). Do not change P1b behaviour (compiler, checker, certification hooks, send guard). If a P1b defect blocks P1c, stop and report it; do not fix it inside this task.

## Read first
1. `work-items/LEZ-001/design-p1c.md`: **the specification of this task**
2. `work-items/LEZ-001/user-decision-02.md` (D7 structured rule form, D8 spec setup permission)
3. `work-items/LEZ-001/context-p1c.md` (client facts and the API contracts you consume)
4. `work-items/LEZ-001/design.md` §7, §8, §9 (D1), §10, §12 (AC-04, AC-09), §15, §16
5. `work-items/LEZ-001/implementation.md` §P1a (Deviations 2, 3, 9), §P1b (N8), §P1b fix
6. `docs/engineering/LESSONS.md`: L-2026-001, -003, -004, -007, -009, -010, -011, -012, -014

If the design is wrong or impossible, stop and report. Do not redesign.

## Step 0 — preconditions
1. Run `node tools/session-brief.mjs` and include the output.
2. Run `git status --short` on branch `feature/lez-001`. Expected: the P1a/P1b/P1b-fix changes, `work-items/LEZ-001/*`, `docs/engineering/LESSONS.md`, `docs/security/SECURITY_CHECKLIST.md`, and one fix by Claude on 2026-10-28: `GVRP_Entry_API/.../entity/Route.java` (`lez_ruleset_hash` mapped as `columnDefinition = "CHAR(64)"` so `ddl-auto=validate` accepts the migrated `CHAR(64)` column; the app failed to start before it). If anything else is modified or untracked (apart from `GVRP_*/.claude/`, `diff1*.txt`, `.mcp.json`, `*.log`), stop and report.
3. Run `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json`. State must be `implement`. Warnings on `work-items/LEZ-001/*` files are expected; continue.
4. Stop any running `ng serve` (L-2026-011).

## Step 1 — baseline (before code changes)
- `GVRP_Entry_API` (JDK 21, L-2026-007): the user applied both LEZ migrations on 2026-10-28, so run the full `mvnw.cmd -B clean test`. Expected **246** run, 0 failures (243 + `DepotMapperTest` 2 + `GvrpEntryApiApplicationTests` 1). If the two `@SpringBootTest` classes fail on schema validation, record the exact message, then run the exclusion command `mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` and continue with that as the baseline.
- `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` (record total and every failing spec by name) and `npm run build` (record exit code and warnings). Do **not** run `npm install`; run `npm ci` only if `node_modules` is missing.
- Engine API is not touched. Do not run it.

## What to build

### Part B — Entry API (read-only additions)

**B1. Order zone membership** (`design-p1c.md` §2.1). New files:
- `service/lez/ZoneMembershipService.java` (same package as `ZoneRuleLoader`, so it can use its package-private helpers);
- `controller/LezMembershipController.java` with `GET /api/v1/lez/order-zones?orderIds=…` (comma-separated `Long`s);
- `dto/response/OrderZoneMembershipDTO.java` (`orderId`, `zones[{branchZoneId, name}]`).

Rules: the controller reads the branch only from `CurrentUserUtil.getCurrentBranchId()` and passes it, with the raw `orderIds` string, to the service (`LezSupport` is package-private, so guards and parsing live in the service); the service applies `LezSupport.requireBranch` and parses the list itself: 1…200 ids, else 400 via `LezSupport.invalid` (a malformed or missing list is 400, not 500); orders loaded with `orderRepository.findAllById` and **filtered in Java** to `order.getBranch().getId().equals(branchId)` (other branch or missing → omitted); only `enabled` zones from `BranchZoneRepository.findByBranchIdOrderByIdAsc`; zone name = template name for LINKED, copy name for COPIED (as `BranchZoneDTO`); `GeoJsonGeometryParser.covers`. `@Transactional(readOnly = true)`. No log line with coordinates or customer data.

**B2. Compliance additions** (`design-p1c.md` §2.2). Edit only `dto/response/SolutionComplianceDTO.java` (new fields `zones`, `lezUnassigned` with nested static classes) and `service/lez/ComplianceReadService.java` (`compliance()`, `recheck()`, `complianceOf()`): compile once, take the hash and `zones` from the same `CompiledRestrictions`; `lezUnassigned` from `solution.getUnassignedOrders()` with `reasonCode` starting `LEZ_` (order id, order code, reason code, reason). Constructor, `geometry()`, `STALE`/`UNCHECKED` logic unchanged.

### Part C — Client v2 (`GVRP_Entry_client_v2/src/app/`)

Follow `design-p1c.md` §3 for every rule below. New UI text in English (no `public/i18n` edits). Keep each new component's SCSS under the 4 kB budget warning where practical and **always** under the 8 kB error; never change `angular.json`.

**C0. Shared**
- `core/models/lez.model.ts` (new; export from `core/models/index.ts`): templates, branch zones, rules (selector/schedule camelCase inside), warnings, verdict (`forbidden` = `[start, end]` minute pairs), permits, compliance (incl. `zones`, `lez_unassigned`), route geometry, order membership. Verify the violation JSON keys against `RouteComplianceService.violationsJson` + `WebConfig.objectMapper` (expected `code, seq, order_id, zone_key, detail`) and record what you found.
- `core/models/vehicle.model.ts`: legal fields on `VehicleTypeInputDTO` (`legal_class`, `gross_weight_kg`, `energy_type`, `emission_standard`, all `… | null`) and `VehicleTypeDTO` (same + `suggested_legal_class`); `LEGAL_CLASSES`, `ENERGY_TYPES` constants. `core/models/driver.model.ts`: `DriverStopDTO` gains `geometry_to_next?`, `instructions_to_next?`, `zone_names?` (optional, so existing fixtures stay valid).
- `core/services/lez-api.service.ts` (new, `providedIn: 'root'`): every LEZ endpoint of `context-p1c.md` §2 plus B1.
- `shared/utils/polyline.utils.ts` (+ spec): `decodePolyline(encoded: string): [number, number][]`.
- `shared/utils/lez-format.utils.ts` (+ spec): minutes ↔ `HH:mm`, interval list text, status labels, violation text (code, stop name, zone name).

**C1. Zones page** — `features/admin/zones/` (new), route `admin/zones` in `app.routes.ts`, menu item "Low-emission zones" in `admin-layout.component.ts`.
- Templates table (code, name, jurisdiction, version, boundary confidence, legal basis); actions *Preview*, *Enable* (LINKED; disabled when a LINKED zone for that template exists), *Copy to edit* (COPIED). Empty state: templates are added by migration/seed only (D2).
- My zones table (name, template code, mode, enabled toggle via PATCH, effective version, "Template updated" badge when `update_available`); actions *View*, *Edit* (COPIED only), *Acknowledge* (when `update_available`; confirm shows the template version), *Delete* (confirm; 409 → show the server message).
- Leaflet preview map (same Google tile layer as `MapComponent`): selected geometry with `L.geoJSON` and the branch depots (`DepotStore`).
- `DEPOT_IN_ZONE` warnings from write responses → banner naming the depots.
- Verdict check: vehicle (from `ApiService.getVehicles`) + date → kind, forbidden intervals `HH:mm–HH:mm`, missing attributes, applied permits.

**C2. COPIED zone editor** (dialog or child route, your choice).
- Geometry: paste or upload (`.geojson`/`.json`, client limit 2 MB) → `JSON.parse` + type check (Polygon, MultiPolygon, Feature) → preview → *Save* (PUT geometry). Server 400 text shown verbatim.
- Rules (D7): structured form exactly as `design-p1c.md` §3.11; pure mapping in `zone-rule-form.utils.ts` (+ spec); *Save* sends the full list (PUT rules).

**C3. Vehicle-type legal attributes** — `features/admin/vehicle-types/**`: dialog section "Legal attributes (low-emission zones)" (legal class select with "Suggested: X" and a *Use suggestion* button, gross weight kg, energy type, emission standard; non-negative integers); missing-attributes hint; payload always carries the four keys (§3.10). Admin list: "LEZ data missing" badge when any of the four is null. `/setup` screens unchanged.

**C4. Permits** — `features/admin/vehicles/**`: *Permits* action per row → new `vehicle-permits-dialog` (list; add/edit/delete with confirm; zone select from the branch zones; permit no ≤ 100; valid from/to with to ≥ from; schedule per §3.12; server messages shown).

**C5. Order badge** — `orders-section` fetches membership for the loaded page ids through `LezApiService` (skip when empty) and passes a map to `orders-table` (new optional input); the order-code cell shows a chip "LEZ: name[, name]". A failed request shows no chip, only `console.warn`.

**C6. D4 on submit** — `orders-section.onPlanRoutesClick`: §3.9.

**C7. Compliance panel** — new `features/main/orders/lez-compliance-panel/` per §3.7–3.8: summary by status; per route (license plate from `solution.routes` by `route_id`), status chip, checked at, violations (code, stop name, zone name, detail); LEZ-unassigned orders (order code, reason); *Recheck now* (POST, busy state); clicking a violation calls the map focus of C9. When there are no zones and every route is `COMPLIANT`, one line "No low-emission zone applies to this plan." Emits the loaded compliance.

**C8. Route view** — new optional input `compliance` (default `null`): status chip per card; send rule §3.6; after a 422 on send, emit an event so the panel reloads.

**C9. Planner map** — `shared/services/map.service.ts`: geometry source rule §3.4 using `LezApiService.routeGeometry(route.id)` (no request when `route.id` is absent); same colour, weight, arrows and tooltip as today; enabled zones drawn once per `displaySolution` in their own layer (light red fill, name tooltip, cleared with the routes); `focusLeg(routeId, seq)` highlights that leg (or stop) and fits the bounds. Failures fall back to today's path silently (`console.warn`).

**C10. Driver view** — `features/driver/driver-view/**`: geometry and instruction rules §3.4–3.5 (no `getRouteGeometry` / `getRouteLegs` call when the server data is complete); stop list shows `zone_names` chips "LEZ: name". Fixtures without the new fields must behave exactly as today.

## Tests to add (new files; client: Jasmine/Karma with spies or `HttpTestingController`, no real HTTP; server: DB-free)

| ID | Case | Expected |
|---|---|---|
| PU-T1 | `decodePolyline` on the published Google example (literal below the table, same vector as Engine EB-T3); on `""` | `[[38.5,-120.2],[40.7,-120.95],[43.252,-126.453]]` (±1e-5); `[]` |
| RF-T1 | Rule form → request: ALWAYS_FORBIDDEN; no days; weight GROSS gt 3500; empty selector; round trip from a `ZoneRuleDTO` | no `intervals`; no `daysOfWeek`; `{"weightMeasure":"GROSS","weightKg":{"gt":3500}}`; `{}` + "every vehicle" flag; equal model |
| RF-T2 | Client validation: start == end; `24:00` as start; ALLOWED_ONLY without interval; to < from; 51 rules; measure without bound | each invalid, save blocked |
| ZP-T1 | Zones page with mocked `LezApiService` | templates and zones listed; *Enable* → create LINKED; *Copy* → create COPIED; 409 text shown; `DEPOT_IN_ZONE` banner names the depot; LINKED zone has no *Edit* |
| ZP-T2 | COPIED editor geometry | invalid JSON / Point → save blocked; Polygon → PUT geometry called with the parsed object |
| VT-T1 | Vehicle-type dialog | legal fields prefilled on edit; payload has the four keys (null when empty); suggestion shown and applied only after the click |
| VP-T1 | Permits dialog | whole day → `schedule: null`; intervals → `ALLOWED_ONLY` payload; delete only after confirm |
| OB-T1 | Order badge | membership requested with the page ids; chip rendered; error → no chip, no toast |
| JD-T1 | Plan routes with two delivery dates; server 400 on submit | no dialog, toast lists both dates; server message surfaced |
| CP-T1 | Compliance panel | statuses, stop names, zone names from `zones`, LEZ-unassigned listed; *Recheck* POSTs and re-renders; compliance emitted |
| RV-T1 | Route view send rule | VIOLATION / STALE / UNCHECKED → send disabled with reason; COMPLIANT → enabled; `compliance` null → today's behaviour |
| MP-T1 | Planner map source rule | complete legs → no `RoutingService.getRouteGeometry`; one leg null → OSRM path called (if Leaflet cannot run in Karma, extract the decision into a pure function and test that) |
| DV-T1 | Driver view | complete geometry + instructions → no `getRouteGeometry`/`getRouteLegs`, maneuvers from server text, zone chips rendered |
| OZ-T1 | `ZoneMembershipService`: two zones (one disabled), orders inside / outside / on the boundary | only the enabled zone's name; boundary inside; outside → empty `zones` |
| OZ-T2 | Other branch's order id; missing id; null branch; 0 and 201 ids; malformed list | omitted; omitted; 403; 400; 400; 400 |
| OZ-T3 | Roles on `/api/v1/lez/order-zones` (new `@WebMvcTest` class, same mechanism as `LezComplianceRoleAccessTest`) | DRIVER/CUSTOMER 403, anonymous 401, service not called |
| CR-T1 | Compliance additions (new test class, not an edit of `ComplianceReadServiceTest`) | `zones` = compiled zones; `lez_unassigned` only for `LEZ_*` codes; recheck returns both |

PU-T1 input (Google encoded polyline example):
```
_p~iF~ps|U_ulLnnqC_mqNvxq`@
```

If a DB-free or HTTP-free test of any row is impossible, stop and report.

## Allowed changes
- **Entry:** the B1 new files; `dto/response/SolutionComplianceDTO.java`; `service/lez/ComplianceReadService.java` (B2 only); new test classes.
- **Client:** the C0–C10 files named above and new files under `features/admin/zones/`, `features/admin/vehicles/` (permits dialog), `features/main/orders/lez-compliance-panel/`, `shared/utils/`; `app.routes.ts` (admin child route only); `orders-table` (chip + optional input); `orders-section` (C5, C6, C7 wiring); `route-view` (C8); new spec files.
- **Existing client specs (D8 only):** `orders-section.component.spec.ts`, `orders-section-pareto.component.spec.ts`, `route-view.component.spec.ts`, `driver-view.component.spec.ts`, `send-route-dialog.component.spec.ts` — add missing mock methods / providers in `beforeEach` only.
- **Work item:** `work-items/LEZ-001/implementation.md` (add `## P1c`; do not rewrite earlier sections), `work-items/LEZ-001/checkpoint-p1c.json`.

## Forbidden
- Editing, renaming, disabling, skipping, focusing or excluding any existing test, fixture or helper, apart from the D8 setup additions. Existing Java tests are not touched at all.
- `package.json`, `package-lock.json`, `angular.json`, `tsconfig*`, `public/**`; any new npm or Maven dependency (no `leaflet-draw`, no polyline/GeoJSON library).
- `SecurityConfig`, `pom.xml`, `application*.properties`, `.github/`, `AGENTS.md`, `docs/`, Engine API, migrations or any schema change, `OrderService`/`OrderController`/`OrderDTO`/`OrderMapper`.
- Any change to P1b behaviour (send guard, compiler, checker, certification hooks) or to review-p1b F2–F8.
- Sending polygons or rule data to the driver; logging coordinates, customer data, tokens or secrets.
- Committing, pushing, rewriting history; INSERT/UPDATE/DELETE/DDL against `gvrp_db`.

## Step 2 — implement
Order: B1–B2 → C0 → C3 → C1–C2 → C4 → C5–C6 → C7–C8 → C9 → C10. After Part B, run the Entry tests (Step 1 command) to green.

## Step 3 — verification
- Entry: the Step 1 command. Report counts (= baseline + new) and the new test names.
- Client: `npx ng test --watch=false --browsers=ChromeHeadless` → all green, count = baseline + new; `npm run build` → exit 0, no ERROR; list warnings that are new compared with the baseline.
- Static: `git diff --name-status` and `git status --short` show only allowed files; `router.project-osrm.org` still appears only in `routing.service.ts`; no new `console.log` with coordinates.
- A live browser check is **not** required from you; the user does it after the review. If you do one, record every database row it created (L-2026-012).

## Step 4 — record and checkpoint (no transition)
Add `## P1c` to `implementation.md` with: Scope, Baseline (both modules), Changes Made (start with the Claude `Route.java` fix of 2026-10-28 noted in Step 0), Commands Executed (with exit codes, incl. Steps 0–1), Verification Results, Modified Tests (every D8 setup addition by spec file, reason "D8 setup only"; nothing else), Security-Relevant Changes (B1 endpoint, tenant scoping, roles), Deviations From Design (must include: separate membership endpoint instead of `zoneCodes[]` on orders; English UI text), Remaining Uncertainty (must include: P1b runtime behaviour NOT_VERIFIED because the user postponed its manual test; P1-seed not done, so the zones page lists only templates inserted by hand; the violation JSON keys you found), Lesson Candidates, Handoff to Reviewer.

Then create `work-items/LEZ-001/checkpoint-p1c.json` (current run revision; `next_action`: "Claude reviews P1c; user runs P1b review §5 and the P1c browser check") and run:
```
node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1c.json
```
**Do not** transition to `verify` (P1-seed and the P1b manual test are still open). Tell the user you are done.
