ALTER TABLE places
    ADD COLUMN representative_tags JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT 'LLM 근거를 집계한 장소 대표 태그 목록' AFTER photo_urls;

CREATE TABLE post_place_tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 장소 태그 식별자',
    post_id BIGINT NOT NULL COMMENT '태그 근거가 포함된 게시물 식별자',
    place_id BIGINT NOT NULL COMMENT '태그가 설명하는 장소 식별자',
    tag VARCHAR(30) NOT NULL COMMENT '허용된 장소 태그 enum 이름',
    confidence DECIMAL(4, 3) NOT NULL COMMENT 'LLM 태그 신뢰도',
    evidence_source VARCHAR(30) NOT NULL COMMENT '태그 근거 출처',
    evidence_text VARCHAR(500) NOT NULL COMMENT '태그 선택의 실제 근거 문구',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id_place_id_tag (post_id, place_id, tag),
    KEY idx_place_id_tag (place_id, tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시물별 장소 태그 추출 근거';
