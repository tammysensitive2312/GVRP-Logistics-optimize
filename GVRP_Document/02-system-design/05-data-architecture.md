# 05 — Data architecture

The schema as it exists in MySQL, not as an entity diagram would like it to be. Nineteen tables are
mapped by JPA; three more hold only a sequence counter. The schema is owned by SQL files
in the repository and enforced at startup by `ddl-auto=validate`, so this document and those files
must agree — if they diverge, the application refuses to start, which is the intended coupling.

Diagram: [er-model.svg](diagrams/er-model.svg) ([source](diagrams/er-model.puml)).

---

## 1. Shape

| Aspect | Value |
|---|---|
| Database | `gvrp_db`, MySQL 8, InnoDB, `utf8mb4` |
| Tables | 22 — 19 mapped by JPA, 3 holding only a sequence counter |
| Foreign keys | 35 — 11 `ON DELETE CASCADE`, 2 `SET NULL`, 12 `RESTRICT`, 10 `NO ACTION` |
| Spatial columns | `depots.location point`, `orders.location point`, `route_stops.location point`, `zone_templates.geometry multipolygon`, `branch_zones.geometry multipolygon` — all SRID 4326 |
| JSON columns | `optimization_jobs.input_data`, `optimization_jobs.lez_snapshot`, `pareto_candidates.plan_json`, `vehicle_types.vehicle_features`, `orders.required_skills`, `branch_zone_rules.selector`, `branch_zone_rules.schedule`, `vehicle_permits.schedule`, `route_stops.instructions_to_next` |
| Encoding convention | snake_case column names; JPA maps camelCase fields explicitly with `@Column` |
| Audit columns | `created_at` / `updated_at` on most tables, filled by Spring Data auditing (`BaseEntity`); `created_at` is `timestamp` on older tables and `datetime(6)` on newer ones |
| Soft delete | none — deletions are hard deletes |
| Optimistic locking | none — no `@Version` column anywhere |
| Triggers | none in `gvrp_db` (`information_schema.triggers` is empty) |

## 2. Tables by area

### Tenancy and identity

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `branches` | Tenant row | `name` UNIQUE; `branch_webhook_url` used only by the Slack channel, written only by direct SQL |
| `users` | Login identity | `branch_id` FK; `username`, `email`, `slack_user_id` are each **globally** UNIQUE; `role` enum `PLANNER|CUSTOMER|DRIVER`; `enabled` |

### Master data

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `depots` | Start/end points for vehicles | `location point`; no unique name per branch |
| `fleets` | Grouping of vehicles | UNIQUE `(branch_id, fleet_name)` |
| `vehicle_types` | Cost, capacity and emission profile | `vehicle_features` JSON carries `emissionFactor`; `legal_class`, `energy_type`, `emission_standard`, `gross_weight_kg` feed LEZ rules; UNIQUE `(branch_id, type_name)` |
| `vehicles` | A physical vehicle | `fleet_id`, `vehicle_type_id`, `start_depot_id`, `end_depot_id`; `vehicle_license_plate` UNIQUE (two identical unique indexes exist, a migration artefact) |
| `vehicle_permits` | LEZ permission of a vehicle for a zone | `branch_zone_id`, `valid_from`/`valid_to`, `schedule` JSON |

### Orders

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `orders` | Delivery demand | `location point`; `status` enum of six values; `required_skills` JSON; `delivery_date`; UNIQUE `(branch_id, order_code)` |

### Planning and solutions

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `optimization_jobs` | One planning request | `status` enum of five values; `input_data` JSON (the full engine request); `lez_snapshot` JSON (frozen zones, rules, permits, skills); `external_job_id`; `created_by_user_id`; indexes on `(branch_id, status, created_at)` and `(branch_id, created_at)` |
| `solutions` | A plan, or a revision of one | `job_id` **nullable but UNIQUE**, so at most one solution per job while manual revisions carry `NULL`; `type` enum `ENGINE_GENERATED|FILE_IMPORTED|MANUAL_EDITED|PARETO_ALTERNATIVE`; `status` enum `INITIAL|SUCCESS|PARTIAL_SUCCESS|INFEASIBLE`; `parent_solution_id` for the revision chain; `pareto_candidate_id` |
| `routes` | One vehicle's route in a solution | `vehicle_id`; `route_order`; `lez_status` enum `UNCHECKED|COMPLIANT|VIOLATION`; `lez_ruleset_hash char(64)`; `lez_violations` JSON; `distance`, `co2_emission`, `load_utilization` |
| `route_stops` | Ordered stop list of a route | `type` (`DEPOT|ORDER`); `order_id` nullable (depot stops have none); `sequence_number`; `geometry_to_next` mediumtext (encoded polyline); `instructions_to_next` JSON; denormalised `arrival_time`, `load_after` |
| `unassigned_orders` | Orders the planner must handle manually | `solution_id`, `order_id`, `reason_code` |
| `pareto_candidates` | Pareto alternatives returned by the engine | `plan_json`; `frontier_status` enum `FRONTIER|DOMINATED|SERVICE_DOMINATED`; `cost_weight`/`co2_weight`; `engine_selected` |

### Dispatch

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `route_sends` | Immutable record of a route being sent to a driver | `route_id`, `solution_id`, `driver_id`, `sender_id`, `status` enum `CURRENT|SUPERSEDED`, `sent_at`; the only audit-like table in the system |

### Low-emission zones

| Table | Purpose | Notable columns and constraints |
|---|---|---|
| `zone_templates` | Master zone definition | `code` UNIQUE; `geometry multipolygon`; `version`; `boundary_confidence` |
| `zone_rule_templates` | Rules of a template version | `zone_template_id`; `selector` and `schedule` JSON; `template_version` |
| `branch_zones` | A branch's zone, linked to a template or copied from one | `mode` enum `LINKED|COPIED`; `source_template_id`; `source_template_version`; `acknowledged_template_version`; own `geometry` for copied zones; UNIQUE `(branch_id, linked_template_id)` |
| `branch_zone_rules` | Effective rules of a branch zone | `branch_zone_id`; `selector`, `schedule` JSON; `effective_from`/`effective_to`; `legal_basis` |

### Identifier allocation — three tables use sequences, sixteen use AUTO_INCREMENT

| Group | Entities | Strategy |
|---|---|---|
| `routes`, `route_stops`, `unassigned_orders` | `Route`, `RouteStop`, `UnassignedOrder` | `GenerationType.SEQUENCE` with `@SequenceGenerator` on `route_sequence`, `route_stop_sequence`, `unassigned_order_sequence`, `allocationSize = 50` |
| everything else | 16 entities | `GenerationType.IDENTITY` — MySQL `AUTO_INCREMENT` |

Those three tables hold exactly one row (`next_val`), and the corresponding `id` columns are plain
`bigint` **without** `AUTO_INCREMENT`. They are not legacy leftovers: they are the id allocators for
the three highest-volume tables, and Hibernate reserves ids in blocks of 50 from them.

Consequences worth knowing:

* An `INSERT` into `routes`, `route_stops` or `unassigned_orders` written by hand must supply an `id`
  explicitly; the column will not generate one. Any script that copies rows into these tables must
  read and advance the matching sequence row, or it will collide with ids the application has already
  reserved.
* A bulk load that bypasses the application must leave the sequence rows at a value above every id it
  inserted, otherwise the next application insert fails on a duplicate key.
* Deleting rows does not move the sequences back, which is normal but makes "recreate from a dump"
  fragile: a dump that copies rows without the sequence tables produces a database whose application
  inserts fail immediately.


## 3. Relationship rules that carry behaviour

1. **A job produces at most one solution.** `solutions.job_id` is `UNIQUE`; this is the database-level
   half of the callback idempotency rule (BR-066). Manual edits create a new solution row with
   `job_id NULL` and `parent_solution_id` set, so the chain, not the job, is the lineage.
2. **A solution belongs to a branch directly** (`solutions.branch_id`), and its routes inherit the
   branch through the solution. Routes, stops, sends and unassigned orders therefore have no branch
   column of their own except `route_sends` (which has none) — tenancy is resolved through the parent.
3. **LEZ verdicts are cached on the route** as `lez_status` + `lez_ruleset_hash` + `lez_violations`.
   `STALE` is computed by comparing the stored hash with the current rules, never stored. This is why
   changing a zone does not require touching existing routes.
4. **A send is append-only.** Superseding writes a new status on the old row; nothing is deleted, and
   the route's current send is the single `CURRENT` row.
5. **Deletion cascades are declared in the schema, and they are uneven.** Of the 35 foreign keys:
   11 cascade on delete, 2 set null, 12 restrict, 10 do not act. The cascading ones matter:

   | Parent → child | Rule | Consequence |
   |---|---|---|
   | `branches` → `orders`, `users`, `depots`, `fleets`, `vehicle_types`, `optimization_jobs`, `solutions` | CASCADE | one `DELETE FROM branches` removes the branch's orders (990 k in the local database), users and plans without any confirmation |
   | `optimization_jobs` → `solutions` → `routes` | CASCADE | deleting a job deletes its solutions and their routes; stops follow their route |
   | `optimization_jobs` → `pareto_candidates` | CASCADE | same |
   | `orders` → `route_stops.order_id` | SET NULL | deleting an order silently turns its stop into a stop with no order, leaving the sequence and load figures intact |
   | `pareto_candidates` → `solutions.pareto_candidate_id` | SET NULL | applying a candidate then deleting it detaches the applied revision from its origin |

   So the sweep's explicit delete order is not required by the schema — the schema would do it — but it
   is what makes the deletion auditable. And the javadoc's claim that a trigger protects a job with a
   solution is wrong twice over: no trigger exists, and the foreign key cascades rather than blocking.

## 4. Migrations

Location: `GVRP_Entry_API/src/main/resources/database/`.

| File | Role |
|---|---|
| `init.sql` | version 1.0.0 schema: branches, users, depots, fleets, vehicle types, vehicles, orders, jobs |
| `v2.0.sql` | second-generation changes; drops `route_segments` and rebuilds the route tables |
| `GVRP_master_initial.sql` | a second, independent `CREATE TABLE` set for eleven of the same tables (branches, depots, fleets, orders, users, jobs, solutions, vehicle types, vehicles, routes, route stops); no inserts |
| `migrations/2026-09-07-order-required-skills.sql` | `orders.required_skills` |
| `migrations/2026-10-02-manual-solution-revision.sql` | revision chain columns |
| `migrations/2026-10-03-revision-nullable-job.sql` | `solutions.job_id` → nullable |
| `migrations/2026-10-06-driver-delivery.sql` | `route_sends`, leg columns |
| `migrations/2026-10-18-pareto-candidates.sql` | `pareto_candidates` |
| `migrations/2026-10-27-lez-zones.sql` | zone templates, branch zones, rules, permits |
| `migrations/2026-10-28-lez-compliance.sql` | `routes.lez_*` columns and indexes |

There is no migration runner (no Flyway, no Liquibase). The files are applied by hand, in filename
order, and `ddl-auto=validate` is the only check that the result matches the entities. A fresh
environment is therefore a manual procedure that no document or script performs, and a schema built by
applying the files out of order fails at startup with a column mismatch rather than at the point of
the mistake.

## 5. Data observations from the local development database

Read-only `information_schema` queries and `SELECT COUNT(*)`, `2026-10-30`. No business row was
selected, only counts.

| Table | Rows | Note |
|---|---|---|
| `orders` | 990 266 | a bulk load, far beyond any planning run |
| `route_stops` | 215 473 | |
| `vehicles` | 100 192 | |
| `routes` | 38 026 | |
| `optimization_jobs` | 91 | |
| `solutions` | 65 | more than the 91 jobs because manual/Pareto revisions have `job_id NULL` |
| `branches` | 13 | |
| `users` | 8 | |
| `depots` | 25 | |
| `fleets` | 11 | |
| `vehicle_types` | 18 | |
| `pareto_candidates` | 20 | |
| `unassigned_orders` | 3 051 | |
| `branch_zones` | 2 | |
| `branch_zone_rules` | 1 | |
| `vehicle_permits` | 2 | |
| `zone_templates` / `zone_rule_templates` | 1 / 1 | one template, so the "create a zone from a template" flow has exactly one choice |
| `route_sends` | 3 | |
| the three `*_sequence` tables | 1 each | id allocators for `routes`, `route_stops`, `unassigned_orders` |

Counting method: `SELECT COUNT(*)` for each table. The `information_schema.tables.table_rows`
estimate is unusable on this database — it reports `0` for the sequence tables that hold one row, `7`
users where there are 8, `83` jobs where there are 91, and `54` solutions where there are 65. The
difference is large enough that an earlier draft of this document carried wrong numbers; the table above
is the counted one.

The volume of `orders` and `route_stops` against 91 jobs is a warning for anyone benchmarking: this
database is not a clean production-shaped fixture, and timing measured on it is not comparable to
timing on a fresh one.

## 6. Contradictions and gaps in the schema

1. **`JobCleanupService` javadoc cites a trigger `trg_prevent_job_deletion_with_solution`.** No trigger
   exists in `gvrp_db`, and `optimization_jobs → solutions` is `ON DELETE CASCADE`, so the database does
   the opposite of what the comment describes: it deletes the solution together with the job instead of
   refusing. The sweep's own ordering is the only thing that makes that cascade visible.
2. **`users.username` is globally UNIQUE while BR-009 says per branch.** The rule and the schema
   disagree; the schema wins at runtime. Same for `email` and `slack_user_id`. Not resolved here —
   deciding which one is correct is a product decision.
3. **`solutions.status` and `solutions.type` overlap.** `status` describes feasibility
   (INITIAL/SUCCESS/PARTIAL_SUCCESS/INFEASIBLE) and `type` describes provenance
   (ENGINE_GENERATED/FILE_IMPORTED/MANUAL_EDITED/PARETO_ALTERNATIVE). Every query must know which one
   it means; there is no constraint tying them together.
4. **`FILE_IMPORTED` is a declared solution type with no producer.** No code path creates a solution of
   that type.
5. **No `@Version` anywhere** means concurrent edits are last-write-wins except for route sending.
6. **No archival.** Job retention is hard delete and dry-run by default, so the opposite of archiving:
   nothing is ever removed, and no history table exists for the day someone turns it on.
7. **Three SQL files define overlapping schemas and none states its precedence.** `init.sql`
   (self-described "version 1.0.0"), `v2.0.sql` (which drops and rebuilds the route tables) and
   `GVRP_master_initial.sql` (a second full `CREATE TABLE` set for eleven of the same tables) overlap.
   The live database is the result of applying some sequence of these plus the seven dated migrations;
   nothing in the repository records which files were applied, in what order, or which one wins on a
   fresh install. `ddl-auto=validate` catches a mismatch at startup, which keeps the system honest but
   does not tell anyone how to rebuild the schema correctly.
8. **`branches` is a deletion root.** Because of the cascades in §3.5, a single `DELETE` of a branch row
   removes its orders, users, depots, fleets, vehicles, vehicle types, jobs, solutions and routes. No
   endpoint performs it and no foreign key stops it; direct SQL, a restore script or a test helper that
   truncates the wrong table would.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/entity/**`,
`GVRP_Entry_API/src/main/resources/database/**` (10 SQL files),
`GVRP_Entry_API/src/main/resources/application.properties` (`ddl-auto=validate`, batching),
`information_schema.tables`, `information_schema.columns`, `information_schema.statistics`,
`information_schema.key_column_usage`, `information_schema.triggers` for `gvrp_db` (read-only
`agent_reader` session, no business rows selected).
