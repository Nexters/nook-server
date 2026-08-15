-- NOOK-178 후속 롤백: up-2.sql 을 되돌린다.
-- 게시물별로 나뉘어 있던 메모는 복원되지 않는다. 사용자·장소 단위 메모가
-- 그 사용자의 해당 장소를 가진 모든 저장 게시물로 복제된다.

CREATE TABLE user_saved_post_place_memos (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 저장 게시물 장소 메모 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    user_saved_post_id BIGINT NOT NULL COMMENT '사용자 저장 게시물 식별자',
    place_id BIGINT NOT NULL COMMENT '장소 식별자',
    memo TEXT NOT NULL COMMENT '사용자가 작성한 저장 게시물 장소 메모',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_saved_post_id_place_id (user_saved_post_id, place_id),
    KEY idx_user_id (user_id),
    KEY idx_place_id (place_id)
) ENGINE = InnoDB
  COMMENT = '사용자 저장 게시물 장소별 메모';

INSERT IGNORE INTO user_saved_post_place_memos (
    user_id,
    user_saved_post_id,
    place_id,
    memo,
    created_at,
    updated_at
)
SELECT
    b.user_id,
    s.id,
    b.place_id,
    b.memo,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM user_place_bookmarks b
JOIN user_saved_posts s ON s.user_id = b.user_id AND s.deleted_at IS NULL
JOIN post_places pp ON pp.post_id = s.post_id AND pp.place_id = b.place_id
WHERE b.memo IS NOT NULL;

ALTER TABLE user_place_bookmarks DROP COLUMN memo;
