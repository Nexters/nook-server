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
