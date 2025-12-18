-- Test data for VRP-002: INFEASIBLE dataset
-- Generated at 2025-12-18T00:43:16.711181
-- Database: MySQL 8.0+ with Spatial support

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (400, 'Branch Infeasible', NOW(), NOW());

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    400, 
    400, 
    'Depot Inf', 
    'Address',
    ST_GeomFromText('POINT(105.813676 21.055745)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (400, 400, 'Fleet Inf', NOW(), NOW());

INSERT IGNORE INTO vehicle_types (
    id, branch_id, type_name, capacity, fixed_cost,
    cost_per_km, cost_per_hour, max_distance, max_duration,
    vehicle_features, created_at, updated_at
)
VALUES (
    400, 400, 'Tiny Truck', 100,
    50000, 5000, 4000,
    300, 480,
    '{"skills": [], "electric": false, "emission_factor": 12.3}', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    400, 400, 400,
    400, 400,
    '29A-TINY', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    400, 400, 'INF-000',
    'Infeasible Customer 1', '0967774765',
    '109 Test St, Hanoi',
    ST_GeomFromText('POINT(105.780691 20.979566)', 4326, 'axis-order=long-lat'),
    200.0, 30,
    '08:00:00', '09:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    401, 400, 'INF-001',
    'Infeasible Customer 2', '0978541804',
    '765 Test St, Hanoi',
    ST_GeomFromText('POINT(105.812869 21.030391)', 4326, 'axis-order=long-lat'),
    200.0, 30,
    '08:00:00', '09:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    402, 400, 'INF-002',
    'Infeasible Customer 3', '0987206788',
    '227 Test St, Hanoi',
    ST_GeomFromText('POINT(105.793930 21.023089)', 4326, 'axis-order=long-lat'),
    200.0, 30,
    '08:00:00', '09:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);


-- End of test data
