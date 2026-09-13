-- MySQL 8.4, one-time additive DDL. Apply before deploying API/worker.
ALTER TABLE posts ADD COLUMN parsing_alert_fingerprint VARCHAR(64) NULL
    COMMENT '마지막 게시물 처리 요약 알림 상태 해시', ALGORITHM=INSTANT;
