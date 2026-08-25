CREATE TABLE external_provider_price_policies
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '외부 Provider 단가 정책 식별자',
    provider            VARCHAR(50)    NOT NULL COMMENT '외부 Provider 코드',
    sku                 VARCHAR(100)   NOT NULL COMMENT '과금 SKU 코드',
    unit_type           VARCHAR(40)    NOT NULL COMMENT 'CALL, RECORD, IMAGE, INPUT_TOKEN 등 사용량 단위',
    source_currency     CHAR(3)        NOT NULL COMMENT '공식 단가 통화',
    source_unit_price   DECIMAL(19, 8) NOT NULL COMMENT '공식 통화 기준 단가',
    unit_size           DECIMAL(19, 6) NOT NULL COMMENT '단가가 적용되는 사용량 크기',
    free_monthly_units  DECIMAL(19, 6) NOT NULL DEFAULT 0 COMMENT '월 무료 사용량',
    source_url          VARCHAR(500)   NULL COMMENT '공식 가격 출처 URL',
    pricing_status      VARCHAR(20)    NOT NULL COMMENT 'PRICED, UNPRICED 또는 QUOTA_ONLY',
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '정책 활성 여부',
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider_sku_unit_type (provider, sku, unit_type),
    CONSTRAINT chk_external_provider_price_unit_size CHECK (unit_size > 0),
    CONSTRAINT chk_external_provider_source_unit_price CHECK (source_unit_price >= 0),
    CONSTRAINT chk_external_provider_free_monthly_units CHECK (free_monthly_units >= 0)
) ENGINE = InnoDB COMMENT = '외부 Provider SKU 단가 정책';

CREATE TABLE external_provider_budget_policies
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '외부 Provider 예산 정책 식별자',
    provider            VARCHAR(50)    NOT NULL COMMENT '외부 Provider 코드',
    monthly_budget_krw  DECIMAL(19, 2) NOT NULL COMMENT '월간 예상 원화 예산',
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '정책 활성 여부',
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider (provider),
    CONSTRAINT chk_external_provider_monthly_budget CHECK (monthly_budget_krw >= 0)
) ENGINE = InnoDB COMMENT = '외부 Provider 월간 예산 정책';

CREATE TABLE external_provider_usage_events
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '외부 Provider 요청 이벤트 식별자',
    invocation_key      VARCHAR(120)   NOT NULL COMMENT '중복 기록 방지용 물리 요청 식별자',
    operation_id        VARCHAR(120)   NULL COMMENT 'fallback과 retry를 묶는 논리 작업 식별자',
    provider            VARCHAR(50)    NOT NULL COMMENT '외부 Provider 코드',
    operation           VARCHAR(100)   NOT NULL COMMENT '외부 API operation',
    sku                 VARCHAR(100)   NOT NULL COMMENT '과금 SKU 코드',
    unit_type           VARCHAR(40)    NOT NULL COMMENT '기본 사용량 단위',
    units               DECIMAL(19, 6) NOT NULL COMMENT '실제 사용량',
    status              VARCHAR(20)    NOT NULL COMMENT 'SUCCEEDED 또는 FAILED',
    runtime             VARCHAR(20)    NOT NULL COMMENT 'API, WORKER 또는 BATCH',
    flow                VARCHAR(50)    NULL COMMENT 'content, place, place-thumbnail 등 업무 흐름',
    stage               VARCHAR(100)   NULL COMMENT '업무 처리 단계',
    duration_ms         BIGINT         NOT NULL COMMENT '외부 요청 소요 시간(ms)',
    http_status         INT            NULL COMMENT '외부 응답 HTTP 상태 코드',
    failure_code        VARCHAR(100)   NULL COMMENT '실패 분류 코드',
    input_tokens        BIGINT         NULL COMMENT 'OpenAI 입력 token 수',
    cached_input_tokens BIGINT         NULL COMMENT 'OpenAI 캐시 입력 token 수',
    output_tokens       BIGINT         NULL COMMENT 'OpenAI 출력 token 수',
    source_currency     CHAR(3)        NULL COMMENT '호출 당시 가격 통화',
    source_unit_price   DECIMAL(19, 8) NULL COMMENT '호출 당시 공식 단가 snapshot',
    price_unit_size     DECIMAL(19, 6) NULL COMMENT '호출 당시 가격 단위 크기',
    exchange_rate_krw   DECIMAL(19, 6) NULL COMMENT '호출 당시 원화 환율',
    estimated_cost_krw  DECIMAL(19, 6) NULL COMMENT '호출 당시 예상 원화 비용',
    pricing_status      VARCHAR(20)    NOT NULL COMMENT 'PRICED, UNPRICED 또는 QUOTA_ONLY',
    request_id          VARCHAR(100)   NULL COMMENT '요청 추적 식별자',
    post_id             BIGINT         NULL COMMENT '관련 게시물 식별자',
    occurred_at         TIMESTAMP(6)   NOT NULL COMMENT '외부 요청 시각',
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_invocation_key (invocation_key),
    INDEX idx_provider_occurred_at (provider, occurred_at),
    INDEX idx_sku_occurred_at (sku, occurred_at),
    INDEX idx_status_occurred_at (status, occurred_at),
    INDEX idx_runtime_occurred_at (runtime, occurred_at),
    INDEX idx_pricing_status_occurred_at (pricing_status, occurred_at),
    INDEX idx_post_id (post_id)
) ENGINE = InnoDB COMMENT = '외부 Provider 물리 요청 사용량 및 예상 비용 원장';

INSERT INTO external_provider_price_policies
    (provider, sku, unit_type, source_currency, source_unit_price, unit_size, free_monthly_units,
     source_url, pricing_status, enabled)
VALUES
    ('OPENAI', 'GPT_5_NANO_INPUT', 'INPUT_TOKEN', 'USD', 0.05, 1000000, 0,
     'https://developers.openai.com/api/docs/models/gpt-5-nano', 'PRICED', TRUE),
    ('OPENAI', 'GPT_5_NANO_CACHED_INPUT', 'CACHED_INPUT_TOKEN', 'USD', 0.005, 1000000, 0,
     'https://developers.openai.com/api/docs/models/gpt-5-nano', 'PRICED', TRUE),
    ('OPENAI', 'GPT_5_NANO_OUTPUT', 'OUTPUT_TOKEN', 'USD', 0.40, 1000000, 0,
     'https://developers.openai.com/api/docs/models/gpt-5-nano', 'PRICED', TRUE),
    ('GOOGLE_VISION', 'TEXT_DETECTION', 'IMAGE', 'USD', 1.50, 1000, 1000,
     'https://cloud.google.com/vision/pricing', 'PRICED', TRUE),
    ('GOOGLE_PLACES', 'NEARBY_SEARCH_PRO', 'CALL', 'USD', 32.00, 1000, 5000,
     'https://developers.google.com/maps/billing-and-pricing/pricing', 'PRICED', TRUE),
    ('GOOGLE_PLACES', 'TEXT_SEARCH_PRO', 'CALL', 'USD', 32.00, 1000, 5000,
     'https://developers.google.com/maps/billing-and-pricing/pricing', 'PRICED', TRUE),
    ('GOOGLE_PLACES', 'PLACE_DETAILS_PRO', 'CALL', 'USD', 17.00, 1000, 5000,
     'https://developers.google.com/maps/billing-and-pricing/pricing', 'PRICED', TRUE),
    ('GOOGLE_PLACES', 'PLACE_DETAILS_PHOTOS', 'CALL', 'USD', 7.00, 1000, 1000,
     'https://developers.google.com/maps/billing-and-pricing/pricing', 'PRICED', TRUE),
    ('BRIGHT_DATA', 'WEB_SCRAPER_SUCCESS_RECORD', 'RECORD', 'USD', 1.50, 1000, 5000,
     'https://brightdata.com/pricing/web-scraper', 'PRICED', TRUE),
    ('APIFY', 'INSTAGRAM_SCRAPER', 'CALL', 'USD', 2.30, 1000, 0,
     'https://apify.com/pricing', 'PRICED', TRUE),
    ('APIFY_GOOGLE_MAPS', 'GOOGLE_MAPS_SCRAPER', 'CALL', 'USD', 3.00, 1000, 0,
     'https://apify.com/compass/crawler-google-places', 'PRICED', TRUE),
    ('APIFY_NAVER_PLACE', 'NAVER_MAP_SEARCH_RESULTS_SCRAPER', 'CALL', 'USD', 1.50, 1000, 0,
     'https://apify.com/delicious_zebu/naver-map-search-results-scraper', 'PRICED', TRUE),
    ('APIFY_NAVER_PLACE', 'NAVER_PLACE_PHOTO_SCRAPER', 'CALL', 'USD', 0.50, 1000, 0,
     'https://apify.com/oxygenated_quagmire/naver-place-photos', 'PRICED', TRUE);
