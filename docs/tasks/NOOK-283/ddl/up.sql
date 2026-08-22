CREATE TABLE app_version_policies (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '앱 버전 정책 식별자',
    platform VARCHAR(20) NOT NULL COMMENT '앱 플랫폼',
    minimum_supported_build_number BIGINT NOT NULL COMMENT '최소 지원 빌드 번호',
    latest_build_number BIGINT NOT NULL COMMENT '최신 빌드 번호',
    latest_version VARCHAR(30) NOT NULL COMMENT '사용자 표시용 최신 앱 버전',
    store_url VARCHAR(500) NOT NULL COMMENT '플랫폼 앱 스토어 URL',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_platform (platform),
    CONSTRAINT chk_app_version_policies_platform CHECK (platform IN ('IOS', 'ANDROID')),
    CONSTRAINT chk_app_version_policies_minimum_build CHECK (minimum_supported_build_number >= 0),
    CONSTRAINT chk_app_version_policies_latest_build CHECK (latest_build_number >= minimum_supported_build_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '플랫폼별 앱 업데이트 정책';
