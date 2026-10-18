-- Pareto candidates of a job (PARETO-001). Run manually: ddl-auto=update does not
-- widen the solutions.type ENUM.
CREATE TABLE IF NOT EXISTS pareto_candidates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    candidate_index INT NOT NULL,
    label VARCHAR(40) NOT NULL,
    cost_weight DECIMAL(10,6) NOT NULL,
    co2_weight DECIMAL(10,6) NOT NULL,
    total_cost DECIMAL(18,2) NULL,
    total_distance DECIMAL(10,2) NULL,
    total_co2 DECIMAL(10,2) NULL,
    total_time DECIMAL(10,2) NULL,
    total_vehicles_used INT NULL,
    served_orders INT NULL,
    unserved_orders INT NULL,
    frontier_status VARCHAR(20) NOT NULL,
    engine_selected BOOLEAN NOT NULL DEFAULT FALSE,
    clustered BOOLEAN NOT NULL DEFAULT FALSE,
    cluster_count INT NULL,
    plan_json JSON NULL COMMENT 'routes + unassigned_orders as received; FRONTIER only',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_pareto_job FOREIGN KEY (job_id) REFERENCES optimization_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_pareto_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT,
    CONSTRAINT uk_pareto_job_index UNIQUE (job_id, candidate_index),
    INDEX idx_pareto_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE solutions
    ADD COLUMN pareto_candidate_id BIGINT NULL
        COMMENT 'Pareto candidate this solution materialises; NULL otherwise' AFTER parent_solution_id,
    ADD CONSTRAINT fk_solution_pareto_candidate
        FOREIGN KEY (pareto_candidate_id) REFERENCES pareto_candidates (id) ON DELETE SET NULL;

ALTER TABLE solutions
    MODIFY COLUMN type ENUM('ENGINE_GENERATED', 'FILE_IMPORTED', 'MANUAL_EDITED', 'PARETO_ALTERNATIVE')
        NOT NULL DEFAULT 'ENGINE_GENERATED';
