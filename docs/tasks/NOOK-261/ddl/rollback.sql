ALTER TABLE place_parsing_jobs
    DROP COLUMN progress_percent,
    DROP COLUMN progress_stage_started_at,
    DROP COLUMN progress_stage,
    ALGORITHM=INSTANT;

ALTER TABLE post_content_parsing_jobs
    DROP COLUMN progress_percent,
    DROP COLUMN progress_stage_started_at,
    DROP COLUMN progress_stage,
    ALGORITHM=INSTANT;
