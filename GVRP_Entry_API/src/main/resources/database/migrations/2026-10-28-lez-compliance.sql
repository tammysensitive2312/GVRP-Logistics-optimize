-- Low-emission zone compliance state, schema only (LEZ-001 P1b).
-- Run manually (the user runs it; there is no migration runner), BEFORE starting
-- the app: the default profile uses ddl-auto=validate, so the entities below
-- fail to boot while these columns are missing.
-- Requires 2026-10-27-lez-zones.sql (P1a) to be applied first.
--
-- Time semantics: the solver plans a single service date and jsprit trips start
-- at 08:00, so compliance minutes are minutes from 08:00 of the service date.
-- STALE is computed on read from lez_ruleset_hash, never stored (design §16.2-6).

ALTER TABLE routes
    ADD COLUMN lez_status ENUM('UNCHECKED', 'COMPLIANT', 'VIOLATION') NULL
        COMMENT 'Result of the last compliance check; NULL on rows created before P1b' AFTER load_utilization,
    ADD COLUMN lez_checked_at DATETIME NULL COMMENT 'When the compliance check last ran' AFTER lez_status,
    ADD COLUMN lez_ruleset_hash CHAR(64) NULL COMMENT 'SHA-256 of the compiled restrictions used by that check' AFTER lez_checked_at,
    ADD COLUMN lez_violations JSON NULL COMMENT 'List of violations from that check; NULL when compliant' AFTER lez_ruleset_hash;

ALTER TABLE route_stops
    ADD COLUMN geometry_to_next MEDIUMTEXT NULL
        COMMENT 'Encoded polyline (precision 1e5, [lat, lon]) of the leg to the next stop' AFTER time_to_next,
    ADD COLUMN instructions_to_next JSON NULL
        COMMENT 'Turn-by-turn instructions of the leg to the next stop' AFTER geometry_to_next;

ALTER TABLE optimization_jobs
    ADD COLUMN lez_snapshot JSON NULL
        COMMENT 'Compiled restrictions the job ran with; NULL when no zone applied' AFTER input_data;

ALTER TABLE unassigned_orders
    ADD COLUMN reason_code VARCHAR(40) NULL
        COMMENT 'Machine-readable engine reason, e.g. LEZ_UNREACHABLE' AFTER reason;
