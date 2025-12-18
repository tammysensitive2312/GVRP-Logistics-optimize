create table branches
(
    id         bigint auto_increment
        primary key,
    name       varchar(100)                        not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint name
        unique (name)
)
    comment 'Branch entities for multi-tenant support' collate = utf8mb4_unicode_ci;

create index idx_name
    on branches (name);

create table depots
(
    id         bigint auto_increment
        primary key,
    branch_id  bigint                              not null,
    name       varchar(255)                        null,
    address    varchar(255)                        not null,
    location   point                               not null comment 'GPS coordinates (longitude, latitude)',
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint depots_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade
)
    comment 'Depot/warehouse locations with spatial data' collate = utf8mb4_unicode_ci;

create index idx_branch_id
    on depots (branch_id);

create index idx_depot_branch
    on depots (branch_id);

create spatial index idx_depot_location
    on depots (location);

create spatial index idx_location
    on depots (location);

create table fleets
(
    id         bigint auto_increment
        primary key,
    branch_id  bigint                              not null,
    fleet_name varchar(100)                        not null,
    created_at timestamp default CURRENT_TIMESTAMP null,
    updated_at timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_branch_fleet
        unique (branch_id, fleet_name),
    constraint fleets_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade
)
    comment 'Fleet groups for organizing vehicles' collate = utf8mb4_unicode_ci;

create index idx_branch_id
    on fleets (branch_id);

create index idx_fleet_branch
    on fleets (branch_id);

create table orders
(
    id                bigint auto_increment
        primary key,
    branch_id         bigint                                                                         not null,
    order_code        varchar(50)                                                                    not null,
    customer_name     varchar(100)                                                                   not null,
    customer_phone    varchar(20)                                                                    null,
    address           varchar(255)                                                                   not null,
    location          point                                                                          not null comment 'GPS coordinates (longitude, latitude)',
    demand            decimal(10, 2)                                                                 not null comment 'Demand in kg or units',
    service_time      int       default 0                                                            null comment 'Service time in minutes',
    time_window_start time                                                                           null comment 'Earliest delivery time',
    time_window_end   time                                                                           null comment 'Latest delivery time',
    status            enum ('COMPLETED', 'FAILED', 'ON_ROUTE', 'REJECTED', 'SCHEDULED', 'SERVICING') not null,
    priority          int       default 1                                                            null comment '1=highest priority',
    delivery_notes    text                                                                           null comment 'Additional delivery instructions',
    delivery_date     date      default (curdate())                                                  null,
    created_at        timestamp default CURRENT_TIMESTAMP                                            null,
    updated_at        timestamp default CURRENT_TIMESTAMP                                            null on update CURRENT_TIMESTAMP,
    constraint uk_branch_order_code
        unique (branch_id, order_code),
    constraint orders_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade,
    constraint chk_demand
        check (`demand` > 0),
    constraint chk_priority
        check (`priority` >= 0)
)
    comment 'Delivery orders with time windows and spatial data' collate = utf8mb4_unicode_ci;

create index idx_branch_id
    on orders (branch_id);

create index idx_branch_status
    on orders (branch_id, status);

create spatial index idx_location
    on orders (location);

create index idx_order_branch
    on orders (branch_id);

create index idx_order_code
    on orders (order_code);

create spatial index idx_order_location
    on orders (location);

create index idx_order_status
    on orders (status);

create index idx_priority
    on orders (priority);

create index idx_status
    on orders (status);

create table users
(
    id         bigint auto_increment
        primary key,
    branch_id  bigint                               not null,
    username   varchar(50)                          not null,
    password   varchar(255)                         not null comment 'BCrypt hashed password',
    email      varchar(100)                         not null,
    full_name  varchar(100)                         not null,
    role       enum ('CUSTOMER', 'PLANNER')         not null,
    enabled    tinyint(1) default 1                 not null,
    created_at timestamp  default CURRENT_TIMESTAMP null,
    updated_at timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint email
        unique (email),
    constraint username
        unique (username),
    constraint users_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade
)
    comment 'User accounts with role-based access control' collate = utf8mb4_unicode_ci;

create table optimization_jobs
(
    id                         bigint auto_increment
        primary key,
    branch_id                  bigint                                                             not null,
    status                     enum ('CANCELLED', 'COMPLETED', 'FAILED', 'PENDING', 'PROCESSING') not null,
    created_at                 timestamp default CURRENT_TIMESTAMP                                null,
    started_at                 timestamp                                                          null,
    completed_at               timestamp                                                          null,
    cancelled_at               timestamp                                                          null,
    external_job_id            varchar(255)                                                       null,
    input_data                 json                                                               not null comment 'Serialized RoutePlanningRequest for retry capability',
    error_message              text                                                               null comment 'Error details if status=FAILED',
    estimated_duration_minutes int                                                                null,
    created_by_user_id         bigint                                                             not null,
    constraint optimization_jobs_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade,
    constraint optimization_jobs_ibfk_2
        foreign key (created_by_user_id) references users (id)
)
    comment 'Optimization job tracking and lifecycle management' collate = utf8mb4_unicode_ci;

create index idx_branch_status_created
    on optimization_jobs (branch_id asc, status asc, created_at desc);

create index idx_external_job_id
    on optimization_jobs (external_job_id);

create index idx_job_branch
    on optimization_jobs (branch_id);

create index idx_job_created
    on optimization_jobs (created_at);

create index idx_job_status
    on optimization_jobs (status);

create index idx_status_created
    on optimization_jobs (status, created_at);

create index idx_user_created
    on optimization_jobs (created_at desc);

create definer = root@`%` trigger trg_job_completed_at
    before update
    on optimization_jobs
    for each row
BEGIN
    IF NEW.status IN ('COMPLETED', 'FAILED')
        AND OLD.status NOT IN ('COMPLETED', 'FAILED')
        AND NEW.completed_at IS NULL THEN
        SET NEW.completed_at = CURRENT_TIMESTAMP;
    END IF;

    IF NEW.status = 'CANCELLED'
        AND OLD.status != 'CANCELLED'
        AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END;

create definer = root@`%` trigger trg_prevent_job_deletion_with_solution
    before delete
    on optimization_jobs
    for each row
BEGIN
    DECLARE solution_count INT;

    SELECT COUNT(*) INTO solution_count
    FROM solutions
    WHERE job_id = OLD.id;

    IF solution_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot delete job: solution exists. Delete solution first.';
    END IF;
END;

create table solutions
(
    id                  bigint auto_increment
        primary key,
    job_id              bigint                                                       not null comment 'One-to-one relationship with job',
    branch_id           bigint                                                       not null,
    status              enum ('INFEASIBLE', 'INITIAL', 'PARTIAL_SUCCESS', 'SUCCESS') not null,
    type                enum ('ENGINE_GENERATED', 'FILE_IMPORTED')                   not null,
    total_cost          decimal(10, 2)                                               null,
    total_distance      decimal(10, 2)                                               null comment 'Total distance in km',
    total_co2           decimal(10, 2)                                               null comment 'Total CO2 emission in kg',
    total_time          decimal(10, 2)                                               null comment 'Total service time in hours',
    total_vehicles_used int                                                          null comment 'Number of vehicles used',
    served_orders       int                                                          null comment 'Number of orders served',
    unserved_orders     int                                                          null comment 'Number of unserved orders',
    created_at          timestamp default CURRENT_TIMESTAMP                          null,
    completed_at        datetime(6)                                                  null,
    error_message       text                                                         null,
    constraint job_id
        unique (job_id),
    constraint solutions_ibfk_1
        foreign key (job_id) references optimization_jobs (id)
            on delete cascade,
    constraint solutions_ibfk_2
        foreign key (branch_id) references branches (id)
            on delete cascade
)
    comment 'Optimization solution results linked to jobs' collate = utf8mb4_unicode_ci;

create index idx_branch_id
    on solutions (branch_id);

create index idx_branch_type
    on solutions (branch_id, type);

create index idx_created_at
    on solutions (created_at desc);

create index idx_job_id
    on solutions (job_id);

create index idx_solution_branch
    on solutions (branch_id);

create index idx_solution_created
    on solutions (created_at);

create index idx_solution_status
    on solutions (status);

create index idx_branch_id
    on users (branch_id);

create index idx_email
    on users (email);

create index idx_role
    on users (role);

create index idx_username
    on users (username);

create table vehicle_types
(
    id               bigint auto_increment
        primary key,
    branch_id        bigint                      not null,
    type_name        varchar(100)                not null,
    vehicle_features json                        null comment 'Additional vehicle features/description',
    description      varchar(255)                null comment 'Mô tả chi tiết loại xe (e.g., Tải lạnh, Xe điện)',
    capacity         int                         not null comment 'Capacity in kg or units (Tải trọng chuẩn)',
    fixed_cost       decimal(10, 2) default 0.00 null comment 'Fixed cost per trip in VND',
    cost_per_km      decimal(10, 2) default 0.00 null comment 'Variable cost per km in VND',
    cost_per_hour    decimal(10, 2) default 0.00 null comment 'Variable cost per hour in VND',
    max_distance     decimal(10, 2)              null comment 'Maximum distance in km',
    max_duration     decimal(10, 2)              null comment 'Maximum duration in hours',
    created_at       timestamp                   null,
    updated_at       timestamp                   null,
    constraint uk_branch_type_name
        unique (branch_id, type_name),
    constraint vehicle_types_ibfk_1
        foreign key (branch_id) references branches (id)
            on delete cascade,
    constraint chk_vehicle_capacity
        check (`capacity` > 0),
    constraint chk_vehicle_costs
        check ((`fixed_cost` >= 0) and (`cost_per_km` >= 0) and (`cost_per_hour` >= 0))
)
    comment 'Chuẩn hóa thông số kỹ thuật và chi phí của các loại xe' collate = utf8mb4_unicode_ci;

create index idx_branch_id
    on vehicle_types (branch_id);

create table vehicles
(
    id                    bigint auto_increment
        primary key,
    fleet_id              bigint                                                 not null,
    vehicle_type_id       bigint                                                 null comment 'Foreign key to vehicle_types',
    start_depot_id        bigint                                                 not null,
    end_depot_id          bigint                                                 not null,
    vehicle_license_plate varchar(20)                                            not null,
    status                enum ('AVAILABLE', 'IN_USE', 'MAINTENANCE', 'RETIRED') not null,
    created_at            timestamp default CURRENT_TIMESTAMP                    null,
    updated_at            timestamp default CURRENT_TIMESTAMP                    null on update CURRENT_TIMESTAMP,
    constraint idx_vehicle_license
        unique (vehicle_license_plate),
    constraint vehicle_license_plate
        unique (vehicle_license_plate),
    constraint fk_vehicle_type
        foreign key (vehicle_type_id) references vehicle_types (id),
    constraint vehicles_ibfk_1
        foreign key (fleet_id) references fleets (id)
            on delete cascade,
    constraint vehicles_ibfk_2
        foreign key (start_depot_id) references depots (id),
    constraint vehicles_ibfk_3
        foreign key (end_depot_id) references depots (id)
)
    comment 'Vehicle fleet with operational constraints' collate = utf8mb4_unicode_ci;

create table routes
(
    id               bigint auto_increment
        primary key,
    solution_id      bigint                              not null,
    vehicle_id       bigint                              not null,
    route_order      int                                 not null comment 'Sequence number within solution',
    distance         decimal(10, 2)                      null comment 'Route distance in km',
    co2_emission     decimal(10, 2)                      null comment 'Route CO2 emission in kg',
    service_time     decimal(10, 2)                      null comment 'Total service time in hours',
    order_count      int                                 null comment 'Number of orders in this route',
    load_utilization decimal(5, 2)                       null comment 'Vehicle load utilization percentage',
    created_at       timestamp default CURRENT_TIMESTAMP null,
    constraint routes_ibfk_1
        foreign key (solution_id) references solutions (id)
            on delete cascade,
    constraint routes_ibfk_2
        foreign key (vehicle_id) references vehicles (id),
    constraint chk_load_utilization
        check ((`load_utilization` is null) or ((`load_utilization` >= 0) and (`load_utilization` <= 100)))
)
    comment 'Individual routes assigned to vehicles' collate = utf8mb4_unicode_ci;

create table route_stops
(
    id               bigint auto_increment comment 'Primary key'
        primary key,
    route_id         bigint         not null comment 'Foreign key to routes table',
    order_id         bigint         null comment 'Foreign key to orders table (NULL for depot stops)',
    sequence_number  int            null comment 'Stop sequence in route (0-based)',
    type             varchar(10)    not null comment 'DEPOT or ORDER',
    location_id      varchar(255)   null,
    location_name    varchar(255)   null comment 'Display name of location',
    address          varchar(255)   null,
    location         point          null comment 'GPS coordinates (longitude, latitude)',
    arrival_time     time           null,
    departure_time   time           null,
    service_time     decimal(10, 2) null comment 'Service time in minutes',
    wait_time        decimal(10, 2) null comment 'Wait time in minutes',
    demand           decimal(10, 2) null comment 'Demand delivered at this stop (kg)',
    load_after       decimal(10, 2) null comment 'Remaining load after this stop (kg)',
    distance_to_next decimal(10, 2) null comment 'Distance to next stop (km)',
    time_to_next     decimal(10, 2) null comment 'Time to next stop (minutes)',
    constraint FK_route_stops_order
        foreign key (order_id) references orders (id)
            on delete set null,
    constraint FK_route_stops_route
        foreign key (route_id) references routes (id)
            on delete cascade,
    constraint CHK_stop_type
        check (`type` in ('DEPOT','ORDER'))
)
comment 'Route stops - each record represents a stop in a route' collate=utf8mb4_unicode_ci;

create index idx_stop_order
    on route_stops (order_id);

create index idx_stop_route
    on route_stops (route_id);

create index idx_stop_sequence
    on route_stops (route_id, sequence_number);

create index idx_stop_type
    on route_stops (type);

create index idx_route_solution
    on routes (solution_id);

create index idx_route_vehicle
    on routes (vehicle_id);

create index idx_solution_id
    on routes (solution_id);

create index idx_solution_order
    on routes (solution_id, route_order);

create index idx_vehicle_id
    on routes (vehicle_id);

create index idx_end_depot
    on vehicles (end_depot_id);

create index idx_fleet_id
    on vehicles (fleet_id);

create index idx_license_plate
    on vehicles (vehicle_license_plate);

create index idx_start_depot
    on vehicles (start_depot_id);

create index idx_status
    on vehicles (status);

create index idx_vehicle_fleet
    on vehicles (fleet_id);

create index idx_vehicle_status
    on vehicles (status);

create index idx_vehicle_type_id
    on vehicles (vehicle_type_id);

create definer = root@`%` view v_active_jobs as
select `j`.`id`                                       AS `id`,
       `j`.`branch_id`                                AS `branch_id`,
       `j`.`created_by_user_id`                       AS `user_id`,
       `u`.`username`                                 AS `username`,
       `u`.`full_name`                                AS `full_name`,
       `j`.`status`                                   AS `status`,
       `j`.`created_at`                               AS `created_at`,
       `j`.`started_at`                               AS `started_at`,
       timestampdiff(SECOND, `j`.`started_at`, now()) AS `elapsed_seconds`,
       `j`.`error_message`                            AS `error_message`
from (`gvrp_db`.`optimization_jobs` `j` join `gvrp_db`.`users` `u` on ((`j`.`created_by_user_id` = `u`.`id`)))
where (`j`.`status` in ('PENDING', 'PROCESSING'));

create definer = root@`%` view v_route_stops_detail as
select `rs`.`id`                   AS `stop_id`,
       `rs`.`route_id`             AS `route_id`,
       `r`.`vehicle_id`            AS `vehicle_id`,
       `v`.`vehicle_license_plate` AS `vehicle_license_plate`,
       `rs`.`sequence_number`      AS `sequence_number`,
       `rs`.`type`                 AS `type`,
       `rs`.`location_name`        AS `location_name`,
       `rs`.`address`              AS `address`,
       `rs`.`arrival_time`         AS `arrival_time`,
       `rs`.`departure_time`       AS `departure_time`,
       `rs`.`service_time`         AS `service_time`,
       `rs`.`wait_time`            AS `wait_time`,
       `rs`.`demand`               AS `demand`,
       `rs`.`load_after`           AS `load_after`,
       `rs`.`distance_to_next`     AS `distance_to_next`,
       `rs`.`time_to_next`         AS `time_to_next`,
       `o`.`id`                    AS `order_id`,
       `o`.`order_code`            AS `order_code`,
       `o`.`customer_name`         AS `customer_name`,
       `o`.`customer_phone`        AS `customer_phone`,
       `r`.`distance`              AS `route_distance`,
       `r`.`service_time`          AS `route_duration`,
       `r`.`co2_emission`          AS `route_co2`
from (((`gvrp_db`.`route_stops` `rs` left join `gvrp_db`.`routes` `r`
        on ((`rs`.`route_id` = `r`.`id`))) left join `gvrp_db`.`vehicles` `v`
       on ((`r`.`vehicle_id` = `v`.`id`))) left join `gvrp_db`.`orders` `o` on ((`rs`.`order_id` = `o`.`id`)))
order by `rs`.`route_id`, `rs`.`sequence_number`;

-- comment on column v_route_stops_detail.stop_id not supported: Primary key

-- comment on column v_route_stops_detail.route_id not supported: Foreign key to routes table

-- comment on column v_route_stops_detail.sequence_number not supported: Stop sequence in route (0-based)

-- comment on column v_route_stops_detail.type not supported: DEPOT or ORDER

-- comment on column v_route_stops_detail.location_name not supported: Display name of location

-- comment on column v_route_stops_detail.service_time not supported: Service time in minutes

-- comment on column v_route_stops_detail.wait_time not supported: Wait time in minutes

-- comment on column v_route_stops_detail.demand not supported: Demand delivered at this stop (kg)

-- comment on column v_route_stops_detail.load_after not supported: Remaining load after this stop (kg)

-- comment on column v_route_stops_detail.distance_to_next not supported: Distance to next stop (km)

-- comment on column v_route_stops_detail.time_to_next not supported: Time to next stop (minutes)

-- comment on column v_route_stops_detail.route_distance not supported: Route distance in km

-- comment on column v_route_stops_detail.route_duration not supported: Total service time in hours

-- comment on column v_route_stops_detail.route_co2 not supported: Route CO2 emission in kg

create definer = root@`%` view v_solution_summary as
select `s`.`id`                                                    AS `solution_id`,
       `s`.`job_id`                                                AS `job_id`,
       `j`.`created_by_user_id`                                    AS `user_id`,
       `u`.`username`                                              AS `username`,
       `s`.`branch_id`                                             AS `branch_id`,
       `s`.`status`                                                AS `status`,
       `s`.`type`                                                  AS `type`,
       `s`.`total_distance`                                        AS `total_distance`,
       `s`.`total_co2`                                             AS `total_co2`,
       `s`.`total_vehicles_used`                                   AS `total_vehicles_used`,
       `s`.`served_orders`                                         AS `served_orders`,
       `s`.`unserved_orders`                                       AS `unserved_orders`,
       `s`.`created_at`                                            AS `created_at`,
       `j`.`created_at`                                            AS `job_created_at`,
       timestampdiff(SECOND, `j`.`created_at`, `j`.`completed_at`) AS `processing_duration_seconds`
from ((`gvrp_db`.`solutions` `s` join `gvrp_db`.`optimization_jobs` `j`
       on ((`s`.`job_id` = `j`.`id`))) join `gvrp_db`.`users` `u` on ((`j`.`created_by_user_id` = `u`.`id`)));

create definer = root@`%` view v_vehicle_availability as
select `v`.`id`                    AS `id`,
       `v`.`fleet_id`              AS `fleet_id`,
       `f`.`fleet_name`            AS `fleet_name`,
       `f`.`branch_id`             AS `branch_id`,
       `v`.`vehicle_license_plate` AS `vehicle_license_plate`,
       `v`.`status`                AS `status`,
       `d`.`name`                  AS `start_depot_name`,
       count(distinct `r`.`id`)    AS `active_routes`
from (((((`gvrp_db`.`vehicles` `v` join `gvrp_db`.`fleets` `f`
          on ((`v`.`fleet_id` = `f`.`id`))) join `gvrp_db`.`depots` `d`
         on ((`v`.`start_depot_id` = `d`.`id`))) left join `gvrp_db`.`routes` `r`
        on ((`v`.`id` = `r`.`vehicle_id`))) left join `gvrp_db`.`solutions` `s`
       on ((`r`.`solution_id` = `s`.`id`))) left join `gvrp_db`.`optimization_jobs` `j`
      on (((`s`.`job_id` = `j`.`id`) and (`j`.`status` = 'PROCESSING'))))
group by `v`.`id`, `v`.`fleet_id`, `f`.`fleet_name`, `f`.`branch_id`, `v`.`vehicle_license_plate`,
         `v`.`status`, `d`.`name`;

create
    definer = root@`%` procedure sp_can_submit_job(IN p_branch_id bigint, OUT p_can_submit tinyint(1),
                                                   OUT p_running_job_id bigint)
BEGIN
    SELECT
        COUNT(*) = 0,
        MAX(id)
    INTO p_can_submit, p_running_job_id
    FROM optimization_jobs
    WHERE branch_id = p_branch_id
      AND status = 'PROCESSING';
END;

create
    definer = root@`%` procedure sp_cleanup_old_jobs()
BEGIN
    DECLARE rows_deleted INT DEFAULT 0;

    -- Delete CANCELLED jobs older than 7 days
    DELETE FROM optimization_jobs
    WHERE status = 'CANCELLED'
      AND created_at < DATE_SUB(NOW(), INTERVAL 7 DAY);

    SET rows_deleted = rows_deleted + ROW_COUNT();

    -- Delete FAILED jobs older than 30 days
    DELETE FROM optimization_jobs
    WHERE status = 'FAILED'
      AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

    SET rows_deleted = rows_deleted + ROW_COUNT();

    -- Delete COMPLETED jobs older than 90 days (optional)
    -- Uncomment if you want to auto-cleanup completed jobs
    -- DELETE FROM optimization_jobs
    -- WHERE status = 'COMPLETED'
    --   AND created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
    -- SET rows_deleted = rows_deleted + ROW_COUNT();

    SELECT rows_deleted as jobs_deleted;
END;

create
    definer = root@`%` procedure sp_get_job_statistics(IN p_branch_id bigint, IN p_days int)
BEGIN
    SELECT
        status,
        COUNT(*) as count,
        AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)) as avg_duration_seconds,
        MIN(created_at) as first_job,
        MAX(created_at) as last_job
    FROM optimization_jobs
    WHERE branch_id = p_branch_id
      AND created_at >= DATE_SUB(NOW(), INTERVAL p_days DAY)
    GROUP BY status
    ORDER BY status;
END;

