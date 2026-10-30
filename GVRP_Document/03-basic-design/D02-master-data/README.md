# D02 — Master data

The rows a plan is built from: depots, fleets, vehicles, vehicle types, and the driver directory. Every
row here belongs to the caller's branch and is the input the planner job later snapshots.

Screens: `/setup/depot`, `/setup/vehicle-types`, `/setup/fleet`, `/admin/depots`,
`/admin/vehicle-types`, `/admin/vehicles`.
Diagrams: [class-master-data.svg](diagrams/class-master-data.svg)
([source](diagrams/class-master-data.puml)), [sequence-vehicle-create.svg](diagrams/sequence-vehicle-create.svg)
([source](diagrams/sequence-vehicle-create.puml)).

Use cases: UC-D02-01 … UC-D02-07. Rules: BR-010 … BR-015, BR-001, BR-002.

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `/setup/depot` | `DepotSetupComponent` | first wizard step; **create only**, then link to `/setup/vehicle-types` |
| `/setup/vehicle-types` | `VehicleTypeSetupComponent` | create and edit types |
| `/setup/fleet` | `FleetSetupComponent` | create a fleet together with its vehicles in one submit |
| `/admin/depots` | `DepotsAdminComponent` | list + create; the screen states in its own header that full CRUD needs `PUT`/`DELETE /depots` |
| `/admin/vehicle-types` | `VehicleTypesAdminComponent` | list, create, edit, and a Delete button |
| `/admin/vehicles` | `VehiclesAdminComponent` | paginated list, create, edit |

The wizard exists because a branch with no depot, type or vehicle cannot plan anything: `setupGuard`
redirects `/main` into the missing step (`D01` §1). The guard counts vehicles by summing
`fleet.vehicle_count` across fleets, so a branch whose vehicles are not in a fleet is treated as incomplete.

### Known dead action

`api.service.ts` exposes `deleteVehicleType(id)` → `DELETE /api/v1/vehicle-types/{id}`. The server has no such
mapping (three vehicle-type routes: POST, GET, PUT). The click therefore reaches an unmapped path and the
generic exception handler answers `500`; nothing is deleted. BR-015.

---

## 2. API

Sixteen endpoints, all `ROLE_PLANNER`, all branch-scoped from the token (twelve master-data rows, the driver
list read by D06, and the four vehicle-permit rows added by LEZ-001).

| Method | Path | Body / query | Success | Notes |
|---|---|---|---|---|
| GET | `/api/v1/depots` | — | `200 DepotDTO[]` | unpaginated; a branch with many depots gets all of them |
| POST | `/api/v1/depots` | `DepotInputDTO` | `201 DepotDTO` | |
| GET | `/api/v1/vehicle-types` | — | `200 VehicleTypeDTO[]` | unpaginated |
| POST | `/api/v1/vehicle-types` | `VehicleTypeInputDTO` | `201 VehicleTypeDTO` | |
| PUT | `/api/v1/vehicle-types/{id}` | `VehicleTypeInputDTO` | `200 VehicleTypeDTO` | full replacement |
| GET | `/api/v1/vehicles` | `page`=0, `size`=10 | `200 PageResponse<VehicleDTO>` | |
| POST | `/api/v1/vehicles` | `VehicleInputDTO` + `?fleetId=` | `201 VehicleDTO` | the fleet is a **query parameter**, not part of the body |
| PUT | `/api/v1/vehicles/{id}` | `VehicleUpdateDTO` | `200 VehicleDTO` | the fleet cannot be changed; the body has no `fleetId` |
| GET | `/api/v1/fleets` | — | `200 FleetDTO[]` | unpaginated; includes `vehicle_count` per fleet |
| POST | `/api/v1/fleets` | `FleetInputDTO` | `201 FleetDTO` | creates the fleet and its vehicles in one transaction |
| GET | `/api/v1/fleets/{fleetId}` | — | `200 FleetDTO` | |
| GET | `/api/v1/drivers` | — | `200 DriverDTO[]` | enabled `DRIVER` users of the branch; used by the send dialog (D06) |
| GET | `/api/v1/vehicles/{vehicleId}/permits` | — | `200 VehiclePermitDTO[]` | permits of one vehicle of the branch |
| POST | `/api/v1/vehicles/{vehicleId}/permits` | `VehiclePermitInputDTO` | `201 VehiclePermitDTO` | at most 50 permits per vehicle |
| PUT | `/api/v1/vehicles/{vehicleId}/permits/{permitId}` | `VehiclePermitInputDTO` | `200 VehiclePermitDTO` | full replacement |
| DELETE | `/api/v1/vehicles/{vehicleId}/permits/{permitId}` | — | `204` | |

### Payloads

`DepotInputDTO` — `name` 2…100, `address` 5…255, `latitude` −90…90, `longitude` −180…180; all required.

`VehicleTypeInputDTO` — `typeName` required, `capacity` ≥ 1, `fixed_cost` / `cost_per_km` / `cost_per_hour` /
`max_distance` / `max_duration` ≥ 0, `features` (`category`, `emission_factor` required and ≥ 0, `skills[]`),
and the four LEZ attributes `legal_class`, `gross_weight_kg`, `energy_type`, `emission_standard`.

The emission factor is **not** a column: it lives inside `vehicle_features.emissionFactor` and is read by
`VehicleFeaturesService`. Every consumer of "the vehicle's emission factor" goes through that path, which is
why the master-data screen writes it as a nested object.

`VehicleInputDTO` — `vehicle_license_plate` ≤ 20, `vehicle_type_id`, `start_depot_id`, `end_depot_id`, all
required. `VehicleUpdateDTO extends` it and adds `status`.

`FleetInputDTO` — `fleet_name` ≤ 100 required, `vehicles` non-empty list of `VehicleInputDTO`.

`VehiclePermitInputDTO` — `branch_zone_id` (required, a zone of the branch), `permit_no` ≤ 100,
`schedule` (optional JSON; absent = whole day), `valid_from` and `valid_to` (both required, from ≤ to), `note`.
Permit errors follow the D07 mapping: unknown or foreign vehicle, permit or zone → `404` + `0040402`
(`LezNotFoundException`); invalid input → `400` + `0040001`. Screen: `VehiclePermitsDialogComponent` from
`/admin/vehicles`. Sources: `controller/VehiclePermitController.java`, `service/lez/VehiclePermitService.java`.

### Cross-field checks that are not bean validation

| Check | Where | Result |
|---|---|---|
| fleet, vehicle type, start depot and end depot must belong to the branch | `VehicleService`, `FleetService` | `400` + `0040402` with `resource` naming the missing one |
| license plate uniqueness | `VehicleService` | `500` — see §3 |
| type-level consistency (`gross_weight_kg` without `legal_class`, `emission_standard` without `energy_type`) | `VehicleTypeService` | `400` + `0040001` with `field` `grossWeightKg` / `emissionStandard` |

---

## 3. Errors

| Situation | Status | Body |
|---|---|---|
| invalid field | 400 | `0040001`, one detail per field |
| fleet / type / depot of another branch, or unknown | **400** | `0040402`, `resource: fleet` / `vehicle_type` / `depot` |
| vehicle id unknown or foreign | **400** | `0040402`, `resource: vehicle` |
| fleet id unknown on `GET /fleets/{fleetId}` | **400** | `0040402`, `resource: fleet` |
| duplicate license plate | **500** | `0050001` |
| `DELETE /vehicle-types/{id}` | **500** | `0050001` — no handler |
| duplicate `(branch_id, type_name)` or `(branch_id, fleet_name)` | **500** | `0050001` — the unique index rejects the insert, `DataIntegrityViolationException` is unhandled |

The duplicate-plate row is a real defect with a clear cause: `VehicleService` throws
`jakarta.validation.ValidationException("License plate already exists")`, which is not the project's
`DataInvalidException` and matches no handler, so it becomes a `500`. The message never reaches the client —
`handleGenericException` returns the fixed *Internal server error.* — and the user sees a server fault when
the plate is simply taken. Status `VERIFIED` by inspection of the throw site and the handler table; no test
covers it.

The two unique-index rows are the same class of failure one layer lower: the service does not pre-check, the
database refuses, and the planner sees `500`.

Branch scoping is `400`, not `404`: the resource is named in the body, so a caller can distinguish "does not
exist" from "belongs to someone else" only by guessing. BR-002 states `404`; the implemented status is `400`
(`03-basic-design/common/README.md` §5). This document records the implemented behaviour.

---

## 4. What a save writes

| Action | Rows |
|---|---|
| depot create | `depots` (branch from token, `location` from lat/long) |
| vehicle type create/update | `vehicle_types`, with `vehicle_features` JSON merged from `features` |
| fleet create | `fleets` + one `vehicles` row per element of `vehicles[]` |
| vehicle create | `vehicles` (fleet, type, start and end depot all validated first) |
| vehicle update | `vehicles`; plate, type and depots replaced, `status` optional |

No deletes exist for depots, fleets, vehicles or vehicle types. A typo in a depot is permanent; the workaround
is to create another one and stop using the first. The `depots-admin` screen says so.

---

## 5. Data touched

`branches` (read), `depots`, `vehicle_types`, `fleets`, `vehicles`, `users` (read, driver directory).

Column-level detail is in `02-system-design/05-data-architecture.md` §2. Three constraints are worth repeating
here because they surface as the `500`s in §3: `vehicle_types (branch_id, type_name)` unique,
`fleets (branch_id, fleet_name)` unique, `vehicles.vehicle_license_plate` unique with two identical indexes.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/{DepotController,FleetController,VehicleController,VehicleTypeController,DriverController}.java`,
`.../service/{DepotService,FleetService,VehicleService,VehicleTypeService,RouteSendService}.java`,
`.../service/VehicleFeaturesService.java`,
`.../dto/request/{DepotInputDTO,VehicleTypeInputDTO,VehicleInputDTO,VehicleUpdateDTO,FleetInputDTO,VehicleFeaturesDTO}.java`,
`.../dto/response/{DepotDTO,FleetDTO,VehicleDTO,VehicleTypeDTO,DriverDTO}.java`,
`.../repository/{DepotRepository,FleetRepository,VehicleRepository,VehicleTypeRepository,UserRepository}.java`,
`.../exception/GlobalExceptionHandler.java`,
`GVRP_Entry_client_v2/src/app/app.routes.ts`,
`GVRP_Entry_client_v2/src/app/features/{depot,vehicle-type,fleet,admin}/**`,
`GVRP_Entry_client_v2/src/app/core/services/api.service.ts`,
`GVRP_Entry_client_v2/src/app/core/guards/setup.guard.ts`,
`01-requirements/03-business-rules.md` (BR-010…BR-015).
