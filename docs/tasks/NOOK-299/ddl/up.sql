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
    threshold_percent   SMALLINT     NOT NULL COMMENT '50, 80, 95 또는 100 임계치',
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

