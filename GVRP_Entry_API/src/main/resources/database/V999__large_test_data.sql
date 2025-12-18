-- Test data for VRP-002: LARGE dataset
-- Generated at 2025-12-18T00:43:16.710089
-- Database: MySQL 8.0+ with Spatial support

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (300, 'Branch Large 1', NOW(), NOW());

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (301, 'Branch Large 2', NOW(), NOW());

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (302, 'Branch Large 3', NOW(), NOW());

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    300, 
    300, 
    'Depot Large 1', 
    'Address 1, Hanoi',
    ST_GeomFromText('POINT(105.821506 21.029223)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    301, 
    301, 
    'Depot Large 2', 
    'Address 2, Hanoi',
    ST_GeomFromText('POINT(105.824538 21.011129)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    302, 
    302, 
    'Depot Large 3', 
    'Address 3, Hanoi',
    ST_GeomFromText('POINT(105.774001 21.033952)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    303, 
    300, 
    'Depot Large 4', 
    'Address 4, Hanoi',
    ST_GeomFromText('POINT(105.792732 21.045004)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    304, 
    301, 
    'Depot Large 5', 
    'Address 5, Hanoi',
    ST_GeomFromText('POINT(105.772220 21.053324)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    305, 
    302, 
    'Depot Large 6', 
    'Address 6, Hanoi',
    ST_GeomFromText('POINT(105.795425 21.035422)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (300, 300, 'Fleet Large 1', NOW(), NOW());

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (301, 301, 'Fleet Large 2', NOW(), NOW());

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (302, 302, 'Fleet Large 3', NOW(), NOW());

INSERT IGNORE INTO vehicle_types (
    id, branch_id, type_name, capacity, fixed_cost,
    cost_per_km, cost_per_hour, max_distance, max_duration,
    vehicle_features, created_at, updated_at
)
VALUES (
    300, 300, 'Truck 10T Large', 200,
    50000, 5000, 4000,
    500, 600,
    '{"skills": [], "electric": false, "emission_factor": 12.3}', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    300, 300, 300,
    300, 300,
    '29A-LRG-000', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    301, 301, 300,
    301, 301,
    '29A-LRG-001', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    302, 302, 300,
    302, 302,
    '29A-LRG-002', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    303, 300, 300,
    303, 303,
    '29A-LRG-003', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    304, 301, 300,
    304, 304,
    '29A-LRG-004', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    305, 302, 300,
    305, 305,
    '29A-LRG-005', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    306, 300, 300,
    300, 300,
    '29A-LRG-006', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    307, 301, 300,
    301, 301,
    '29A-LRG-007', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    308, 302, 300,
    302, 302,
    '29A-LRG-008', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    309, 300, 300,
    303, 303,
    '29A-LRG-009', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    310, 301, 300,
    304, 304,
    '29A-LRG-010', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    311, 302, 300,
    305, 305,
    '29A-LRG-011', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    312, 300, 300,
    300, 300,
    '29A-LRG-012', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    313, 301, 300,
    301, 301,
    '29A-LRG-013', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    314, 302, 300,
    302, 302,
    '29A-LRG-014', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    315, 300, 300,
    303, 303,
    '29A-LRG-015', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    316, 301, 300,
    304, 304,
    '29A-LRG-016', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    317, 302, 300,
    305, 305,
    '29A-LRG-017', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    318, 300, 300,
    300, 300,
    '29A-LRG-018', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    319, 301, 300,
    301, 301,
    '29A-LRG-019', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    320, 302, 300,
    302, 302,
    '29A-LRG-020', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    321, 300, 300,
    303, 303,
    '29A-LRG-021', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    322, 301, 300,
    304, 304,
    '29A-LRG-022', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    323, 302, 300,
    305, 305,
    '29A-LRG-023', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    324, 300, 300,
    300, 300,
    '29A-LRG-024', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    300, 300, 'TEST-0300',
    'Customer 300', '0938210242',
    '441 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.815483 21.057087)', 4326, 'axis-order=long-lat'),
    19.5, 19,
    '10:00:00', '13:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    301, 300, 'TEST-0301',
    'Customer 301', '0932775593',
    '675 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.806362 20.986991)', 4326, 'axis-order=long-lat'),
    33.48, 15,
    '14:00:00', '17:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    302, 300, 'TEST-0302',
    'Customer 302', '0951663795',
    '231 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.769552 21.059167)', 4326, 'axis-order=long-lat'),
    7.08, 20,
    '09:00:00', '13:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    303, 300, 'TEST-0303',
    'Customer 303', '0965625330',
    '908 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.774261 21.041488)', 4326, 'axis-order=long-lat'),
    36.34, 20,
    '08:00:00', '11:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    304, 300, 'TEST-0304',
    'Customer 304', '0998054615',
    '705 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829913 20.979065)', 4326, 'axis-order=long-lat'),
    39.65, 8,
    NULL, NULL, 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    305, 300, 'TEST-0305',
    'Customer 305', '0979519112',
    '476 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.779737 20.983533)', 4326, 'axis-order=long-lat'),
    43.18, 19,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    306, 300, 'TEST-0306',
    'Customer 306', '0989920257',
    '325 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.843960 21.073515)', 4326, 'axis-order=long-lat'),
    32.57, 28,
    '14:00:00', '18:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    307, 300, 'TEST-0307',
    'Customer 307', '0931361812',
    '762 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.799824 21.064660)', 4326, 'axis-order=long-lat'),
    38.83, 25,
    '14:00:00', '17:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    308, 300, 'TEST-0308',
    'Customer 308', '0994120751',
    '245 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.762565 21.005971)', 4326, 'axis-order=long-lat'),
    17.86, 13,
    '14:00:00', '17:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    309, 300, 'TEST-0309',
    'Customer 309', '0928572252',
    '155 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.824212 21.001637)', 4326, 'axis-order=long-lat'),
    36.79, 7,
    '14:00:00', '16:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    310, 300, 'TEST-0310',
    'Customer 310', '0965804273',
    '64 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.796831 20.999194)', 4326, 'axis-order=long-lat'),
    45.72, 23,
    '14:00:00', '17:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    311, 300, 'TEST-0311',
    'Customer 311', '0974019136',
    '7 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.784678 21.072785)', 4326, 'axis-order=long-lat'),
    22.55, 18,
    '14:00:00', '17:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    312, 300, 'TEST-0312',
    'Customer 312', '0939600202',
    '500 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.798401 21.000453)', 4326, 'axis-order=long-lat'),
    6.31, 15,
    '14:00:00', '18:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    313, 300, 'TEST-0313',
    'Customer 313', '0993517915',
    '547 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.794219 20.981208)', 4326, 'axis-order=long-lat'),
    30.4, 5,
    '13:00:00', '15:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    314, 300, 'TEST-0314',
    'Customer 314', '0934391257',
    '52 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.787552 21.004527)', 4326, 'axis-order=long-lat'),
    25.46, 15,
    '09:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    315, 300, 'TEST-0315',
    'Customer 315', '0966581473',
    '259 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.801848 21.062002)', 4326, 'axis-order=long-lat'),
    38.71, 6,
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
    316, 300, 'TEST-0316',
    'Customer 316', '0919209801',
    '800 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.758843 21.074240)', 4326, 'axis-order=long-lat'),
    6.4, 12,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    317, 300, 'TEST-0317',
    'Customer 317', '0942016960',
    '130 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.766256 21.025867)', 4326, 'axis-order=long-lat'),
    47.65, 19,
    '14:00:00', '16:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    318, 300, 'TEST-0318',
    'Customer 318', '0991503378',
    '987 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.766270 21.053304)', 4326, 'axis-order=long-lat'),
    41.87, 14,
    '09:00:00', '13:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    319, 300, 'TEST-0319',
    'Customer 319', '0960372847',
    '407 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.774650 21.072658)', 4326, 'axis-order=long-lat'),
    31.64, 25,
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
    320, 300, 'TEST-0320',
    'Customer 320', '0950477742',
    '871 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.835370 21.046937)', 4326, 'axis-order=long-lat'),
    40.83, 23,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    321, 300, 'TEST-0321',
    'Customer 321', '0959737181',
    '71 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.788940 21.029107)', 4326, 'axis-order=long-lat'),
    43.23, 20,
    '13:00:00', '17:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    322, 300, 'TEST-0322',
    'Customer 322', '0971705170',
    '725 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.772431 20.993810)', 4326, 'axis-order=long-lat'),
    28.48, 25,
    '10:00:00', '14:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    323, 300, 'TEST-0323',
    'Customer 323', '0972394301',
    '447 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.814064 21.061084)', 4326, 'axis-order=long-lat'),
    19.5, 12,
    '14:00:00', '17:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    324, 300, 'TEST-0324',
    'Customer 324', '0970505620',
    '250 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.811802 21.053559)', 4326, 'axis-order=long-lat'),
    35.07, 15,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    325, 300, 'TEST-0325',
    'Customer 325', '0975437844',
    '218 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.780652 21.013993)', 4326, 'axis-order=long-lat'),
    17.58, 24,
    '10:00:00', '12:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    326, 300, 'TEST-0326',
    'Customer 326', '0921490777',
    '248 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.803675 21.050518)', 4326, 'axis-order=long-lat'),
    39.12, 27,
    '14:00:00', '16:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    327, 300, 'TEST-0327',
    'Customer 327', '0912314351',
    '96 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.795257 21.007935)', 4326, 'axis-order=long-lat'),
    15.95, 26,
    '13:00:00', '16:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    328, 300, 'TEST-0328',
    'Customer 328', '0956138336',
    '436 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.809856 21.078174)', 4326, 'axis-order=long-lat'),
    20.83, 19,
    '14:00:00', '18:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    329, 300, 'TEST-0329',
    'Customer 329', '0935848226',
    '324 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.808403 20.990466)', 4326, 'axis-order=long-lat'),
    39.3, 10,
    '09:00:00', '11:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    330, 300, 'TEST-0330',
    'Customer 330', '0974988034',
    '284 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.852478 21.050970)', 4326, 'axis-order=long-lat'),
    28.61, 14,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    331, 300, 'TEST-0331',
    'Customer 331', '0958436637',
    '184 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.825619 21.008738)', 4326, 'axis-order=long-lat'),
    10.7, 6,
    '10:00:00', '12:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    332, 300, 'TEST-0332',
    'Customer 332', '0975884623',
    '106 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.812223 21.065783)', 4326, 'axis-order=long-lat'),
    26.12, 19,
    '09:00:00', '13:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    333, 300, 'TEST-0333',
    'Customer 333', '0916895666',
    '259 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.802590 21.072617)', 4326, 'axis-order=long-lat'),
    42.0, 17,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    334, 300, 'TEST-0334',
    'Customer 334', '0994482772',
    '703 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.769737 20.983872)', 4326, 'axis-order=long-lat'),
    30.33, 14,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    335, 300, 'TEST-0335',
    'Customer 335', '0965857931',
    '621 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.816659 21.038124)', 4326, 'axis-order=long-lat'),
    39.91, 17,
    '08:00:00', '12:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    336, 300, 'TEST-0336',
    'Customer 336', '0967556566',
    '313 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.760840 21.035374)', 4326, 'axis-order=long-lat'),
    48.2, 8,
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
    337, 300, 'TEST-0337',
    'Customer 337', '0920896797',
    '161 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.810016 21.002498)', 4326, 'axis-order=long-lat'),
    12.04, 18,
    '10:00:00', '14:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    338, 300, 'TEST-0338',
    'Customer 338', '0914380847',
    '238 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.783089 21.007321)', 4326, 'axis-order=long-lat'),
    43.68, 7,
    '13:00:00', '16:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    339, 300, 'TEST-0339',
    'Customer 339', '0936550845',
    '436 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.777298 20.989989)', 4326, 'axis-order=long-lat'),
    11.7, 13,
    '14:00:00', '18:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    340, 300, 'TEST-0340',
    'Customer 340', '0932269779',
    '812 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829713 21.009272)', 4326, 'axis-order=long-lat'),
    30.61, 14,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    341, 300, 'TEST-0341',
    'Customer 341', '0950814428',
    '717 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.782042 21.018762)', 4326, 'axis-order=long-lat'),
    29.3, 19,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    342, 300, 'TEST-0342',
    'Customer 342', '0953261270',
    '619 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.763950 21.003548)', 4326, 'axis-order=long-lat'),
    48.29, 23,
    '13:00:00', '17:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    343, 300, 'TEST-0343',
    'Customer 343', '0915399803',
    '782 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.801867 21.054138)', 4326, 'axis-order=long-lat'),
    34.32, 13,
    '10:00:00', '14:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    344, 300, 'TEST-0344',
    'Customer 344', '0975998319',
    '993 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.789613 20.987635)', 4326, 'axis-order=long-lat'),
    20.0, 26,
    '13:00:00', '17:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    345, 300, 'TEST-0345',
    'Customer 345', '0976500726',
    '296 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.794862 21.044769)', 4326, 'axis-order=long-lat'),
    39.22, 6,
    '10:00:00', '13:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    346, 300, 'TEST-0346',
    'Customer 346', '0943876400',
    '332 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832072 20.990103)', 4326, 'axis-order=long-lat'),
    43.92, 5,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    347, 300, 'TEST-0347',
    'Customer 347', '0979583757',
    '371 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.804666 21.040774)', 4326, 'axis-order=long-lat'),
    24.89, 6,
    '08:00:00', '10:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    348, 300, 'TEST-0348',
    'Customer 348', '0927584297',
    '950 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.842902 21.007314)', 4326, 'axis-order=long-lat'),
    26.81, 5,
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
    349, 300, 'TEST-0349',
    'Customer 349', '0983932360',
    '15 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.764138 21.033736)', 4326, 'axis-order=long-lat'),
    49.65, 8,
    '09:00:00', '12:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    350, 300, 'TEST-0350',
    'Customer 350', '0949172995',
    '522 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.796366 21.049052)', 4326, 'axis-order=long-lat'),
    26.71, 20,
    '09:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    351, 300, 'TEST-0351',
    'Customer 351', '0935583290',
    '944 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829446 21.038449)', 4326, 'axis-order=long-lat'),
    11.14, 7,
    '09:00:00', '12:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    352, 300, 'TEST-0352',
    'Customer 352', '0978147397',
    '274 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.783101 21.060566)', 4326, 'axis-order=long-lat'),
    18.44, 23,
    '13:00:00', '16:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    353, 300, 'TEST-0353',
    'Customer 353', '0969934576',
    '552 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.788061 21.026945)', 4326, 'axis-order=long-lat'),
    39.33, 17,
    '13:00:00', '15:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    354, 300, 'TEST-0354',
    'Customer 354', '0942051937',
    '586 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.840418 21.016808)', 4326, 'axis-order=long-lat'),
    23.48, 15,
    '09:00:00', '13:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    355, 300, 'TEST-0355',
    'Customer 355', '0999078806',
    '812 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.852899 21.060586)', 4326, 'axis-order=long-lat'),
    27.29, 6,
    '13:00:00', '16:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    356, 300, 'TEST-0356',
    'Customer 356', '0923470708',
    '896 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.764788 21.063015)', 4326, 'axis-order=long-lat'),
    45.97, 5,
    '14:00:00', '17:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    357, 300, 'TEST-0357',
    'Customer 357', '0973013961',
    '801 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.788673 21.075431)', 4326, 'axis-order=long-lat'),
    36.17, 25,
    '09:00:00', '11:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    358, 300, 'TEST-0358',
    'Customer 358', '0952501540',
    '642 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.830757 21.050383)', 4326, 'axis-order=long-lat'),
    44.26, 6,
    '14:00:00', '17:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    359, 300, 'TEST-0359',
    'Customer 359', '0994712997',
    '701 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.854560 21.070604)', 4326, 'axis-order=long-lat'),
    38.6, 18,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    360, 300, 'TEST-0360',
    'Customer 360', '0932329103',
    '711 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.757712 21.008458)', 4326, 'axis-order=long-lat'),
    19.6, 6,
    '08:00:00', '11:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    361, 300, 'TEST-0361',
    'Customer 361', '0942775624',
    '544 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.822997 21.019716)', 4326, 'axis-order=long-lat'),
    13.1, 10,
    '13:00:00', '15:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    362, 300, 'TEST-0362',
    'Customer 362', '0942323627',
    '510 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.769129 21.069794)', 4326, 'axis-order=long-lat'),
    25.75, 13,
    '13:00:00', '17:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    363, 300, 'TEST-0363',
    'Customer 363', '0911261301',
    '921 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.844938 21.058974)', 4326, 'axis-order=long-lat'),
    35.49, 10,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    364, 300, 'TEST-0364',
    'Customer 364', '0950151803',
    '655 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.823848 21.074714)', 4326, 'axis-order=long-lat'),
    25.56, 14,
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
    365, 300, 'TEST-0365',
    'Customer 365', '0941837210',
    '391 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.812298 21.035703)', 4326, 'axis-order=long-lat'),
    49.67, 14,
    '13:00:00', '15:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    366, 300, 'TEST-0366',
    'Customer 366', '0911086890',
    '580 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832648 21.065018)', 4326, 'axis-order=long-lat'),
    48.81, 24,
    '13:00:00', '16:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    367, 300, 'TEST-0367',
    'Customer 367', '0991478884',
    '822 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.818479 21.013743)', 4326, 'axis-order=long-lat'),
    32.94, 26,
    '10:00:00', '12:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    368, 300, 'TEST-0368',
    'Customer 368', '0969166287',
    '35 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.828053 21.036468)', 4326, 'axis-order=long-lat'),
    9.06, 14,
    '08:00:00', '11:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    369, 300, 'TEST-0369',
    'Customer 369', '0927738187',
    '806 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.851966 21.032452)', 4326, 'axis-order=long-lat'),
    28.89, 13,
    '09:00:00', '11:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    370, 300, 'TEST-0370',
    'Customer 370', '0974672520',
    '991 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829473 21.059160)', 4326, 'axis-order=long-lat'),
    20.24, 8,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    371, 300, 'TEST-0371',
    'Customer 371', '0963339667',
    '991 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.810548 21.063085)', 4326, 'axis-order=long-lat'),
    9.06, 17,
    '09:00:00', '11:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    372, 300, 'TEST-0372',
    'Customer 372', '0926587799',
    '466 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829718 21.015370)', 4326, 'axis-order=long-lat'),
    16.8, 17,
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
    373, 300, 'TEST-0373',
    'Customer 373', '0941383044',
    '483 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.843240 20.981013)', 4326, 'axis-order=long-lat'),
    30.26, 24,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    374, 300, 'TEST-0374',
    'Customer 374', '0950559278',
    '665 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.768795 21.019341)', 4326, 'axis-order=long-lat'),
    47.58, 14,
    '13:00:00', '17:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    375, 300, 'TEST-0375',
    'Customer 375', '0941514326',
    '909 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.793682 21.032266)', 4326, 'axis-order=long-lat'),
    21.7, 28,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    376, 300, 'TEST-0376',
    'Customer 376', '0997902129',
    '102 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.816382 21.061880)', 4326, 'axis-order=long-lat'),
    47.26, 13,
    '09:00:00', '12:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    377, 300, 'TEST-0377',
    'Customer 377', '0969686067',
    '974 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.791086 21.002122)', 4326, 'axis-order=long-lat'),
    49.14, 16,
    '09:00:00', '12:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    378, 300, 'TEST-0378',
    'Customer 378', '0963437398',
    '283 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.767033 20.997492)', 4326, 'axis-order=long-lat'),
    43.16, 19,
    '10:00:00', '12:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    379, 300, 'TEST-0379',
    'Customer 379', '0916788532',
    '806 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.853699 21.011862)', 4326, 'axis-order=long-lat'),
    40.41, 11,
    '14:00:00', '16:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    380, 300, 'TEST-0380',
    'Customer 380', '0988703005',
    '222 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.778117 21.059816)', 4326, 'axis-order=long-lat'),
    39.85, 30,
    '14:00:00', '16:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    381, 300, 'TEST-0381',
    'Customer 381', '0929419691',
    '134 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.834668 21.032533)', 4326, 'axis-order=long-lat'),
    9.95, 5,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    382, 300, 'TEST-0382',
    'Customer 382', '0941934639',
    '603 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.772242 21.010888)', 4326, 'axis-order=long-lat'),
    7.36, 28,
    NULL, NULL, 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    383, 300, 'TEST-0383',
    'Customer 383', '0970166480',
    '797 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.814184 21.014715)', 4326, 'axis-order=long-lat'),
    25.34, 12,
    '08:00:00', '11:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    384, 300, 'TEST-0384',
    'Customer 384', '0998436473',
    '534 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.819151 21.008674)', 4326, 'axis-order=long-lat'),
    6.4, 20,
    NULL, NULL, 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    385, 300, 'TEST-0385',
    'Customer 385', '0969533928',
    '76 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.787028 21.068438)', 4326, 'axis-order=long-lat'),
    11.68, 9,
    '08:00:00', '11:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    386, 300, 'TEST-0386',
    'Customer 386', '0953645651',
    '391 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.807873 21.077974)', 4326, 'axis-order=long-lat'),
    25.42, 24,
    '14:00:00', '18:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    387, 300, 'TEST-0387',
    'Customer 387', '0925356396',
    '874 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.842588 21.043982)', 4326, 'axis-order=long-lat'),
    29.81, 11,
    NULL, NULL, 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    388, 300, 'TEST-0388',
    'Customer 388', '0955490631',
    '848 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.796416 21.023861)', 4326, 'axis-order=long-lat'),
    9.28, 18,
    '09:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    389, 300, 'TEST-0389',
    'Customer 389', '0973655158',
    '69 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.763353 20.987633)', 4326, 'axis-order=long-lat'),
    24.43, 28,
    '10:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    390, 300, 'TEST-0390',
    'Customer 390', '0988714010',
    '980 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.787782 21.034670)', 4326, 'axis-order=long-lat'),
    10.5, 16,
    '14:00:00', '16:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    391, 300, 'TEST-0391',
    'Customer 391', '0990585678',
    '320 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.812609 21.013676)', 4326, 'axis-order=long-lat'),
    14.57, 26,
    '08:00:00', '11:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    392, 300, 'TEST-0392',
    'Customer 392', '0924524825',
    '359 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.791573 21.063048)', 4326, 'axis-order=long-lat'),
    39.32, 23,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    393, 300, 'TEST-0393',
    'Customer 393', '0992352411',
    '692 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.757442 21.042781)', 4326, 'axis-order=long-lat'),
    46.81, 27,
    '14:00:00', '18:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    394, 300, 'TEST-0394',
    'Customer 394', '0946677730',
    '720 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.846971 21.054752)', 4326, 'axis-order=long-lat'),
    20.8, 10,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    395, 300, 'TEST-0395',
    'Customer 395', '0929042093',
    '759 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.757883 21.041828)', 4326, 'axis-order=long-lat'),
    38.58, 11,
    '13:00:00', '15:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    396, 300, 'TEST-0396',
    'Customer 396', '0959667685',
    '320 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832501 21.050671)', 4326, 'axis-order=long-lat'),
    30.54, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    397, 300, 'TEST-0397',
    'Customer 397', '0992924837',
    '51 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.782027 21.045908)', 4326, 'axis-order=long-lat'),
    34.79, 20,
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
    398, 300, 'TEST-0398',
    'Customer 398', '0978765957',
    '117 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.765906 21.013028)', 4326, 'axis-order=long-lat'),
    35.52, 23,
    '10:00:00', '12:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    399, 300, 'TEST-0399',
    'Customer 399', '0939597483',
    '405 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.760295 21.076956)', 4326, 'axis-order=long-lat'),
    14.2, 11,
    '10:00:00', '12:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    410, 301, 'TEST-0410',
    'Customer 410', '0926104026',
    '8 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.797883 21.028250)', 4326, 'axis-order=long-lat'),
    10.81, 22,
    '10:00:00', '13:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    411, 301, 'TEST-0411',
    'Customer 411', '0963293990',
    '883 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.798439 21.052715)', 4326, 'axis-order=long-lat'),
    25.69, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    412, 301, 'TEST-0412',
    'Customer 412', '0995918360',
    '428 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.795320 21.007463)', 4326, 'axis-order=long-lat'),
    48.48, 10,
    '14:00:00', '17:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    413, 301, 'TEST-0413',
    'Customer 413', '0968614462',
    '865 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.798385 20.989097)', 4326, 'axis-order=long-lat'),
    23.02, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    414, 301, 'TEST-0414',
    'Customer 414', '0954703713',
    '798 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.805865 20.995324)', 4326, 'axis-order=long-lat'),
    10.13, 21,
    '10:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    415, 301, 'TEST-0415',
    'Customer 415', '0996629889',
    '835 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.765097 20.993280)', 4326, 'axis-order=long-lat'),
    16.52, 10,
    '10:00:00', '13:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    416, 301, 'TEST-0416',
    'Customer 416', '0997980930',
    '78 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832102 20.996226)', 4326, 'axis-order=long-lat'),
    27.24, 29,
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
    417, 301, 'TEST-0417',
    'Customer 417', '0985764804',
    '659 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.817269 21.042041)', 4326, 'axis-order=long-lat'),
    43.89, 25,
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
    418, 301, 'TEST-0418',
    'Customer 418', '0919165226',
    '481 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.785099 21.022732)', 4326, 'axis-order=long-lat'),
    17.36, 6,
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
    419, 301, 'TEST-0419',
    'Customer 419', '0970654852',
    '39 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.837987 20.984200)', 4326, 'axis-order=long-lat'),
    8.45, 7,
    '10:00:00', '13:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    420, 301, 'TEST-0420',
    'Customer 420', '0987890494',
    '568 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.828709 21.073934)', 4326, 'axis-order=long-lat'),
    6.85, 30,
    '13:00:00', '16:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    421, 301, 'TEST-0421',
    'Customer 421', '0973837847',
    '514 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.761004 20.993597)', 4326, 'axis-order=long-lat'),
    9.66, 15,
    '10:00:00', '14:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    422, 301, 'TEST-0422',
    'Customer 422', '0943251599',
    '725 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.798759 21.022287)', 4326, 'axis-order=long-lat'),
    28.52, 10,
    '09:00:00', '11:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    423, 301, 'TEST-0423',
    'Customer 423', '0964864099',
    '794 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.814606 21.012348)', 4326, 'axis-order=long-lat'),
    40.52, 25,
    '10:00:00', '13:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    424, 301, 'TEST-0424',
    'Customer 424', '0922684373',
    '572 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.783232 21.046349)', 4326, 'axis-order=long-lat'),
    37.57, 26,
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
    425, 301, 'TEST-0425',
    'Customer 425', '0988213973',
    '680 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.789799 20.992654)', 4326, 'axis-order=long-lat'),
    48.55, 27,
    '10:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    426, 301, 'TEST-0426',
    'Customer 426', '0921370793',
    '318 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.819166 21.034403)', 4326, 'axis-order=long-lat'),
    19.78, 9,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    427, 301, 'TEST-0427',
    'Customer 427', '0999987086',
    '434 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.756640 21.029360)', 4326, 'axis-order=long-lat'),
    18.9, 11,
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
    428, 301, 'TEST-0428',
    'Customer 428', '0940406092',
    '141 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.784396 20.994006)', 4326, 'axis-order=long-lat'),
    40.47, 21,
    '13:00:00', '15:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    429, 301, 'TEST-0429',
    'Customer 429', '0998842638',
    '345 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.816620 21.066150)', 4326, 'axis-order=long-lat'),
    31.88, 9,
    '14:00:00', '16:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    430, 301, 'TEST-0430',
    'Customer 430', '0993851247',
    '829 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.826935 21.068777)', 4326, 'axis-order=long-lat'),
    6.97, 16,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    431, 301, 'TEST-0431',
    'Customer 431', '0970254091',
    '240 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.785764 21.031912)', 4326, 'axis-order=long-lat'),
    41.39, 20,
    '14:00:00', '17:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    432, 301, 'TEST-0432',
    'Customer 432', '0971965637',
    '788 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.793004 21.006688)', 4326, 'axis-order=long-lat'),
    28.74, 10,
    '14:00:00', '17:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    433, 301, 'TEST-0433',
    'Customer 433', '0917003454',
    '657 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.791938 21.026570)', 4326, 'axis-order=long-lat'),
    47.04, 27,
    '09:00:00', '12:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    434, 301, 'TEST-0434',
    'Customer 434', '0931516757',
    '280 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.806152 21.023443)', 4326, 'axis-order=long-lat'),
    42.4, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    435, 301, 'TEST-0435',
    'Customer 435', '0965686520',
    '55 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.792213 21.018156)', 4326, 'axis-order=long-lat'),
    22.38, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    436, 301, 'TEST-0436',
    'Customer 436', '0952773159',
    '954 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.826287 20.988419)', 4326, 'axis-order=long-lat'),
    20.09, 9,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    437, 301, 'TEST-0437',
    'Customer 437', '0973411827',
    '713 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.830701 21.061622)', 4326, 'axis-order=long-lat'),
    26.11, 24,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    438, 301, 'TEST-0438',
    'Customer 438', '0938944689',
    '856 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848840 20.993463)', 4326, 'axis-order=long-lat'),
    32.4, 18,
    '08:00:00', '11:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    439, 301, 'TEST-0439',
    'Customer 439', '0926354208',
    '49 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.818712 21.002355)', 4326, 'axis-order=long-lat'),
    33.03, 7,
    '09:00:00', '12:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    440, 301, 'TEST-0440',
    'Customer 440', '0981947921',
    '17 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.812322 21.041706)', 4326, 'axis-order=long-lat'),
    37.33, 14,
    '13:00:00', '17:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    441, 301, 'TEST-0441',
    'Customer 441', '0957354167',
    '247 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.773548 21.035577)', 4326, 'axis-order=long-lat'),
    35.07, 21,
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
    442, 301, 'TEST-0442',
    'Customer 442', '0980606503',
    '558 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.853650 21.029244)', 4326, 'axis-order=long-lat'),
    29.94, 17,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    443, 301, 'TEST-0443',
    'Customer 443', '0961926331',
    '383 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.756441 21.003876)', 4326, 'axis-order=long-lat'),
    40.48, 16,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    444, 301, 'TEST-0444',
    'Customer 444', '0954629892',
    '137 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.809418 20.982945)', 4326, 'axis-order=long-lat'),
    41.61, 10,
    '08:00:00', '12:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    445, 301, 'TEST-0445',
    'Customer 445', '0934464938',
    '831 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.826422 20.991996)', 4326, 'axis-order=long-lat'),
    47.46, 6,
    '13:00:00', '17:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    446, 301, 'TEST-0446',
    'Customer 446', '0936772387',
    '908 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848154 20.982702)', 4326, 'axis-order=long-lat'),
    28.19, 22,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    447, 301, 'TEST-0447',
    'Customer 447', '0996825515',
    '196 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.841031 21.007122)', 4326, 'axis-order=long-lat'),
    7.15, 25,
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
    448, 301, 'TEST-0448',
    'Customer 448', '0959394697',
    '448 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829148 21.067460)', 4326, 'axis-order=long-lat'),
    45.26, 17,
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
    449, 301, 'TEST-0449',
    'Customer 449', '0976776459',
    '377 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.806745 21.070942)', 4326, 'axis-order=long-lat'),
    41.05, 28,
    '13:00:00', '17:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    450, 301, 'TEST-0450',
    'Customer 450', '0990859912',
    '843 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.784194 20.996557)', 4326, 'axis-order=long-lat'),
    9.62, 15,
    NULL, NULL, 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    451, 301, 'TEST-0451',
    'Customer 451', '0967192331',
    '171 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.789972 21.047487)', 4326, 'axis-order=long-lat'),
    6.91, 16,
    '14:00:00', '18:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    452, 301, 'TEST-0452',
    'Customer 452', '0917698469',
    '77 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.795438 21.045629)', 4326, 'axis-order=long-lat'),
    28.09, 28,
    '10:00:00', '14:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    453, 301, 'TEST-0453',
    'Customer 453', '0968810561',
    '36 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.778417 20.991134)', 4326, 'axis-order=long-lat'),
    34.03, 16,
    '09:00:00', '13:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    454, 301, 'TEST-0454',
    'Customer 454', '0930594121',
    '696 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.791913 21.023503)', 4326, 'axis-order=long-lat'),
    24.98, 7,
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
    455, 301, 'TEST-0455',
    'Customer 455', '0959251188',
    '408 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.782686 21.009950)', 4326, 'axis-order=long-lat'),
    48.22, 5,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    456, 301, 'TEST-0456',
    'Customer 456', '0925805613',
    '269 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.825221 21.056007)', 4326, 'axis-order=long-lat'),
    14.66, 24,
    '13:00:00', '17:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    457, 301, 'TEST-0457',
    'Customer 457', '0926460116',
    '139 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.800021 21.063794)', 4326, 'axis-order=long-lat'),
    45.44, 19,
    '13:00:00', '15:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    458, 301, 'TEST-0458',
    'Customer 458', '0956630633',
    '727 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.809034 20.985005)', 4326, 'axis-order=long-lat'),
    45.11, 10,
    '10:00:00', '14:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    459, 301, 'TEST-0459',
    'Customer 459', '0926288992',
    '206 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.778491 21.057718)', 4326, 'axis-order=long-lat'),
    27.23, 16,
    '14:00:00', '16:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    460, 301, 'TEST-0460',
    'Customer 460', '0927417599',
    '627 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.761392 21.066936)', 4326, 'axis-order=long-lat'),
    22.92, 27,
    '13:00:00', '17:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    461, 301, 'TEST-0461',
    'Customer 461', '0919898653',
    '129 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.819051 21.075749)', 4326, 'axis-order=long-lat'),
    25.25, 26,
    '13:00:00', '17:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    462, 301, 'TEST-0462',
    'Customer 462', '0988891300',
    '187 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.767720 21.055289)', 4326, 'axis-order=long-lat'),
    27.63, 6,
    '14:00:00', '18:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    463, 301, 'TEST-0463',
    'Customer 463', '0931738837',
    '331 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.777364 21.071985)', 4326, 'axis-order=long-lat'),
    47.64, 21,
    '10:00:00', '12:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    464, 301, 'TEST-0464',
    'Customer 464', '0995235919',
    '980 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.767334 21.033595)', 4326, 'axis-order=long-lat'),
    18.63, 22,
    '10:00:00', '12:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    465, 301, 'TEST-0465',
    'Customer 465', '0987933054',
    '158 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.817265 20.995631)', 4326, 'axis-order=long-lat'),
    45.59, 15,
    '09:00:00', '13:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    466, 301, 'TEST-0466',
    'Customer 466', '0913808225',
    '84 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.818962 20.983057)', 4326, 'axis-order=long-lat'),
    30.95, 25,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    467, 301, 'TEST-0467',
    'Customer 467', '0995763946',
    '32 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.817533 21.028315)', 4326, 'axis-order=long-lat'),
    18.04, 14,
    '13:00:00', '17:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    468, 301, 'TEST-0468',
    'Customer 468', '0964504816',
    '305 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.823678 21.023865)', 4326, 'axis-order=long-lat'),
    12.11, 18,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    469, 301, 'TEST-0469',
    'Customer 469', '0929282885',
    '321 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.786749 21.064660)', 4326, 'axis-order=long-lat'),
    49.46, 16,
    '10:00:00', '14:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    470, 301, 'TEST-0470',
    'Customer 470', '0959733063',
    '528 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.786729 21.034676)', 4326, 'axis-order=long-lat'),
    25.99, 13,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    471, 301, 'TEST-0471',
    'Customer 471', '0922993840',
    '52 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.793232 21.007532)', 4326, 'axis-order=long-lat'),
    32.69, 12,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    472, 301, 'TEST-0472',
    'Customer 472', '0951971886',
    '195 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.804644 21.054823)', 4326, 'axis-order=long-lat'),
    28.16, 20,
    '14:00:00', '18:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    473, 301, 'TEST-0473',
    'Customer 473', '0977839607',
    '469 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.776331 21.075180)', 4326, 'axis-order=long-lat'),
    20.88, 6,
    '08:00:00', '11:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    474, 301, 'TEST-0474',
    'Customer 474', '0982033413',
    '9 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.797910 21.063228)', 4326, 'axis-order=long-lat'),
    44.71, 28,
    '13:00:00', '16:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    475, 301, 'TEST-0475',
    'Customer 475', '0963765155',
    '53 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.774283 21.035539)', 4326, 'axis-order=long-lat'),
    29.9, 7,
    '10:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    476, 301, 'TEST-0476',
    'Customer 476', '0993650068',
    '697 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.767683 21.039615)', 4326, 'axis-order=long-lat'),
    9.35, 16,
    '14:00:00', '17:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    477, 301, 'TEST-0477',
    'Customer 477', '0936717096',
    '617 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.804825 21.029423)', 4326, 'axis-order=long-lat'),
    7.04, 9,
    '10:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    478, 301, 'TEST-0478',
    'Customer 478', '0929990477',
    '621 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.768795 21.067824)', 4326, 'axis-order=long-lat'),
    46.91, 15,
    '14:00:00', '17:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    479, 301, 'TEST-0479',
    'Customer 479', '0950162553',
    '608 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.837646 21.012148)', 4326, 'axis-order=long-lat'),
    28.96, 27,
    '14:00:00', '18:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    480, 301, 'TEST-0480',
    'Customer 480', '0912232974',
    '378 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.765777 21.011635)', 4326, 'axis-order=long-lat'),
    23.74, 14,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    481, 301, 'TEST-0481',
    'Customer 481', '0987641510',
    '592 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.759953 21.001265)', 4326, 'axis-order=long-lat'),
    26.62, 21,
    '10:00:00', '14:00:00', 'SCHEDULED', 10,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    482, 301, 'TEST-0482',
    'Customer 482', '0942507072',
    '33 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.824869 21.035768)', 4326, 'axis-order=long-lat'),
    13.59, 19,
    '13:00:00', '15:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    483, 301, 'TEST-0483',
    'Customer 483', '0937365878',
    '421 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.815965 21.028693)', 4326, 'axis-order=long-lat'),
    26.22, 28,
    '13:00:00', '17:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    484, 301, 'TEST-0484',
    'Customer 484', '0985280948',
    '333 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.802654 21.078323)', 4326, 'axis-order=long-lat'),
    21.96, 10,
    '14:00:00', '16:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    485, 301, 'TEST-0485',
    'Customer 485', '0957551594',
    '693 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.826909 21.055710)', 4326, 'axis-order=long-lat'),
    33.95, 27,
    '10:00:00', '14:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    486, 301, 'TEST-0486',
    'Customer 486', '0947447604',
    '572 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848966 21.008361)', 4326, 'axis-order=long-lat'),
    49.2, 29,
    '09:00:00', '11:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    487, 301, 'TEST-0487',
    'Customer 487', '0974398125',
    '358 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848266 21.034560)', 4326, 'axis-order=long-lat'),
    37.47, 14,
    '13:00:00', '16:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    488, 301, 'TEST-0488',
    'Customer 488', '0962953249',
    '839 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832123 21.013024)', 4326, 'axis-order=long-lat'),
    11.59, 6,
    '14:00:00', '17:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    489, 301, 'TEST-0489',
    'Customer 489', '0969374716',
    '672 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.802725 21.004155)', 4326, 'axis-order=long-lat'),
    14.09, 22,
    '08:00:00', '11:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    500, 302, 'TEST-0500',
    'Customer 500', '0924660143',
    '631 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.778745 21.052600)', 4326, 'axis-order=long-lat'),
    7.28, 21,
    '10:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    501, 302, 'TEST-0501',
    'Customer 501', '0965472370',
    '339 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.764871 21.050247)', 4326, 'axis-order=long-lat'),
    39.68, 5,
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
    502, 302, 'TEST-0502',
    'Customer 502', '0997629787',
    '958 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.802387 21.066576)', 4326, 'axis-order=long-lat'),
    34.27, 29,
    '09:00:00', '12:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    503, 302, 'TEST-0503',
    'Customer 503', '0997550053',
    '588 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.828686 21.001733)', 4326, 'axis-order=long-lat'),
    43.26, 6,
    '08:00:00', '10:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    504, 302, 'TEST-0504',
    'Customer 504', '0963283709',
    '807 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848478 21.028049)', 4326, 'axis-order=long-lat'),
    48.3, 14,
    '09:00:00', '11:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    505, 302, 'TEST-0505',
    'Customer 505', '0986230551',
    '618 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.853116 20.989241)', 4326, 'axis-order=long-lat'),
    17.81, 25,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    506, 302, 'TEST-0506',
    'Customer 506', '0972816684',
    '280 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.766082 20.997792)', 4326, 'axis-order=long-lat'),
    12.31, 19,
    '09:00:00', '13:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    507, 302, 'TEST-0507',
    'Customer 507', '0955246430',
    '810 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.822257 21.008013)', 4326, 'axis-order=long-lat'),
    13.69, 24,
    '08:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    508, 302, 'TEST-0508',
    'Customer 508', '0921641746',
    '411 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.773295 21.045437)', 4326, 'axis-order=long-lat'),
    11.33, 15,
    '14:00:00', '17:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    509, 302, 'TEST-0509',
    'Customer 509', '0961482483',
    '242 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.781516 21.023185)', 4326, 'axis-order=long-lat'),
    19.83, 23,
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
    510, 302, 'TEST-0510',
    'Customer 510', '0997735896',
    '369 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.761029 21.047788)', 4326, 'axis-order=long-lat'),
    10.32, 14,
    NULL, NULL, 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    511, 302, 'TEST-0511',
    'Customer 511', '0951738753',
    '707 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.846430 20.990253)', 4326, 'axis-order=long-lat'),
    18.28, 24,
    '14:00:00', '18:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    512, 302, 'TEST-0512',
    'Customer 512', '0927927298',
    '490 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829590 20.993830)', 4326, 'axis-order=long-lat'),
    32.26, 18,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    513, 302, 'TEST-0513',
    'Customer 513', '0939314711',
    '781 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.830291 21.003258)', 4326, 'axis-order=long-lat'),
    44.29, 7,
    '14:00:00', '18:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    514, 302, 'TEST-0514',
    'Customer 514', '0985721444',
    '115 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.809582 20.984687)', 4326, 'axis-order=long-lat'),
    27.75, 23,
    '10:00:00', '12:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    515, 302, 'TEST-0515',
    'Customer 515', '0954039203',
    '875 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.766441 21.030545)', 4326, 'axis-order=long-lat'),
    14.24, 23,
    NULL, NULL, 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    516, 302, 'TEST-0516',
    'Customer 516', '0978501420',
    '457 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.800160 21.059380)', 4326, 'axis-order=long-lat'),
    28.1, 19,
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
    517, 302, 'TEST-0517',
    'Customer 517', '0972059365',
    '689 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.827145 21.059203)', 4326, 'axis-order=long-lat'),
    22.82, 5,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    518, 302, 'TEST-0518',
    'Customer 518', '0956241881',
    '718 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.854229 20.984887)', 4326, 'axis-order=long-lat'),
    47.06, 7,
    '08:00:00', '11:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    519, 302, 'TEST-0519',
    'Customer 519', '0964877191',
    '185 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.831400 21.055408)', 4326, 'axis-order=long-lat'),
    49.02, 25,
    NULL, NULL, 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    520, 302, 'TEST-0520',
    'Customer 520', '0960666702',
    '385 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.851231 20.986536)', 4326, 'axis-order=long-lat'),
    44.35, 9,
    '13:00:00', '16:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    521, 302, 'TEST-0521',
    'Customer 521', '0982109613',
    '403 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.827603 21.031395)', 4326, 'axis-order=long-lat'),
    15.02, 5,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    522, 302, 'TEST-0522',
    'Customer 522', '0983074216',
    '435 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.837153 21.031704)', 4326, 'axis-order=long-lat'),
    16.14, 16,
    '13:00:00', '17:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    523, 302, 'TEST-0523',
    'Customer 523', '0925145751',
    '33 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.796741 21.059459)', 4326, 'axis-order=long-lat'),
    39.47, 5,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    524, 302, 'TEST-0524',
    'Customer 524', '0923542127',
    '609 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.814595 20.981869)', 4326, 'axis-order=long-lat'),
    36.65, 6,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    525, 302, 'TEST-0525',
    'Customer 525', '0941452485',
    '553 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.841190 21.000238)', 4326, 'axis-order=long-lat'),
    7.54, 21,
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
    526, 302, 'TEST-0526',
    'Customer 526', '0987324220',
    '327 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832071 21.036258)', 4326, 'axis-order=long-lat'),
    41.83, 12,
    NULL, NULL, 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    527, 302, 'TEST-0527',
    'Customer 527', '0965507111',
    '308 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.810489 21.006026)', 4326, 'axis-order=long-lat'),
    31.63, 28,
    '14:00:00', '16:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    528, 302, 'TEST-0528',
    'Customer 528', '0976521096',
    '49 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848976 21.075740)', 4326, 'axis-order=long-lat'),
    35.21, 30,
    '13:00:00', '17:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    529, 302, 'TEST-0529',
    'Customer 529', '0930027648',
    '308 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.830259 21.016157)', 4326, 'axis-order=long-lat'),
    26.31, 12,
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
    530, 302, 'TEST-0530',
    'Customer 530', '0917734153',
    '576 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.796468 21.019779)', 4326, 'axis-order=long-lat'),
    28.85, 17,
    '09:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    531, 302, 'TEST-0531',
    'Customer 531', '0954327148',
    '663 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.799796 20.986418)', 4326, 'axis-order=long-lat'),
    21.7, 22,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    532, 302, 'TEST-0532',
    'Customer 532', '0960622628',
    '690 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.758772 21.039008)', 4326, 'axis-order=long-lat'),
    8.28, 30,
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
    533, 302, 'TEST-0533',
    'Customer 533', '0974343201',
    '214 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.788113 21.065458)', 4326, 'axis-order=long-lat'),
    48.07, 5,
    '14:00:00', '16:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    534, 302, 'TEST-0534',
    'Customer 534', '0974279671',
    '937 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.815257 21.002770)', 4326, 'axis-order=long-lat'),
    14.2, 12,
    '08:00:00', '12:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    535, 302, 'TEST-0535',
    'Customer 535', '0972579077',
    '547 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.785659 21.043383)', 4326, 'axis-order=long-lat'),
    21.18, 20,
    '10:00:00', '13:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    536, 302, 'TEST-0536',
    'Customer 536', '0973013937',
    '780 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.846250 21.062484)', 4326, 'axis-order=long-lat'),
    21.68, 18,
    NULL, NULL, 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    537, 302, 'TEST-0537',
    'Customer 537', '0929585415',
    '17 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.848293 21.004588)', 4326, 'axis-order=long-lat'),
    31.06, 18,
    '09:00:00', '13:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    538, 302, 'TEST-0538',
    'Customer 538', '0954273769',
    '236 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.838153 21.016495)', 4326, 'axis-order=long-lat'),
    27.47, 25,
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
    539, 302, 'TEST-0539',
    'Customer 539', '0975581613',
    '990 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.828776 21.050867)', 4326, 'axis-order=long-lat'),
    25.74, 28,
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
    540, 302, 'TEST-0540',
    'Customer 540', '0983356206',
    '501 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.843753 20.996916)', 4326, 'axis-order=long-lat'),
    49.56, 6,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    541, 302, 'TEST-0541',
    'Customer 541', '0919976458',
    '968 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.759668 21.060311)', 4326, 'axis-order=long-lat'),
    5.29, 9,
    NULL, NULL, 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    542, 302, 'TEST-0542',
    'Customer 542', '0930270856',
    '10 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.800418 21.000383)', 4326, 'axis-order=long-lat'),
    7.72, 25,
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
    543, 302, 'TEST-0543',
    'Customer 543', '0910887495',
    '545 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.756002 21.033675)', 4326, 'axis-order=long-lat'),
    28.82, 13,
    '13:00:00', '15:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    544, 302, 'TEST-0544',
    'Customer 544', '0967790557',
    '826 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.772744 21.072482)', 4326, 'axis-order=long-lat'),
    46.31, 21,
    '08:00:00', '12:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    545, 302, 'TEST-0545',
    'Customer 545', '0993185576',
    '540 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.790261 21.003753)', 4326, 'axis-order=long-lat'),
    40.77, 7,
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
    546, 302, 'TEST-0546',
    'Customer 546', '0942642810',
    '714 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.823221 21.001097)', 4326, 'axis-order=long-lat'),
    43.15, 25,
    '13:00:00', '17:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    547, 302, 'TEST-0547',
    'Customer 547', '0960903955',
    '387 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.760428 21.033781)', 4326, 'axis-order=long-lat'),
    5.43, 10,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    548, 302, 'TEST-0548',
    'Customer 548', '0954462389',
    '580 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.852201 21.076244)', 4326, 'axis-order=long-lat'),
    45.27, 6,
    '13:00:00', '17:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    549, 302, 'TEST-0549',
    'Customer 549', '0986072480',
    '487 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.847233 21.005621)', 4326, 'axis-order=long-lat'),
    35.8, 13,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    550, 302, 'TEST-0550',
    'Customer 550', '0952209421',
    '17 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.813521 21.074658)', 4326, 'axis-order=long-lat'),
    38.85, 27,
    '08:00:00', '10:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    551, 302, 'TEST-0551',
    'Customer 551', '0985693188',
    '247 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.839761 21.035181)', 4326, 'axis-order=long-lat'),
    35.47, 22,
    '10:00:00', '12:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    552, 302, 'TEST-0552',
    'Customer 552', '0920526386',
    '513 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.789403 21.053057)', 4326, 'axis-order=long-lat'),
    9.4, 12,
    '09:00:00', '13:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    553, 302, 'TEST-0553',
    'Customer 553', '0963244581',
    '993 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.757780 21.055866)', 4326, 'axis-order=long-lat'),
    17.3, 19,
    '14:00:00', '18:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    554, 302, 'TEST-0554',
    'Customer 554', '0984227466',
    '964 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.773391 21.016054)', 4326, 'axis-order=long-lat'),
    31.36, 17,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    555, 302, 'TEST-0555',
    'Customer 555', '0919865900',
    '85 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.792817 21.005308)', 4326, 'axis-order=long-lat'),
    40.28, 9,
    '10:00:00', '12:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    556, 302, 'TEST-0556',
    'Customer 556', '0910697290',
    '966 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.790809 21.009338)', 4326, 'axis-order=long-lat'),
    17.14, 9,
    '08:00:00', '10:00:00', 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    557, 302, 'TEST-0557',
    'Customer 557', '0970242778',
    '571 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.795621 21.033985)', 4326, 'axis-order=long-lat'),
    6.18, 16,
    NULL, NULL, 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    558, 302, 'TEST-0558',
    'Customer 558', '0990268079',
    '804 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.793341 21.010954)', 4326, 'axis-order=long-lat'),
    18.13, 17,
    NULL, NULL, 'SCHEDULED', 2,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    559, 302, 'TEST-0559',
    'Customer 559', '0986767229',
    '534 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.792890 20.995469)', 4326, 'axis-order=long-lat'),
    11.25, 14,
    '14:00:00', '16:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    560, 302, 'TEST-0560',
    'Customer 560', '0968341261',
    '283 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.803240 21.020621)', 4326, 'axis-order=long-lat'),
    8.46, 13,
    '08:00:00', '10:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    561, 302, 'TEST-0561',
    'Customer 561', '0992767884',
    '201 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.768366 21.024297)', 4326, 'axis-order=long-lat'),
    5.3, 17,
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
    562, 302, 'TEST-0562',
    'Customer 562', '0969085720',
    '343 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.837171 21.021573)', 4326, 'axis-order=long-lat'),
    34.3, 24,
    '13:00:00', '16:00:00', 'SCHEDULED', 3,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    563, 302, 'TEST-0563',
    'Customer 563', '0936957830',
    '491 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.851274 21.009929)', 4326, 'axis-order=long-lat'),
    48.59, 14,
    '14:00:00', '18:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    564, 302, 'TEST-0564',
    'Customer 564', '0960473288',
    '288 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.794111 21.060160)', 4326, 'axis-order=long-lat'),
    10.12, 23,
    '09:00:00', '12:00:00', 'SCHEDULED', 4,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    565, 302, 'TEST-0565',
    'Customer 565', '0983833059',
    '28 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.800979 21.073811)', 4326, 'axis-order=long-lat'),
    36.88, 19,
    '14:00:00', '16:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    566, 302, 'TEST-0566',
    'Customer 566', '0977015343',
    '143 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.779019 21.041845)', 4326, 'axis-order=long-lat'),
    34.64, 27,
    '08:00:00', '11:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    567, 302, 'TEST-0567',
    'Customer 567', '0990253323',
    '491 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.808254 21.036735)', 4326, 'axis-order=long-lat'),
    46.56, 27,
    '08:00:00', '11:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    568, 302, 'TEST-0568',
    'Customer 568', '0982062301',
    '609 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.763315 21.042329)', 4326, 'axis-order=long-lat'),
    39.6, 26,
    '10:00:00', '14:00:00', 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    569, 302, 'TEST-0569',
    'Customer 569', '0992232236',
    '46 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.819320 21.034833)', 4326, 'axis-order=long-lat'),
    34.1, 29,
    NULL, NULL, 'SCHEDULED', 6,
    'Test order', '2025-12-19', NOW(), NOW()
);


-- End of test data
