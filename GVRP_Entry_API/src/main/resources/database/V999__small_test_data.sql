-- Test data for VRP-002: SMALL dataset
-- Generated at 2025-12-18T00:43:16.709090
-- Database: MySQL 8.0+ with Spatial support

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (100, 'Test Branch Small', NOW(), NOW());

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    100, 
    100, 
    'Depot Dong Da Test', 
    '291 Luong Dinh Cua, Dong Da, Hanoi',
    ST_GeomFromText('POINT(105.757318 21.042454)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (100, 100, 'Fleet Small', NOW(), NOW());

INSERT IGNORE INTO vehicle_types (
    id, branch_id, type_name, capacity, fixed_cost,
    cost_per_km, cost_per_hour, max_distance, max_duration,
    vehicle_features, created_at, updated_at
)
VALUES (
    100, 100, 'Truck 5T Small', 100,
    50000, 5000, 4000,
    300, 480,
    '{"skills": [], "electric": false, "emission_factor": 12.3}', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    100, 100, 100,
    100, 100,
    '29A-TEST00', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    101, 100, 100,
    100, 100,
    '29A-TEST01', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    102, 100, 100,
    100, 100,
    '29A-TEST02', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    100, 100, 'TEST-0100',
    'Customer 100', '0939958838',
    '143 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.822487 21.052158)', 4326, 'axis-order=long-lat'),
    45.15, 7,
    NULL, NULL, 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    101, 100, 'TEST-0101',
    'Customer 101', '0939345092',
    '239 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.757471 21.029047)', 4326, 'axis-order=long-lat'),
    13.95, 25,
    '08:00:00', '10:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    102, 100, 'TEST-0102',
    'Customer 102', '0947338124',
    '829 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.830698 21.065441)', 4326, 'axis-order=long-lat'),
    12.18, 18,
    '13:00:00', '17:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    103, 100, 'TEST-0103',
    'Customer 103', '0938898923',
    '981 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.765038 21.054860)', 4326, 'axis-order=long-lat'),
    22.1, 16,
    NULL, NULL, 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    104, 100, 'TEST-0104',
    'Customer 104', '0971662963',
    '550 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.847047 20.990994)', 4326, 'axis-order=long-lat'),
    8.55, 14,
    '08:00:00', '12:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    105, 100, 'TEST-0105',
    'Customer 105', '0935808537',
    '722 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.820943 20.985467)', 4326, 'axis-order=long-lat'),
    39.79, 7,
    '10:00:00', '14:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    106, 100, 'TEST-0106',
    'Customer 106', '0970855700',
    '651 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.771082 21.061922)', 4326, 'axis-order=long-lat'),
    20.99, 26,
    '13:00:00', '16:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    107, 100, 'TEST-0107',
    'Customer 107', '0995225343',
    '176 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.779298 21.031925)', 4326, 'axis-order=long-lat'),
    25.8, 13,
    '08:00:00', '12:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);


-- End of test data
