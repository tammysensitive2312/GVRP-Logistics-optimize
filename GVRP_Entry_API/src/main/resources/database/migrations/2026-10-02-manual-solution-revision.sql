-- Manual route-customization revisions (ROUTE-CUSTOM-001).
-- A dispatcher-saved edit is a NEW solution row pointing at its base solution.
-- Also widens solutions.type, whose ENUM predates the JPA values.
ALTER TABLE solutions
    ADD COLUMN parent_solution_id BIGINT NULL
        COMMENT 'Base solution this revision was edited from; NULL for originals'
        AFTER job_id;

ALTER TABLE solutions
    ADD CONSTRAINT fk_solution_parent
        FOREIGN KEY (parent_solution_id) REFERENCES solutions (id)
            ON DELETE RESTRICT;

ALTER TABLE solutions
    ADD INDEX idx_solution_parent (parent_solution_id);

ALTER TABLE solutions
    MODIFY COLUMN type ENUM('ENGINE_GENERATED', 'FILE_IMPORTED', 'MANUAL_EDITED')
        NOT NULL DEFAULT 'ENGINE_GENERATED';
