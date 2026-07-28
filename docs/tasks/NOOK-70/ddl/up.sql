ALTER TABLE place_parsing_jobs
    ADD COLUMN attempt_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '장소 파싱 처리 시도 횟수'
        AFTER failure_reason,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '다음 장소 파싱 시도 가능 시각'
        AFTER attempt_count,
    ADD KEY idx_status_next_attempt_at (status, next_attempt_at);
