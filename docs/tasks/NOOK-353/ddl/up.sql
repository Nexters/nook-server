-- MySQL 8.4. 일회용. 기존 worker 중지 후 적용; 새 버전 최초 실행 전 예산 보정 필요.
-- 과거 실패 시각/단계는 추정하지 않는다. NULL 유지.
ALTER TABLE post_content_parsing_jobs
    ADD COLUMN last_failed_at TIMESTAMP(6) NULL COMMENT '마지막 실제 실패 시각; 과거 미기록은 NULL',
    ADD COLUMN retry_attempt_count INT NOT NULL DEFAULT 0 COMMENT '현재 재시도 회차의 실행 횟수',
    ADD COLUMN execution_stage VARCHAR(40) NULL COMMENT '현재 실행이 진입한 단계',
    ADD COLUMN last_failure_stage VARCHAR(40) NULL COMMENT '마지막 실제 실패 단계';
UPDATE post_content_parsing_jobs SET retry_attempt_count = attempt_count, updated_at = updated_at;
ALTER TABLE place_parsing_jobs
    ADD COLUMN last_failed_at TIMESTAMP(6) NULL COMMENT '마지막 실제 실패 시각; 과거 미기록은 NULL',
    ADD COLUMN retry_attempt_count INT NOT NULL DEFAULT 0 COMMENT '현재 재시도 회차의 실행 횟수',
    ADD COLUMN execution_stage VARCHAR(40) NULL COMMENT '현재 실행이 진입한 단계',
    ADD COLUMN last_failure_stage VARCHAR(40) NULL COMMENT '마지막 실제 실패 단계';
UPDATE place_parsing_jobs SET retry_attempt_count = attempt_count, updated_at = updated_at;
ALTER TABLE parsing_follow_up_jobs
    ADD COLUMN last_failed_at TIMESTAMP(6) NULL COMMENT '마지막 실제 실패 시각; 과거 미기록은 NULL';
