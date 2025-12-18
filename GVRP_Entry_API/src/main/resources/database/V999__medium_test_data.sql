-- Test data for VRP-002: MEDIUM dataset
-- Generated at 2025-12-18T00:43:16.709090
-- Database: MySQL 8.0+ with Spatial support

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (200, 'Branch Medium 1', NOW(), NOW());

INSERT IGNORE INTO branches (id, name, created_at, updated_at)
VALUES (201, 'Branch Medium 2', NOW(), NOW());

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    200, 
    200, 
    'Depot Van Dien', 
    'Thanh Tri, Hanoi',
    ST_GeomFromText('POINT(105.787245 21.000473)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    201, 
    200, 
    'Depot Dong Da', 
    '291 Luong Dinh Cua',
    ST_GeomFromText('POINT(105.760411 21.055342)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO depots (id, branch_id, name, address, location, created_at, updated_at)
VALUES (
    202, 
    201, 
    'Depot Ha Dong', 
    'Ngo 200, Ha Dong',
    ST_GeomFromText('POINT(105.835322 21.060691)', 4326, 'axis-order=long-lat'),
    NOW(), 
    NOW()
);

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (200, 200, 'Fleet Medium 1', NOW(), NOW());

INSERT IGNORE INTO fleets (id, branch_id, fleet_name, created_at, updated_at)
VALUES (201, 201, 'Fleet Medium 2', NOW(), NOW());

INSERT IGNORE INTO vehicle_types (
    id, branch_id, type_name, capacity, fixed_cost,
    cost_per_km, cost_per_hour, max_distance, max_duration,
    vehicle_features, created_at, updated_at
)
VALUES (
    200, 200, 'Truck 5T Medium', 150,
    50000, 5000, 4000,
    300, 480,
    '{"skills": [], "electric": false, "emission_factor": 12.3}', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    200, 200, 200,
    200, 200,
    '29A-MED1-00', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    201, 200, 200,
    200, 200,
    '29A-MED1-01', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    202, 200, 200,
    200, 200,
    '29A-MED1-02', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    203, 200, 200,
    200, 200,
    '29A-MED1-03', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    204, 200, 200,
    200, 200,
    '29A-MED1-04', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    210, 201, 200,
    202, 202,
    '29A-MED2-00', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    211, 201, 200,
    202, 202,
    '29A-MED2-01', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO vehicles (
    id, fleet_id, vehicle_type_id, start_depot_id, end_depot_id,
    vehicle_license_plate, status, created_at, updated_at
)
VALUES (
    212, 201, 200,
    202, 202,
    '29A-MED2-02', 'AVAILABLE', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    200, 200, 'TEST-0200',
    'Customer 200', '0986125617',
    '898 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.776080 21.050302)', 4326, 'axis-order=long-lat'),
    27.47, 25,
    '08:00:00', '10:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    201, 200, 'TEST-0201',
    'Customer 201', '0928740864',
    '253 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.808715 21.053010)', 4326, 'axis-order=long-lat'),
    38.62, 18,
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
    202, 200, 'TEST-0202',
    'Customer 202', '0978387461',
    '506 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.759529 20.987602)', 4326, 'axis-order=long-lat'),
    9.93, 25,
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
    203, 200, 'TEST-0203',
    'Customer 203', '0918526544',
    '395 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.854429 21.016673)', 4326, 'axis-order=long-lat'),
    28.81, 22,
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
    204, 200, 'TEST-0204',
    'Customer 204', '0982070937',
    '769 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.818913 21.005194)', 4326, 'axis-order=long-lat'),
    10.02, 18,
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
    205, 200, 'TEST-0205',
    'Customer 205', '0933978249',
    '520 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.841869 21.069774)', 4326, 'axis-order=long-lat'),
    18.43, 25,
    '10:00:00', '14:00:00', 'SCHEDULED', 9,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    206, 200, 'TEST-0206',
    'Customer 206', '0931682744',
    '553 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.847060 21.073861)', 4326, 'axis-order=long-lat'),
    46.33, 24,
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
    207, 200, 'TEST-0207',
    'Customer 207', '0951273847',
    '246 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.842618 20.984304)', 4326, 'axis-order=long-lat'),
    47.61, 7,
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
    208, 200, 'TEST-0208',
    'Customer 208', '0927232410',
    '676 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.809797 21.026039)', 4326, 'axis-order=long-lat'),
    16.93, 24,
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
    209, 200, 'TEST-0209',
    'Customer 209', '0936998038',
    '731 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.854332 21.009683)', 4326, 'axis-order=long-lat'),
    34.24, 19,
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
    210, 200, 'TEST-0210',
    'Customer 210', '0918593410',
    '347 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.810209 20.980614)', 4326, 'axis-order=long-lat'),
    31.48, 5,
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
    211, 200, 'TEST-0211',
    'Customer 211', '0919046318',
    '928 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.787860 20.981652)', 4326, 'axis-order=long-lat'),
    28.14, 13,
    '08:00:00', '10:00:00', 'SCHEDULED', 8,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    212, 200, 'TEST-0212',
    'Customer 212', '0927758595',
    '741 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.811921 21.072062)', 4326, 'axis-order=long-lat'),
    26.27, 30,
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
    213, 200, 'TEST-0213',
    'Customer 213', '0923009833',
    '675 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.797175 21.021616)', 4326, 'axis-order=long-lat'),
    26.02, 28,
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
    214, 200, 'TEST-0214',
    'Customer 214', '0964038913',
    '746 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.840984 21.012441)', 4326, 'axis-order=long-lat'),
    16.19, 11,
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
    215, 200, 'TEST-0215',
    'Customer 215', '0947385696',
    '474 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.847144 21.003492)', 4326, 'axis-order=long-lat'),
    24.94, 22,
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
    216, 200, 'TEST-0216',
    'Customer 216', '0982556484',
    '857 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.764143 20.979987)', 4326, 'axis-order=long-lat'),
    38.91, 12,
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
    217, 200, 'TEST-0217',
    'Customer 217', '0963826716',
    '925 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.792714 20.984375)', 4326, 'axis-order=long-lat'),
    49.34, 13,
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
    218, 200, 'TEST-0218',
    'Customer 218', '0984593961',
    '678 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.770297 21.050352)', 4326, 'axis-order=long-lat'),
    18.35, 6,
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
    219, 200, 'TEST-0219',
    'Customer 219', '0952091325',
    '59 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.802496 20.983525)', 4326, 'axis-order=long-lat'),
    46.37, 21,
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
    220, 200, 'TEST-0220',
    'Customer 220', '0978159587',
    '83 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.761669 21.063645)', 4326, 'axis-order=long-lat'),
    8.06, 12,
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
    221, 200, 'TEST-0221',
    'Customer 221', '0986460539',
    '253 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.758791 21.036403)', 4326, 'axis-order=long-lat'),
    8.69, 26,
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
    222, 200, 'TEST-0222',
    'Customer 222', '0937415205',
    '686 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.778686 21.050130)', 4326, 'axis-order=long-lat'),
    22.81, 26,
    '10:00:00', '13:00:00', 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    223, 200, 'TEST-0223',
    'Customer 223', '0971510041',
    '637 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.854427 21.078356)', 4326, 'axis-order=long-lat'),
    8.3, 11,
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
    224, 200, 'TEST-0224',
    'Customer 224', '0956843172',
    '903 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.779245 20.985390)', 4326, 'axis-order=long-lat'),
    17.82, 19,
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
    230, 201, 'TEST-0230',
    'Customer 230', '0980993218',
    '9 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.810277 21.045297)', 4326, 'axis-order=long-lat'),
    46.93, 8,
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
    231, 201, 'TEST-0231',
    'Customer 231', '0924366125',
    '761 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.782052 21.033833)', 4326, 'axis-order=long-lat'),
    32.22, 27,
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
    232, 201, 'TEST-0232',
    'Customer 232', '0995125977',
    '874 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.803670 21.004909)', 4326, 'axis-order=long-lat'),
    45.74, 6,
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
    233, 201, 'TEST-0233',
    'Customer 233', '0910475894',
    '342 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.818528 21.055623)', 4326, 'axis-order=long-lat'),
    16.79, 28,
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
    234, 201, 'TEST-0234',
    'Customer 234', '0911297845',
    '115 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.843128 20.986035)', 4326, 'axis-order=long-lat'),
    45.68, 22,
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
    235, 201, 'TEST-0235',
    'Customer 235', '0929876811',
    '441 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.785643 20.991256)', 4326, 'axis-order=long-lat'),
    45.45, 30,
    '14:00:00', '18:00:00', 'SCHEDULED', 1,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    236, 201, 'TEST-0236',
    'Customer 236', '0943491314',
    '683 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.832829 20.988790)', 4326, 'axis-order=long-lat'),
    44.79, 18,
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
    237, 201, 'TEST-0237',
    'Customer 237', '0933763566',
    '903 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.772753 21.019740)', 4326, 'axis-order=long-lat'),
    46.6, 30,
    '09:00:00', '11:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    238, 201, 'TEST-0238',
    'Customer 238', '0931367172',
    '807 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.793071 21.048639)', 4326, 'axis-order=long-lat'),
    6.74, 20,
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
    239, 201, 'TEST-0239',
    'Customer 239', '0971780854',
    '359 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.834352 21.009030)', 4326, 'axis-order=long-lat'),
    15.24, 5,
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
    240, 201, 'TEST-0240',
    'Customer 240', '0947463522',
    '360 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.794785 21.042659)', 4326, 'axis-order=long-lat'),
    49.15, 22,
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
    241, 201, 'TEST-0241',
    'Customer 241', '0933966966',
    '595 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.781364 21.074764)', 4326, 'axis-order=long-lat'),
    9.88, 18,
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
    242, 201, 'TEST-0242',
    'Customer 242', '0991363974',
    '524 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.844767 20.990076)', 4326, 'axis-order=long-lat'),
    13.55, 6,
    '10:00:00', '13:00:00', 'SCHEDULED', 7,
    'Test order', '2025-12-19', NOW(), NOW()
);

INSERT IGNORE INTO orders (
    id, branch_id, order_code, customer_name, customer_phone,
    address, location, demand, service_time,
    time_window_start, time_window_end, status, priority,
    delivery_notes, delivery_date, created_at, updated_at
)
VALUES (
    243, 201, 'TEST-0243',
    'Customer 243', '0982269803',
    '704 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.829012 21.050454)', 4326, 'axis-order=long-lat'),
    35.18, 16,
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
    244, 201, 'TEST-0244',
    'Customer 244', '0999152472',
    '943 Test Street, Hanoi',
    ST_GeomFromText('POINT(105.786209 21.011531)', 4326, 'axis-order=long-lat'),
    43.16, 28,
    NULL, NULL, 'SCHEDULED', 5,
    'Test order', '2025-12-19', NOW(), NOW()
);


-- End of test data
