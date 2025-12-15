-- Enable spatial support
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE gvrp_db;

-- Start transaction
START TRANSACTION;

-- =====================================================
-- STEP 1: Drop old route_segments table and related objects
-- =====================================================

-- Drop foreign keys

alter table route_segments
    drop foreign key route_segments_ibfk_1;

alter table route_segments
    drop foreign key route_segments_ibfk_2;

-- Drop indexes

drop index idx_from_location on route_segments;

drop index idx_order_id on route_segments;

drop index idx_route_sequence on route_segments;

drop index idx_segment_order on route_segments;

drop index idx_segment_route on route_segments;

drop index idx_segment_sequence on route_segments;

drop index idx_to_location on route_segments;

-- Drop the table
DROP TABLE IF EXISTS route_segments;

-- =====================================================
-- STEP 2: Create new route_stops table
-- =====================================================

CREATE TABLE route_stops (
    -- Primary key
     id BIGINT NOT NULL AUTO_INCREMENT,

    -- Foreign keys
     route_id BIGINT NOT NULL,
     order_id BIGINT NULL,

    -- Sequence
     sequence_number INT NOT NULL,

    -- Location info
     type VARCHAR(10) NOT NULL,
     location_id VARCHAR(50) NULL,
     location_name VARCHAR(255) NULL,
     address VARCHAR(500) NULL,
     location POINT SRID 4326 NULL,

    -- Timing
     arrival_time TIME NULL,
     departure_time TIME NULL,
     service_time DECIMAL(10, 2) NULL COMMENT 'Service time in minutes',
     wait_time DECIMAL(10, 2) NULL COMMENT 'Wait time in minutes',

    -- Load info
     demand DECIMAL(10, 2) NULL COMMENT 'Demand delivered at this stop (kg)',
     load_after DECIMAL(10, 2) NULL COMMENT 'Remaining load after this stop (kg)',

    -- Distance/time to next stop (optional)
     distance_to_next DECIMAL(10, 2) NULL COMMENT 'Distance to next stop (km)',
     time_to_next DECIMAL(10, 2) NULL COMMENT 'Time to next stop (minutes)',

    -- Audit fields (optional - add if you have these in other tables)
    -- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

     PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Route stops - each record represents a stop in a route';

-- =====================================================
-- STEP 3: Add constraints
-- =====================================================

-- Foreign key to routes table
ALTER TABLE route_stops
    ADD CONSTRAINT FK_route_stops_route
        FOREIGN KEY (route_id) REFERENCES routes(id)
            ON DELETE CASCADE;


ALTER TABLE route_stops
    ADD CONSTRAINT FK_route_stops_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE RESTRICT;

-- Check constraint for type
ALTER TABLE route_stops
    ADD CONSTRAINT CHK_stop_type
        CHECK (type IN ('DEPOT', 'ORDER'));

-- =====================================================
-- STEP 4: Create indexes
-- =====================================================

-- Index for querying stops by route
CREATE INDEX idx_stop_route
    ON route_stops(route_id);

-- Index for querying stops by order
CREATE INDEX idx_stop_order
    ON route_stops(order_id);

-- Composite index for route + sequence (most common query)
CREATE INDEX idx_stop_sequence
    ON route_stops(route_id, sequence_number);

-- Index for location type queries
CREATE INDEX idx_stop_type
    ON route_stops(type);

-- Spatial index for location field (if you use it)
-- CREATE SPATIAL INDEX idx_stop_location
-- ON route_stops(location);

-- =====================================================
-- STEP 5: Add comments to columns (optional but recommended)
-- =====================================================

ALTER TABLE route_stops
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    MODIFY COLUMN route_id BIGINT NOT NULL COMMENT 'Foreign key to routes table',
    MODIFY COLUMN order_id BIGINT NULL COMMENT 'Foreign key to orders table (NULL for depot stops)',
    MODIFY COLUMN sequence_number INT NOT NULL COMMENT 'Stop sequence in route (0-based)',
    MODIFY COLUMN type VARCHAR(10) NOT NULL COMMENT 'DEPOT or ORDER',
    MODIFY COLUMN location_id VARCHAR(50) NULL COMMENT 'depot-{id} or order-{id}',
    MODIFY COLUMN location_name VARCHAR(255) NULL COMMENT 'Display name of location',
    MODIFY COLUMN address VARCHAR(500) NULL COMMENT 'Full address',
    MODIFY COLUMN location POINT SRID 4326 NULL COMMENT 'GPS coordinates (longitude, latitude)';

-- =====================================================
-- STEP 6: Verify table structure
-- =====================================================

-- Show table structure
DESCRIBE route_stops;

-- Show indexes
SHOW INDEX FROM route_stops;

-- Show constraints
SELECT
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE,
    TABLE_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_NAME = 'route_stops'
  AND TABLE_SCHEMA = DATABASE();

-- Commit transaction
COMMIT;

CREATE OR REPLACE VIEW v_route_stops_detail AS
SELECT
    rs.id AS stop_id,
    rs.route_id,
    r.vehicle_id,
    v.vehicle_license_plate,
    rs.sequence_number,
    rs.type,
    rs.location_name,
    rs.address,
    rs.arrival_time,
    rs.departure_time,
    rs.service_time,
    rs.wait_time,
    rs.demand,
    rs.load_after,
    rs.distance_to_next,
    rs.time_to_next,
    -- Order details (if applicable)
    o.id AS order_id,
    o.order_code,
    o.customer_name,
    o.customer_phone,
    -- Route details
    r.distance AS route_distance,
    r.service_time AS route_duration,
    r.co2_emission AS route_co2
FROM route_stops rs
         LEFT JOIN routes r ON rs.route_id = r.id
         LEFT JOIN vehicles v ON r.vehicle_id = v.id
         LEFT JOIN orders o ON rs.order_id = o.id
ORDER BY rs.route_id, rs.sequence_number;


alter table solutions
    drop foreign key FKqumi4xrqfsitk6hk3u54oi157;

alter table solutions
    drop index FKqumi4xrqfsitk6hk3u54oi157;

alter table solutions
    drop column created_by_user_id;
