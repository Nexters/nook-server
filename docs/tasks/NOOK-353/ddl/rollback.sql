-- 이전 코드로 복귀 후 실행. 신규 실패 시각 및 수동 재시도 예산 정보가 유실된다.
ALTER TABLE post_content_parsing_jobs DROP COLUMN last_failed_at, DROP COLUMN retry_attempt_count, DROP COLUMN execution_stage, DROP COLUMN last_failure_stage;
ALTER TABLE place_parsing_jobs DROP COLUMN last_failed_at, DROP COLUMN retry_attempt_count, DROP COLUMN execution_stage, DROP COLUMN last_failure_stage;
ALTER TABLE parsing_follow_up_jobs DROP COLUMN last_failed_at;
