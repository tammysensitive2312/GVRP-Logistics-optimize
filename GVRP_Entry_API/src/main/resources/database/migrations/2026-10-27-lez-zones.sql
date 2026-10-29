 -- Low-emission / restricted traffic zones, schema only (LEZ-001 P1a).
-- Run manually (the user runs it; there is no migration runner). Run it BEFORE
-- starting the app with these entities: ddl-auto=update would otherwise create
-- branch_zones without the LINKED unique key below, and ddl-auto=validate
-- (default profile) fails while the tables are missing.
-- No seed data here: Hanoi templates come in a separate dated file (P1-seed).
-- Geometry: SRID 4326, x = longitude, y = latitude, as for orders.location.

CREATE TABLE IF NOT EXISTS zone_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    jurisdiction VARCHAR(20) NOT NULL COMMENT 'e.g. VN-HN',
    geometry MULTIPOLYGON NOT NULL SRID 4326,
    version INT NOT NULL COMMENT 'Raised by one on every geometry or rule change',
    boundary_confidence ENUM('SURVEYED', 'DIGITISED_APPROX', 'WARD_PROXY') NOT NULL,
    boundary_source TEXT NULL,
    legal_basis TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT uk_zone_template_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='System zone templates; no branch_id; read-only for branches';

CREATE TABLE IF NOT EXISTS zone_rule_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_template_id BIGINT NOT NULL,
    template_version INT NOT NULL COMMENT 'Template version the rule belongs to',
    selector JSON NOT NULL,
    schedule JSON NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL COMMENT 'NULL = open-ended',
    legal_basis TEXT NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_zone_rule_template FOREIGN KEY (zone_template_id) REFERENCES zone_templates (id) ON DELETE RESTRICT,
    INDEX idx_zone_rule_template_version (zone_template_id, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MySQL has no partial (filtered) unique index, so "one LINKED row per
-- (branch, template)" cannot be declared directly. ZoneService enforces it and
-- answers 409; linked_template_id (NULL for COPIED rows, and NULLs never
-- collide in a UNIQUE key) gives the database a backstop. It is not mapped by JPA.
CREATE TABLE IF NOT EXISTS branch_zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    mode ENUM('LINKED', 'COPIED') NOT NULL,
    source_template_id BIGINT NOT NULL,
    source_template_version INT NULL COMMENT 'COPIED: version copied; LINKED: NULL',
    acknowledged_template_version INT NULL COMMENT 'COPIED: latest acknowledged template version',
    name VARCHAR(200) NULL COMMENT 'COPIED only',
    geometry MULTIPOLYGON NULL SRID 4326 COMMENT 'COPIED only',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    linked_template_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN mode = 'LINKED' THEN source_template_id ELSE NULL END) STORED,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_branch_zone_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_branch_zone_template FOREIGN KEY (source_template_id) REFERENCES zone_templates (id) ON DELETE RESTRICT,
    CONSTRAINT uk_branch_zone_linked UNIQUE (branch_id, linked_template_id),
    INDEX idx_branch_zone_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='A branch enables (LINKED) or copies (COPIED) a zone template';

CREATE TABLE IF NOT EXISTS branch_zone_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    branch_zone_id BIGINT NOT NULL,
    template_version INT NULL COMMENT 'Template version copied from; NULL for branch-written rules',
    selector JSON NOT NULL,
    schedule JSON NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL COMMENT 'NULL = open-ended',
    legal_basis TEXT NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_branch_zone_rule_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_branch_zone_rule_zone FOREIGN KEY (branch_zone_id) REFERENCES branch_zones (id) ON DELETE RESTRICT,
    INDEX idx_branch_zone_rule_branch (branch_id),
    INDEX idx_branch_zone_rule_zone (branch_zone_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Rules of COPIED branch zones';

CREATE TABLE IF NOT EXISTS vehicle_permits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    branch_zone_id BIGINT NOT NULL,
    permit_no VARCHAR(100) NULL,
    schedule JSON NULL COMMENT 'NULL = the whole day',
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_vehicle_permit_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_permit_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_permit_zone FOREIGN KEY (branch_zone_id) REFERENCES branch_zones (id) ON DELETE RESTRICT,
    INDEX idx_vehicle_permit_branch (branch_id),
    INDEX idx_vehicle_permit_vehicle (vehicle_id),
    INDEX idx_vehicle_permit_zone (branch_zone_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Per-vehicle exemption for one branch zone';

-- Legal attributes, separate from capacity (LEZ-R04). All nullable: unknown
-- means restricted by every rule that needs the attribute.
ALTER TABLE vehicle_types
    ADD COLUMN legal_class ENUM('MOTORBIKE', 'CAR', 'TRUCK', 'BUS', 'NON_MOTORIZED') NULL
        COMMENT 'Explicit; a default is only suggested from the category' AFTER max_duration,
    ADD COLUMN gross_weight_kg INT NULL COMMENT 'Gross design weight, kg' AFTER legal_class,
    ADD COLUMN energy_type ENUM('ELECTRIC', 'HYBRID', 'PETROL', 'DIESEL', 'OTHER') NULL AFTER gross_weight_kg,
    ADD COLUMN emission_standard INT NULL COMMENT 'Emission level, e.g. 4 for Euro 4' AFTER energy_type;
