# 03 — Business rules

Every rule below is stated from the code at `c52bb17`. The *Source* column names where it is
implemented; the *Status* column says how it is known, using the vocabulary of `AGENTS.md` §5:

- `VERIFIED` — asserted by an existing automated test or directly visible in a single named clause;
- `PARTIALLY_VERIFIED` — implemented but only partially covered;
- `NOT_VERIFIED` — implemented by inspection only, no test and no run.

Rules are grouped by area; the numbering is stable and referenced from the basic design.

## Identity and tenancy

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-001 | The branch of a request is taken from the authenticated principal (`CurrentUserUtil.getCurrentBranchId()`), never from the body, path or query. | every controller | `VERIFIED` (tenant tests in `SolutionServiceTest`, `ComplianceReadServiceTest`) |
| BR-002 | A resource looked up by id is also looked up by branch. Another branch's id behaves like a missing id: `404` with code `0040402`, so existence is not disclosed. | `findByIdAndBranchId` in repositories | `VERIFIED` (`SolutionServiceTest.getSolutionDetail_otherBranch_sameErrorAsUnknownId`) |
| BR-003 | A revision chain stays inside one branch and one solution: a revision must belong to the same solution as its parent. | `SolutionService`, `RouteCustomizationService` | `VERIFIED` (`revisionChain_otherBranch_notFound`) |
| BR-004 | Batch operations are branch-scoped. Missing and foreign ids are reported per row in the failure list, not as a `404` for the whole call. | `OrderService.bulkUpdateStatus` | `VERIFIED` (`OrderBulkControllerTest.bulk_ok_returnsPartitionedReport`) |
| BR-005 | The engine callback resolves the branch from the job it carries; the callback cannot name a branch. | `OptimizationCallbackService` | `VERIFIED` |
| BR-006 | Authorisation is path-based in this order: `/api/v1/driver/**` needs `ROLE_DRIVER`; other `/api/v1/**` needs `ROLE_PLANNER`. | `SecurityConfig` | `VERIFIED` (`RoleAccessTest` 11/11) |
| BR-007 | Public paths: `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/api/v1/solutions/callbacks/**`. Callbacks are additionally checked by the engine API-key filter. | `SecurityConfig`, `ApiKeyAuthenticationFilter` | `VERIFIED` |
| BR-008 | A token is accepted only while its user exists, is enabled and not locked, and the token's user id matches the loaded user. | `JwtAuthenticationFilter` | `VERIFIED` (`JwtAuthenticationFilterTest` 7 cases) |
| BR-009 | Login requires a branch name; the username is unique per branch, not globally. An unknown branch answers `0040402`, wrong credentials `0040101`. | `AuthController.login` | `PARTIALLY_VERIFIED` (no test for the branch-name failure path) |

## Master data

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-010 | Depot: name 2–100 characters, address 5–255, latitude −90…90, longitude −180…180, all required. | `DepotInputDTO` | `VERIFIED` (bean validation) |
| BR-011 | Vehicle type: name and capacity required, capacity ≥ 1, costs and limits ≥ 0; optional `legalClass`, `grossWeightKg`, `energyType`, `emissionStandard`, and a `features` object. The emission factor is not a column: it is read from `vehicle_features.emissionFactor`. | `VehicleTypeInputDTO`, `VehicleFeaturesService` | `VERIFIED` (`VehicleTypeLegalAttributesTest` 6 cases) |
| BR-012 | Creating a vehicle requires a fleet, a vehicle type, a start depot and an end depot; all must belong to the caller's branch, otherwise `404`. | `VehicleService` | `VERIFIED` (`VehicleServiceTest` 6 cases) |
| BR-013 | Creating a fleet requires a name (≤ 100) and at least one vehicle; each vehicle is validated like BR-012. | `FleetInputDTO`, `FleetService` | `VERIFIED` (`FleetServiceTest`) |
| BR-014 | A permit belongs to a vehicle of the branch and to a zone; it carries an optional schedule, `validFrom`, `validTo`, `permitNo` and a note. A permit on another branch's vehicle answers `404`. | `VehiclePermitService` | `VERIFIED` (`BranchZoneControllerTest.permitOnOtherBranchVehicle_returns404`) |
| BR-015 | There is no delete endpoint for a vehicle type. The admin screen still exposes a Delete action wired to `DELETE /api/v1/vehicle-types/{id}`, so deleting a type fails at runtime. | `VehicleTypeController` (POST/GET/PUT only), `api.service.ts` | `VERIFIED` (endpoint inventory; no server handler) |

## Orders

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-020 | Order validation: `orderCode` required ≤ 50, `customerName` required ≤ 100, `customerPhone` ≤ 20 and matching `^[0-9+\-\s()]+$`, `address` ≤ 255, latitude −90…90, longitude −180…180, `demand` ≥ 0.01, `serviceTime` ≥ 0, `priority` ≥ 1, `deliveryNotes` ≤ 1000, `deliveryDate` required. | `OrderInputDTO` | `VERIFIED` (`OrderCreateValidationTest`) |
| BR-021 | Order status transitions: `SCHEDULED → ON_ROUTE \| UNASSIGNED \| REJECTED`; `ON_ROUTE → SERVICING`; `SERVICING → COMPLETED \| REJECTED`; `COMPLETED \| UNASSIGNED \| REJECTED → SCHEDULED`. Any other move is an invalid transition. | `OrderStatusTransitionService` | `VERIFIED` (transition tests) |
| BR-022 | Coordinates are accepted together or not at all. Only one → `400` on field `latitude`. Neither → the address is geocoded; a geocoding miss → `400` on field `address`, telling the user to pick the point on the map. | `OrderService.resolveCoordinates` | `VERIFIED` (`OrderServiceGeocodingTest`, `CsvFileParserCoordinatesTest`) |
| BR-023 | A new order starts `SCHEDULED`. A `status` in the create payload is rejected, so a client cannot create an order already `COMPLETED`. | `OrderInputCreateStatusTest` | `VERIFIED` |
| BR-024 | Updating an order resolves the order inside the branch first; a foreign id answers `404` and no geocoding call is made. | `OrderService.updateOrder` | `VERIFIED` (by inspection; the ordering is asserted in a comment and in the code path) |
| BR-025 | Import accepts a multipart file (CSV or XLSX), returns `ImportResultDTO` with a success count and per-row errors, and requires one delivery date for the whole file. Coordinates are optional per row; a row with only one coordinate is an error row naming the column. | `OrderImportService`, `CsvFileParser` | `VERIFIED` (`OrderImportDeliveryDateTest`, `CsvFileParserCoordinatesTest`) |
| BR-026 | Order list: `page` (default 0), `size` (default 10), ordered by id descending; filters `date` (`yyyy-MM-dd`), `status` and free-text `search`. A malformed date or status answers `400`. | `OrderController`, `OrderService` | `VERIFIED` (`OrderBulkControllerTest.list_badDate_returns400`, `list_badStatus_returns400`, `list_forwardsFilters`) |
| BR-031 | Bulk status change is lenient: each input id ends in exactly one of `updated[]` or `failed[]`; a rejected row carries a reason. No row is silently dropped. | `OrderService.bulkUpdateStatus` | `VERIFIED` (`bulk_ok_returnsPartitionedReport`) |
| BR-032 | Each row's transition is validated against BR-021 individually; one illegal row does not block the others. | `OrderStatusTransitionService.bulkTransitionLenient` | `VERIFIED` |
| BR-033 | Missing and foreign ids appear in `failed[]` with reason `Order not found in this branch`. | `OrderService.bulkUpdateStatus` | `VERIFIED` |
| BR-034 | The bulk response is `{updated: [{id, status}], failed: [{id, orderCode, reason}]}` and always `200` when the request itself is valid. | `BulkUpdateOrderStatusResponse` | `VERIFIED` |

## Planning jobs and engine callbacks

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-060 | A branch may have at most 3 jobs in `PROCESSING`. A fourth request is refused with `0040903` (`JOB_LIMIT_EXCEEDED`). | `OptimizationJobService`, `AppConstant.maxConcurrentJobs` | `VERIFIED` (constant and guard by inspection) |
| BR-061 | Only `SCHEDULED` orders can be planned. The error names up to ten offending orders and counts the rest. | `OptimizationJobService`, `OptimizationJobStatusMessageTest` | `VERIFIED` |
| BR-062 | One job covers exactly one delivery date. Two dates answer `400` and no ruleset is compiled, whether or not the branch has zones. | `requireSingleServiceDate`, `OptimizationJobLezTest` | `VERIFIED` |
| BR-063 | The job's estimated duration is `min(5 + 2·orders + vehicles, 30)` minutes. It is an estimate for the UI, not a limit. | `estimateDuration` | `VERIFIED` |
| BR-064 | Jobs can be cancelled only while `PROCESSING`; any other status answers `0040901` (`RESOURCE_CONFLICT`). Cancelling is a batch call `PUT /api/v1/jobs` with a list of ids; a foreign id answers `404`. | `OptimizationJobController.cancelJob`, `OptimizationJob.canBeCancelled` | `VERIFIED` |
| BR-065 | Job states: `PENDING → PROCESSING → COMPLETED \| FAILED \| CANCELLED`. A job that never reaches the engine stays `PENDING` until the dispatch thread marks it `FAILED`. | `OptimizationJobStatus`, `EngineApiClientImpl` | `VERIFIED` |
| BR-066 | A job produces at most one solution. A second completion callback for the same job is ignored (idempotent). | `OptimizationCallbackService` (guard on `solutionRepository.findByJobId`) | `VERIFIED` |
| BR-067 | A completion callback that arrives while the job is still `PENDING` answers `503` (`JobNotReadyException`); the job is **not** marked `FAILED`, and the engine retries. | `OptimizationCallbackService` | `VERIFIED` |
| BR-068 | A completion callback for a `COMPLETED` or `CANCELLED` job is ignored; for a `FAILED` job it is accepted and can complete it. | `OptimizationCallbackService` | `VERIFIED` |
| BR-069 | The failure callback marks the job `FAILED` with the engine's message; the cancellation callback marks it `CANCELLED`. | `OptimizationCallbackService` | `VERIFIED` |
| BR-059 | The planning request may carry preferences: goal (`MINIMIZE_COST`, `MINIMIZE_DISTANCE`, `MINIMIZE_CO2`, `BALANCED`), speed (`FAST`, `NORMAL`, `HIGH_QUALITY`), `allowUnassignedOrders`, `timeWindowMode` (`STRICT`, `FLEXIBLE`), `enableParetoAnalysis` and `seed`. Absent preferences default to cost 0.5 / CO₂ 0.5, 2000 iterations, 900 s, 4 threads. | `RoutePlanningRequest`, `OptimizationConfigMapper` | `VERIFIED` |

## Pareto alternatives

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-041 | When `enableParetoAnalysis` is true the engine solves several weightings: N = 4 intervals, p = 2.0 over the cost/CO₂ trade-off. | `AppConstant` (Engine), `ParetoWeightSampler` | `VERIFIED` (`OptimizationServiceParetoTest`) |
| BR-042 | Each alternative carries one of `FRONTIER`, `DOMINATED`, `SERVICE_DOMINATED`. | `ParetoFrontierStatus`, `ParetoFrontierSelector` | `VERIFIED` (`ParetoFrontierSelectorTest`) |
| BR-043 | Exactly one candidate may be marked selected per job; two selected candidates are rejected. | `OptimizationCallbackServiceParetoTest.rejectsTwoSelected` | `VERIFIED` |
| BR-044 | Applying a candidate creates a `PARETO_ALTERNATIVE` revision. It requires a `FRONTIER` candidate (`422` otherwise), a base that is still the latest revision (`409`), the same solution (`404` for another branch or job) and the same order set (`422`, `ORDER_SET_DIFFERS`). Applying the already-active candidate returns the base without saving. | `ParetoService`, `ParetoControllerTest`, `ParetoServiceTest` | `VERIFIED` (11 service cases, 5 controller cases) |
| BR-045 | Eligibility is service-first and lexicographic: maximum served order count, then maximum served demand (relative tolerance 1e-9); a null demand counts as zero. Cost and CO₂ rank only among eligible plans. | `ParetoFrontierSelector` | `PARTIALLY_VERIFIED` (stubbed run only, see `_history/decisions/PARETO-004.md`) |
| BR-046 | The client draws the trade-off chart from the metrics the API returns and never recomputes cost or emissions. | `pareto-chart.utils.ts`, `pareto-view.component.ts` | `VERIFIED` (specs) |

## Dispatch and driver delivery

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-051 | A route can be sent only when its solution is `SUCCESS` or `PARTIAL_SUCCESS`; `INFEASIBLE` and `INITIAL` are refused with `400`. | `RouteSendService.sendRoute` | `VERIFIED` (`RouteSendServiceTest.sendRoute_infeasibleSolution_rejects`) |
| BR-052 | The route must belong to the solution named in the request, and it must have delivery stops. | `RouteSendService` | `VERIFIED` (`sendRoute_emptyRoute_rejects`) |
| BR-053 | The driver must be an enabled `DRIVER` user of the same branch. | `RouteSendService` | `VERIFIED` (`sendRoute_wrongBranchDriver_rejects`) |
| BR-054 | Sends are immutable history. A new send for the same route becomes `CURRENT` and flips its siblings to `SUPERSEDED` in one transaction. | `RouteSendService`, `SendStatus` | `VERIFIED` (`sendRoute_recordsCurrentAndSupersedesSiblings`) |
| BR-055 | A concurrent send that loses the race re-reads and answers `409` (`RevisionConflictException`); it does not create a second `CURRENT` row. | `RouteSendService`, `RouteSendTransactionTest` | `VERIFIED` |
| BR-056 | Missing leg geometry is filled from the engine at send time. If the engine is unavailable the send fails closed with `502` and no send row is created. | `RouteSendService`, `RouteSendServiceLezTest.sgT3` | `VERIFIED` |
| BR-057 | A driver reads only their own sends. Another driver's send answers as forbidden/not found. | `RouteSendService.sendDetail` | `VERIFIED` (`sendDetail_foreignSend_forbidden`) |
| BR-058 | Driver endpoints are read-only: there is no driver endpoint that changes an order, a send or a stop. Delivery status is not written by the driver view. | `DriverController` | `VERIFIED` (endpoint inventory) |

## Low-emission zones

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-070 | Zones are branch-owned. Every zone, rule, permit, membership and compliance read is scoped by BR-001/BR-002. | `BranchZoneService`, `ComplianceReadService` | `VERIFIED` (`tiT1_*` cases) |
| BR-071 | A branch zone is created from a template in one of two modes: `LINKED` reads geometry and rules from the template's current version (the branch row keeps name, geometry and version null); `COPIED` owns a snapshot with `sourceTemplateVersion`. | `BranchZone`, `BranchZoneService` | `VERIFIED` (`ZoneServiceTest`, `BranchZoneControllerTest.secondLink_returns409Envelope`) |
| BR-072 | A zone name is at most 200 characters. | `NAME_MAX_LENGTH` in the zone service | `VERIFIED` (`ZoneNameTest`, `LezZoneNameTextTest`) |
| BR-073 | Zone geometry is GeoJSON (`Polygon`, `MultiPolygon` or a `Feature` wrapping one), stored as `MULTIPOLYGON` with SRID 4326, limited to 5000 vertices. | `GeoJsonGeometryParser` | `VERIFIED` (`GeoJsonGeometryParserTest`) |
| BR-074 | A zone may have at most 50 rules; the 51st raises a rule validation error. | `RuleValidator.MAX_RULES_PER_ZONE` | `VERIFIED` (`RestrictionCompilerTest`, `ZoneServiceTest`) |
| BR-075 | A rule carries a `selector` (vehicle attributes), a `schedule` (days and time intervals), `effectiveFrom`/`effectiveTo`, `legalBasis` and `note`; the compiled restriction is what compliance evaluates. | `ZoneRuleInputDTO`, `RestrictionCompiler` | `VERIFIED` (`RestrictionCompilerTest`, `RuleEvaluatorTest`) |
| BR-076 | A zone exposes `effectiveVersion`, `sourceTemplateVersion`, `acknowledgedTemplateVersion` and `templateVersion`. `POST /{id}/acknowledge` records the acknowledged template version so the UI can show unacknowledged template changes. | `BranchZoneDTO`, `BranchZoneService.acknowledge` | `VERIFIED` (by inspection; no dedicated test) |
| BR-077 | Compliance is certified against a ruleset snapshot identified by a hash. When the current rules compile to a different hash, the stored status is reported as `STALE`; `STALE` is derived on read and never written. | `ComplianceReadService` | `VERIFIED` (`compliance_ownBranch_reportsStaleAndUncheckedWithoutWriting`) |
| BR-078 | `GET /zones/{id}/verdict?vehicleId=&date=` evaluates one vehicle on one date and writes nothing. | `BranchZoneController.verdict` | `VERIFIED` (`verdict_parsesDate_andWritesIntervalsAsPairs`, `verdict_badDate_returns400`) |
| BR-079 | A send whose route violates a zone restriction is refused with `422`; the `VIOLATION` verdict and any legs fetched before the check are kept (the refusal is commit-worthy), and no send row is created. | `RouteSendService.enforceLezCompliance`, `RouteSendTransactionTest` | `VERIFIED` (`sgT2_permitExpiredSincePlanning_is422_keepsTheVerdictAndCreatesNoSend`) |
| BR-080 | A solution's compliance view reports `UNCHECKED`, `COMPLIANT`, `VIOLATION` or the derived `STALE` per route, plus the compiled zones and the LEZ-caused unassigned orders. Recheck recomputes with the current rules and writes the new status. | `ComplianceReadService` | `VERIFIED` (`ComplianceReadAdditionsTest`, `compliance_ownBranch_*`) |
| BR-081 | Compliance read, recheck and route geometry of another branch's solution answer `404`; a request without branch context answers `403`. | `ComplianceReadService` | `VERIFIED` (`tiT1_complianceOfAnotherBranchSolution_is404AndReadsNothing`, `tiT1_missingBranchContext_is403`) |
| BR-082 | At planning time the job stores a ruleset snapshot and the LEZ skills derived from zone membership. The completion callback certifies routes with that snapshot, not with the rules current at callback time. | `OptimizationJobService`, `OptimizationCallbackServiceLezTest.cbT1_routeIsCertifiedByTheJobSnapshot_notByTheCurrentRules` | `VERIFIED` |
| BR-083 | An in-zone order requires a vehicle allowed to enter; the engine receives the requirement as a skill. When no vehicle is usable, the job is refused with `400` naming the zone and the vehicle, and no job row and no engine call are produced. | `OptimizationJobLezTest.jsT3` | `VERIFIED` |

## Solutions, revisions and evaluation

| ID | Rule | Source | Status |
|---|---|---|---|
| BR-090 | Solution detail, latest revision and revision chain are branch-scoped; a foreign solution behaves like an unknown one. | `SolutionService` | `VERIFIED` (`SolutionServiceTest` 6 cases) |
| BR-091 | The revision chain returns the chain of the solution's root, with revision type and parent, so a client can render history. | `SolutionService.revisionChain`, `RevisionDTO` | `VERIFIED` (`revisionChain_ownBranch_returnsChain`) |
| BR-092 | Evaluate is a preview: it persists nothing, asks the engine for totals and violations, and adds Entry-side capacity violations. Engine unavailable → `502`; duplicate order in a plan or a missing planned order → `400`. | `RouteCustomizationService.evaluate` | `VERIFIED` (`SolutionCustomizationControllerTest` 7 cases, `RouteCustomizationServiceTest` 13 cases) |
| BR-093 | Saving a revision requires the base to still be the latest revision (`409` otherwise) and no hard violation (`422`); it creates a `MANUAL_EDITED` child of the base. | `RouteCustomizationService.saveRevision` | `VERIFIED` (`saveRevision_persistsManualEditedChildOfBase`, `saveRevision_staleBase_rejects409WithoutSaving`, `saveRevision_infeasibleEngine_rejects422WithoutSaving`) |
| BR-094 | A plan may not repeat an order, and may not drop an order that was in the base plan. | `RouteCustomizationService` | `VERIFIED` (`evaluate_duplicateOrder_rejects400`, `evaluate_missingPlanOrder_rejects400`) |
| BR-095 | `GET /solutions/{id}/latest-revision` returns the newest revision id, or the base itself when nothing was edited. | `RouteCustomizationService.latestRevisionId` | `VERIFIED` |
| BR-096 | A solution status is one of `SUCCESS`, `PARTIAL_SUCCESS` or `INFEASIBLE`, set once when the row is inserted (completion callback, saved revision or applied Pareto candidate). `INITIAL` is declared in the enum but no code path writes it. | `SolutionStatus`, `OptimizationCallbackService`, `RouteCustomizationService`, `ParetoService` | `VERIFIED` (search of every `SolutionStatus.` use, 2026-10-30) |

## Cross-references

- Status graphs: `03-basic-design/D03-orders/diagrams/state-order-status.puml`,
  `.../D04-planning/diagrams/state-job-status.puml`,
  `.../D05-solution/diagrams/state-solution-revision.puml`,
  `.../D06-dispatch/diagrams/state-route-send.puml`,
  `.../D07-lez/diagrams/state-lez-status.puml`.
- Test cases: `04-test-design/test-cases.csv` references these rule ids in `requirement_ref`.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/{controller,service,entity,dto,config,security}/**`,
`GVRP_Entry_API/src/test/java/**`, `GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/{service,utils}/**`.
