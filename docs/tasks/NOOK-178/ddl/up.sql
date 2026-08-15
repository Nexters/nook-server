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

-- 기존 게시물 메모를 해당 게시물에 연결된 모든 장소의 장소 메모로 복사한다.
-- 장소 상세는 더 이상 게시물 메모로 폴백하지 않으므로, 이 백필이 없으면
-- 기존 메모가 장소 화면에서 사라진다.
-- 삭제된 저장 게시물(deleted_at)은 제외하고, INSERT IGNORE 로 재실행에 안전하게 둔다.
INSERT IGNORE INTO user_saved_post_place_memos (
    user_id,
    user_saved_post_id,
    place_id,
    memo,
    created_at,
    updated_at
)
SELECT
    s.user_id,
    s.id,
    pp.place_id,
    s.memo,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM user_saved_posts s
JOIN post_places pp ON pp.post_id = s.post_id
WHERE s.memo IS NOT NULL
  AND s.memo <> ''
  AND s.deleted_at IS NULL;
