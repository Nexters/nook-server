ALTER TABLE posts
    ADD COLUMN source_location_tag VARCHAR(500) NULL COMMENT '외부 provider에 설정된 장소 태그'
        AFTER published_at;

CREATE TABLE post_hashtags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 해시태그 식별자',
    post_id BIGINT NOT NULL COMMENT '게시물 식별자',
    hashtag VARCHAR(100) NOT NULL COMMENT '해시 기호를 제외한 해시태그',
    display_order INT NOT NULL COMMENT '게시물 내 해시태그 노출 순서',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id_hashtag (post_id, hashtag),
    UNIQUE KEY idx_u_post_id_display_order (post_id, display_order),
    CONSTRAINT fk_post_hashtags_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT chk_post_hashtags_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '게시물 해시태그';

CREATE TABLE place_parsing_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '장소 파싱 작업 식별자',
    post_id BIGINT NOT NULL COMMENT '게시물 식별자',
    status VARCHAR(20) COLLATE utf8mb4_bin NOT NULL COMMENT '클라이언트 공개 장소 파싱 상태',
    failure_reason VARCHAR(500) NULL COMMENT '클라이언트에 공개 가능한 실패 사유',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id (post_id),
    KEY idx_status_updated_at (status, updated_at),
    CONSTRAINT fk_place_parsing_jobs_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '게시물 장소 파싱 작업';
