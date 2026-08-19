CREATE TABLE external_api_price_policies (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '외부 API 단가 정책 식별자',
    provider VARCHAR(50) NOT NULL COMMENT '외부 API 제공자',
    sku VARCHAR(100) NOT NULL COMMENT '과금 SKU',
    unit_price_krw DECIMAL(19,6) NOT NULL COMMENT '단위 크기당 원화 단가',
    unit_size DECIMAL(19,6) NOT NULL COMMENT '단가가 적용되는 사용량 단위 크기',
    free_monthly_units DECIMAL(19,6) NOT NULL DEFAULT 0 COMMENT '월 무료 사용량',
    source_url VARCHAR(500) NULL COMMENT '공식 가격 출처 URL',
    source_currency CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT '공식 가격 통화',
    source_unit_price DECIMAL(19,6) NOT NULL COMMENT '공식 통화 기준 단가',
    managed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '시스템 관리 기본 단가 여부',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '정책 활성 여부',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_provider_sku (provider, sku),
    CONSTRAINT chk_external_api_price_unit_size CHECK (unit_size > 0),
    CONSTRAINT chk_external_api_unit_price CHECK (unit_price_krw >= 0),
    CONSTRAINT chk_external_api_free_monthly_units CHECK (free_monthly_units >= 0)
) ENGINE=InnoDB COMMENT='외부 API SKU별 원화 단가 정책';

CREATE TABLE external_api_budget_policies (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '외부 API 예산 정책 식별자',
    provider VARCHAR(50) NOT NULL COMMENT '외부 API 제공자',
    monthly_budget_krw DECIMAL(19,2) NOT NULL COMMENT '월간 원화 예산',
    mode VARCHAR(20) NOT NULL COMMENT 'ALERT_ONLY 또는 BLOCK',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '정책 활성 여부',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_provider (provider),
    CONSTRAINT chk_external_api_monthly_budget CHECK (monthly_budget_krw >= 0)
) ENGINE=InnoDB COMMENT='외부 API 제공자별 월간 예산 정책';

CREATE TABLE external_api_usage_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '외부 API 사용 이벤트 식별자',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '중복 기록 방지 키',
    provider VARCHAR(50) NOT NULL COMMENT '외부 API 제공자',
    sku VARCHAR(100) NOT NULL COMMENT '과금 SKU',
    feature VARCHAR(100) NOT NULL COMMENT '호출 기능',
    status VARCHAR(20) NOT NULL COMMENT 'RESERVED, SUCCEEDED 또는 FAILED',
    estimated_units DECIMAL(19,6) NOT NULL COMMENT '예약 시 예상 사용량',
    actual_units DECIMAL(19,6) NULL COMMENT '정산된 실제 사용량',
    estimated_cost_krw DECIMAL(19,6) NOT NULL COMMENT '예약 시 예상 원화 비용',
    actual_cost_krw DECIMAL(19,6) NULL COMMENT '정산된 실제 원화 비용',
    unit_price_krw DECIMAL(19,6) NOT NULL COMMENT '호출 시점 원화 단가 스냅샷',
    price_unit_size DECIMAL(19,6) NOT NULL COMMENT '호출 시점 가격 단위 크기',
    input_tokens BIGINT NULL COMMENT 'OpenAI 입력 토큰 수',
    cached_input_tokens BIGINT NULL COMMENT 'OpenAI 캐시 입력 토큰 수',
    output_tokens BIGINT NULL COMMENT 'OpenAI 출력 토큰 수',
    metadata_json JSON NULL COMMENT '호출 부가 정보',
    failure_code VARCHAR(100) NULL COMMENT '실패 분류 코드',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '호출 예약 시각',
    settled_at TIMESTAMP(6) NULL COMMENT '호출 정산 시각',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_idempotency_key (idempotency_key),
    KEY idx_provider_occurred_at (provider, occurred_at),
    KEY idx_sku_occurred_at (sku, occurred_at)
) ENGINE=InnoDB COMMENT='외부 API 호출별 사용량 및 비용 원장';

CREATE TABLE external_api_budget_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '예산 알림 식별자',
    provider VARCHAR(50) NOT NULL COMMENT '외부 API 제공자',
    budget_month CHAR(7) NOT NULL COMMENT 'UTC 기준 예산 월 YYYY-MM',
    threshold_percent INT NOT NULL COMMENT '도달한 예산 임계치 백분율',
    spent_krw DECIMAL(19,6) NOT NULL COMMENT '알림 시점 누적 예상 비용',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_provider_budget_month_threshold (provider, budget_month, threshold_percent)
) ENGINE=InnoDB COMMENT='외부 API 월간 예산 임계치 알림 이력';
