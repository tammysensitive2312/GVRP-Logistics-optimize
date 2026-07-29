-- MySQL dump 10.13  Distrib 8.0.39, for Linux (x86_64)
--
-- Host: localhost    Database: gvrp_db
-- ------------------------------------------------------
-- Server version	8.0.39

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `branches`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `branches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `branch_webhook_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=9001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Branch entities for multi-tenant support';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `depots`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `depots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` point NOT NULL /*!80003 SRID 4326 */ COMMENT 'GPS coordinates (longitude, latitude)',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_branch_id` (`branch_id`),
  SPATIAL KEY `idx_location` (`location`),
  KEY `idx_depot_branch` (`branch_id`),
  SPATIAL KEY `idx_depot_location` (`location`),
  CONSTRAINT `depots_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Depot/warehouse locations with spatial data';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fleets`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fleets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `fleet_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_branch_fleet` (`branch_id`,`fleet_name`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_fleet_branch` (`branch_id`),
  CONSTRAINT `fleets_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Fleet groups for organizing vehicles';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `optimization_jobs`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `optimization_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `status` enum('CANCELLED','COMPLETED','FAILED','PENDING','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` timestamp NULL DEFAULT NULL,
  `completed_at` timestamp NULL DEFAULT NULL,
  `cancelled_at` timestamp NULL DEFAULT NULL,
  `external_job_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_data` json NOT NULL COMMENT 'Serialized RoutePlanningRequest for retry capability',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT 'Error details if status=FAILED',
  `estimated_duration_minutes` int DEFAULT NULL,
  `created_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_branch_status_created` (`branch_id`,`status`,`created_at` DESC),
  KEY `idx_user_created` (`created_at` DESC),
  KEY `idx_external_job_id` (`external_job_id`),
  KEY `optimization_jobs_ibfk_2` (`created_by_user_id`),
  KEY `idx_job_branch` (`branch_id`),
  KEY `idx_job_status` (`status`),
  KEY `idx_job_created` (`created_at`),
  CONSTRAINT `optimization_jobs_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `optimization_jobs_ibfk_2` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Optimization job tracking and lifecycle management';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `trg_job_completed_at` BEFORE UPDATE ON `optimization_jobs` FOR EACH ROW BEGIN
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
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `trg_prevent_job_deletion_with_solution` BEFORE DELETE ON `optimization_jobs` FOR EACH ROW BEGIN
    DECLARE solution_count INT;

    SELECT COUNT(*) INTO solution_count
    FROM solutions
    WHERE job_id = OLD.id;

    IF solution_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot delete job: solution exists. Delete solution first.';
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `orders`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `order_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` point NOT NULL /*!80003 SRID 4326 */ COMMENT 'GPS coordinates (longitude, latitude)',
  `demand` decimal(10,2) NOT NULL COMMENT 'Demand in kg or units',
  `service_time` int DEFAULT '0' COMMENT 'Service time in minutes',
  `time_window_start` time DEFAULT NULL COMMENT 'Earliest delivery time',
  `time_window_end` time DEFAULT NULL COMMENT 'Latest delivery time',
  `status` enum('COMPLETED','ON_ROUTE','REJECTED','SCHEDULED','SERVICING','UNASSIGNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` int DEFAULT '1' COMMENT '1=highest priority',
  `delivery_notes` text COLLATE utf8mb4_unicode_ci COMMENT 'Additional delivery instructions',
  `delivery_date` date DEFAULT (curdate()),
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_branch_order_code` (`branch_id`,`order_code`),
  UNIQUE KEY `idx_order_code` (`order_code`,`branch_id`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_orders_branch_created` (`branch_id`,`created_at` DESC),
  KEY `idx_orders_id_status_priority` (`id`,`status`,`priority`),
  KEY `idx_order_branch` (`branch_id`),
  KEY `idx_order_status` (`status`),
  SPATIAL KEY `idx_order_location` (`location`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_demand` CHECK ((`demand` > 0)),
  CONSTRAINT `chk_priority` CHECK ((`priority` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=1000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Delivery orders with time windows and spatial data';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `route_sequence`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `route_sequence` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `route_stop_sequence`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `route_stop_sequence` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `route_stops`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `route_stops` (
  `id` bigint NOT NULL,
  `route_id` bigint NOT NULL COMMENT 'Foreign key to routes table',
  `order_id` bigint DEFAULT NULL COMMENT 'Foreign key to orders table (NULL for depot stops)',
  `sequence_number` int DEFAULT NULL COMMENT 'Stop sequence in route (0-based)',
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DEPOT or ORDER',
  `location_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Display name of location',
  `arrival_time` time DEFAULT NULL,
  `departure_time` time DEFAULT NULL,
  `service_time` decimal(10,2) DEFAULT NULL COMMENT 'Service time in minutes',
  `wait_time` decimal(10,2) DEFAULT NULL COMMENT 'Wait time in minutes',
  `demand` decimal(10,2) DEFAULT NULL COMMENT 'Demand delivered at this stop (kg)',
  `load_after` decimal(10,2) DEFAULT NULL COMMENT 'Remaining load after this stop (kg)',
  `distance_to_next` decimal(10,2) DEFAULT NULL COMMENT 'Distance to next stop (km)',
  `time_to_next` decimal(10,2) DEFAULT NULL COMMENT 'Time to next stop (minutes)',
  `location` point /*!80003 SRID 4326 */ DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stop_route` (`route_id`),
  KEY `idx_stop_order` (`order_id`),
  KEY `idx_stop_sequence` (`route_id`,`sequence_number`),
  KEY `idx_stop_type` (`type`),
  CONSTRAINT `FK_route_stops_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FK_route_stops_route` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`),
  CONSTRAINT `CHK_stop_type` CHECK ((`type` in (_utf8mb4'DEPOT',_utf8mb4'ORDER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Route stops - each record represents a stop in a route';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `routes`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `routes` (
  `id` bigint NOT NULL,
  `solution_id` bigint NOT NULL,
  `vehicle_id` bigint NOT NULL,
  `route_order` int NOT NULL COMMENT 'Sequence number within solution',
  `distance` decimal(10,2) DEFAULT NULL COMMENT 'Route distance in km',
  `co2_emission` decimal(10,2) DEFAULT NULL COMMENT 'Route CO2 emission in kg',
  `service_time` decimal(10,2) DEFAULT NULL COMMENT 'Total service time in hours',
  `order_count` int DEFAULT NULL COMMENT 'Number of orders in this route',
  `load_utilization` decimal(5,2) DEFAULT NULL COMMENT 'Vehicle load utilization percentage',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_solution_id` (`solution_id`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_solution_order` (`solution_id`,`route_order`),
  KEY `idx_route_solution` (`solution_id`),
  KEY `idx_route_vehicle` (`vehicle_id`),
  CONSTRAINT `routes_ibfk_1` FOREIGN KEY (`solution_id`) REFERENCES `solutions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `routes_ibfk_2` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_load_utilization` CHECK (((`load_utilization` is null) or ((`load_utilization` >= 0) and (`load_utilization` <= 100))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Individual routes assigned to vehicles';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `solutions`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solutions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL COMMENT 'One-to-one relationship with job',
  `branch_id` bigint NOT NULL,
  `status` enum('INFEASIBLE','INITIAL','PARTIAL_SUCCESS','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('ENGINE_GENERATED','FILE_IMPORTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_cost` decimal(18,2) DEFAULT NULL COMMENT 'Tổng chi phí tối ưu hóa trong VND',
  `total_distance` decimal(10,2) DEFAULT NULL COMMENT 'Total distance in km',
  `total_co2` decimal(10,2) DEFAULT NULL COMMENT 'Total CO2 emission in kg',
  `total_time` decimal(10,2) DEFAULT NULL COMMENT 'Total service time in hours',
  `total_vehicles_used` int DEFAULT NULL COMMENT 'Number of vehicles used',
  `served_orders` int DEFAULT NULL COMMENT 'Number of orders served',
  `unserved_orders` int DEFAULT NULL COMMENT 'Number of unserved orders',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime(6) DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `job_id` (`job_id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_branch_type` (`branch_id`,`type`),
  KEY `idx_created_at` (`created_at` DESC),
  KEY `idx_solution_branch` (`branch_id`),
  KEY `idx_solution_status` (`status`),
  KEY `idx_solution_created` (`created_at`),
  CONSTRAINT `solutions_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `optimization_jobs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `solutions_ibfk_2` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Optimization solution results linked to jobs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `unassigned_order_sequence`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `unassigned_order_sequence` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `unassigned_orders`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `unassigned_orders` (
  `id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `solution_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe309y35oc72vgeoctmnfkoc9g` (`order_id`),
  KEY `FK5imsje4xw5qnv43kueaanh3q2` (`solution_id`),
  CONSTRAINT `FK5imsje4xw5qnv43kueaanh3q2` FOREIGN KEY (`solution_id`) REFERENCES `solutions` (`id`),
  CONSTRAINT `FKe309y35oc72vgeoctmnfkoc9g` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt hashed password',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('CUSTOMER','PLANNER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `slack_user_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `slack_user_id` (`slack_user_id`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_role` (`role`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User accounts with role-based access control';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vehicle_types`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vehicle_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `type_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vehicle_features` json DEFAULT NULL COMMENT 'Additional vehicle features/description',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Mô tả chi tiết loại xe (e.g., Tải lạnh, Xe điện)',
  `capacity` int NOT NULL COMMENT 'Capacity in kg or units (Tải trọng chuẩn)',
  `fixed_cost` decimal(10,2) DEFAULT '0.00' COMMENT 'Fixed cost per trip in VND',
  `cost_per_km` decimal(10,2) DEFAULT '0.00' COMMENT 'Variable cost per km in VND',
  `cost_per_hour` decimal(10,2) DEFAULT '0.00' COMMENT 'Variable cost per hour in VND',
  `max_distance` decimal(10,2) DEFAULT NULL COMMENT 'Maximum distance in km',
  `max_duration` decimal(10,2) DEFAULT NULL COMMENT 'Maximum duration in hours',
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_branch_type_name` (`branch_id`,`type_name`),
  KEY `idx_branch_id` (`branch_id`),
  CONSTRAINT `vehicle_types_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_vehicle_capacity` CHECK ((`capacity` > 0)),
  CONSTRAINT `chk_vehicle_costs` CHECK (((`fixed_cost` >= 0) and (`cost_per_km` >= 0) and (`cost_per_hour` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=9003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chuẩn hóa thông số kỹ thuật và chi phí của các loại xe';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vehicles`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vehicles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `fleet_id` bigint NOT NULL,
  `vehicle_type_id` bigint DEFAULT NULL COMMENT 'Foreign key to vehicle_types',
  `start_depot_id` bigint NOT NULL,
  `end_depot_id` bigint NOT NULL,
  `vehicle_license_plate` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('AVAILABLE','IN_USE','MAINTENANCE','RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `vehicle_license_plate` (`vehicle_license_plate`),
  UNIQUE KEY `idx_vehicle_license` (`vehicle_license_plate`),
  KEY `idx_fleet_id` (`fleet_id`),
  KEY `idx_status` (`status`),
  KEY `idx_license_plate` (`vehicle_license_plate`),
  KEY `idx_start_depot` (`start_depot_id`),
  KEY `idx_end_depot` (`end_depot_id`),
  KEY `idx_vehicle_fleet` (`fleet_id`),
  KEY `idx_vehicle_status` (`status`),
  KEY `idx_vehicle_type_id` (`vehicle_type_id`),
  CONSTRAINT `fk_vehicle_type` FOREIGN KEY (`vehicle_type_id`) REFERENCES `vehicle_types` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `vehicles_ibfk_1` FOREIGN KEY (`fleet_id`) REFERENCES `fleets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `vehicles_ibfk_2` FOREIGN KEY (`start_depot_id`) REFERENCES `depots` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `vehicles_ibfk_3` FOREIGN KEY (`end_depot_id`) REFERENCES `depots` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=109040 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Vehicle fleet with operational constraints';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-27 15:48:42
