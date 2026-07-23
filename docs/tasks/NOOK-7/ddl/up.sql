CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 식별자',
    source_type VARCHAR(50) COLLATE utf8mb4_bin NOT NULL COMMENT '외부 게시물 출처 유형',
    external_post_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '출처가 제공하는 게시물 식별자',
    canonical_url VARCHAR(2048) NOT NULL COMMENT '게시물 정규 URL',
    author_identifier VARCHAR(255) NULL COMMENT '출처 내 작성자 식별 정보',
    title VARCHAR(500) NULL COMMENT '게시물 제목',
    body TEXT NULL COMMENT '게시물 본문',
    published_at DATETIME(6) NULL COMMENT '외부 게시물 발행 일시',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_source_type_external_post_id (source_type, external_post_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '공용 외부 게시물';

CREATE TABLE post_media (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 미디어 식별자',
    post_id BIGINT NOT NULL COMMENT '게시물 식별자',
    media_type VARCHAR(20) NOT NULL COMMENT '미디어 유형',
    media_url VARCHAR(2048) NOT NULL COMMENT '미디어 URL',
    display_order INT NOT NULL COMMENT '게시물 내 노출 순서',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id_display_order (post_id, display_order),
    CONSTRAINT fk_post_media_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT chk_post_media_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '게시물 미디어';

CREATE TABLE places (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '장소 식별자',
    provider VARCHAR(50) COLLATE utf8mb4_bin NOT NULL COMMENT '장소 정보 제공자',
    external_place_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '제공자가 부여한 장소 식별자',
    name VARCHAR(255) NOT NULL COMMENT '장소명',
    category VARCHAR(100) NULL COMMENT '장소 카테고리',
    address VARCHAR(500) NOT NULL COMMENT '장소 주소',
    latitude DECIMAL(10, 7) NOT NULL COMMENT '위도',
    longitude DECIMAL(10, 7) NOT NULL COMMENT '경도',
    phone_number VARCHAR(30) NULL COMMENT '장소 전화번호',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_provider_external_place_id (provider, external_place_id),
    CONSTRAINT chk_places_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_places_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '지도에 표시할 공용 장소';

CREATE TABLE post_places (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 장소 연결 식별자',
    post_id BIGINT NOT NULL COMMENT '게시물 식별자',
    place_id BIGINT NOT NULL COMMENT '장소 식별자',
    display_order INT NOT NULL COMMENT '게시물 내 장소 노출 순서',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_post_id_place_id (post_id, place_id),
    UNIQUE KEY idx_u_post_id_display_order (post_id, display_order),
    KEY idx_place_id (place_id),
    CONSTRAINT fk_post_places_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_places_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT chk_post_places_display_order CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '게시물과 장소 연결';

CREATE TABLE user_saved_posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 저장 게시물 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    post_id BIGINT NOT NULL COMMENT '공용 게시물 식별자',
    memo TEXT NULL COMMENT '사용자별 게시물 메모',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '저장 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_post_id (post_id),
    CONSTRAINT fk_user_saved_posts_post FOREIGN KEY (post_id) REFERENCES posts (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자별 저장 게시물';

CREATE TABLE user_groups (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 그룹 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    name VARCHAR(100) NOT NULL COMMENT '그룹명',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_id_name (user_id, name)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자 게시물 그룹';

CREATE TABLE group_posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '그룹 게시물 연결 식별자',
    group_id BIGINT NOT NULL COMMENT '사용자 그룹 식별자',
    user_saved_post_id BIGINT NOT NULL COMMENT '사용자 저장 게시물 식별자',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_group_id_user_saved_post_id (group_id, user_saved_post_id),
    KEY idx_user_saved_post_id (user_saved_post_id),
    CONSTRAINT fk_group_posts_group FOREIGN KEY (group_id) REFERENCES user_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_posts_user_saved_post
        FOREIGN KEY (user_saved_post_id) REFERENCES user_saved_posts (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '그룹과 사용자 저장 게시물 연결';
