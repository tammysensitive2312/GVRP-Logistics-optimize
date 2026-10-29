# Design — LEZ-001 (Low-emission / restricted traffic zones)

Status: **ACCEPTED** by the user on 2026-10-26 22:49 +07:00 ("tôi đồng ý cả 3 đề xuất cho 3 câu hỏi của bạn. Tôi duyệt thiết kế của bạn"). D1–D3 were resolved as recommended (§9; `user-decision-01.md`). Accepting the design does **not** authorise implementation (request §8).
Inputs: `request.md` (requirements LEZ-R01…R18 and the decisions of 2026-10-25) and `context.md` (code facts at `617b720`).
Business flows written for reading are in `business-flows.md`. Diagrams are in `diagrams/` (§14).

Labels: **[code]** = verified by static inspection in `context.md`. **[proposal]** = new design. **[assumption]** = not verified.

---

## 1. Principles

1. **Entry compiles and certifies; Engine optimises.** The Entry API owns the law: templates, branch zones, rules, permits and vehicle legal attributes. For one job it compiles these into a *date-specific, law-free* restriction set: "vehicle *v* must not be present in polygon *z* during intervals *I*". The engine only enforces that set. The **final compliance verdict** comes from one place, the Entry `ComplianceChecker`, which runs on stored geometry. Optimisation, manual edits and sending therefore share one checker.
2. **Hard.** No soft or warning mode. A plan with a violation can be saved for inspection, but it **cannot be sent**.
3. **One path.** The path that is checked is the path that is shown. The engine returns GraphHopper leg geometry and instructions, the Entry API stores them, and both maps render them (R12).
4. **Explainable later.** Every job stores the compiled restriction set and the template versions it used (R13).
5. **Law as data.** Rules are rows with a selector, a schedule and an effective range. A new city or a new phase means new seed data, not new code.

## 2. Domain model (Entry API) — [proposal]

See `diagrams/lez-domain-model.puml`.

### 2.1 System level (no `branch_id`; read-only for branches; written only by migration or seed in P1)

**`zone_template`**: `id`, `code` (unique, e.g. `HN-LEZ-VD1-KV1`), `name`, `jurisdiction` (e.g. `VN-HN`), `geometry` (`MULTIPOLYGON`, SRID 4326, lon/lat), `version` (int, +1 on every change), `boundary_confidence` (`SURVEYED | DIGITISED_APPROX | WARD_PROXY`), `boundary_source` (text, e.g. "QĐ 3273/QĐ-UBND map, digitised 2026-11"), `legal_basis`, `created_at`, `updated_at`.

**`zone_rule_template`**: `id`, `zone_template_id`, `template_version` (the rule set belongs to one template version), `selector` (JSON, §3.1), `schedule` (JSON, §3.2), `effective_from` (date), `effective_to` (date, null = open), `legal_basis`, `note`.

Phases of the Đề án are modelled with **effective dates**, not as separate templates. Example: `HN-LEZ-VD1-KV3` has rules effective from 2027-01-01. A wider "Vành đai 1" template has rules effective from 2028-01-01. One branch can enable both today, and the engine applies each rule only on dates where it is effective.

### 2.2 Branch level (every row has `branch_id`, taken from the principal)

**`branch_zone`**: `id`, `branch_id`, `mode` (`LINKED | COPIED`), `source_template_id`, `source_template_version` (for `COPIED`: the version it was copied from; for `LINKED`: null, because it always follows the latest version), `name` (copy only), `geometry` (copy only), `enabled` (bool), `acknowledged_template_version` (copy only, §6), timestamps. Unique key: (`branch_id`, `source_template_id`, `mode` = `LINKED`).

**`branch_zone_rule`**: rows for `COPIED` zones only. Same columns as `zone_rule_template`, plus `branch_id`.

**`vehicle_permit`**: `id`, `branch_id`, `vehicle_id` (must belong to the same branch), `branch_zone_id` (must belong to the same branch), `permit_no`, `schedule` (JSON, null = any time), `valid_from`, `valid_to`, `note`.

### 2.3 Changes to existing entities

- **`VehicleType`**, legal attributes separate from `capacity` (R04):
  - `legal_class` (`MOTORBIKE | CAR | TRUCK | BUS | NON_MOTORIZED`, nullable). The UI suggests a default from `VehicleCategory` (§3.4) and the user must confirm it.
  - `gross_weight_kg` (int, nullable).
  - `energy_type` (`ELECTRIC | HYBRID | PETROL | DIESEL | OTHER`, nullable). The existing energy skills stay for compatibility.
  - `emission_standard` (int level, nullable).
- **`Route`**: `lez_status` (`UNCHECKED | COMPLIANT | VIOLATION | STALE`), `lez_checked_at`, `lez_ruleset_hash`, `lez_violations` (JSON list of violations, §5.3).
- **`RouteStop`**: `geometry_to_next` (encoded polyline of the leg to the next stop), `instructions_to_next` (JSON list of turn instructions). Both come from GraphHopper.
- **`OptimizationJob`**: `lez_snapshot` (JSON: service date, compiled restrictions, zone keys with template id and version, permits applied, compiler version).
- **`UnassignedOrder.reason`**: free text today [code]. Add a machine code `reason_code` (e.g. `LEZ_NO_COMPLIANT_VEHICLE`) and keep the human text.

Migration: one dated file in `GVRP_Entry_API/src/main/resources/database/migrations/` following the existing naming [code]. The Hanoi seed goes in a separate dated file.

## 3. Rule model and evaluation

### 3.1 Selector — all present fields must match (AND)

```json
{ "legalClasses": ["TRUCK"], "energyTypes": ["PETROL","DIESEL"],
  "weightMeasure": "GROSS", "weightKg": {"gt": 3500},
  "emissionStandardBelow": 4, "appBased": true }
```

- `weightMeasure` is `GROSS` or `PAYLOAD`. The seed uses `GROSS` for the ambiguous "2 tấn" rules (request §4.3). `PAYLOAD` would read `capacity` and is offered only so a rule can follow a text that names payload.
- `appBased` matches the branch's operating mode. Every branch in scope is an internal fleet, so `appBased: true` never matches (request §6).
- **Missing data:** if a selector field needs an attribute that is null on the vehicle type, the vehicle is treated as **matching**, i.e. restricted, and the result records `MISSING_ATTRIBUTE:<name>` (R04).

### 3.2 Schedule

```json
{ "type": "ALWAYS_FORBIDDEN" }
{ "type": "FORBIDDEN_DURING", "daysOfWeek": ["MON",...], "intervals": [["06:00","09:00"],["16:00","19:30"]] }
{ "type": "ALLOWED_ONLY",     "intervals": [["21:00","06:00"]] }
```

- Each rule is normalised to a **forbidden interval set** on the service date, in minutes of the day [0, 1440). `ALLOWED_ONLY` becomes its complement. An interval that crosses midnight is split (21:00–06:00 → [1260,1440) and [0,360)).
- `daysOfWeek` is absent → every day.
- Several rules on one zone combine by **union** of forbidden intervals.

### 3.3 Evaluation — `diagrams/lez-activity-rule-evaluation.puml`

For vehicle *v*, zone *z*, service date *d*:

1. Take the rules of *z* (template rules for `LINKED` at the latest version; branch rules for `COPIED`) with `effective_from ≤ d ≤ effective_to`.
2. Keep the rules whose selector matches *v*'s vehicle type (missing attribute = match).
3. Forbidden(v,z,d) = ⋃ normalised forbidden intervals of those rules on *d*.
4. Subtract the windows of every valid permit of *v* for *z* on *d* (a permit with no schedule covers the whole day).
5. Result: `ALLOWED` (empty set), `ALL_DAY` (the whole [0,1440)), or `PARTIAL(intervals)`.

### 3.4 Default `legalClass` suggestion (the user confirms it; not applied silently)

`BICYCLE → NON_MOTORIZED`, `MOTORCYCLE → MOTORBIKE`, `CAR → CAR`, `VAN | PICKUP_TRUCK | BOX_TRUCK | FLATBED_TRUCK | TANKER_TRUCK | SEMI_TRUCK → TRUCK`. A vehicle type with a null `legal_class` is restricted by every rule that names a legal class.

### 3.5 Hanoi seed (R16) — [assumption: Đề án content as summarised in `request.md` §5]

Peak hours are 06:00–09:00 and 16:00–19:30. Rules effective from 2027-01-01 on the Đề án phase-2 areas, and from 2028-01-01 on the Vành đai 1 template:

| Selector | Schedule |
|---|---|
| MOTORBIKE, PETROL/DIESEL, `appBased` | ALWAYS_FORBIDDEN |
| MOTORBIKE, `emissionStandardBelow` 3 | ALWAYS_FORBIDDEN |
| CAR, PETROL/DIESEL, `emissionStandardBelow` 4 | ALWAYS_FORBIDDEN |
| TRUCK, GROSS > 3500 | ALWAYS_FORBIDDEN |
| TRUCK, GROSS 2000–3500 | ALLOWED_ONLY 21:00–06:00 |
| TRUCK, GROSS < 2000 | FORBIDDEN_DURING peak hours |

The motorbike emission rule assumes a fossil-fuel engine. The selector should add `energyTypes: [PETROL, DIESEL]`, so that electric motorbikes are not affected.

## 4. Restriction compilation (Entry) — `diagrams/lez-seq-optimization.puml`

`RestrictionCompiler.compile(branch, serviceDate, vehicles, depots, orders)` produces `CompiledRestrictions`:

```json
{ "serviceDate": "2027-03-04", "compilerVersion": 1,
  "zones": [{ "key": "Z17", "branchZoneId": 17, "templateId": 3, "templateVersion": 2, "geometry": "<GeoJSON MultiPolygon>" }],
  "vehicleRestrictions": [{ "vehicleId": 42, "zoneKey": "Z17", "kind": "ALL_DAY" },
                          { "vehicleId": 43, "zoneKey": "Z17", "kind": "PARTIAL", "forbidden": [[360,540],[960,1170]] }],
  "orderZones": { "1001": ["Z17"] },
  "depotZones": { "5": ["Z17"] },
  "unusableVehicles": [{ "vehicleId": 44, "reason": "DEPOT_IN_ZONE_ALL_DAY", "zoneKey": "Z17" }],
  "warnings": [{ "vehicleTypeId": 9, "code": "MISSING_ATTRIBUTE", "attribute": "grossWeightKg" }] }
```

- **Service date:** a job is planned for one `deliveryDate`. Mixed dates in one job are refused before the solver runs. [assumption: today's job flow already uses one date per job. Chưa xác minh.]
- **Membership:** order and depot points are tested against zone polygons in Java with JTS (planar lon/lat), avoiding MySQL geographic predicates (`context.md` §3).
- **Depot in zone (R09):** a vehicle whose depot lies in a zone where it is `ALL_DAY` restricted cannot start its route, so it is listed in `unusableVehicles` and left out of the problem. With `PARTIAL`, the route start and end times must fall outside the forbidden intervals; this is a P2 constraint.
- **Snapshot (R13):** the result plus its hash is written to `OptimizationJob.lez_snapshot` before dispatch.

## 5. Enforcement and certification

### 5.1 Engine, P1: all-day bans as skills

- For every zone key *Z*: an order in *Z* gets the required skill `LEZ_Z`. A vehicle gets skill `LEZ_Z` unless it is `ALL_DAY` restricted in *Z*. This uses the existing `addRequiredSkill`/`addSkill` [code]. Vehicles are modelled one by one (`vehicle-<id>`) [code], so per-vehicle permits are exact.
- An order inside two zones requires both skills.
- `unusableVehicles` are not added to the problem.
- **Unassigned reason:** if an order ends unassigned and **no** vehicle in the request carries all its LEZ skills, the reason is `LEZ_NO_COMPLIANT_VEHICLE` with the zone keys. Otherwise the existing diagnostics run.
- **Post-solve assert**, like `assertMaxDistances` [code]: no LEZ-skilled order on a vehicle without the skill.

### 5.2 Engine: per-leg geometry and instructions (R08, R12)

After solving (and in `evaluate`), the engine routes each consecutive stop pair of each final route once more with GraphHopper, using profile `car` and locale `vi` [assumption: Vietnamese translation quality Chưa xác minh]. It keeps the points as an encoded polyline and the instruction list. `RouteDetail` gains `legs[{fromSeq, toSeq, polyline, instructions[]}]`. The geometry comes from the same router and profile as the matrix, so the travel times and the drawn path agree. This adds one request per leg, cheap with CH.

### 5.3 Entry: `ComplianceChecker`, the single verdict

Input: a route (stops with times and leg polylines), its vehicle, and a `CompiledRestrictions`. Output: a list of violations:

| Code | When |
|---|---|
| `ZONE_ACCESS` | An order stop (or depot visit) lies in *Z* and the vehicle is `ALL_DAY` restricted there, or [arrival, departure] overlaps its `PARTIAL` forbidden intervals. |
| `ZONE_TRANSIT` | A leg polyline intersects *Z* (JTS `intersects`) and the vehicle is `ALL_DAY` restricted there, or [leg departure, next arrival] overlaps its `PARTIAL` forbidden intervals. This approximates the time spent inside the zone (request §4). |
| `LEZ_DATA_MISSING` | The decision depends on a missing legal attribute. It blocks like a violation and names the attribute. |

**Phase note (design decision):** the checker evaluates **both** `ALL_DAY` and `PARTIAL` restrictions in **P1**. What waits for P2 (R07) is only the *optimiser* avoiding `PARTIAL` conflicts. Until then, a time-window conflict is caught at certification and blocks sending. This follows the hard mode, because a P1 plan can never be sent in breach of a seeded time-of-day rule. It is a strengthening of R08's phase split and is recorded in `traceability.md`.

The result is stored on `Route` with the ruleset hash. **Triggers:** (a) the optimisation callback materialises a solution; (b) `RouteCustomizationService.saveRevision` [code]; (c) **every send**; (d) on demand from the compliance panel.

`lez_status` lifecycle: `diagrams/lez-state-route-compliance.puml`. **`STALE`** means the ruleset hash at check time differs from the current one, because a template, copy, permit or vehicle attribute changed. A stale route is re-checked at send.

### 5.4 Send guard (R11) — `diagrams/lez-seq-edit-and-send.puml`

`RouteSendService.send` [code] gets one more step before the supersede/insert block: compile the restrictions for the route's delivery date with the **current** data, run `ComplianceChecker`, persist the result, and refuse with **422** (the existing `UnprocessablePlanException` [code]) carrying the violations when any violation or missing-data finding exists. This also catches permits that expired, or rules that changed, after planning.

### 5.5 Engine, P2: time-window restrictions (R07)

- A `HardActivityConstraint` refuses inserting an in-zone order into a vehicle with `PARTIAL` restriction when [arrival, departure] overlaps the forbidden set.
- Inserting a stop shifts the times of later stops, so the constraint must also check the later in-zone stops (using jsprit route state for propagated times) and the depot return when the depot is in the zone.
- A post-solve assert remains the safety net, and the Entry checker remains the verdict.

## 6. Shared templates, linking and copying (R01, R02) — `diagrams/lez-seq-zone-setup.puml`, `diagrams/lez-state-branch-zone.puml`

- **Enable (LINKED):** this creates a `branch_zone` row that points at the template. Rules and geometry are always read from the latest template version. Disabling only flips `enabled`.
- **Copy (COPIED):** this creates a `branch_zone` row with its own `geometry` and `branch_zone_rule` rows copied from the template's latest version. The copy records `source_template_version`. Editing is allowed.
- **Template update:** a migration raises `zone_template.version`. Linked zones change immediately. Every route that used the zone becomes `STALE` through the ruleset hash. Copied zones are **never overwritten**. While `source_template_version < template.version` and `acknowledged_template_version < template.version`, the zone list shows "template updated", offers a side-by-side diff, and lets the planner acknowledge or re-copy.
- Rules effective in the future need no action: the date filter in §3.3 applies them when their date arrives.

## 7. API — [proposal]

Entry API. All endpoints are authenticated and PLANNER-only unless marked. The branch always comes from the principal.

| Method and path | Purpose |
|---|---|
| `GET /api/v1/zone-templates` | List templates (summary, version, confidence) |
| `GET /api/v1/zone-templates/{id}` | Template detail: geometry and rules of the latest version |
| `GET /api/v1/zones` | The branch's zones, with an "update available" flag |
| `POST /api/v1/zones` `{templateId, mode}` | Enable (LINKED) or copy (COPIED) |
| `PATCH /api/v1/zones/{id}` `{enabled}` | Enable or disable |
| `PUT /api/v1/zones/{id}/geometry`, `PUT /api/v1/zones/{id}/rules` | Edit a copy (refused for LINKED) |
| `POST /api/v1/zones/{id}/acknowledge` `{version}` | Acknowledge a template update for a copy |
| `GET/POST/PUT/DELETE /api/v1/vehicles/{vehicleId}/permits[/{permitId}]` | Manage permits |
| `GET /api/v1/solutions/{id}/compliance` | Per-route status and violations, with a re-check option |
| `GET /api/v1/routes/{id}/geometry` | Leg polylines and instructions (planner map) |
| existing `GET /api/v1/driver/routes/{sendId}` (DRIVER) | Adds leg polylines and instructions (driver map) |
| existing vehicle-type endpoints | Add the legal attributes |
| existing order list | Each order gains `zoneCodes[]` for the badge |

Engine API: `EngineOptimizationRequest` gains `serviceDate`, `zones[]` and `vehicleRestrictions[]` (the compiled form, without law). `EngineOrderDTO` gains `zoneKeys[]`. The response `RouteDetail.legs[]` and the evaluate response gain leg geometry. The engine does not run LEZ checks in `evaluate`; Entry does (§5.3).

## 8. UI (client v2) — [proposal]

- **Zones page** (admin layout): template list with confidence and legal basis. Actions: *Enable*, *Copy to edit*. Leaflet map preview. A copy editor needs a drawing plugin, which is a new dependency to verify (AGENTS.md §15). A template-update badge with diff and acknowledge.
- **Vehicle-type dialog:** a "Legal attributes" section with the suggested `legalClass` and a warning when anything is missing.
- **Vehicle → Permits** tab.
- **Orders table:** an "LEZ: <zone>" badge.
- **Job result / route view:** a compliance panel (orders in zones, unassigned for LEZ by zone, routes with `VIOLATION`/`STALE`). A violation list per route, with the offending leg highlighted on the map.
- **Send dialog:** disabled with the reason while the route is not `COMPLIANT`. The server is still the authority (§5.4).
- **Maps:** the driver view (R12) and the planner map (D1, accepted) render the stored server polylines. The driver view renders the stored instructions. OSRM is no longer called for these routes.

## 9. Decisions (resolved 2026-10-26 22:49, `user-decision-01.md`)

- **D1 — yes:** the **planner** map also renders the stored server geometry. The planner sees exactly the certified path. R12 scope therefore covers both the driver view and the planner route view.
- **D2 — yes:** zone templates are authored only through migrations and seed data until an administrator role exists (request §6).
- **D3 — yes:** this run (LEZ-001) covers **P1 only**. P2 (optimiser avoidance of time windows), P3 (avoidance routing) and P4 (reporting and experiment) become separate linked runs.

## 10. Tenant isolation and security (R14)

- Every branch table has `branch_id`. Repositories use `findByIdAndBranchId`. The branch comes from the principal only.
- A permit must reference a vehicle **and** a zone of the same branch, checked on the server.
- A copy is created inside the caller's branch only, and templates are read-only.
- A LINKED zone of branch A never exposes data of branch B: templates carry no branch data.
- New tests: branch A cannot list, read, edit, copy-into or reference (through a permit) branch B's zones or permits. Driver geometry is served only for the driver's own send [code: existing driver ownership check].
- Bound inputs: polygon vertex count, rules per zone, permits per vehicle.
- No customer data in logs. Polygons are not personal data, but order coordinates are (AGENTS.md §15).

## 11. Failure cases and edge cases

| Case | Behaviour |
|---|---|
| No compliant vehicle for an in-zone order | Unassigned, `LEZ_NO_COMPLIANT_VEHICLE` + zone |
| Every vehicle unusable (depot in zone, all-day ban) | Job completes with every order unassigned. The compliance panel explains the depot-in-zone cause |
| Missing legal attribute | Restricted. `LEZ_DATA_MISSING` names the attribute. The vehicle-type dialog shows a warning |
| Order on a zone boundary | JTS `covers` counts the boundary as inside, which is the conservative reading |
| GraphHopper cannot route a leg | `ROUTING_UNAVAILABLE` (existing) [code]. The compliance status cannot be `COMPLIANT`, so sending is blocked |
| Leg crosses a banned zone (P1, no avoidance) | `ZONE_TRANSIT`. The planner must change the order sequence or vehicle, or add a permit. P3 removes most of these cases |
| Rules, permits or templates change after planning | Route `STALE`. Re-checked at send. Refused if it is now violating |
| Template updated while a branch has a copy | Notice only, never overwritten |
| Job with mixed delivery dates | Refused before dispatch [assumption, §4] |
| Midnight-crossing interval | Split (§3.2) |

## 12. Acceptance cases (for verification of P1)

- **AC-01** A petrol truck with gross weight 4,000 kg and an EV van, one order inside Z (all-day truck ban): the EV gets the order, the truck does not.
- **AC-02** Same as AC-01 without the EV: the order is unassigned with `LEZ_NO_COMPLIANT_VEHICLE`, Z.
- **AC-03** The truck has a valid permit for Z: it may take the order. The permit has expired at the send date: the send is refused with 422.
- **AC-04** A vehicle type with null `gross_weight_kg` against a weight rule: restricted. The UI names the missing attribute.
- **AC-05** Two stops outside Z whose GraphHopper path crosses Z, on a banned vehicle: `ZONE_TRANSIT`, and the send is refused.
- **AC-06** Depot inside Z, all-day banned vehicle: the vehicle is listed as unusable and gets no route.
- **AC-07** A LINKED zone after a template version bump: the route becomes `STALE` and is re-checked at send. A COPIED zone shows the notice and its geometry is unchanged.
- **AC-08** Branch A cannot read, modify or reference branch B's zones or permits, by ID or in lists.
- **AC-09** The driver view draws the stored polyline and instructions. No OSRM request is made for that route.
- **AC-10** The job's `lez_snapshot` reproduces the restrictions that were used, after the template changes.
- **AC-11** A rule effective from 2028-01-01: it is ignored for a 2027 service date and applied for a 2028 one.
- **AC-12** A truck with gross weight 1,800 kg whose stop in Z is at 07:30 (peak, `PARTIAL`): `ZONE_ACCESS`, and the send is refused. The same stop at 10:00 is compliant.

## 13. Assumptions and known limitations

- **Approximate transit timing:** presence in a zone is approximated by leg [departure, arrival] (request §4).
- **One `car` profile** for every vehicle [code]: motorbike and truck paths are car paths.
- **P1 detects transit but does not avoid it.** Plans for banned vehicles near zones may need manual fixes until P3.
- **P3 needs more prepared profiles,** because CH fixes the weighting [code], and a graph rebuild when zones change.
- **Hanoi rule content and polygons** come from secondary sources and approximate digitisation (`boundary_confidence`). This is not legal advice.
- **The planner can still save a violating revision** for inspection; only sending is blocked. This is how the hard mode is applied here; confirm it during review.

## 14. Diagram index (PlantUML source + rendered SVG in `diagrams/`)

| File | Type | Shows |
|---|---|---|
| `lez-component` | component | Where each LEZ component lives (Client, Entry, Engine, DB, GraphHopper) |
| `lez-domain-model` | class | Templates, branch zones, rules, permits, legal attributes, snapshot, compliance fields |
| `lez-seq-zone-setup` | sequence | Enable/copy a template, template update, acknowledge |
| `lez-seq-optimization` | sequence | Submit → compile → engine (skills, geometry) → callback → certify |
| `lez-seq-edit-and-send` | sequence | Manual edit → evaluate → certify → send guard |
| `lez-seq-driver-view` | sequence | Driver opens a route → stored server geometry and instructions |
| `lez-activity-rule-evaluation` | activity | How one (vehicle, zone, date) is evaluated |
| `lez-activity-daily-planning` | activity (swimlanes) | The planner's day with LEZ, end to end |
| `lez-state-route-compliance` | state | `UNCHECKED / COMPLIANT / VIOLATION / STALE` |
| `lez-state-branch-zone` | state | LINKED/COPIED lifecycle and template-update notice |

Diagrams marked *proposal* show intended design. Participants that exist today are named after real classes [code]; new ones are marked «new».

## 15. P1 implementation increments and technical refinements (added 2026-10-26, after acceptance)

Implementation was authorized on 2026-10-26 22:54 +07:00 ("hãy bắt đầu triển khai P1"). The run stays in `implement` across three increments. Each increment is reviewed by Claude before the next task is issued, and only after P1c does the run move to `verify`.

| Increment | Content | Requirements |
|---|---|---|
| **P1a** | Schema and entities (templates, rules, branch zones, branch rules, permits, vehicle-type legal attributes); GeoJSON polygon parsing; `RuleEvaluator` + schedule normalisation (pure, unit-tested); `ZoneService` + controllers (templates read-only, enable/copy/edit/acknowledge/delete); permits API; legal attributes on the vehicle-type API; depot-in-zone warning on enable/copy | R01–R05, R09 (warning), R14 |
| **P1-seed** | Hanoi templates and rules (Đề án phases 2–3). The **boundary data is prepared by Claude from a documented source** before the seed SQL is written. Coordinates are never invented | R16 |
| **P1b** | Engine: `serviceDate`, zones and compiled restrictions in the request, `LEZ_<zone>` skills, unusable vehicles, post-solve assert, `LEZ_NO_COMPLIANT_VEHICLE`, per-leg geometry and instructions (locale `vi`) in results and evaluate. Entry: `RestrictionCompiler`, `ComplianceChecker`, job snapshot, route compliance fields, certification on callback and on revision save, send guard (422) | R06, R08, R09, R10, R11, R13 |
| **P1c** | Client: zones page, legal attributes in the vehicle-type dialog, permits tab, order badge, compliance panel, driver and planner maps on stored server geometry and instructions | R12, R15 |

Technical refinements (implementation choices, not business changes):

- **No new dependency in P1.** `pom.xml` and `package.json` are outside the run scope.
  - A copied zone's geometry is edited by **uploading or pasting GeoJSON** (with a Leaflet preview). An in-map drawing tool needs a new dependency and is deferred to a later run.
  - GeoJSON is parsed with Jackson into JTS, because `jts-core` has no GeoJSON reader.
  - The client decodes encoded polylines with a small local function.
- **Schema delivery** follows the repository convention: entities plus a dated SQL file in `database/migrations/` that the user runs manually (`ddl-auto=update` covers new tables and columns) [code: `2026-10-18-pareto-candidates.sql` header].
- **Geometry storage** follows the existing `Order.location` mapping: JTS with x = longitude, y = latitude, SRID 4326. Polygon read and write through MySQL is checked in the manual test, because only `POINT` has been exercised before. Chưa xác minh for polygons.
- **Deleting a zone** (`DELETE /api/v1/zones/{id}`) that is referenced by a permit is refused with 409.
- **New read-only endpoint** `GET /api/v1/zones/{id}/verdict?vehicleId=&date=` returns the `RuleEvaluator` verdict for one vehicle and date. It supports manual testing in P1a and is reused by P1b.
- **`appBased`** evaluates with `branchAppBased = false` in P1 (internal fleets). No branch flag is added.
- The P1a task and its test table: `task-opencode-p1a.md`.

## 16. P1b decisions and technical design (added 2026-10-27)

### 16.1 User decisions (2026-10-27)
- **D4 — one delivery date per optimisation job, for every branch** (09:45, "tôi chọn a"). The engine plans one day: 08:00 start, times of day only (`context-p1b.md` §2). A job whose orders span several `deliveryDate`s is refused with 400 and lists the dates. This also fixes the existing defect for branches without zones, a scope addition by user decision.
- **D5 — LEZ violations are hard on revision save** (2026-10-27, answer to the question round that preceded the 09:45 message; recommended option). This is consistent with the existing 422 for capacity, time-window and skill violations. Preview still shows them. Engine-generated solutions keep `VIOLATION` and are blocked at send. This replaces the earlier note in §13 ("can still save a violating revision").
- **D6 — Engine test exclusions** for LEZ-001 runs: `JspritConvergenceBenchmarkTest`, `CircuityFactorMeasurementTest` (not edited, not disabled, only not run; listed under Deviations).

### 16.2 Technical design (refines §4–§5; implementation choices)
1. **The engine stays law-free and zone-free in P1.** Entry compiles. The engine receives only extra skills:
   - `lezSkills` (`Set<String>`) on the engine **Order** and **Vehicle** DTOs, on both sides;
   - the engine adds them with `addRequiredSkill` / `addSkill` beside the existing skills;
   - vehicles unusable under LEZ (depot in an ALL_DAY zone) are **left out of the request** by Entry.
2. **Unassigned reason** `LEZ_NO_COMPLIANT_VEHICLE` is assigned by **Entry** at callback time (it knows the compiled restrictions). Rule: an unassigned order with zone keys where no vehicle in the request carried all of its LEZ skills. The text names the zone(s). `unassigned_orders.reason_code` is added.
3. **Geometry.** The engine fills `Stop.geometryToNext` (encoded polyline, precision 1e5, Google polyline algorithm, `[lat, lon]` order) for every stop except the last:
   - in the single-objective result;
   - in **every FRONTIER alternative**;
   - in the **evaluate** response.

   It uses a new `RoutePathProvider` interface (GraphHopper implementation: profile `car`, the same router as the matrix). A leg that cannot be routed leaves the field null and is logged.
4. **Turn instructions** are not in optimisation results (payload size). A new engine endpoint `POST /api/v1/optimization/legs` takes ordered points plus a locale and returns, per leg, the polyline, distance, time and instructions. Entry calls it **at send time**, and only when the route lacks geometry or instructions, then stores them on `RouteStop`. Locale `vi` if GraphHopper's translations provide it, else `en` (verify and record).
5. **Entry `ComplianceChecker` is the only verdict** (§5.3), on stored geometry. In P1 it evaluates ALL_DAY **and PARTIAL** restrictions (§5.3 phase note). Additional code `LEZ_GEOMETRY_MISSING`: a leg without geometry while the branch has enabled zones. It blocks.
6. **Ruleset hash:** SHA-256 of the canonical compiled restrictions for (branch, date), covering enabled zones with their effective version or copy rules, geometry, rules, permits and vehicle legal facts. `STALE` is computed on read (stored hash ≠ current hash). It is never written by a GET.
7. **Branch without enabled zones:** compilation is empty, every route is `COMPLIANT`, and no LEZ skill is added. Behaviour is unchanged except for D4.
8. **Polygons are not sent to the engine** in P1. They are needed only from P3 (avoidance).
