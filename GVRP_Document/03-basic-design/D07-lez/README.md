# D07 — Low-emission zones

Branch-owned low-emission zones built from system templates, their access rules, which orders lie inside
them, and whether a planned route complies. Planning (D04) and sending (D06) consume what this domain
compiles; vehicle permits are maintained in D02.

Screens: `/admin/zones`; the compliance panel lives in the Route tab of `/main`.
Diagrams: [class-lez.svg](diagrams/class-lez.svg) ([source](diagrams/class-lez.puml)),
[sequence-compliance-recheck.svg](diagrams/sequence-compliance-recheck.svg) ([source](diagrams/sequence-compliance-recheck.puml)),
[state-lez-status.svg](diagrams/state-lez-status.svg) ([source](diagrams/state-lez-status.puml)).

Use cases: UC-D07-01 … UC-D07-11. Rules: BR-070 … BR-083 (LEZ section).

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/admin/zones` | `ZonesAdminComponent` | the branch's zones, link or copy a template, enable/disable, delete, open the editor |
| — | `ZoneEditorDialogComponent` | COPIED zones only: name, geometry (draw on the map, paste or upload GeoJSON, load example), rules with presets and warnings |
| — | `ZoneMapComponent` | Leaflet preview; editable drawing control when used by the editor |
| `/main` (Route tab) | `CompliancePanelComponent` | per-route status, violations (clickable, focuses the leg on the map), recheck, LEZ-unassigned orders |

`/admin/zones` is guarded by `AuthGuard` and `setupGuard` inside `AdminLayoutComponent`.

---

## 2. API

Fifteen endpoints, `ROLE_PLANNER`, all branch-scoped (BR-070, BR-081). A request without branch context
answers `403` (`LezSupport.requireBranch`).

| Method | Path | Request | Success | Notes |
|---|---|---|---|---|
| GET | `/api/v1/zone-templates` | — | `200 ZoneTemplateDTO[]` | system templates |
| GET | `/api/v1/zone-templates/{id}` | — | `200 ZoneTemplateDTO` | |
| GET | `/api/v1/zones` | — | `200 BranchZoneDTO[]` | ordered by id |
| GET | `/api/v1/zones/{id}` | — | `200 BranchZoneDTO` | |
| POST | `/api/v1/zones` | `{template_id, mode: LINKED\|COPIED}` | **`201`** `BranchZoneDTO` | second LINK of a template → `409` (BR-071); a COPIED zone gets a unique name (`"<name> (copy)"`, `"(copy 2)"`, …) |
| PATCH | `/api/v1/zones/{id}` | `{enabled?, name?}` | `200 BranchZoneDTO` | rename COPIED only (`409` for LINKED); name unique per branch, ≤ 200 (BR-072) |
| PUT | `/api/v1/zones/{id}/geometry` | GeoJSON | `200 BranchZoneDTO` | COPIED only; Polygon/MultiPolygon/Feature, ≤ 5000 vertices (BR-073) |
| PUT | `/api/v1/zones/{id}/rules` | `ZoneRuleInputDTO[]` | `200 BranchZoneDTO` | COPIED only; replaces every rule; ≤ 50 rules (BR-074) |
| POST | `/api/v1/zones/{id}/acknowledge` | `{version}` | `200 BranchZoneDTO` | COPIED only; `version` must equal the template's current version (`400` otherwise) |
| DELETE | `/api/v1/zones/{id}` | — | `204` | `409` while a vehicle permit references the zone; rules deleted with it |
| GET | `/api/v1/zones/{id}/verdict` | `vehicleId`, `date` (`yyyy-MM-dd`) | `200 ZoneVerdict` | read-only (BR-078); both parameters required |
| GET | `/api/v1/lez/order-zones` | `orderIds` = comma list, 1…200 ids | `200 OrderZoneMembershipDTO[]` | zones covering each order (badges in the orders table) |
| GET | `/api/v1/solutions/{id}/compliance` | — | `200 SolutionComplianceDTO` | stored status per route, `STALE` derived (BR-077, BR-080) |
| POST | `/api/v1/solutions/{id}/compliance/recheck` | — | `200 SolutionComplianceDTO` | recertify every route with the current rules (the only write) |
| GET | `/api/v1/routes/{id}/geometry` | — | `200 RouteGeometryDTO` | stored legs `{from_seq, to_seq, polyline, instructions}` for the map |

### Main payloads

* `BranchZoneDTO` = `{id, mode, template_id, template_code, name, enabled, effective_version,
  source_template_version, acknowledged_template_version, template_version, update_available, geometry,
  rules[], warnings[]}`. For a LINKED zone, name, geometry and rules come from the template's current
  version (BR-071). `warnings` lists depots that lie in an enabled zone.
* `SolutionComplianceDTO` = `{solution_id, routes[{route_id, vehicle_id, status, checked_at, violations}],
  zones[{key, branch_zone_id, name}], lez_unassigned[{order_id, order_code, reason_code, reason}]}`.
* `OrderZoneMembershipDTO` = `{order_id, zones[{branch_zone_id, name}]}`.

---

## 3. Compilation and certification

`RestrictionCompiler.compile(branch, date, vehicles, orders)` produces `CompiledRestrictions`: the enabled
zones with effective rules for the date, per-vehicle restrictions (permits applied), order→zone membership,
vehicles unusable because their depot lies in a zone that bans them all day, and a hash. The same structure:

* is stored as the job's snapshot at planning time and used by the completion callback (BR-082);
* is recompiled with the **current** rules for the compliance read (to derive `STALE`), the recheck, a saved
  revision (D05) and the send guard (D06).

`ComplianceChecker` evaluates every stop and leg of a route; violation codes include `ZONE_ACCESS`,
`ZONE_TRANSIT`, `LEZ_GEOMETRY_MISSING`, `LEZ_DATA_MISSING`. User-facing details name the zone by its
name, not by its key. Statuses: `state-lez-status.puml`.

---

## 4. Errors

| Situation | Status | Code |
|---|---|---|
| no branch context | 403 | `0040301` |
| unknown or foreign template, zone, vehicle, solution, route | **404** | `0040402` (`LezNotFoundException`) |
| second LINK, edit/rename/acknowledge of a LINKED zone, delete with permits | 409 | `0040901` (`LezConflictException`) |
| invalid GeoJSON, too many vertices or rules, invalid rule, bad `date`, bad `orderIds`, wrong acknowledge version, blank/duplicate/too long name | 400 | `0040001` |

D07 is the one planner domain where unknown ids really answer `404`; D03–D06 answer `400` for the same
situation.

---

## 5. Data touched

`zone_templates`, `zone_rule_templates` (read), `branch_zones`, `branch_zone_rules` (CRUD), `vehicle_permits`
(read; delete guard), `routes` (`lez_status`, `lez_checked_at`, `lez_ruleset_hash`, `lez_violations` written by
recheck), `route_stops` (legs read), `orders`, `vehicles`, `depots` (read). Migrations
`2026-10-27-lez-zones.sql` and `2026-10-28-lez-compliance.sql`.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{ZoneTemplateController,BranchZoneController,LezMembershipController,LezComplianceController}.java`,
`.../service/lez/{ZoneService,ZoneTemplateService,ZoneMembershipService,ComplianceReadService,RouteComplianceService,RestrictionCompiler,ComplianceChecker,RuleValidator,RuleEvaluator,GeoJsonGeometryParser,LezSupport}.java`,
`.../dto/request/{BranchZoneCreateRequest,BranchZoneUpdateRequest,ZoneAcknowledgeRequest,ZoneRuleInputDTO}.java`,
`.../dto/response/{BranchZoneDTO,SolutionComplianceDTO,OrderZoneMembershipDTO,RouteGeometryDTO}.java`,
`.../entity/{BranchZone,BranchZoneRule,ZoneTemplate,ZoneRuleTemplate,VehiclePermit,Route}.java`,
`GVRP_Entry_API/src/main/resources/database/migrations/2026-10-2{7,8}-*.sql`,
`GVRP_Entry_client_v2/src/app/features/admin/zones/**`, `.../features/main/orders/lez-compliance-panel/**`,
`01-requirements/03-business-rules.md`.
