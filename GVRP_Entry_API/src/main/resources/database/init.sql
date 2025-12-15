-- ============================================
-- VRP SYSTEM - DATABASE MIGRATION SCRIPTS
-- Version: 1.0.0
-- Description: Complete database schema for VRP system with job management
-- Database: MySQL 8.0+
-- ============================================

-- Enable spatial support
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE gvrp_db;

-- ============================================
-- 1. BRANCHES TABLE (Multi-tenant root)
-- ============================================
CREATE TABLE IF NOT EXISTS branches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Branch entities for multi-tenant support';

-- ============================================
-- 2. USERS TABLE (Authentication & Authorization)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     branch_id BIGINT NOT NULL,
     username VARCHAR(50) NOT NULL UNIQUE,
     password VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
     email VARCHAR(100) NOT NULL UNIQUE,
     full_name VARCHAR(100) NOT NULL,
     role ENUM('PLANNER', 'CUSTOMER') NOT NULL DEFAULT 'CUSTOMER',
     enabled BOOLEAN NOT NULL DEFAULT TRUE,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

     FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

     INDEX idx_branch_id (branch_id),
     INDEX idx_username (username),
     INDEX idx_email (email),
     INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='User accounts with role-based access control';

-- ============================================
-- 3. DEPOTS TABLE (Warehouse locations)
-- ============================================
CREATE TABLE IF NOT EXISTS depots (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      branch_id BIGINT NOT NULL,
      name VARCHAR(100) NOT NULL,
      address VARCHAR(255) NOT NULL,
      location POINT NOT NULL SRID 4326 COMMENT 'GPS coordinates (longitude, latitude)',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

      FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

      INDEX idx_branch_id (branch_id),
      SPATIAL INDEX idx_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Depot/warehouse locations with spatial data';

-- ============================================
-- 4. FLEETS TABLE (Vehicle groups)
-- ============================================
CREATE TABLE IF NOT EXISTS fleets (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      branch_id BIGINT NOT NULL,
      fleet_name VARCHAR(100) NOT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

      FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

      UNIQUE KEY uk_branch_fleet (branch_id, fleet_name),
      INDEX idx_branch_id (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Fleet groups for organizing vehicles';

-- ============================================
-- 5. VEHICLES TABLE (Delivery vehicles)
-- ============================================
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fleet_id BIGINT NOT NULL,
    start_depot_id BIGINT NOT NULL,
    end_depot_id BIGINT NOT NULL,
    vehicle_license_plate VARCHAR(20) NOT NULL UNIQUE,
    vehicle_feature VARCHAR(255) COMMENT 'Additional vehicle features/description',
    capacity INT NOT NULL COMMENT 'Capacity in kg or units',
    fixed_cost DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Fixed cost per trip in VND',
    cost_per_km DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Variable cost per km in VND',
    cost_per_hour DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Variable cost per hour in VND',
    max_distance DECIMAL(10,2) COMMENT 'Maximum distance in km',
    max_duration DECIMAL(10,2) COMMENT 'Maximum duration in hours',
    status ENUM('AVAILABLE', 'IN_USE', 'MAINTENANCE', 'RETIRED') NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (fleet_id) REFERENCES fleets(id) ON DELETE CASCADE,
    FOREIGN KEY (start_depot_id) REFERENCES depots(id) ON DELETE RESTRICT,
    FOREIGN KEY (end_depot_id) REFERENCES depots(id) ON DELETE RESTRICT,

    INDEX idx_fleet_id (fleet_id),
    INDEX idx_status (status),
    INDEX idx_license_plate (vehicle_license_plate),
    INDEX idx_start_depot (start_depot_id),
    INDEX idx_end_depot (end_depot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Vehicle fleet with operational constraints';

-- ============================================
-- 6. ORDERS TABLE (Delivery orders)
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      branch_id BIGINT NOT NULL,
      order_code VARCHAR(50) NOT NULL,
      customer_name VARCHAR(100) NOT NULL,
      customer_phone VARCHAR(20),
      address VARCHAR(255) NOT NULL,
      location POINT NOT NULL SRID 4326 COMMENT 'GPS coordinates (longitude, latitude)',
      demand DECIMAL(10,2) NOT NULL COMMENT 'Demand in kg or units',
      time_window_start TIME COMMENT 'Earliest delivery time',
      time_window_end TIME COMMENT 'Latest delivery time',
      status ENUM('SCHEDULED', 'ON_ROUTE', 'SERVICING', 'COMPLETED', 'FAILED', 'REJECTED') NOT NULL DEFAULT 'SCHEDULED',
      priority INT DEFAULT 1 COMMENT '1=highest priority',
      delivery_notes TEXT COMMENT 'Additional delivery instructions',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

      FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

      UNIQUE KEY uk_branch_order_code (branch_id, order_code),
      INDEX idx_branch_id (branch_id),
      INDEX idx_status (status),
      INDEX idx_branch_status (branch_id, status),
      INDEX idx_order_code (order_code),
      INDEX idx_priority (priority),
      SPATIAL INDEX idx_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Delivery orders with time windows and spatial data';

-- ============================================
-- 7. OPTIMIZATION_JOBS TABLE (Job management)
-- ============================================
CREATE TABLE IF NOT EXISTS optimization_jobs (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     branch_id BIGINT NOT NULL,
     user_id BIGINT,
     status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     started_at TIMESTAMP NULL DEFAULT NULL,
     completed_at TIMESTAMP NULL DEFAULT NULL,
     cancelled_at TIMESTAMP NULL DEFAULT NULL,
     external_job_id VARCHAR(100) COMMENT 'Job ID from external optimization engine',
     input_data JSON NOT NULL COMMENT 'Serialized RoutePlanningRequest for retry capability',
     error_message TEXT COMMENT 'Error details if status=FAILED',

     FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,

     INDEX idx_branch_status_created (branch_id, status, created_at DESC),
     INDEX idx_user_created (user_id, created_at DESC),
     INDEX idx_status_created (status, created_at),
     INDEX idx_external_job_id (external_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Optimization job tracking and lifecycle management';

-- ============================================
-- 8. SOLUTIONS TABLE (Optimization results)
-- ============================================
CREATE TABLE IF NOT EXISTS solutions (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     job_id BIGINT NOT NULL UNIQUE COMMENT 'One-to-one relationship with job',
     branch_id BIGINT NOT NULL,
     status ENUM('FEASIBLE', 'INFEASIBLE', 'OPTIMIZED') NOT NULL DEFAULT 'FEASIBLE',
     type ENUM('ENGINE_GENERATED', 'FILE_IMPORTED') NOT NULL DEFAULT 'ENGINE_GENERATED',
     total_distance DECIMAL(10,2) COMMENT 'Total distance in km',
     total_co2 DECIMAL(10,2) COMMENT 'Total CO2 emission in kg',
     total_service_time DECIMAL(10,2) COMMENT 'Total service time in hours',
     total_vehicles_used INT COMMENT 'Number of vehicles used',
     served_orders INT COMMENT 'Number of orders served',
     unserved_orders INT COMMENT 'Number of unserved orders',
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

     FOREIGN KEY (job_id) REFERENCES optimization_jobs(id) ON DELETE CASCADE,
     FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

     INDEX idx_job_id (job_id),
     INDEX idx_branch_id (branch_id),
     INDEX idx_branch_type (branch_id, type),
     INDEX idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Optimization solution results linked to jobs';

-- ============================================
-- 9. ROUTES TABLE (Individual vehicle routes)
-- ============================================
CREATE TABLE IF NOT EXISTS routes (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      solution_id BIGINT NOT NULL,
      vehicle_id BIGINT NOT NULL,
      route_order INT NOT NULL COMMENT 'Sequence number within solution',
      distance DECIMAL(10,2) COMMENT 'Route distance in km',
      co2_emission DECIMAL(10,2) COMMENT 'Route CO2 emission in kg',
      service_time DECIMAL(10,2) COMMENT 'Total service time in hours',
      order_count INT COMMENT 'Number of orders in this route',
      load_utilization DECIMAL(5,2) COMMENT 'Vehicle load utilization percentage',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

      FOREIGN KEY (solution_id) REFERENCES solutions(id) ON DELETE CASCADE,
      FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE RESTRICT,

      INDEX idx_solution_id (solution_id),
      INDEX idx_vehicle_id (vehicle_id),
      INDEX idx_solution_order (solution_id, route_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Individual routes assigned to vehicles';

-- ============================================
-- 10. ROUTE_SEGMENTS TABLE (Step-by-step details)
-- ============================================
CREATE TABLE IF NOT EXISTS route_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    order_id BIGINT COMMENT 'Order being delivered (null for depot segments)',
    sequence_number INT NOT NULL COMMENT 'Step sequence within route',
    from_type ENUM('DEPOT', 'ORDER') NOT NULL,
    from_location_id BIGINT NOT NULL COMMENT 'ID of depot or order',
    from_address VARCHAR(255) NOT NULL,
    from_location POINT NOT NULL SRID 4326,
    to_type ENUM('DEPOT', 'ORDER') NOT NULL,
    to_location_id BIGINT NOT NULL COMMENT 'ID of depot or order',
    to_address VARCHAR(255) NOT NULL,
    to_location POINT NOT NULL SRID 4326,
    distance DECIMAL(10,2) COMMENT 'Segment distance in km',
    duration DECIMAL(10,2) COMMENT 'Travel duration in hours',
    arrival_time TIME COMMENT 'Arrival time at destination',
    departure_time TIME COMMENT 'Departure time from origin',
    service_time DECIMAL(10,2) COMMENT 'Service time at location in hours',
    load_before DECIMAL(10,2) COMMENT 'Vehicle load before delivery',
    load_after DECIMAL(10,2) COMMENT 'Vehicle load after delivery',

    FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,

    INDEX idx_route_sequence (route_id, sequence_number),
    INDEX idx_order_id (order_id),
    SPATIAL INDEX idx_from_location (from_location),
    SPATIAL INDEX idx_to_location (to_location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Detailed step-by-step route segments with spatial data';

-- ============================================
-- CONSTRAINTS & CHECKS
-- ============================================

-- Add check constraints (MySQL 8.0.16+)
ALTER TABLE vehicles
    ADD CONSTRAINT chk_capacity CHECK (capacity > 0),
    ADD CONSTRAINT chk_costs CHECK (
        fixed_cost >= 0 AND
        cost_per_km >= 0 AND
        cost_per_hour >= 0
        );

ALTER TABLE orders
    ADD CONSTRAINT chk_demand CHECK (demand > 0),
    ADD CONSTRAINT chk_priority CHECK (priority >= 0);

ALTER TABLE routes
    ADD CONSTRAINT chk_load_utilization CHECK (
        load_utilization IS NULL OR
        (load_utilization >= 0 AND load_utilization <= 100)
        );

-- ============================================
-- INITIAL DATA
-- ============================================

-- Insert default branch for development/testing
INSERT INTO branches (name) VALUES ('Default Branch')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================
-- VIEWS FOR CONVENIENCE
-- ============================================

-- View: Active jobs with user info
CREATE OR REPLACE VIEW v_active_jobs AS
SELECT
    j.id,
    j.branch_id,
    j.user_id,
    u.username,
    u.full_name,
    j.status,
    j.created_at,
    j.started_at,
    TIMESTAMPDIFF(SECOND, j.started_at, NOW()) as elapsed_seconds,
    j.error_message
FROM optimization_jobs j
         JOIN users u ON j.user_id = u.id
WHERE j.status IN ('PENDING', 'PROCESSING');

-- View: Solution summary with job info
CREATE OR REPLACE VIEW v_solution_summary AS
SELECT
    s.id as solution_id,
    s.job_id,
    j.user_id,
    u.username,
    s.branch_id,
    s.status,
    s.type,
    s.total_distance,
    s.total_co2,
    s.total_vehicles_used,
    s.served_orders,
    s.unserved_orders,
    s.created_at,
    j.created_at as job_created_at,
    TIMESTAMPDIFF(SECOND, j.created_at, j.completed_at) as processing_duration_seconds
FROM solutions s
         JOIN optimization_jobs j ON s.job_id = j.id
         JOIN users u ON j.user_id = u.id;

-- View: Vehicle availability
CREATE OR REPLACE VIEW v_vehicle_availability AS
SELECT
    v.id,
    v.fleet_id,
    f.fleet_name,
    f.branch_id,
    v.vehicle_license_plate,
    v.capacity,
    v.status,
    d.name as start_depot_name,
    COUNT(DISTINCT r.id) as active_routes
FROM vehicles v
         JOIN fleets f ON v.fleet_id = f.id
         JOIN depots d ON v.start_depot_id = d.id
         LEFT JOIN routes r ON v.id = r.vehicle_id
         LEFT JOIN solutions s ON r.solution_id = s.id
         LEFT JOIN optimization_jobs j ON s.job_id = j.id AND j.status = 'PROCESSING'
GROUP BY v.id, v.fleet_id, f.fleet_name, f.branch_id,
         v.vehicle_license_plate, v.capacity, v.status, d.name;

-- ============================================
-- STORED PROCEDURES
-- ============================================

DELIMITER $$

-- Procedure: Check if branch can submit new job
CREATE PROCEDURE sp_can_submit_job(
    IN p_branch_id BIGINT,
    OUT p_can_submit BOOLEAN,
    OUT p_running_job_id BIGINT
)
BEGIN
    SELECT
        COUNT(*) = 0,
        MAX(id)
    INTO p_can_submit, p_running_job_id
    FROM optimization_jobs
    WHERE branch_id = p_branch_id
      AND status = 'PROCESSING';
END$$

-- Procedure: Get job statistics for branch
CREATE PROCEDURE sp_get_job_statistics(
    IN p_branch_id BIGINT,
    IN p_days INT
)
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
END$$

-- Procedure: Cleanup old jobs
CREATE PROCEDURE sp_cleanup_old_jobs()
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
END$$

DELIMITER ;

-- ============================================
-- TRIGGERS
-- ============================================

DELIMITER $$

-- Trigger: Prevent job deletion if it has a solution
CREATE TRIGGER trg_prevent_job_deletion_with_solution
    BEFORE DELETE ON optimization_jobs
    FOR EACH ROW
BEGIN
    DECLARE solution_count INT;

    SELECT COUNT(*) INTO solution_count
    FROM solutions
    WHERE job_id = OLD.id;

    IF solution_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot delete job: solution exists. Delete solution first.';
    END IF;
END$$

-- Trigger: Auto-set completed_at timestamp
CREATE TRIGGER trg_job_completed_at
    BEFORE UPDATE ON optimization_jobs
    FOR EACH ROW
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
END$$

DELIMITER ;

-- ============================================
-- GRANTS (Optional - adjust for your setup)
-- ============================================

-- Create application user (uncomment and modify as needed)
-- CREATE USER IF NOT EXISTS 'vrp_app'@'localhost' IDENTIFIED BY 'your_secure_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON vrp_db.* TO 'vrp_app'@'localhost';
-- GRANT EXECUTE ON PROCEDURE vrp_db.sp_can_submit_job TO 'vrp_app'@'localhost';
-- GRANT EXECUTE ON PROCEDURE vrp_db.sp_get_job_statistics TO 'vrp_app'@'localhost';
-- FLUSH PRIVILEGES;

-- ============================================
-- SAMPLE DATA (For development/testing)
-- ============================================

/*

-- Insert sample branch
INSERT INTO branches (id, name) VALUES (1, 'Ho Chi Minh Branch')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Insert sample user
INSERT INTO users (branch_id, username, password, email, full_name, role) VALUES
    (1, 'planner1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'planner1@example.com', 'John Planner', 'PLANNER')
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- Insert sample depot
INSERT INTO depots (branch_id, name, address, location) VALUES
    (1, 'Main Warehouse', '123 Nguyen Van Linh, District 7, HCMC', ST_GeomFromText('POINT(106.6917 10.7769)', 4326))
ON DUPLICATE KEY UPDATE name = VALUES(name);
 */

-- ============================================
-- VERIFICATION QUERIES-- ============================================

-- Check all tables exist
SELECT TABLE_NAME, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
                     'branches', 'users', 'depots', 'fleets', 'vehicles',
                     'orders', 'optimization_jobs', 'solutions', 'routes', 'route_segments'
    )
ORDER BY TABLE_NAME;

-- Check all indexes
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- Check all foreign keys
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
SELECT 'VRP Database Migration Completed Successfully!' as status;

SET FOREIGN_KEY_CHECKS = 0;

-- branches
ALTER TABLE branches MODIFY COLUMN updated_at TIMESTAMP NULL;

-- users
ALTER TABLE users MODIFY COLUMN updated_at TIMESTAMP NULL;

-- depots
ALTER TABLE depots MODIFY COLUMN updated_at TIMESTAMP NULL;

-- fleets
ALTER TABLE fleets MODIFY COLUMN updated_at TIMESTAMP NULL;

-- vehicles
ALTER TABLE vehicles MODIFY COLUMN updated_at TIMESTAMP NULL;

-- orders
ALTER TABLE orders MODIFY COLUMN updated_at TIMESTAMP NULL;

-- branches
ALTER TABLE branches MODIFY COLUMN created_at TIMESTAMP NULL;

-- users
ALTER TABLE users MODIFY COLUMN created_at TIMESTAMP NULL;

-- depots
ALTER TABLE depots MODIFY COLUMN created_at TIMESTAMP NULL;

-- fleets
ALTER TABLE fleets MODIFY COLUMN created_at TIMESTAMP NULL;

-- vehicles
ALTER TABLE vehicles MODIFY COLUMN created_at TIMESTAMP NULL;

-- orders
ALTER TABLE orders MODIFY COLUMN created_at TIMESTAMP NULL;

-- optimization_jobs
ALTER TABLE optimization_jobs MODIFY COLUMN created_at TIMESTAMP NULL;

-- solutions
ALTER TABLE solutions MODIFY COLUMN created_at TIMESTAMP NULL;

-- routes
ALTER TABLE routes MODIFY COLUMN created_at TIMESTAMP NULL;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'Removed automatic CURRENT_TIMESTAMP from all audit columns successfully!' as status;

ALTER TABLE orders
ADD COLUMN service_time INT DEFAULT 0 COMMENT 'Service time in minutes' AFTER demand,
ADD COLUMN delivery_date DATE DEFAULT (CURRENT_DATE) AFTER delivery_notes;


UPDATE vehicles
SET vehicle_feature = '{}'
WHERE vehicle_feature = '';
ALTER TABLE vehicles
CHANGE COLUMN vehicle_feature vehicle_features JSON COMMENT 'Additional vehicle features/description';

UPDATE vehicles
SET vehicle_features = JSON_OBJECT(
        'vehicle_type', CASE
                           WHEN capacity <= 50 THEN 'PETROL_MOTORCYCLE'
                           WHEN capacity <= 500 THEN 'PETROL_CAR'
                           WHEN capacity <= 1000 THEN 'PETROL_VAN'
                           ELSE 'DIESEL_TRUCK'
            END,
        'emission_factor', CASE
                              WHEN capacity <= 50 THEN 120.0
                              WHEN capacity <= 500 THEN 180.0
                              WHEN capacity <= 1000 THEN 220.0
                              ELSE 280.0
            END
        );

SET FOREIGN_KEY_CHECKS = 0;

USE gvrp_db;

-- ============================================
-- 1. VEHICLE_TYPES TABLE (NEW)
-- ============================================
CREATE TABLE IF NOT EXISTS vehicle_types (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     branch_id BIGINT NOT NULL,
     type_name VARCHAR(100) NOT NULL,
     description VARCHAR(255) COMMENT 'Mô tả chi tiết loại xe (e.g., Tải lạnh, Xe điện)',
     capacity INT NOT NULL COMMENT 'Capacity in kg or units (Tải trọng chuẩn)',
     fixed_cost DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Fixed cost per trip in VND',
     cost_per_km DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Variable cost per km in VND',
     cost_per_hour DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Variable cost per hour in VND',
     max_distance DECIMAL(10,2) COMMENT 'Maximum distance in km',
     max_duration DECIMAL(10,2) COMMENT 'Maximum duration in hours',
     created_at TIMESTAMP NULL DEFAULT NULL,
     updated_at TIMESTAMP NULL DEFAULT NULL,

     FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,

     UNIQUE KEY uk_branch_type_name (branch_id, type_name),
     INDEX idx_branch_id (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Chuẩn hóa thông số kỹ thuật và chi phí của các loại xe';

ALTER TABLE vehicles
    ADD COLUMN vehicle_type_id BIGINT COMMENT 'Foreign key to vehicle_types' AFTER fleet_id;

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicle_type
        FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id) ON DELETE RESTRICT; -- Đổi tên constraint

ALTER TABLE vehicles
    ADD INDEX idx_vehicle_type_id (vehicle_type_id);


alter table vehicles
    drop constraint chk_capacity;

alter table vehicles
    drop constraint chk_costs;
ALTER TABLE vehicles DROP COLUMN capacity, DROP COLUMN vehicle_features, DROP COLUMN fixed_cost, DROP COLUMN cost_per_km, DROP COLUMN cost_per_hour, DROP COLUMN max_distance, DROP COLUMN max_duration;

ALTER TABLE vehicle_types
    ADD CONSTRAINT chk_vehicle_capacity CHECK (capacity > 0),
    ADD CONSTRAINT chk_vehicle_costs CHECK (
        fixed_cost >= 0 AND
        cost_per_km >= 0 AND
        cost_per_hour >= 0
        ),
    ADD COLUMN vehicle_features JSON COMMENT 'Additional vehicle features/description' AFTER type_name;


SET FOREIGN_KEY_CHECKS = 1;
SELECT 'VRP Database Migration 1.0.1 (Vehicle Types Refactor) Completed Successfully!' as status;

ALTER TABLE solutions ADD COLUMN total_cost DECIMAL(15,2) COMMENT 'Tổng chi phí tối ưu hóa trong VND' AFTER type;
