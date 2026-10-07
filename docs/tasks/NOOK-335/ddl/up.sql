CREATE TABLE user_push_preferences
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 푸시 알림 설정 식별자',
    user_id                 BIGINT       NOT NULL COMMENT '사용자 식별자',
    post_processing_enabled BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '게시물 저장 처리 결과 푸시 알림 수신 여부',
    created_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_id (user_id)
) ENGINE = InnoDB
  COMMENT = '사용자별 푸시 알림 설정';
