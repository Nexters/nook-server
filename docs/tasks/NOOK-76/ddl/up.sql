CREATE TABLE post_content_parsing_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 콘텐츠 파싱 작업 식별자',
    post_id BIGINT NOT NULL COMMENT '게시물 식별자',
    status VARCHAR(20) COLLATE utf8mb4_bin NOT NULL COMMENT '게시물 콘텐츠 파싱 상태',
    failure_reason VARCHAR(500) NULL COMMENT '마지막 게시물 콘텐츠 파싱 실패 사유',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '게시물 콘텐츠 파싱 처리 시도 횟수',
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '다음 게시물 콘텐츠 파싱 시도 가능 시각',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id (post_id),
    KEY idx_status_updated_at (status, updated_at),
    KEY idx_status_next_attempt_at (status, next_attempt_at),
    CONSTRAINT fk_post_content_parsing_jobs_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '게시물 콘텐츠 파싱 작업';

INSERT INTO post_content_parsing_jobs (
    post_id,
    status,
    failure_reason,
    attempt_count,
    next_attempt_at,
    created_at,
    updated_at
)
SELECT
    post.id,
    'COMPLETED',
    NULL,
    0,
    CURRENT_TIMESTAMP(6),
    post.created_at,
    CURRENT_TIMESTAMP(6)
FROM posts AS post;
