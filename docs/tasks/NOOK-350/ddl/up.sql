-- MySQL 8.4. 한 번만 실행. API/worker 배포 전에 적용. 테이블 크기에 따른 backfill 영향 확인.
ALTER TABLE parsing_follow_up_jobs
    ADD COLUMN retry_attempt_count INT NOT NULL DEFAULT 0 COMMENT '현재 재시도 회차의 실행 횟수';
UPDATE parsing_follow_up_jobs SET retry_attempt_count = attempt_count;
