CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '관리자 감사 로그 식별자',
    actor_subject VARCHAR(255) NOT NULL COMMENT 'Cloudflare Access 사용자 고유 식별자',
    actor_email VARCHAR(320) NOT NULL COMMENT 'Cloudflare Access에서 검증한 운영자 이메일',
    action VARCHAR(100) NOT NULL COMMENT '관리자 수행 동작',
    target_type VARCHAR(100) NOT NULL COMMENT '변경 대상 유형',
    target_id VARCHAR(255) NOT NULL COMMENT '변경 대상 식별자',
    reason VARCHAR(500) NOT NULL COMMENT '운영자가 입력한 변경 사유',
    before_value JSON NULL COMMENT '변경 전 값',
    after_value JSON NULL COMMENT '변경 후 값',
    request_id VARCHAR(100) NULL COMMENT '변경 요청 추적 식별자',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    INDEX idx_target_type_target_id_created_at (target_type, target_id, created_at),
    INDEX idx_actor_email_created_at (actor_email, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관리자 변경 감사 로그';

CREATE TABLE post_place_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시글 장소 검수 식별자',
    post_id BIGINT NOT NULL COMMENT '검수된 게시글 식별자',
    reviewer_subject VARCHAR(255) NOT NULL COMMENT '마지막 검수 운영자의 Cloudflare Access 식별자',
    reviewer_email VARCHAR(320) NOT NULL COMMENT '마지막 검수 운영자 이메일',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_post_id (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관리자 검수가 완료된 게시글 장소 매핑';
