-- Manual route-customization revisions, follow-up (ROUTE-CUSTOM-001, user decision: nullable job).
-- Revisions keep job_id NULL so the 1:1 engine-original mapping (callback
-- idempotency, job history) is untouched. MySQL UNIQUE permits multiple NULLs,
-- so the existing unique key stays as is.
-- NOTE: this repo has no migration runner (no Flyway; ddl-auto=update applies
-- JPA nullability automatically on restart). Run manually where ddl-auto is off.
ALTER TABLE solutions
    MODIFY COLUMN job_id BIGINT NULL
        COMMENT 'Engine originals link 1:1; manual revisions use NULL and hang off parent_solution_id';
