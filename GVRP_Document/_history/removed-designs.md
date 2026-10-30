# Removed designs

Old design files removed while rebuilding this documentation set. Git keeps the content; this
index records why each file was removed and what replaces it.

Compared against the code at `c52bb17` (2026-10-29). Last commit from
`git log -1 --format=%h --date=short -- <path>`.

| # | Removed path (under `GVRP_Document/`) | Last commit | Reason | Replaced by |
|---|---|---|---|---|
| 1 | `source/analysis/analysis class.puml` | `25b887e` 2025-11-20 | Class analysis of the 2025-11 Entry API. Contains none of `BranchZone`, `BranchZoneRule`, `ZoneTemplate`, `VehiclePermit`, `RouteSend`, `ParetoCandidate` or the solution revision chain (0 occurrences each), so it models a different system. | `03-basic-design/D02-master-data/diagrams/class-master-data.puml`, `.../D03-orders/...`, `.../D07-lez/diagrams/class-lez.puml` |
| 2 | `source/design/class/01_Entity_Layer.puml` | `3807193` 2026-01-06 | Entity diagram without the LEZ, permit, Pareto and route-send entities added after January 2026 (0 occurrences of `BranchZone`, `Pareto`, `RouteSend`, `VehiclePermit`, `ZoneTemplate`, `ParetoCandidate`). | same as row 1; entity list in `02-system-design/05-data-architecture.md` |
| 3 | `source/design/class/02_Repository_Layer.puml` | `3807193` 2026-01-06 | Repository map that predates the repositories of the six entities added later. | class diagrams in `03-basic-design/*/diagrams/` |
| 4 | `source/design/class/03_Service_Layer.puml` | `3807193` 2026-01-06 | Service map without `Lez*`, `ParetoService`, `RouteSendService`, `VehiclePermitService`, `ZoneTemplateService`, `SolutionRevisionService`. | class diagrams in `03-basic-design/*/diagrams/` |
| 5 | `source/design/class/04_Validator_Mapper_Parser.puml` | `3807193` 2026-01-06 | Validator/parser map without `RuleValidator` and `GeoJsonGeometryParser` (LEZ) and without the current geocoding parser set. | `03-basic-design/D07-lez/README.md` (rules), `03-basic-design/common/README.md` |
| 6 | `source/design/class/05_Engine_Calculator.puml` | `3807193` 2026-01-06 | Engine calculator view predating the Pareto sampler and the `evaluate`/`legs` services. | `03-basic-design/D08-engine/diagrams/class-overview.puml` |
| 7 | `source/design/class/06_Controller_Layer.puml` | `3807193` 2026-01-06 | Controller map covering 10 of the 17 controllers now present; missing BranchZone, LezCompliance, LezMembership, Pareto, RouteSend, VehiclePermit, ZoneTemplate controllers. | API tables in `03-basic-design/*/README.md` |
| 8 | `source/design/class/07_DTO_Exception.puml` | `25b887e` 2025-11-20 | DTO and exception map predating the current error codes (`RevisionConflictException`, `JobNotReadyException`, LEZ validation errors). | `03-basic-design/common/README.md`, per-domain error tables |
| 9 | `source/design/class/system_architecture.puml` | `574eb12` 2026-01-22 | Names **Genetic Algorithm** and **Tabu Search** as the solver. The Engine API calls GraphHopper/jsprit (`RegretInsertion` + ruin-and-recreate); neither algorithm exists in the code. Factually wrong, so not repairable as written. | `02-system-design/diagrams/c4-container.puml`, `03-basic-design/D08-engine/diagrams/engine-architecture.puml` |
| 10 | `source/design/component/entry_service_component_diagram.puml` | `574eb12` 2026-01-22 | Component map of the January 2026 service (0 occurrences of `Lez`, `RouteSend`, `ZoneTemplate`, `Permit`). | `02-system-design/diagrams/c4-container.puml` and the domain class diagrams |
| 11 | `source/design/database/er_diagram.puml` | `574eb12` 2026-01-22 | Shows 9 entities; 19 tables are mapped by JPA entities. Missing `branch_zones`, `branch_zone_rules`, `zone_templates`, `zone_rule_templates`, `vehicle_permits`, `route_sends`, `pareto_candidates`, `unassigned_orders`. | `02-system-design/diagrams/er-model.puml` |
| 12 | `source/design/database/init.sql` | `25b887e` 2025-11-20 | Creates `route_segments`, a table dropped in `v2.0.sql` and replaced by `route_stops`. Predates all seven files in `database/migrations/`. A documentation copy of a migration was a second, drifting source of truth. | `GVRP_Entry_API/src/main/resources/database/**` (unchanged, remains the only source) |
| 13 | `source/design/entry_package_structure.txt` | `25b887e` 2025-11-20 | Describes package `com.vrp.entryservice` with `EntryServiceApplication` and `BranchController`. The code uses `org.truong.gvrp_entry_api` and no `BranchController` exists. | `03-basic-design/common/README.md` (package conventions) |
| 14 | `source/design/sequence_old/VRP-001.puml` | `25b887e` 2025-11-20 | Superseded by `sequence_new/` in December 2025 and by the current API since. | `03-basic-design/D02-master-data/diagrams/` |
| 15 | `source/design/sequence_old/VRP-002.puml` | `012c9a1` 2025-12-17 | Same as row 14 for the planning flow. | `03-basic-design/D04-planning/diagrams/` |
| 16 | `source/design/sequence_new/VRP-001_00_Master_Flow.puml` | `db180a5` 2025-12-13 | Calls `POST /api/v1/branches/{id}/depots`, `/vehicle-models`, `/fleets`, `/orders/import`. No `/api/v1/branches` endpoint exists in any controller, and the real paths are `/api/v1/depots`, `/api/v1/vehicle-types`, `/api/v1/fleets`, `/api/v1/orders/import`. | `03-basic-design/D02-master-data/README.md` (API table) and `diagrams/sequence-vehicle-create.puml`, `D03-orders/diagrams/sequence-order-import.puml` |
| 17 | `source/design/sequence_new/VRP-001_01_Sub_Create_Depot.puml` | `25b887e` 2025-11-20 | Same wrong endpoint family as row 16. | `03-basic-design/D02-master-data/README.md` (API table; no separate depot diagram) |
| 18 | `source/design/sequence_new/VRP-001_01.5_Sub_Create_VehicleType.puml` | `db180a5` 2025-12-13 | Same wrong endpoint family as row 16. | `03-basic-design/D02-master-data/README.md` (API table) and `diagrams/sequence-vehicle-create.puml` |
| 19 | `source/design/sequence_new/VRP-001_02_Sub_Create_Fleet.puml` | `25b887e` 2025-11-20 | Same wrong endpoint family as row 16. | `03-basic-design/D02-master-data/diagrams/sequence-vehicle-create.puml` and the D02 API table |
| 20 | `source/design/sequence_new/VRP-001_03_Sub_Import_Orders.puml` | `25b887e` 2025-11-20 | Same wrong endpoint family as row 16. | `03-basic-design/D03-orders/diagrams/sequence-order-import.puml` |
| 21 | `source/design/sequence_new/VRP-002.puml` | `3807193` 2026-01-06 | Engine dispatch drawn as `POST /api/engine/optimize` (actual `POST /api/v1/optimization`); cancel drawn as `DELETE /api/v1/jobs/{id}` (actual `PUT /api/v1/jobs` with a list of ids); no LEZ gate before planning, no progress callback, no Pareto block. | `03-basic-design/D04-planning/diagrams/sequence-job-submit.puml`, `sequence-engine-callback.puml` |

## Kept and moved

| Old path | New path | Change |
|---|---|---|
| `ENGINE_V1_TO_V2.md` | `_history/engine-v1-to-v2-log.md` | migration log, archived; still-valid implemented facts extracted into `02-system-design/03-integration.md` and `03-basic-design/D08-engine/README.md` |
| `SOLUTION_DEEP_LINK.md` | `03-basic-design/D09-solution-link/README.md` | content kept and extended with the current code |
| `source/design/class/00_Styling.puml` | `_shared/puml-styling.puml` | styling only; retained so class diagrams keep one colour legend |
| `source/design/class/08_Engine_API_Classes.puml` | `03-basic-design/D08-engine/diagrams/class-overview.puml` | verified against the Engine source; `evaluate` and `legs` added |
| `source/design/Engine Architecture/01_architecture.puml` | `03-basic-design/D08-engine/diagrams/engine-architecture.puml` | updated with the spool, cancellation and Pareto components |
| `source/design/Engine Architecture/02_sequence.puml` | `03-basic-design/D08-engine/diagrams/sequence-optimize.puml` | updated: `/evaluate`, progress, cancel |
| `source/design/Engine Architecture/03_domain_class.puml` | `03-basic-design/D08-engine/diagrams/class-domain.puml` | kept; LEZ skills added |
| `source/design/Engine Architecture/04_activity.puml` | `03-basic-design/D08-engine/diagrams/activity-solve.puml` | kept; clustering path added |
| `source/design/sequence_new/VRP-003_Failure_Callback.puml` | `03-basic-design/D04-planning/diagrams/sequence-failure-callback.puml` | matched `CallbackService.sendFailureCallback` / `handleFailure`; retry interval corrected |
| `source/design/sequence_new/driver-delivery-send.puml` | `03-basic-design/D06-dispatch/diagrams/sequence-route-send.puml` | LEZ gate, supersede rule and leg fill added |
| `source/design/sequence_new/driver-delivery-view.puml` | `03-basic-design/D06-dispatch/diagrams/sequence-driver-view.puml` | participant corrected to `DriverController` |
| `source/design/sequence_new/driver-delivery-domain.puml` | `03-basic-design/D06-dispatch/diagrams/class-dispatch-domain.puml` | kept |

33 files were tracked before the rebuild: 21 removed, 12 kept and moved.
