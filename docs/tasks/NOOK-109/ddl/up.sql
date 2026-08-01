ALTER TABLE user_saved_posts
    ADD COLUMN deleted_at TIMESTAMP(6) NULL COMMENT '저장 게시물 삭제 일시' AFTER memo;

ALTER TABLE user_groups
    ADD COLUMN deleted_at TIMESTAMP(6) NULL COMMENT '그룹 삭제 일시' AFTER color;

ALTER TABLE group_posts
    ADD COLUMN deleted_at TIMESTAMP(6) NULL COMMENT '그룹 게시물 연결 삭제 일시' AFTER user_saved_post_id;

CREATE TABLE bright_data_responses
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Bright Data 응답 식별자',
    source_type      VARCHAR(50)  NOT NULL COMMENT '원본 제공자 유형',
    external_post_id VARCHAR(255) NOT NULL COMMENT '제공자 게시물 식별자',
    response_body    LONGTEXT     NOT NULL COMMENT 'Bright Data 성공 응답 원문',
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_source_type_external_post_id (source_type, external_post_id)
) COMMENT 'Bright Data 원본 응답 캐시';

CREATE TABLE media_url_caches
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '미디어 URL 캐시 식별자',
    source_url_hash CHAR(64)      NOT NULL COMMENT '외부 원본 미디어 URL SHA-256',
    source_url      VARCHAR(2048) NOT NULL COMMENT '외부 원본 미디어 URL',
    stored_url      VARCHAR(2048) NOT NULL COMMENT '버킷에 저장된 미디어 공개 URL',
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_source_url_hash (source_url_hash)
) COMMENT '원본 미디어 URL별 저장 결과 캐시';
