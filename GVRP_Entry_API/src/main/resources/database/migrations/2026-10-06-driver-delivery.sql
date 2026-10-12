-- Driver delivery chặng 1 (DRIVER-DELIVERY-001).
-- NOTE: this repo has no migration runner (no Flyway; ddl-auto=update applies
-- JPA nullability automatically on restart). Run manually where ddl-auto is off.
ALTER TABLE users
    MODIFY COLUMN role ENUM('CUSTOMER', 'PLANNER', 'DRIVER') NOT NULL;

ALTER TABLE users
    ADD COLUMN phone VARCHAR(20) NULL
        COMMENT 'Driver contact number'
        AFTER full_name;

CREATE TABLE IF NOT EXISTS route_sends (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    solution_id BIGINT NOT NULL COMMENT 'Revision that was sent',
    route_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL COMMENT 'Receiving driver (users, role DRIVER)',
    sender_id BIGINT NOT NULL COMMENT 'Dispatching planner (users)',
    sent_at DATETIME NOT NULL,
    status ENUM('CURRENT', 'SUPERSEDED') NOT NULL DEFAULT 'CURRENT',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_send_solution FOREIGN KEY (solution_id) REFERENCES solutions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_send_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_send_driver FOREIGN KEY (driver_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_send_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_send_route (route_id),
    INDEX idx_send_driver (driver_id),
    INDEX idx_send_solution (solution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='Immutable dispatch history: which route revision went to which driver';
