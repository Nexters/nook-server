CREATE TABLE parsing_follow_up_jobs
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '파싱 후속 작업 식별자',
    job_type        VARCHAR(30)  NOT NULL COMMENT '후속 작업 유형',
    post_id         BIGINT       NOT NULL COMMENT '원본 게시물 식별자',
    payload         MEDIUMTEXT   NOT NULL COMMENT '후속 작업 요청 JSON',
    status          VARCHAR(20)  NOT NULL COMMENT '작업 상태',
    attempt_count   INT          NOT NULL DEFAULT 0 COMMENT '실행 시도 횟수',
    next_attempt_at TIMESTAMP(6) NOT NULL COMMENT '다음 실행 가능 시각',
    failure_reason  VARCHAR(500) NULL COMMENT '마지막 실패 사유',
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    INDEX idx_status_next_attempt_at (status, next_attempt_at),
    INDEX idx_status_updated_at (status, updated_at),
    INDEX idx_job_type_created_at (job_type, created_at),
    INDEX idx_post_id (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '파싱 완료 후 실행할 영속 후속 작업';
