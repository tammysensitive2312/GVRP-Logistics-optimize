-- Job-history sort-memory fix (JOBHIST-IDX-001).
-- Serves `WHERE branch_id = ? ORDER BY created_at DESC LIMIT ?` from the index:
-- no filesort, no dependence on MySQL sort_buffer_size regardless of row width.
-- NOTE: this repo has no migration runner. Apply manually where needed.
ALTER TABLE optimization_jobs
    ADD INDEX idx_branch_created (branch_id, created_at DESC);
