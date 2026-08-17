CREATE TABLE IF NOT EXISTS group_share_links (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '그룹 공유 링크 식별자',
    group_id BIGINT NOT NULL COMMENT '공유 대상 그룹 식별자',
    token_hash CHAR(64) NOT NULL COMMENT '공유 토큰 SHA-256 해시',
    token_value VARCHAR(128) NOT NULL COMMENT '활성 링크 재응답용 URL-safe 공유 토큰',
    expires_at TIMESTAMP(6) NULL COMMENT '공유 링크 만료 시각, NULL이면 무기한',
    revoked_at TIMESTAMP(6) NULL COMMENT '공유 링크 폐기 시각',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_token_hash (token_hash),
    KEY idx_group_id_revoked_at (group_id, revoked_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '그룹 공개 공유 링크';

CREATE TABLE IF NOT EXISTS shared_group_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '공유 그룹 구독 식별자',
    member_id BIGINT NOT NULL COMMENT '공유 그룹을 아카이브에 추가한 회원 식별자',
    share_link_id BIGINT NOT NULL COMMENT '접근 권한의 근거가 되는 공유 링크 식별자',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_member_id_share_link_id (member_id, share_link_id),
    KEY idx_share_link_id (share_link_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '회원의 공유 그룹 구독';
