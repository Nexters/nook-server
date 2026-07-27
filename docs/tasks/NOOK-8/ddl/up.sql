CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 식별자',
    nickname VARCHAR(20) NOT NULL COMMENT '서비스 내 고유 닉네임',
    profile_image_url VARCHAR(2048) NULL COMMENT '프로필 이미지 HTTPS URL',
    status VARCHAR(20) NOT NULL COMMENT '회원 상태',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_nickname (nickname)
) ENGINE = InnoDB COMMENT = '회원';

CREATE TABLE social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '소셜 계정 식별자',
    member_id BIGINT NOT NULL COMMENT '회원 식별자',
    provider VARCHAR(20) NOT NULL COMMENT '소셜 로그인 제공자',
    provider_subject VARCHAR(255) NOT NULL COMMENT '제공자 내 사용자 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY idx_member_id (member_id),
    UNIQUE KEY idx_u_provider_provider_subject (provider, provider_subject),
    CONSTRAINT fk_social_accounts_member_id
        FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB COMMENT = '회원 소셜 계정';

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '리프레시 토큰 식별자',
    member_id BIGINT NOT NULL COMMENT '회원 식별자',
    token_identifier VARCHAR(36) NOT NULL COMMENT 'JWT jti',
    token_hash CHAR(64) NOT NULL COMMENT '리프레시 토큰 SHA-256 해시',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 일시',
    revoked_at DATETIME(6) NULL COMMENT '폐기 일시',
    replaced_by_token_id BIGINT NULL COMMENT '교체한 리프레시 토큰 식별자',
    created_at DATETIME(6) NOT NULL COMMENT '생성 일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY idx_member_id (member_id),
    UNIQUE KEY idx_u_token_identifier (token_identifier),
    UNIQUE KEY idx_u_token_hash (token_hash),
    CONSTRAINT fk_refresh_tokens_member_id
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_refresh_tokens_replaced_by_token_id
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB COMMENT = '로그인 리프레시 토큰';
