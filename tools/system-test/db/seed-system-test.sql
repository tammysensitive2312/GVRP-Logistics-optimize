-- ============================================================================
-- GVRP Logistics Optimize - Fictional System Test Seed Fixture (STP-GVRP-01)
-- ============================================================================
-- Target: isolated `gvrp_test_db` on port 3307 (NEVER run on production or gvrp_db).
-- Placeholder `__BCRYPT_PASSWORD_HASH__` is replaced in memory by setup-test-db.ps1.
-- All entities use fictional ST-* names and synthetic Hanoi coordinates.
-- Zero real credentials or real customer PII (AGENTS.md §15).
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE route_sends;
TRUNCATE TABLE route_stops;
TRUNCATE TABLE unassigned_orders;
TRUNCATE TABLE routes;
TRUNCATE TABLE solutions;
TRUNCATE TABLE pareto_candidates;
TRUNCATE TABLE optimization_jobs;
TRUNCATE TABLE vehicle_permits;
TRUNCATE TABLE branch_zone_rules;
TRUNCATE TABLE branch_zones;
TRUNCATE TABLE zone_rule_templates;
TRUNCATE TABLE zone_templates;
TRUNCATE TABLE orders;
TRUNCATE TABLE vehicles;
TRUNCATE TABLE fleets;
TRUNCATE TABLE vehicle_types;
TRUNCATE TABLE depots;
TRUNCATE TABLE users;
TRUNCATE TABLE branches;

-- 1. Branches (3 isolated tenants)
INSERT INTO branches (id, name) VALUES
  (1, 'ST-BRANCH-A'),
  (2, 'ST-BRANCH-B'),
  (3, 'ST-BRANCH-NEW');

-- 2. Users (BCrypt hash injected at runtime by setup-test-db.ps1)
INSERT INTO users (id, branch_id, username, password, email, full_name, phone, role, enabled) VALUES
  (11, 1, 'planner.a',         '__BCRYPT_PASSWORD_HASH__', 'planner.a@example.test',         'Planner Branch A',         '0900000011', 'PLANNER',  1),
  (12, 1, 'driver.a1',         '__BCRYPT_PASSWORD_HASH__', 'driver.a1@example.test',         'Driver One Branch A',      '0900000012', 'DRIVER',   1),
  (13, 1, 'driver.a2',         '__BCRYPT_PASSWORD_HASH__', 'driver.a2@example.test',         'Driver Two Branch A',      '0900000013', 'DRIVER',   1),
  (14, 1, 'customer.a',        '__BCRYPT_PASSWORD_HASH__', 'customer.a@example.test',        'Customer Branch A',        '0900000014', 'CUSTOMER', 1),
  (15, 1, 'disabled.a',        '__BCRYPT_PASSWORD_HASH__', 'disabled.a@example.test',        'Disabled Planner A',       '0900000015', 'PLANNER',  0),
  (16, 1, 'driver.a.disabled', '__BCRYPT_PASSWORD_HASH__', 'driver.a.disabled@example.test', 'Disabled Driver A',        '0900000016', 'DRIVER',   0),
  (21, 2, 'planner.b',         '__BCRYPT_PASSWORD_HASH__', 'planner.b@example.test',         'Planner Branch B',         '0900000021', 'PLANNER',  1),
  (22, 2, 'driver.b1',         '__BCRYPT_PASSWORD_HASH__', 'driver.b1@example.test',         'Driver One Branch B',      '0900000022', 'DRIVER',   1),
  (31, 3, 'planner.new',       '__BCRYPT_PASSWORD_HASH__', 'planner.new@example.test',       'Planner Empty Branch New', '0900000031', 'PLANNER',  1);

-- 3. Depots (SRID 4326, x=longitude, y=latitude)
INSERT INTO depots (id, branch_id, name, address, location) VALUES
  (101, 1, 'ST-Depot-A1', '1 Example Street, Hoan Kiem, Ha Noi', ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat')),
  (201, 2, 'ST-Depot-B1', '2 Example Street, Ba Dinh, Ha Noi',   ST_GeomFromText('POINT(105.8300 21.0400)', 4326, 'axis-order=long-lat'));

-- 4. Vehicle Types (with LEZ legal attributes)
INSERT INTO vehicle_types (id, branch_id, type_name, vehicle_features, description, capacity, fixed_cost, cost_per_km, cost_per_hour, max_distance, max_duration, legal_class, gross_weight_kg, energy_type, emission_standard) VALUES
  (101, 1, 'ST-Van-A1', '{"category":"VAN","emission_factor":180.5,"skills":[]}', 'Diesel van for Branch A',   1000, 200000.00, 5000.00, 50000.00, 200.00, 12.00, 'TRUCK', 3500, 'DIESEL',   4),
  (102, 1, 'ST-EV-A2',  '{"category":"VAN","emission_factor":0.0,"skills":[]}',   'Electric van for Branch A', 1000, 220000.00, 4500.00, 50000.00, 180.00, 12.00, 'TRUCK', 2500, 'ELECTRIC', 6),
  (201, 2, 'ST-Van-B1', '{"category":"VAN","emission_factor":180.5,"skills":[]}', 'Diesel van for Branch B',   1000, 200000.00, 5000.00, 50000.00, 200.00, 12.00, 'TRUCK', 3500, 'DIESEL',   4);

-- 5. Fleets
INSERT INTO fleets (id, branch_id, fleet_name) VALUES
  (101, 1, 'ST-Fleet-A1'),
  (201, 2, 'ST-Fleet-B1');

-- 6. Vehicles
INSERT INTO vehicles (id, fleet_id, vehicle_type_id, vehicle_license_plate, start_depot_id, end_depot_id, status) VALUES
  (101, 101, 101, 'ST-29A-1001', 101, 101, 'AVAILABLE'),
  (102, 101, 102, 'ST-29A-1002', 101, 101, 'AVAILABLE'),
  (103, 101, 101, 'ST-29A-1003', 101, 101, 'MAINTENANCE'),
  (201, 201, 201, 'ST-29B-2001', 201, 201, 'AVAILABLE');

-- 7. System Zone Templates & Template Rules
INSERT INTO zone_templates (id, code, name, jurisdiction, geometry, version, boundary_confidence, boundary_source, legal_basis, created_at) VALUES
  (101, 'ST-TPL-HN-INNER', 'ST Hanoi Inner Zone', 'VN-HN',
   ST_GeomFromText('MULTIPOLYGON(((105.8400 21.0300, 105.8500 21.0300, 105.8500 21.0400, 105.8400 21.0400, 105.8400 21.0300)))', 4326, 'axis-order=long-lat'),
   2, 'DIGITISED_APPROX', 'Synthetic system test boundary', 'Synthetic test rule', NOW()),
  (102, 'ST-TPL-HN-RING', 'ST Hanoi Ring Zone', 'VN-HN',
   ST_GeomFromText('MULTIPOLYGON(((105.8600 21.0200, 105.8700 21.0200, 105.8700 21.0300, 105.8600 21.0300, 105.8600 21.0200)))', 4326, 'axis-order=long-lat'),
   1, 'DIGITISED_APPROX', 'Synthetic system test boundary', 'Synthetic test rule', NOW());

INSERT INTO zone_rule_templates (id, zone_template_id, template_version, selector, schedule, effective_from, effective_to, legal_basis, note, created_at) VALUES
  (101, 101, 2,
   '{"energyTypes":["DIESEL"],"emissionStandardBelow":5}',
   '{"type":"FORBIDDEN_DURING","intervals":[["06:00","22:00"]]}',
   '2025-01-01', NULL, 'Synthetic test rule', 'Restrict Euro < 5 diesel', NOW()),
  (102, 102, 1,
   '{"energyTypes":["DIESEL"]}',
   '{"type":"ALWAYS_FORBIDDEN"}',
   '2025-01-01', NULL, 'Synthetic test rule', 'All-day diesel ban', NOW());

-- 8. Branch Zones & Rules
INSERT INTO branch_zones (id, branch_id, mode, source_template_id, source_template_version, acknowledged_template_version, name, geometry, enabled, created_at) VALUES
  (101, 1, 'LINKED', 101, NULL, NULL, NULL, NULL, 1, NOW()),
  (102, 1, 'COPIED', 101, 1, 1, 'ST-Zone-A-Copy',
   ST_GeomFromText('MULTIPOLYGON(((105.8400 21.0300, 105.8500 21.0300, 105.8500 21.0400, 105.8400 21.0400, 105.8400 21.0300)))', 4326, 'axis-order=long-lat'),
   1, NOW()),
  (103, 1, 'COPIED', 102, 1, 1, 'ST-Zone-A-PermitRef',
   ST_GeomFromText('MULTIPOLYGON(((105.8600 21.0200, 105.8700 21.0200, 105.8700 21.0300, 105.8600 21.0300, 105.8600 21.0200)))', 4326, 'axis-order=long-lat'),
   1, NOW()),
  (201, 2, 'COPIED', 101, 2, 2, 'ST-Zone-B-Copy',
   ST_GeomFromText('MULTIPOLYGON(((105.8400 21.0300, 105.8500 21.0300, 105.8500 21.0400, 105.8400 21.0400, 105.8400 21.0300)))', 4326, 'axis-order=long-lat'),
   1, NOW());

INSERT INTO branch_zone_rules (id, branch_id, branch_zone_id, template_version, selector, schedule, effective_from, effective_to, legal_basis, note, created_at) VALUES
  (102, 1, 102, 1,
   '{"energyTypes":["DIESEL"],"emissionStandardBelow":5}',
   '{"type":"ALWAYS_FORBIDDEN"}',
   '2025-01-01', NULL, 'Synthetic test rule', 'All-day ban for Euro < 5 diesel in Zone A Copy', NOW()),
  (103, 1, 103, 1,
   '{"energyTypes":["DIESEL"]}',
   '{"type":"ALWAYS_FORBIDDEN"}',
   '2025-01-01', NULL, 'Synthetic test rule', 'Permit-referenced zone rule', NOW()),
  (201, 2, 201, 2,
   '{"energyTypes":["DIESEL"],"emissionStandardBelow":5}',
   '{"type":"FORBIDDEN_DURING","intervals":[["06:00","22:00"]]}',
   '2025-01-01', NULL, 'Synthetic test rule', 'Branch B zone rule', NOW());

-- 9. Vehicle Permits
INSERT INTO vehicle_permits (id, branch_id, vehicle_id, branch_zone_id, permit_no, schedule, valid_from, valid_to, note, created_at) VALUES
  (101, 1, 101, 103, 'ST-PRM-101', NULL, '2026-01-01', '2028-12-31', 'Pins ST-Zone-A-PermitRef', NOW()),
  (201, 2, 201, 201, 'ST-PRM-201', NULL, '2026-01-01', '2028-12-31', 'Branch B permit', NOW());

-- 10. Orders
INSERT INTO orders (id, branch_id, order_code, customer_name, customer_phone, address, location, demand, service_time, time_window_start, time_window_end, status, priority, delivery_date) VALUES
  (101, 1, 'ST-ORD-A1',       'Test Customer A1',  '0901000101', '10 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8550 21.0250)', 4326, 'axis-order=long-lat'), 50.00, 10, '08:00:00', '17:00:00', 'SCHEDULED', 1, CURDATE()),
  (102, 1, 'ST-ORD-A2-INLEZ', 'Test Customer A2',  '0901000102', '12 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8450 21.0350)', 4326, 'axis-order=long-lat'), 40.00, 10, '08:00:00', '17:00:00', 'SCHEDULED', 1, CURDATE()),
  (103, 1, 'ST-ORD-A3-NEXTD', 'Test Customer A3',  '0901000103', '14 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8560 21.0260)', 4326, 'axis-order=long-lat'), 30.00, 10, '08:00:00', '17:00:00', 'SCHEDULED', 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY)),
  (104, 1, 'ST-ORD-DUP-01',   'Test Customer Dup', '0901000104', '16 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8570 21.0270)', 4326, 'axis-order=long-lat'), 25.00, 10, '08:00:00', '17:00:00', 'SCHEDULED', 1, CURDATE()),
  (111, 1, 'ST-ORD-ONR-01',   'OnRoute 01',        '0901000111', '21 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8510 21.0210)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (112, 1, 'ST-ORD-ONR-02',   'OnRoute 02',        '0901000112', '22 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8511 21.0211)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (113, 1, 'ST-ORD-ONR-03',   'OnRoute 03',        '0901000113', '23 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8512 21.0212)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (114, 1, 'ST-ORD-ONR-04',   'OnRoute 04',        '0901000114', '24 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8513 21.0213)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (115, 1, 'ST-ORD-ONR-05',   'OnRoute 05',        '0901000115', '25 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8514 21.0214)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (116, 1, 'ST-ORD-ONR-06',   'OnRoute 06',        '0901000116', '26 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8515 21.0215)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (117, 1, 'ST-ORD-ONR-07',   'OnRoute 07',        '0901000117', '27 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8516 21.0216)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (118, 1, 'ST-ORD-ONR-08',   'OnRoute 08',        '0901000118', '28 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8517 21.0217)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (119, 1, 'ST-ORD-ONR-09',   'OnRoute 09',        '0901000119', '29 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8518 21.0218)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (120, 1, 'ST-ORD-ONR-10',   'OnRoute 10',        '0901000120', '30 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8519 21.0219)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (121, 1, 'ST-ORD-ONR-11',   'OnRoute 11',        '0901000121', '31 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8520 21.0220)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (122, 1, 'ST-ORD-ONR-12',   'OnRoute 12',        '0901000122', '32 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8521 21.0221)', 4326, 'axis-order=long-lat'), 10.00,  5, '08:00:00', '17:00:00', 'ON_ROUTE',  1, CURDATE()),
  (123, 1, 'ST-ORD-COMP-01',  'Completed 01',      '0901000123', '33 Example St, Ha Noi',  ST_GeomFromText('POINT(105.8525 21.0225)', 4326, 'axis-order=long-lat'), 15.00,  5, '08:00:00', '17:00:00', 'COMPLETED', 1, CURDATE()),
  (201, 2, 'SECRET-BRANCH-B-ORD', 'Test Customer B1', '0902000201', '99 Example St, Ha Noi', ST_GeomFromText('POINT(105.8320 21.0410)', 4326, 'axis-order=long-lat'), 45.00, 10, '08:00:00', '17:00:00', 'SCHEDULED', 1, CURDATE());

-- 11. Optimization Jobs (start Branch A with 2 active jobs: 102 PROCESSING, 104 PENDING)
INSERT INTO optimization_jobs (id, branch_id, created_by_user_id, status, external_job_id, input_data, estimated_duration_minutes, created_at) VALUES
  (101, 1, 11, 'COMPLETED',  'engine-101', '{}', 1, NOW()),
  (102, 1, 11, 'PROCESSING', 'engine-102', '{}', 1, NOW()),
  (104, 1, 11, 'PENDING',    NULL,         '{}', 1, NOW()),
  (106, 1, 11, 'FAILED',     'engine-106', '{}', 1, NOW()),
  (107, 1, 11, 'CANCELLED',  'engine-107', '{}', 1, NOW()),
  (201, 2, 21, 'COMPLETED',  'engine-201', '{}', 1, NOW());

-- 12. Pareto Candidates
INSERT INTO pareto_candidates (id, job_id, branch_id, candidate_index, label, cost_weight, co2_weight, total_cost, total_distance, total_co2, total_time, total_vehicles_used, served_orders, unserved_orders, frontier_status, engine_selected, clustered, cluster_count, plan_json, created_at) VALUES
  (101, 101, 1, 0, 'Cost focused',  0.900000, 0.100000, 250000.00, 10.50, 1.90, 45.00, 1, 1, 0, 'FRONTIER',  1, 0, NULL, '{"routes":[],"unassigned_orders":[]}', NOW()),
  (102, 101, 1, 1, 'Balanced',      0.500000, 0.500000, 260000.00, 10.20, 1.75, 46.00, 1, 1, 0, 'FRONTIER',  0, 0, NULL, '{"routes":[],"unassigned_orders":[]}', NOW()),
  (103, 101, 1, 2, 'Dominated',     0.300000, 0.700000, 290000.00, 12.00, 2.20, 52.00, 1, 1, 0, 'DOMINATED', 0, 0, NULL, '{"routes":[],"unassigned_orders":[]}', NOW()),
  (201, 201, 2, 0, 'Branch B Plan', 0.700000, 0.300000, 240000.00,  9.80, 1.70, 42.00, 1, 1, 0, 'FRONTIER',  1, 0, NULL, '{"routes":[],"unassigned_orders":[]}', NOW());

-- 13. Solutions
INSERT INTO solutions (id, job_id, branch_id, status, type, total_cost, total_distance, total_co2, total_time, total_vehicles_used, served_orders, unserved_orders, pareto_candidate_id, created_at) VALUES
  (101, 101,  1, 'SUCCESS',    'ENGINE_GENERATED', 250000.00, 10.50, 1.90, 45.00, 1, 1, 0, 101,  NOW()),
  (102, NULL, 1, 'INFEASIBLE', 'MANUAL_EDITED',    250000.00, 10.50, 1.90, 45.00, 1, 1, 0, NULL, NOW()),
  (201, 201,  2, 'SUCCESS',    'ENGINE_GENERATED', 240000.00,  9.80, 1.70, 42.00, 1, 1, 0, 201,  NOW());

-- 14. Routes
INSERT INTO routes (id, solution_id, vehicle_id, route_order, distance, co2_emission, service_time, order_count, load_utilization, lez_status, lez_checked_at, created_at) VALUES
  (101, 101, 102, 1, 5.20, 0.00, 10.00, 1, 5.00,  'COMPLIANT', NOW(), NOW()),
  (102, 101, 102, 2, 0.00, 0.00,  0.00, 0, 0.00,  'COMPLIANT', NOW(), NOW()),
  (103, 101, 101, 3, 6.40, 1.15, 10.00, 1, 4.00,  'COMPLIANT', NOW(), NOW()),
  (104, 101, 102, 4, 5.20, 0.00, 10.00, 1, 5.00,  'COMPLIANT', NOW(), NOW()),
  (201, 201, 201, 1, 4.80, 0.86, 10.00, 1, 4.50,  'COMPLIANT', NOW(), NOW());

-- 15. Route Stops
INSERT INTO route_stops (id, route_id, sequence_number, type, order_id, location_id, location_name, location, arrival_time, departure_time, service_time, wait_time, demand, load_after, distance_to_next, time_to_next, geometry_to_next, instructions_to_next) VALUES
  (101, 101, 0, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:00:00', '08:00:00',  0.00, 0.00,  0.00, 50.00, 2.60, 10.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Head south","distance_m":2600,"time_ms":600000,"sign":0,"street_name":"Example St"}]'),
  (102, 101, 1, 'ORDER', 101,  '101',       'ST-ORD-A1',       ST_GeomFromText('POINT(105.8550 21.0250)', 4326, 'axis-order=long-lat'), '08:10:00', '08:20:00', 10.00, 0.00, 50.00,  0.00, 2.60, 10.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Return to depot","distance_m":2600,"time_ms":600000,"sign":0,"street_name":"Example St"}]'),
  (103, 101, 2, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:30:00', '08:30:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL),

  (104, 102, 0, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:00:00', '08:00:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL),
  (105, 102, 1, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:05:00', '08:05:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL),

  (107, 103, 0, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:00:00', '08:00:00',  0.00, 0.00,  0.00, 40.00, 3.20, 12.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Enter LEZ","distance_m":3200,"time_ms":720000,"sign":0,"street_name":"Example St"}]'),
  (108, 103, 1, 'ORDER', 102,  '102',       'ST-ORD-A2-INLEZ', ST_GeomFromText('POINT(105.8450 21.0350)', 4326, 'axis-order=long-lat'), '08:12:00', '08:22:00', 10.00, 0.00, 40.00,  0.00, 3.20, 12.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Return","distance_m":3200,"time_ms":720000,"sign":0,"street_name":"Example St"}]'),
  (109, 103, 2, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:34:00', '08:34:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL),

  (110, 104, 0, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:00:00', '08:00:00',  0.00, 0.00,  0.00, 50.00, 2.60, 10.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Head south","distance_m":2600,"time_ms":600000,"sign":0,"street_name":"Example St"}]'),
  (111, 104, 1, 'ORDER', 101,  '101',       'ST-ORD-A1',       ST_GeomFromText('POINT(105.8550 21.0250)', 4326, 'axis-order=long-lat'), '08:10:00', '08:20:00', 10.00, 0.00, 50.00,  0.00, 2.60, 10.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Return","distance_m":2600,"time_ms":600000,"sign":0,"street_name":"Example St"}]'),
  (112, 104, 2, 'DEPOT', NULL, 'DEPOT-101', 'ST-Depot-A1',     ST_GeomFromText('POINT(105.8542 21.0285)', 4326, 'axis-order=long-lat'), '08:30:00', '08:30:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL),

  (201, 201, 0, 'DEPOT', NULL, 'DEPOT-201', 'ST-Depot-B1',     ST_GeomFromText('POINT(105.8300 21.0400)', 4326, 'axis-order=long-lat'), '08:00:00', '08:00:00',  0.00, 0.00,  0.00, 45.00, 2.40,  9.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Go","distance_m":2400,"time_ms":540000,"sign":0,"street_name":"Example St"}]'),
  (202, 201, 1, 'ORDER', 201,  '201',       'SECRET-BRANCH-B-ORD', ST_GeomFromText('POINT(105.8320 21.0410)', 4326, 'axis-order=long-lat'), '08:09:00', '08:19:00', 10.00, 0.00, 45.00,  0.00, 2.40,  9.00, '_p~iF~ps|U_ulLnnqC', '[{"text":"Back","distance_m":2400,"time_ms":540000,"sign":0,"street_name":"Example St"}]'),
  (203, 201, 2, 'DEPOT', NULL, 'DEPOT-201', 'ST-Depot-B1',     ST_GeomFromText('POINT(105.8300 21.0400)', 4326, 'axis-order=long-lat'), '08:28:00', '08:28:00',  0.00, 0.00,  0.00,  0.00, 0.00,  0.00, NULL, NULL);

-- 16. Route Sends
INSERT INTO route_sends (id, solution_id, route_id, driver_id, sender_id, sent_at, status, created_at) VALUES
  (101, 101, 101, 12, 11, NOW(), 'CURRENT', NOW()),
  (102, 101, 104, 13, 11, NOW(), 'CURRENT', NOW()),
  (201, 201, 201, 22, 21, NOW(), 'CURRENT', NOW());

-- 17. Reset Hibernate sequence tables well above seeded IDs (allocationSize = 50)
DELETE FROM route_sequence;
INSERT INTO route_sequence (next_val) VALUES (1000);
DELETE FROM route_stop_sequence;
INSERT INTO route_stop_sequence (next_val) VALUES (1000);
DELETE FROM unassigned_order_sequence;
INSERT INTO unassigned_order_sequence (next_val) VALUES (1000);

ALTER TABLE branches AUTO_INCREMENT = 100;
ALTER TABLE users AUTO_INCREMENT = 100;
ALTER TABLE depots AUTO_INCREMENT = 500;
ALTER TABLE vehicle_types AUTO_INCREMENT = 500;
ALTER TABLE fleets AUTO_INCREMENT = 500;
ALTER TABLE vehicles AUTO_INCREMENT = 500;
ALTER TABLE zone_templates AUTO_INCREMENT = 500;
ALTER TABLE zone_rule_templates AUTO_INCREMENT = 500;
ALTER TABLE branch_zones AUTO_INCREMENT = 500;
ALTER TABLE branch_zone_rules AUTO_INCREMENT = 500;
ALTER TABLE vehicle_permits AUTO_INCREMENT = 500;
ALTER TABLE orders AUTO_INCREMENT = 500;
ALTER TABLE optimization_jobs AUTO_INCREMENT = 500;
ALTER TABLE pareto_candidates AUTO_INCREMENT = 500;
ALTER TABLE solutions AUTO_INCREMENT = 500;
ALTER TABLE route_sends AUTO_INCREMENT = 500;

SET FOREIGN_KEY_CHECKS = 1;
