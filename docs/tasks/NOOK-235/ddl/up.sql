CREATE TABLE user_push_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 푸시 토큰 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 식별자',
    token VARCHAR(512) NOT NULL COMMENT 'FCM 등록 토큰',
    platform VARCHAR(20) NOT NULL COMMENT '푸시 토큰 플랫폼',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '푸시 발송 대상 여부',
    last_registered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 토큰 등록 시각',
    last_failed_at TIMESTAMP(6) NULL COMMENT '마지막 발송 실패 시각',
    failure_reason VARCHAR(500) NULL COMMENT '마지막 발송 실패 또는 비활성화 사유',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_token (token),
    KEY idx_user_id (user_id),
    CONSTRAINT chk_user_push_tokens_platform CHECK (platform IN ('IOS', 'ANDROID'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자 기기별 FCM 푸시 토큰';
