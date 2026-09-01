CREATE TABLE external_provider_usage_limits
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '외부 API 상한 정책 식별자',
    provider            VARCHAR(50)    NOT NULL COMMENT '외부 Provider 코드',
    sku                 VARCHAR(100)   NOT NULL COMMENT '과금 SKU 코드',
    limit_type          VARCHAR(20)    NOT NULL COMMENT 'CALLS 또는 COST_USD',
    monthly_limit       DECIMAL(19, 6) NOT NULL COMMENT '월간 호출량 또는 USD 비용 상한',
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '상한 및 알림 활성 여부',
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider_sku_limit_type (provider, sku, limit_type),
    INDEX idx_enabled (enabled),
    CONSTRAINT chk_external_provider_usage_limit CHECK (monthly_limit > 0),
    CONSTRAINT chk_external_provider_usage_limit_type CHECK (limit_type IN ('CALLS', 'COST_USD'))
) ENGINE = InnoDB COMMENT = '외부 API SKU별 월간 사용 상한 및 알림 정책';

CREATE TABLE external_provider_usage_limit_notifications
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '상한 단계 알림 발송 이력 식별자',
    limit_policy_id     BIGINT       NOT NULL COMMENT '외부 API 상한 정책 식별자',
    period_start        DATE         NOT NULL COMMENT '월간 집계 시작일',
    threshold_percent   INT          NOT NULL COMMENT '50, 80, 95 또는 100 임계치',
    notified_at         TIMESTAMP(6) NOT NULL COMMENT 'Slack 발송 성공 시각',
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_limit_policy_id_period_start_threshold_percent
        (limit_policy_id, period_start, threshold_percent),
    INDEX idx_period_start (period_start),
    CONSTRAINT chk_external_provider_usage_limit_threshold
        CHECK (threshold_percent IN (50, 80, 95, 100))
) ENGINE = InnoDB COMMENT = '외부 API 월간 상한 단계별 Slack 알림 성공 이력';

CREATE TABLE external_provider_billing_snapshots
(
    id                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '빌링 스냅샷 식별자',
    provider          VARCHAR(50)     NOT NULL COMMENT '외부 공급자 코드',
    sku               VARCHAR(100)    NOT NULL COMMENT '공급자 과금 SKU 또는 Actor 코드',
    period_start      DATE            NOT NULL COMMENT '집계 기간 시작일',
    period_end        DATE            NOT NULL COMMENT '집계 기간 종료일(미포함)',
    usage_units       DECIMAL(20, 6)  NOT NULL COMMENT '공급자 기준 사용량',
    cost_usd          DECIMAL(20, 8)  NOT NULL COMMENT '공급자 기준 실제 비용(USD)',
    source            VARCHAR(100)    NOT NULL COMMENT '공식 빌링 데이터 출처',
    source_updated_at TIMESTAMP(6)    NOT NULL COMMENT '공급자 데이터 관측 시각',
    created_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider_sku_period_start_period_end (provider, sku, period_start, period_end),
    INDEX idx_period_start_period_end (period_start, period_end),
    CONSTRAINT chk_external_provider_billing_snapshot_period CHECK (period_start < period_end),
    CONSTRAINT chk_external_provider_billing_snapshot_usage CHECK (usage_units >= 0),
    CONSTRAINT chk_external_provider_billing_snapshot_cost CHECK (cost_usd >= 0)
) ENGINE = InnoDB COMMENT = '외부 공급자 공식 빌링 월간 스냅샷';

CREATE TABLE external_provider_billing_sync_states
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '동기화 상태 식별자',
    provider          VARCHAR(50)  NOT NULL COMMENT '외부 공급자 코드',
    status            VARCHAR(20)  NOT NULL COMMENT '동기화 상태',
    last_attempted_at TIMESTAMP(6) NULL COMMENT '마지막 동기화 시도 시각',
    last_succeeded_at TIMESTAMP(6) NULL COMMENT '마지막 동기화 성공 시각',
    error_message     VARCHAR(500) NULL COMMENT '최근 동기화 오류 요약',
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider (provider),
    CONSTRAINT chk_external_provider_billing_sync_status
        CHECK (status IN ('NEVER_SYNCED', 'SUCCEEDED', 'FAILED', 'DISABLED'))
) ENGINE = InnoDB COMMENT = '외부 공급자 빌링 동기화 상태';

INSERT INTO external_provider_billing_sync_states (provider, status, error_message)
VALUES ('APIFY_BILLING', 'NEVER_SYNCED', NULL),
       ('OPENAI_BILLING', 'DISABLED', 'OPENAI_ADMIN_KEY 설정 후 연동 가능'),
       ('BRIGHT_DATA_BILLING', 'DISABLED', 'Finance 또는 Admin API 키 설정 후 연동 가능'),
       ('GOOGLE_CLOUD_BILLING', 'DISABLED', 'Cloud Billing BigQuery Export 설정 후 연동 가능'),
       ('NAVER_CLOUD_BILLING', 'DISABLED', 'NAVER Cloud Billing API 인증 설정 후 연동 가능'),
       ('KAKAO_BILLING', 'DISABLED', '공개 빌링 조회 API 미지원'),
       ('COREPIN_BILLING', 'DISABLED', '공개 빌링 조회 API 미지원');
