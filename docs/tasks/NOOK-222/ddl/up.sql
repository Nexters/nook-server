CREATE TABLE place_provider_references (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '장소 제공자 참조 식별자',
    place_id BIGINT NOT NULL COMMENT '공용 장소 식별자',
    provider VARCHAR(50) COLLATE utf8mb4_bin NOT NULL COMMENT '장소 정보 제공자',
    external_place_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '제공자가 부여한 장소 식별자',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_provider_external_place_id (provider, external_place_id),
    KEY idx_place_id (place_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '공용 장소에 연결된 provider별 외부 식별자';

ALTER TABLE places
    ADD INDEX idx_latitude_longitude (latitude, longitude);
