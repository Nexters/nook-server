CREATE TABLE user_saved_post_places (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 저장 게시물 장소 연결 식별자',
    user_saved_post_id BIGINT NOT NULL COMMENT '사용자 저장 게시물 식별자',
    place_id BIGINT NOT NULL COMMENT '장소 식별자',
    display_order INT NOT NULL COMMENT '사용자 저장 게시물 내 장소 노출 순서',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_saved_post_id_place_id (user_saved_post_id, place_id),
    UNIQUE KEY idx_u_user_saved_post_id_display_order (user_saved_post_id, display_order),
    KEY idx_place_id (place_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자 저장 게시물별 장소 연결';

INSERT INTO user_saved_post_places (
    user_saved_post_id,
    place_id,
    display_order,
    created_at,
    updated_at
)
SELECT
    saved_post.id,
    post_place.place_id,
    post_place.display_order,
    GREATEST(saved_post.created_at, post_place.created_at),
    GREATEST(saved_post.updated_at, post_place.updated_at)
FROM user_saved_posts saved_post
INNER JOIN post_places post_place ON post_place.post_id = saved_post.post_id;
