CREATE TABLE user_place_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 장소 북마크 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    place_id BIGINT NOT NULL COMMENT '장소 식별자',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_id_place_id (user_id, place_id),
    KEY idx_place_id (place_id),
    CONSTRAINT fk_user_place_bookmarks_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_place_bookmarks_user_id CHECK (user_id > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자별 지도 장소 북마크';

INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, created_at, updated_at)
SELECT DISTINCT
    user_saved_posts.user_id,
    post_places.place_id,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM user_saved_posts
INNER JOIN post_places
    ON post_places.post_id = user_saved_posts.post_id
WHERE post_places.bookmarked = TRUE;
