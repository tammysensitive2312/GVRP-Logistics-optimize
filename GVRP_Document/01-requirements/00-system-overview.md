# 00 — System overview

## Purpose

GVRP Logistics Optimize plans delivery routes for a logistics company that operates several
branches. Each branch imports its orders for one delivery date, chooses vehicles from its fleet, asks
an optimisation engine for a route plan, reviews and adjusts the plan, checks it against
low-emission-zone (LEZ) restrictions, and sends it to a driver.

The documentation set describes the system as implemented at `c52bb17` (see `README.md`). It is
reverse-engineered: every endpoint, screen, state and rule named here exists in the code.

## Scope

In scope:

- Entry API (`GVRP_Entry_API`) — authentication, master data, orders, planning jobs, solutions,
  revisions, Pareto alternatives, LEZ zones and compliance, route sends, driver reads.
- Optimisation Engine API (`GVRP_Engine_API`) — distance matrices, clustering, jsprit solving,
  objective and emission evaluation, Pareto sampling, result spool and callbacks.
- Client v2 (`GVRP_Entry_client_v2`) — the Angular application planners and drivers use.
- MySQL schema `gvrp_db` — 19 mapped tables plus 3 legacy empty tables.

Out of scope:

- The legacy web client (`GVRP_Entry_client`) — not rebuilt here; it is not the current UI.
- The scientific validity of the emission model, of the solver quality, or of the carbon price. The
  documents state what is implemented, not what is validated (`AGENTS.md` §4, §5).
- Deployment automation: the project has no migration runner and no container definitions.

## The system in one paragraph

A user logs in with a branch name, a username and a password. The Entry API resolves the branch by
name and issues a JWT carrying the branch and the user id; every later request is scoped to that
branch on the server. A planner creates master data (depots, vehicle types with legal and emission
attributes, vehicles, permits, fleets), imports or creates orders for one delivery date, and submits
a planning job with a list of order ids and vehicle ids. The Entry API snapshots the applicable LEZ
rules, adds the matching vehicle skills to in-zone orders, records the job as `PENDING`, and calls the
Engine API asynchronously. The Engine API builds a distance matrix (GraphHopper), optionally clusters
large instances, solves with jsprit (regret insertion plus ruin-and-recreate, objective weights over
cost and CO₂), and posts a completion callback to the Entry API; failures are spooled on disk and
retried. The completion callback persists a solution, its routes, stops and unassigned orders,
certifies each route against the ruleset snapshot, and marks the job `COMPLETED`. The planner reviews
the solution, may drag stops into a manual revision (evaluated by the engine before saving), may
compare Pareto alternatives, and finally sends a route revision to a driver. The send is refused when
the route violates an LEZ restriction; otherwise the send record becomes `CURRENT`, older sends of the
same route become `SUPERSEDED`, and the driver reads the ordered stops with leg geometry on a map.

## Containers

| Container | Technology | Responsibility |
|---|---|---|
| Client v2 | Angular (standalone components), Leaflet, transloco | Planner screens, driver screen, maps, charts |
| Entry API | Spring Boot, Spring Security, JPA/Hibernate, MySQL | Tenancy, business rules, persistence, job dispatch, callbacks, email |
| Engine API | Spring Boot, GraphHopper, jsprit | Matrices, clustering, solving, evaluation, Pareto sampling, spool |
| MySQL `gvrp_db` | MySQL 8 with spatial types (`POINT SRID 4326`, `MULTIPOLYGON`) | All business data |
| GraphHopper graph | Local graph files used by the Engine API | Road network, distances, durations, paths |
| OSRM demo server | External HTTP service | Client-side leg geometry and ETA for the planner map `ASSUMPTION:` the client uses the public demo endpoint; the URL is configuration in the client environment |
| SMTP | External mail server | Success, failure and cancellation e-mails to the requesting user |
| Geocoding provider | External HTTP service | Address → coordinates when an order has no coordinates |

## Glossary

| Term | Meaning in this system |
|---|---|
| Branch | Tenant. Every business row carries `branch_id`; a branch is one company. |
| Depot | Start and end point of a vehicle's route. |
| Vehicle type | Template with capacity, costs, skills, legal class, energy type and emission data. `emissionFactor` is not a column: it lives in `vehicle_types.vehicle_features` JSON. |
| Vehicle | A physical vehicle: licence plate, type, start depot, end depot. Belongs to a fleet. |
| Fleet | Group of vehicles used to build a branch's fleet; the branch is resolved through the fleet. |
| Permit | Time-limited right of a vehicle to enter a zone, with an optional schedule. |
| Order | A delivery stop: demand, address or coordinates, time window, service time, required skills, one delivery date. |
| Job | One optimisation request (`optimization_jobs`): the orders, the vehicles and the engine parameters. |
| Solution | One plan produced by a job, a manual edit or a Pareto apply. Statuses `SUCCESS`, `PARTIAL_SUCCESS`, `INFEASIBLE`, `INITIAL`. |
| Revision | A solution that points at a parent solution. Chains grow with manual edits and Pareto applies. |
| Route | One vehicle's tour inside a solution. |
| Stop | One visit of a route (`route_stops`): depot start, delivery, depot end. |
| Unassigned order | An order the engine could not place, with the engine's or the LEZ reason. |
| Zone | Branch-owned geographic restriction (`branch_zones`) built from a template, with rules and a version. |
| Ruleset | The compiled set of zone rules for a branch and a date; identified by a hash. |
| Certification | The compliance verdict written on a route when it is checked against a ruleset snapshot. |
| Send | A record that a route revision was handed to a driver (`route_sends`); `CURRENT` or `SUPERSEDED`. |
| Pareto candidate | One alternative plan of a job, with metrics and a frontier status. |

## Sizing facts as implemented

| Fact | Value | Source |
|---|---|---|
| Entry API endpoints | 59 across 17 controllers | controllers package |
| Engine API endpoints | 8 | `OptimizationController` |
| `gvrp_db` tables | 22 (19 mapped by entities + 3 empty legacy) | `information_schema`, migrations |
| Roles | `PLANNER`, `DRIVER`, `CUSTOMER` | `UserRole` |
| Concurrent jobs per branch | 3 | `AppConstant.maxConcurrentJobs` |
| Job duration estimate | `min(5 + 2·orders + vehicles, 30)` minutes | `OptimizationJobService.estimateDuration` |
| Solver iterations | 800 (FAST), 2000 (NORMAL), 5000 (HIGH_QUALITY) | `OptimizationConfigMapper` |
| Engine timeouts | 480 s, 900 s, 3600 s | `OptimizationConfigMapper` |
| Cluster-first threshold | 1000 orders, target size 150 | `AppConstant` (Engine) |
| Pareto sampling | N = 4, p = 2.0 | `AppConstant` (Engine) |
| Carbon price used in the objective | 150 000 VND per tonne CO₂ | `AppConstant.CARBON_PRICE_PER_TON` |
| Demand scale | 1 solver unit = 0.1 kg (`DEMAND_SCALE` = 10) | `AppConstant` (Engine) |

`ASSUMPTION:` the figures above are the compiled defaults; they can be overridden per request through
the planning preferences, and the spool and retention values are configuration properties.

## How to read this set

1. `01-requirements/` — who uses the system, what each use case promises, which rules constrain it.
2. `02-system-design/` — the containers, deployment, integration contracts, security model and data
   model.
3. `03-basic-design/` — per domain: screens, API rows, sequences, states, errors.
4. `04-test-design/` — how each promise is expected to be tested, and which automated test already
   covers it.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/**`,
`GVRP_Entry_API/src/main/resources/database/**`, `GVRP_Engine_API/src/main/java/**`,
`GVRP_Entry_client_v2/src/app/**`, `information_schema` of `gvrp_db` (schema only).
