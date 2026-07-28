ALTER TABLE place_parsing_jobs
    DROP INDEX idx_status_next_attempt_at,
    DROP COLUMN next_attempt_at,
    DROP COLUMN attempt_count;
