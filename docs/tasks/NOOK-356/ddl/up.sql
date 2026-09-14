-- MySQL 8.4, one-time additive DDL. Apply before API/worker deployment.
ALTER TABLE posts
 ADD COLUMN processing_disposition VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '운영 처리 상태 OPEN 또는 UNPROCESSABLE',
 ADD COLUMN processing_disposition_reason VARCHAR(500) NULL COMMENT '운영 처리 상태 변경 사유',
 ADD COLUMN processing_disposition_changed_at TIMESTAMP(6) NULL COMMENT '운영 처리 상태 변경 시각',
 ALGORITHM=INSTANT;
